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

package com.normation.plugins.datasources.api

import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.plugins.datasources.DataSource
import com.normation.plugins.datasources.DataSourceId
import com.normation.plugins.datasources.DataSourceJsonSerializer
import com.normation.plugins.datasources.DataSourceName
import com.normation.plugins.datasources.DataSourceRunParameters
import com.normation.plugins.datasources.DataSourceSchedule
import com.normation.plugins.datasources.DataSourceType
import com.normation.plugins.datasources.HttpMethod
import com.normation.plugins.datasources.HttpRequestMode
import com.normation.plugins.datasources.MissingNodeBehavior
import com.normation.rudder.rest._
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.apidata.RestDataSerializer
import com.normation.rudder.rest.RestUtils._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.utils.StringUuidGenerator
import net.liftweb.common.Box
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JString
import com.normation.rudder.repository.WoNodeRepository
import com.normation.plugins.datasources.DataSourceRepository
import com.normation.plugins.datasources.DataSourceUpdateCallbacks
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.eventlog.ModificationId
import com.normation.plugins.datasources.UpdateCause
import com.normation.plugins.datasources.NodeUpdateResult
import com.normation.utils.Control
import com.normation.rudder.domain.nodes.Node
import com.normation.rudder.domain.properties.CompareProperties
import net.liftweb.common.Failure
import com.normation.plugins.datasources.api.{DataSourceApi => API}
import com.normation.box._
import com.normation.rudder.domain.properties.GenericProperty._
import net.liftweb.json.JsonAST.JArray
import zio.duration.Duration
import com.normation.zio._

