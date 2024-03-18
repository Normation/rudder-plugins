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

import com.normation.errors.*
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.plugins.datasources.DataSource
import com.normation.plugins.datasources.DataSourceId
import com.normation.plugins.datasources.DataSourceJsonCodec.*
import com.normation.plugins.datasources.DataSourceRepository
import com.normation.plugins.datasources.DataSourceUpdateCallbacks
import com.normation.plugins.datasources.FullDataSource
import com.normation.plugins.datasources.NodeUpdateResult
import com.normation.plugins.datasources.RestResponseMessage
import com.normation.plugins.datasources.UpdateCause
import com.normation.plugins.datasources.api.DataSourceApi as API
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.domain.nodes.Node
import com.normation.rudder.domain.properties.CompareProperties
import com.normation.rudder.domain.properties.GenericProperty.*
import com.normation.rudder.repository.WoNodeRepository
import com.normation.rudder.rest.*
import com.normation.rudder.rest.implicits.*
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.utils.StringUuidGenerator
import io.scalaland.chimney.syntax.*
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import zio.syntax.*

class DataSourceApiImpl(
    extractor:       RestExtractorService,
    dataSourceRepo:  DataSourceRepository with DataSourceUpdateCallbacks,
    nodeInfoService: NodeInfoService,
    nodeRepos:       WoNodeRepository,
    uuidGen:         StringUuidGenerator
) extends LiftApiModuleProvider[API] {
  api =>

  val kind = "datasources"

  override def schemas: ApiModuleProvider[API] = API

  type ActionType = RestUtils.ActionType

  import com.normation.plugins.datasources.DataSourceExtractor.OptionalJson.*

  def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints
      .map(e => {
        e match {
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
        }
      })
  }

  object ReloadAllDatasourcesOneNode extends LiftApiModule {
    val schema: DataSourceApi.ReloadAllDatasourcesOneNode.type = API.ReloadAllDatasourcesOneNode
    val restExtractor = extractor
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        nodeId:     String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      // reloadData OneNode All datasources
      dataSourceRepo
        .onUserAskUpdateNode(authzToken.qc.actor, NodeId(nodeId))
        .forkDaemon
        .as(s"Data for node '${nodeId}', for all configured data sources, is going to be updated")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object ReloadOneDatasourceAllNodes extends LiftApiModule {
    val schema: DataSourceApi.ReloadOneDatasourceAllNodes.type = API.ReloadOneDatasourceAllNodes
    val restExtractor = extractor
    def process(
        version:      ApiVersion,
        path:         ApiPath,
        datasourceId: String,
        req:          Req,
        params:       DefaultParams,
        authzToken:   AuthzToken
    ): LiftResponse = {
      // reloadData AllNodes One datasources
      dataSourceRepo
        .onUserAskUpdateAllNodesFor(authzToken.qc.actor, DataSourceId(datasourceId))
        .forkDaemon
        .as(s"Data for all nodes, for data source '${datasourceId}', are going to be updated")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object ReloadOneDatasourceOneNode extends LiftApiModule {
    val schema: DataSourceApi.ReloadOneDatasourceOneNode.type = API.ReloadOneDatasourceOneNode
    val restExtractor = extractor
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        ids:        (String, String),
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      val (datasourceId, nodeId) = ids
      // reloadData OneNode One datasource
      dataSourceRepo
        .onUserAskUpdateNodeFor(authzToken.qc.actor, NodeId(nodeId), DataSourceId(datasourceId))
        .forkDaemon
        .as(s"Data for node '${nodeId}', for data source '${datasourceId}', is going to be updated")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object ClearValueOneDatasourceAllNodes extends LiftApiModule {
    val schema: DataSourceApi.ClearValueOneDatasourceAllNodes.type = API.ClearValueOneDatasourceAllNodes
    val restExtractor = extractor
    def process(
        version:      ApiVersion,
        path:         ApiPath,
        datasourceId: String,
        req:          Req,
        params:       DefaultParams,
        authzToken:   AuthzToken
    ): LiftResponse = {

      val modId                 = ModificationId(uuidGen.newUuid)
      def cause(nodeId: NodeId) =
        UpdateCause(modId, authzToken.qc.actor, Some(s"API request to clear '${datasourceId}' on node '${nodeId.value}'"), false)

      (for {
        nodes <- nodeInfoService.getAllNodes()
        _     <- nodes.values.accumulate(node => erase(cause(node.id), node, DataSourceId(datasourceId)))
        res    = s"Data for all nodes, for data source '${datasourceId}', cleared"
      } yield res)
        .chainError(s"Could not clear data source property '${datasourceId}'")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object ClearValueOneDatasourceOneNode extends LiftApiModule {
    val schema: DataSourceApi.ClearValueOneDatasourceOneNode.type = API.ClearValueOneDatasourceOneNode
    val restExtractor = extractor
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        ids:        (String, String),
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      val (datasourceId, nodeId) = ids
      val cause                  = UpdateCause(
        ModificationId(uuidGen.newUuid),
        authzToken.qc.actor,
        Some(s"API request to clear '${datasourceId}' on node '${nodeId}'"),
        false
      )

      (for {
        optNode <- nodeInfoService.getNodeInfo(NodeId(nodeId))
        node    <- optNode.notOptional(s"Node with ID '${nodeId}' was not found")
        updated <- erase(cause, node.node, DataSourceId(datasourceId))
        res      = s"Data for node '${nodeId}', for data source '${datasourceId}', cleared"
      } yield res)
        .chainError(s"Could not clear data source property '${datasourceId}'")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object ReloadAllDatasourcesAllNodes extends LiftApiModule0 {
    val schema: DataSourceApi.ReloadAllDatasourcesAllNodes.type = API.ReloadAllDatasourcesAllNodes
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      // reloadData All Nodes All Datasources
      dataSourceRepo
        .onUserAskUpdateAllNodes(authzToken.qc.actor)
        .forkDaemon
        .as("Data for all nodes, for all configured data sources are going to be updated")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object GetAllDataSources extends LiftApiModule0 {
    val schema: DataSourceApi.GetAllDataSources.type = API.GetAllDataSources
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        sources <- dataSourceRepo.getAll
      } yield {
        sources.values.map(_.transformInto[FullDataSource]).toList
      })
        .chainError("Could not get data sources")
        .toLiftResponseList(params, schema)
    }
  }

  object GetDataSource extends LiftApiModule {
    val schema: DataSourceApi.GetDataSource.type = API.GetDataSource
    val restExtractor = extractor
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sourceId:   String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        source <- dataSourceRepo.get(DataSourceId(sourceId)).notOptional(s"Data source ${sourceId} does not exist.")
      } yield {
        List(source.transformInto[FullDataSource])
      }).chainError(s"Could not get data sources from '${sourceId}'")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object DeleteDataSource extends LiftApiModule {
    val schema: DataSourceApi.DeleteDataSource.type = API.DeleteDataSource
    val restExtractor = extractor
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sourceId:   String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        source <- dataSourceRepo.delete(
                    DataSourceId(sourceId),
                    UpdateCause(
                      ModificationId(uuidGen.newUuid),
                      authzToken.qc.actor,
                      Some(s"Deletion of datasource '${sourceId}' requested by API")
                    )
                  )
      } yield {
        RestResponseMessage(DataSourceId(sourceId), s"Data source ${sourceId} deleted")
      }).chainError(s"Could not delete data sources '${sourceId}'")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object CreateDataSource extends LiftApiModule0 {
    val schema: DataSourceApi.CreateDataSource.type = API.CreateDataSource
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        source <- extractNewDataSource(req).toIO
        _      <- dataSourceRepo.save(source)
      } yield {
        source.transformInto[FullDataSource]
      }).chainError(s"Could not create data source")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object UpdateDataSource extends LiftApiModule {
    val schema: DataSourceApi.UpdateDataSource.type = API.UpdateDataSource
    val restExtractor = extractor
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sourceId:   String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        optBase <- dataSourceRepo
                     .get(DataSourceId(sourceId))
        base    <- optBase.notOptional(
                     s"Cannot update data source '${sourceId}', because it does not exist"
                   )
        updated <- extractDataSource(req, base).toIO
        _       <- dataSourceRepo.save(updated)
      } yield {
        updated.transformInto[FullDataSource]
      }).chainError(s"Could not update data source '${sourceId}'")
        .toLiftResponseOne(params, schema, None)
    }
  }

  /// utilities ///
  private[this] def erase(cause: UpdateCause, node: Node, datasourceId: DataSourceId): IOResult[NodeUpdateResult] = {
    val newProp = DataSource.nodeProperty(datasourceId.value, "".toConfigValue)
    node.properties.find(_.name == newProp.name) match {
      case None    => NodeUpdateResult.Unchanged(node.id).succeed
      case Some(p) =>
        if (p.provider == newProp.provider) {
          for {
            newProps    <- CompareProperties.updateProperties(node.properties, Some(newProp :: Nil)).toIO
            newNode      = node.copy(properties = newProps)
            nodeUpdated <- nodeRepos
                             .updateNode(newNode, cause.modId, cause.actor, cause.reason)
                             .chainError(s"Cannot clear value for node '${node.id.value}' for property '${newProp.name}'")
          } yield {
            NodeUpdateResult.Updated(nodeUpdated.id)
          }
        } else {
          Unexpected(
            s"Can not update property '${newProp.name}' on node '${node.id.value}': this property is not managed by data sources."
          ).fail
        }
    }
  }
}
