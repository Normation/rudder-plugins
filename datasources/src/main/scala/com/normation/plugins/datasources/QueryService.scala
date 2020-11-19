/*
*************************************************************************************
* Copyright 2016 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.plugins.datasources

import cats.data.NonEmptyList
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.nodes.CompareProperties
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.nodes.NodeState
import com.normation.rudder.domain.policies.GlobalPolicyMode
import com.normation.rudder.repository.RoParameterRepository
import com.normation.rudder.repository.WoNodeRepository
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.policies.InterpolatedValueCompiler
import com.normation.errors._
import com.normation.rudder.domain.parameters.GlobalParameter
import zio._
import zio.clock.Clock
import zio.syntax._
import zio.duration.DurationOps

/*
 * This file contain the hight level logic to update
 * datasources by name:
 * - get the datasource by name,
 * - get the list of nodes to udpate and there context,
 * - update all nodes
 * - (event log are generated because we are just changing node properties,
 *   so same behaviour)
 * - trigger a regeneration if there was at least one change
 */
trait QueryDataSourceService {
  /**
   * Here, we query the provided datasource and update
   * all the node with the correct logic.
   *
   * An other service is in charge to retrieve the datasource by
   * name, and correctly handle the case where the datasource was
   * deleted.
   */
  def queryAll(datasource: DataSource, cause: UpdateCause): IOResult[Set[NodeUpdateResult]]

  /**
   * A version that use provided nodeinfo / parameters to only query a subpart of nodes
   */
  def querySubset(datasource: DataSource, info: PartialNodeUpdate, cause: UpdateCause): IOResult[Set[NodeUpdateResult]]

  /**
   * A version that only query one node - do not use if you want to query several nodes
   */
  def queryOne(datasource: DataSource, nodeId: NodeId, cause: UpdateCause): IOResult[NodeUpdateResult]
}

/**
 * The implement also takes a "post update" hooks, which takes as arg the set of
 * actually updated (== property's value actually changed) nodes
 */
