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
import com.normation.plugins.datasources.DataSourceId
import com.normation.rudder.rest._
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

class DataSourceApi9 (
    extractor : RestExtractorService
  , apiService: DataSourceApiService
  , uuidGen   : StringUuidGenerator
) extends  LiftApiModuleProvider {
  api =>

  val kind = "datasources"

  def response ( function : Box[JValue], req : Req, errorMessage : String, id : Option[String])(implicit action : String) : LiftResponse = {
    RestUtils.response(extractor, kind, id)(function, req, errorMessage)
  }

  type ActionType = RestUtils.ActionType
  def actionResponse ( function : Box[ActionType], req : Req, errorMessage : String, id : Option[String], actor: EventActor)(implicit action : String) : LiftResponse = {
    RestUtils.actionResponse2(extractor, kind, uuidGen, id)(function, req, errorMessage)(action, actor)
  }

  import com.normation.plugins.datasources.api.{ DataSourceApi => API }

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
      apiService.reloadDataOneNode(authzToken.actor, NodeId(nodeId))

      toJsonResponse(None, JString(s"Data for node '${nodeId}', for all configured data sources, is going to be updated"))(schema.name, params.prettify)
    }
  }

  object ReloadOneDatasourceAllNodes extends LiftApiModule {
    val schema = API.ReloadOneDatasourceAllNodes
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, datasourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      apiService.reloadDataAllNodesFor(authzToken.actor, DataSourceId(datasourceId))
      toJsonResponse(None, JString(s"Data for all nodes, for data source '${datasourceId}', are going to be updated"))(schema.name, params.prettify)
    }
  }

  object ReloadOneDatasourceOneNode extends LiftApiModule {
    val schema = API.ReloadOneDatasourceOneNode
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, ids: (String,String), req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val (datasourceId, nodeId) = ids
      apiService.reloadDataOneNodeFor(authzToken.actor, NodeId(nodeId), DataSourceId(datasourceId))
      toJsonResponse(None, JString(s"Data for node '${nodeId}', for data source '${datasourceId}', is going to be updated"))(schema.name, params.prettify)
    }
  }

  object ClearValueOneDatasourceAllNodes extends LiftApiModule {
    val schema = API.ClearValueOneDatasourceAllNodes
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, datasourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      apiService.clearDataAllNodesFor(authzToken.actor, DataSourceId(datasourceId)) match {
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
      apiService.clearDataOneNodeFor(authzToken.actor, NodeId(nodeId), DataSourceId(datasourceId)) match {
        case Full(_)     => toJsonResponse(None, JString(s"Data for node '${nodeId}', for data source '${datasourceId}', cleared"))(schema.name, params.prettify)
        case eb:EmptyBox => toJsonError(None, JString((eb ?~! s"Could not clear data source property '${datasourceId}'").messageChain))(schema.name, params.prettify)
      }
    }
  }

  object ReloadAllDatasourcesAllNodes extends LiftApiModule0 {
    val schema = API.ReloadAllDatasourcesAllNodes
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      apiService.reloadDataAllNodes(authzToken.actor)
      toJsonResponse(None, JString("Data for all nodes, for all configured data sources are going to be updated"))(schema.name, params.prettify)
    }
  }

  object GetAllDataSources extends LiftApiModule0 {
    val schema = API.GetAllDataSources
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      response(apiService.getSources(), req, "Could not get data sources", None)(schema.name)
    }
  }

  object GetDataSource extends LiftApiModule {
    val schema = API.GetDataSource
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, sourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      response(apiService.getSource(DataSourceId(sourceId)), req, s"Could not get data sources from '${sourceId}'", None)(schema.name)
    }
  }

  object DeleteDataSource extends LiftApiModule {
    val schema = API.DeleteDataSource
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, sourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      response(apiService.deleteSource(DataSourceId(sourceId)), req, s"Could not delete data sources '${sourceId}'", None)(schema.name)
    }
  }

  object CreateDataSource extends LiftApiModule0 {
    val schema = API.CreateDataSource
    val restExtractor = extractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      response(apiService.createSource(req), req, "Could not create data source", None)(schema.name)
    }
  }

  object UpdateDataSource extends LiftApiModule {
    val schema = API.UpdateDataSource
    val restExtractor = extractor
    def process(version: ApiVersion, path: ApiPath, sourceId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      response(apiService.updateSource(DataSourceId(sourceId),req), req, s"Could not update data source '${sourceId}'", None)(schema.name)
    }
  }
}