class DataSourceApiImpl (
    extractor         : RestExtractorService
  , restDataSerializer: RestDataSerializer
  , dataSourceRepo    : DataSourceRepository with DataSourceUpdateCallbacks
  , nodeInfoService   : NodeInfoService
  , nodeRepos         : WoNodeRepository
  , uuidGen           : StringUuidGenerator
) extends LiftApiModuleProvider[API] {
  api =>

  val kind = "datasources"

  def schemas = API

  def response ( function : Box[JValue], req : Req, errorMessage : String, id : Option[String])(implicit action : String) : LiftResponse = {
    RestUtils.response(extractor, kind, id)(function, req, errorMessage)
  }

  type ActionType = RestUtils.ActionType
  def actionResponse ( function : Box[ActionType], req : Req, errorMessage : String, id : Option[String], actor: EventActor)(implicit action : String) : LiftResponse = {
    RestUtils.actionResponse2(extractor, kind, uuidGen, id)(function, req, errorMessage)(action, actor)
  }

  import extractor._
  import com.normation.plugins.datasources.DataSourceExtractor.OptionalJson._
  import net.liftweb.json.JsonDSL._


  def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints.map(e => e match {
        case API.ReloadAllDatasourcesOneNode     => ReloadAllDatasourcesOneNode
        case API.ReloadOneDatasourceAllNodes     => ReloadOneDatasourceAllNodes
        case API.ReloadOneDatasourceOneNode      => ReloadOneDatasourceOneNode
        case API.ReloadAllDatasourcesAllNodes    => ReloadAllDatasourcesAllNodes
        case API.ClearValueOneDatasourceAllNodes => ClearValueOneDatasourceAllNodes
        case API.ClearValueOneDatasourceOneNode  => ClearValueOneDatasourceOneNode
        case API.GetAllDataSources               => GetAllDataSources
        case API.GetDataSource                   => GetDataSource
        case API.DeleteDataSource                => DeleteDataSource
        case API.CreateDataSource                => CreateDataSource
        case API.UpdateDataSource                => UpdateDataSource
    }).toList
  }

  object ReloadAllDatasourcesOneNode extends LiftApiModule {
    val schema = API.ReloadAllDatasourcesOneNode
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, nodeId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      //reloadData OneNode All datasources
      dataSourceRepo.onUserAskUpdateNode(authzToken.actor, NodeId(nodeId)).forkDaemon.runNow
      toJsonResponse(None, JString(s"Data for node '${nodeId}', for all configured data sources, is going to be updated"))(schema.name, params.prettify)
    }
  }

  object ReloadOneDatasourceAllNodes extends LiftApiModule {
    val schema = API.ReloadOneDatasourceAllNodes
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, datasourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      //reloadData AllNodes One datasources
      dataSourceRepo.onUserAskUpdateAllNodesFor(authzToken.actor, DataSourceId(datasourceId)).forkDaemon.runNow
      toJsonResponse(None, JString(s"Data for all nodes, for data source '${datasourceId}', are going to be updated"))(schema.name, params.prettify)
    }
  }

  object ReloadOneDatasourceOneNode extends LiftApiModule {
    val schema = API.ReloadOneDatasourceOneNode
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, ids: (String,String), req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val (datasourceId, nodeId) = ids
      // reload Data One Node One datasource
      dataSourceRepo.onUserAskUpdateNodeFor(authzToken.actor, NodeId(nodeId), DataSourceId(datasourceId)).forkDaemon.runNow
      toJsonResponse(None, JString(s"Data for node '${nodeId}', for data source '${datasourceId}', is going to be updated"))(schema.name, params.prettify)
    }
  }

  object ClearValueOneDatasourceAllNodes extends LiftApiModule {
    val schema = API.ClearValueOneDatasourceAllNodes
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, datasourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      val modId = ModificationId(uuidGen.newUuid)
      def cause(nodeId: NodeId) = UpdateCause(modId, authzToken.actor, Some(s"API request to clear '${datasourceId}' on node '${nodeId.value}'"), false)

      val res: Box[Seq[NodeUpdateResult]] = for {
        nodes   <- nodeInfoService.getAllNodes().toBox
        updated <- Control.bestEffort(nodes.values.toSeq) { node =>
                     erase(cause(node.id), node, DataSourceId(datasourceId)).toBox
                   }
      } yield {
        updated
      }
      res match {
        case Full(_)     => toJsonResponse(None, JString(s"Data for all nodes, for data source '${datasourceId}', cleared"))(schema.name, params.prettify)
        case eb:EmptyBox => toJsonError(None, JString((eb ?~! s"Could not clear data source property '${datasourceId}'").messageChain))(schema.name, params.prettify)
      }
    }
  }

  object ClearValueOneDatasourceOneNode extends LiftApiModule {
    val schema = API.ClearValueOneDatasourceOneNode
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, ids: (String, String), req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val (datasourceId, nodeId) = ids
      val cause = UpdateCause(ModificationId(uuidGen.newUuid), authzToken.actor, Some(s"API request to clear '${datasourceId}' on node '${nodeId}'"), false)
      val res: Box[NodeUpdateResult] = for {
        optNode <- nodeInfoService.getNodeInfo(NodeId(nodeId)).toBox
        node    <- optNode match {
                     case None    => Failure(s"Node with ID '${nodeId}' was not found")
                     case Some(x) => Full(x)
                   }
        updated <- erase(cause, node.node, DataSourceId(datasourceId)).toBox
      } yield {
        updated
      }

      res match {
        case Full(_)     => toJsonResponse(None, JString(s"Data for node '${nodeId}', for data source '${datasourceId}', cleared"))(schema.name, params.prettify)
        case eb:EmptyBox => toJsonError(None, JString((eb ?~! s"Could not clear data source property '${datasourceId}'").messageChain))(schema.name, params.prettify)
      }
    }
  }

  object ReloadAllDatasourcesAllNodes extends LiftApiModule0 {
    val schema = API.ReloadAllDatasourcesAllNodes
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      // reloadData All Nodes All Datasources
      dataSourceRepo.onUserAskUpdateAllNodes(authzToken.actor).forkDaemon.runNow
      toJsonResponse(None, JString("Data for all nodes, for all configured data sources are going to be updated"))(schema.name, params.prettify)
    }
  }

  object GetAllDataSources extends LiftApiModule0 {
    val schema = API.GetAllDataSources
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val res = for {
        sources <- dataSourceRepo.getAll
      } yield {
        JArray(sources.values.map(DataSourceJsonSerializer.serialize(_)).toList)
      }
      response(res.toBox, req, "Could not get data sources", None)(schema.name)
    }
  }

  object GetDataSource extends LiftApiModule {
    val schema = API.GetDataSource
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, sourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val res = for {
        source <- dataSourceRepo.get(DataSourceId(sourceId)).notOptional(s"Data source ${sourceId} does not exist.")
      } yield {
        JArray(DataSourceJsonSerializer.serialize(source) :: Nil)
      }
      response(res.toBox, req, s"Could not get data sources from '${sourceId}'", None)(schema.name)
    }
  }

  object DeleteDataSource extends LiftApiModule {
    val schema = API.DeleteDataSource
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, sourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      val res = for {
        source <- dataSourceRepo.delete(DataSourceId(sourceId))
      } yield {
        JArray((( "id" -> sourceId) ~ ("message" -> s"Data source ${sourceId} deleted")) :: Nil)
      }

      response(res.toBox, req, s"Could not delete data sources '${sourceId}'", None)(schema.name)
    }
  }

  object CreateDataSource extends LiftApiModule0 {
    val schema = API.CreateDataSource
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val defaultDuration = DataSource.defaultDuration
      val baseSourceType = DataSourceType.HTTP("", Map(), HttpMethod.GET, Map(), false,"", DataSourceType.HTTP.defaultMaxParallelRequest, HttpRequestMode.OneRequestByNode, defaultDuration, MissingNodeBehavior.Delete)
      val baseRunParam  = DataSourceRunParameters.apply(DataSourceSchedule.NoSchedule(defaultDuration), false, false)
      val res: Box[JValue] = for {
        sourceId <- extractId(req){ a => val id = DataSourceId(a); Full(id)}.flatMap( Box(_) ?~! "You need to define datasource id to create it via api")
        base     =  DataSource.apply(sourceId, DataSourceName(""), baseSourceType, baseRunParam, "", false, defaultDuration)
        source   <- extractReqDataSource(req, base)
        _        <- dataSourceRepo.save(source).toBox
      } yield {
        DataSourceJsonSerializer.serialize(source) :: Nil
      }
      response(res, req, "Could not create data source", None)(schema.name)
    }
  }

  object UpdateDataSource extends LiftApiModule {
    val schema = API.UpdateDataSource
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, sourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val res: Box[JValue] = for {
        base    <- dataSourceRepo.get(DataSourceId(sourceId)).toBox.flatMap { Box(_) ?~! s"Cannot update data source '${sourceId}', because it does not exist" }
        updated <- extractReqDataSource(req, base)
        _       <- dataSourceRepo.save(updated).toBox
      } yield {
       DataSourceJsonSerializer.serialize(updated) :: Nil
      }
      response(res, req, s"Could not update data source '${sourceId}'", None)(schema.name)
    }
  }

  /// utilities ///

  def extractReqDataSource(req : Req, base : DataSource) : Box[DataSource] = {
    req.json match {
      case Full(json) =>
        extractDataSourceWrapper(base.id,json).map(_.withBase(base))
      case _ =>
        for {
          name         <- extractString("name")(req) (x => Full(DataSourceName(x)))
          description  <- extractString("description")(req) (boxedIdentity)
          sourceType   <- extractObj("type")(req) (extractDataSourceTypeWrapper(_))
          runParam     <- extractObj("runParameters")(req) (extractDataSourceRunParameterWrapper(_))
          timeOut      <- extractInt("updateTimeout")(req)(extractDuration)
          enabled      <- extractBoolean("enabled")(req)(identity)
        } yield {
          base.copy(
              name          = name.getOrElse(base.name)
            , sourceType    = getOrElse(sourceType.map(_.withBase(base.sourceType)),base.sourceType)
            , description   = description.getOrElse(base.description)
            , enabled       = enabled.getOrElse(base.enabled)
            , updateTimeOut = timeOut.map(Duration.fromScala).getOrElse(base.updateTimeOut)
            , runParam      = getOrElse(runParam.map(_.withBase(base.runParam)),base.runParam)
          )
        }
    }
  }

  private[this] def erase(cause: UpdateCause, node: Node, datasourceId: DataSourceId)  = {
    import com.normation.errors._
    import zio.syntax._
    val newProp = DataSource.nodeProperty(datasourceId.value, "".toConfigValue)
    node.properties.find(_.name == newProp.name) match {
      case None => NodeUpdateResult.Unchanged(node.id).succeed
      case Some(p) =>
        if (p.provider == newProp.provider) {
          for {
            newProps <- CompareProperties.updateProperties(node.properties, Some(newProp :: Nil)).toIO
            newNode = node.copy(properties = newProps)
            nodeUpdated <- nodeRepos.updateNode(newNode, cause.modId, cause.actor, cause.reason).chainError(s"Cannot clear value for node '${node.id.value}' for property '${newProp.name}'")
          } yield {
            NodeUpdateResult.Updated(nodeUpdated.id)
          }
        } else {
          Unexpected(s"Can not update property '${newProp.name}' on node '${node.id.value}': this property is not managed by data sources.").fail
        }
    }
  }
}