class HttpQueryDataSourceService(
    nodeInfo        : NodeInfoService
  , parameterRepo   : RoParameterRepository
  , nodeRepository  : WoNodeRepository
  , interpolCompiler: InterpolatedValueCompiler
  , onUpdatedHook   : (Set[NodeId], UpdateCause) => IOResult[Unit]
  , globalPolicyMode: () => IOResult[GlobalPolicyMode]
  , clock           : Clock
) extends QueryDataSourceService {

  val getHttp = new GetDataset(interpolCompiler)

  /*
   * We need a scheduler tailored for I/O, we are mostly doing http requests and
   * database things here
   */

  override def queryAll(datasource: DataSource, cause: UpdateCause): IOResult[Set[NodeUpdateResult]] = {
    query[Set[NodeUpdateResult]]("fetch data for all node", datasource, cause
        , (d:DataSourceType.HTTP) => queryAllByNode(datasource.id, d, globalPolicyMode, cause)
        , (d:DataSourceType.HTTP) => queryAllByNode(datasource.id, d, globalPolicyMode, cause)
        , s"All nodes data updated from data source '${datasource.name.value}' (${datasource.id.value})"
        , s"Error when fetching data from data source '${datasource.name.value}' (${datasource.id.value}) for all nodes"
    )
  }

  override def querySubset(datasource: DataSource, info: PartialNodeUpdate, cause: UpdateCause): IOResult[Set[NodeUpdateResult]] = {
    query[Set[NodeUpdateResult]](s"fetch data for a set of ${info.nodes.size}", datasource, cause
        , (d:DataSourceType.HTTP) => querySubsetByNode(datasource.id, d, globalPolicyMode, info, cause, onUpdatedHook)
        , (d:DataSourceType.HTTP) => querySubsetByNode(datasource.id, d, globalPolicyMode, info, cause, onUpdatedHook)
        , s"Requested nodes data updated from data source '${datasource.name.value}' (${datasource.id.value})"
        , s"Error when fetching data from data source '${datasource.name.value}' (${datasource.id.value}) for requested nodes"
    )
  }

  override def queryOne(datasource: DataSource, nodeId: NodeId, cause: UpdateCause): IOResult[NodeUpdateResult] = {
    query[NodeUpdateResult](s"fetch data for node '${nodeId.value}'", datasource, cause
        , (d:DataSourceType.HTTP) => queryNodeByNode(datasource.id, d, globalPolicyMode, nodeId, cause)
        , (d:DataSourceType.HTTP) => queryNodeByNode(datasource.id, d, globalPolicyMode, nodeId, cause)
        , s"Data for node '${nodeId.value}' updated from data source '${datasource.name.value}' (${datasource.id.value})"
        , s"Error when fetching data from data source '${datasource.name.value}' (${datasource.id.value}) for node '${nodeId.value}'"
    )
  }

  private[this] def query[T](
      actionName: String
    , datasource: DataSource
    , cause     : UpdateCause
    , oneByOne  : DataSourceType.HTTP => IOResult[T]
    , allInOne  : DataSourceType.HTTP => IOResult[T]
    , successMsg: String
    , errorMsg  : String
  ): IOResult[T] = {
    // We need to special case by type of datasource
    (for {
      res <- (datasource.sourceType match {
                case t:DataSourceType.HTTP =>
                  (t.requestMode match {
                    case HttpRequestMode.OneRequestByNode         => oneByOne
                    case HttpRequestMode.OneRequestAllNodes(_, _) => allInOne
                  })(t)
              }).timed
      _   <- DataSourceLoggerPure.Timing.debug(s"[${res._1.toMillis} ms] '${actionName}' for data source '${datasource.name.value}' (${datasource.id.value})")
    } yield res._2).foldM(
        err => DataSourceLoggerPure.error(Chained(errorMsg, err).fullMsg) *> err.fail
      , ok  => DataSourceLoggerPure.trace(successMsg) *> ok.succeed
    ).provide(clock)
  }

  private[this] def buildOneNodeTask(
      datasourceId: DataSourceId
    , datasource      : DataSourceType.HTTP
    , nodeInfo        : NodeInfo
    , policyServers   : Map[NodeId, NodeInfo]
    , globalPolicyMode: GlobalPolicyMode
    , parameters      : Set[GlobalParameter]
    , cause           : UpdateCause
  ): IOResult[NodeUpdateResult] = {
    (for {
      policyServer <- (policyServers.get(nodeInfo.policyServerId) match {
                        case None    => Inconsistency(s"PolicyServer with ID '${nodeInfo.policyServerId.value}' was not found for node '${nodeInfo.hostname}' ('${nodeInfo.id.value}'). Abort.").fail
                        case Some(p) => p.succeed
                      })
                      //connection timeout: 5s ; getdata timeout: freq ?
      optProperty  <- getHttp.getNode(datasourceId, datasource, nodeInfo, policyServer, globalPolicyMode, parameters, datasource.requestTimeOut, datasource.requestTimeOut)
      nodeResult   <- optProperty match {
                        //on none, don't update anything, the life is wonderful (because 'none' means 'don't update')
                        case None           => NodeUpdateResult.Unchanged(nodeInfo.id).succeed
                        case Some(property) =>
                          // look for the property value in the node to know if an update is needed.
                          // we only care about value here (not provider or other meta-info)
                          nodeInfo.properties.find(_.name == property.name).map(_.value) match {
                            case Some(value) if(value == property.value) => NodeUpdateResult.Unchanged(nodeInfo.id).succeed
                            case _                                       =>
                              for {
                                newProps     <- CompareProperties.updateProperties(nodeInfo.properties, Some(property :: Nil)).toIO
                                newNode      =  nodeInfo.node.copy(properties = newProps)
                                nodeUpdated  <- nodeRepository.updateNode(newNode, cause.modId, cause.actor, cause.reason).chainError(
                                                  s"Cannot save value for node '${nodeInfo.id.value}' for property '${property.name}'"
                                                )
                              } yield {
                                NodeUpdateResult.Updated(nodeUpdated.id)
                              }
                          }
                      }
    } yield {
      nodeResult
    }).chainError(s"Error when getting data from datasource '${datasourceId.value}' for node ${nodeInfo.hostname} (${nodeInfo.id.value}):")
  }

  def querySubsetByNode(
      datasourceId    : DataSourceId
    , datasource      : DataSourceType.HTTP
    , globalPolicyMode: () => IOResult[GlobalPolicyMode]
    , info            : PartialNodeUpdate
    , cause           : UpdateCause
    , onUpdatedHook   : (Set[NodeId], UpdateCause) => IOResult[Unit]
  ): IOResult[Set[NodeUpdateResult]] = {

    def tasks(nodes: Map[NodeId, NodeInfo], policyServers: Map[NodeId, NodeInfo], globalPolicyMode: GlobalPolicyMode, parameters: Set[GlobalParameter]): IOResult[List[Either[RudderError, NodeUpdateResult]]] = {

      /*
       * Here, we are executing all the task (one by node) in parallel. We want to limit the number of
       * parallel task, especially to limit the number of output request. It is not very fair to
       * do hundreds of simultaneous requests to the output server (and that make tests on macos
       * fail, see: http://www.rudder-project.org/redmine/issues/10341)
       */
      ZIO.foreachParN(datasource.maxParallelRequest)( nodes.values.toList) { nodeInfo =>
        buildOneNodeTask(datasourceId, datasource, nodeInfo, policyServers, globalPolicyMode, parameters, cause).either
      }
    }

    // transfor a List[Either[RudderError, A]] into a ZIO[R, Accumulated, Set[A]]
    def accumulateErrors(inputs: List[Either[RudderError, NodeUpdateResult]]): ZIO[Any, Accumulated[RudderError], Set[NodeUpdateResult]] = {
      val (errors, success) = inputs.foldLeft((List.empty[RudderError], Set.empty[NodeUpdateResult])) { case ((errors, success), current) => current match {
        case Right(s) => (errors, success + s)
        case Left(e)  => (e :: errors, success)
      }}

      errors match {
        case Nil       => success.succeed
        case h :: tail => Accumulated(NonEmptyList(h, tail)).fail
      }
    }

    // give a timeout for the whole tasks sufficiently large, but that won't overlap too much on following runs
    val timeout = datasource.requestTimeOut

    // filter out nodes with "disabled" state
    val nodes = info.nodes.filter { case (k, v) => v.state != NodeState.Ignored }

    for {
      mode          <- globalPolicyMode()
      updated       <- tasks(nodes, info.policyServers, mode, info.parameters).timeout(timeout).provide(clock).notOptional(
                         s"Timout error after ${timeout.asScala.toString()}"
                       )
                       // execute hooks
      _             <- onUpdatedHook(updated.collect { case Right(NodeUpdateResult.Updated(id)) => id }.toSet, cause)
      gatherErrors  <- accumulateErrors(updated)
    } yield {
      gatherErrors
    }
  }

  def queryAllByNode(datasourceId: DataSourceId, datasource: DataSourceType.HTTP, globalPolicyMode: () => IOResult[GlobalPolicyMode], cause: UpdateCause): IOResult[Set[NodeUpdateResult]] = {
    for {
      nodes         <- nodeInfo.getAll().toIO
      policyServers  = nodes.filter { case (_, n) => n.isPolicyServer }
      parameters    <- parameterRepo.getAllGlobalParameters().map( _.toSet )
      updated       <- querySubsetByNode(datasourceId, datasource, globalPolicyMode, PartialNodeUpdate(nodes, policyServers, parameters), cause, onUpdatedHook)
    } yield {
      updated
    }
  }

  def queryNodeByNode(datasourceId: DataSourceId, datasource: DataSourceType.HTTP, globalPolicyMode: () => IOResult[GlobalPolicyMode], nodeId: NodeId, cause: UpdateCause): IOResult[NodeUpdateResult] = {
    for {
      mode          <- globalPolicyMode()
      allNodes      <- nodeInfo.getAll().toIO
      node          <- allNodes.get(nodeId).notOptional(s"The node with id '${nodeId.value}' was not found")
      policyServers =  allNodes.filter( _._1 == node.policyServerId)
      parameters    <- parameterRepo.getAllGlobalParameters().map( _.toSet )
      updated       <- buildOneNodeTask(datasourceId, datasource, node, policyServers, mode, parameters, cause)
                         .timeout(datasource.requestTimeOut).provide(clock).notOptional(s"Timeout error after ${datasource.requestTimeOut.asScala.toString()} for update of datasource '${datasourceId.value}'")
                       //post update hooks
      _             <- updated match {
                         case NodeUpdateResult.Updated(id) => onUpdatedHook(Set(id), cause)
                         case _                            => onUpdatedHook(Set()  , cause)
                       }
    } yield {
      updated
    }
  }

}
