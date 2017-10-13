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

import com.normation.inventory.domain.NodeId
import com.normation.plugins.datasources.DataSourceId
import com.normation.rudder.web.rest.ApiVersion
import com.normation.rudder.web.rest.RestExtractorService
import com.normation.rudder.web.rest.RestUtils
import com.normation.rudder.web.rest.RestUtils._
import com.normation.utils.StringUuidGenerator

import net.liftweb.common.Box
import net.liftweb.common.Loggable
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JString
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full

class DataSourceApi9 (
    extractor : RestExtractorService
  , apiService: DataSourceApiService
  , uuidGen   : StringUuidGenerator
) extends DataSourceApi with Loggable {

  def response ( function : Box[JValue], req : Req, errorMessage : String, id : Option[String])(implicit action : String) : LiftResponse = {
    RestUtils.response(extractor, kind, id)(function, req, errorMessage)
  }

  type ActionType = RestUtils.ActionType
  def actionResponse ( function : Box[ActionType], req : Req, errorMessage : String, id : Option[String])(implicit action : String) : LiftResponse = {
    RestUtils.actionResponse(extractor, kind, uuidGen, id)(function, req, errorMessage)
  }

  def requestDispatch(apiVersion: ApiVersion) : PartialFunction[Req, () => Box[LiftResponse]] = {

    /* Avoiding POST unreachable endpoint:
     * (note: datasource must not have id "reload")
     *
     * POST /datasources/reload/node/$nodeid
     * POST /datasources/reload/$datasourceid
     * POST /datasources/reload/$datasourceid/node/$nodeid
     * POST /datasources/reload
     * POST /datasources/clear/$datasourceid/node/$nodeid
     * POST /datasources/clear/$datasourceid/
     */

    case Post("reload" :: "node" :: nodeId :: Nil, req) => {
      implicit val prettify = extractor.extractPrettify(req.params)
      implicit val action = "reloadAllDatasourcesOneNode"
      val actor = RestUtils.getActor(req)

      apiService.reloadDataOneNode(actor, NodeId(nodeId))

      toJsonResponse(None, JString(s"Data for node '${nodeId}', for all configured data sources, is going to be updated"))
    }


    case Post( "reload" :: datasourceId :: Nil, req) => {
      implicit val prettify = extractor.extractPrettify(req.params)
      implicit val action = "reloadOneDatasourceAllNodes"
      val actor = RestUtils.getActor(req)

      apiService.reloadDataAllNodesFor(actor, DataSourceId(datasourceId))

      toJsonResponse(None, JString(s"Data for all nodes, for data source '${datasourceId}', are going to be updated"))
    }

    case Post("reload" :: datasourceId :: "node" :: nodeId :: Nil, req) => {
      implicit val prettify = extractor.extractPrettify(req.params)
      implicit val action = "reloadOneDatasourceOneNode"
      val actor = RestUtils.getActor(req)

      apiService.reloadDataOneNodeFor(actor, NodeId(nodeId), DataSourceId(datasourceId))

      toJsonResponse(None, JString(s"Data for node '${nodeId}', for data source '${datasourceId}', is going to be updated"))
    }

    case Post( "clear" :: datasourceId :: Nil, req) => {
      implicit val prettify = extractor.extractPrettify(req.params)
      implicit val action = "clearValueOneDatasourceAllNodes"
      val actor = RestUtils.getActor(req)

      apiService.clearDataAllNodesFor(actor, DataSourceId(datasourceId)) match {
        case Full(_)     => toJsonResponse(None, JString(s"Data for all nodes, for data source '${datasourceId}', cleared"))
        case eb:EmptyBox => toJsonError(None, JString((eb ?~! s"Could not clear data source property '${datasourceId}'").messageChain))
      }
    }

    case Post("clear" :: datasourceId :: "node" :: nodeId :: Nil, req) => {
      implicit val prettify = extractor.extractPrettify(req.params)
      implicit val action = "clearValueOneDatasourceOneNode"
      val actor = RestUtils.getActor(req)

      apiService.clearDataOneNodeFor(actor, NodeId(nodeId), DataSourceId(datasourceId)) match {
        case Full(_)     => toJsonResponse(None, JString(s"Data for node '${nodeId}', for data source '${datasourceId}', cleared"))
        case eb:EmptyBox => toJsonError(None, JString((eb ?~! s"Could not clear data source property '${datasourceId}'").messageChain))
      }
    }

    case Post( "reload" :: Nil, req) => {
      implicit val prettify = extractor.extractPrettify(req.params)
      implicit val action = "reloadAllDatasourcesAllNodes"
      val actor = RestUtils.getActor(req)

      apiService.reloadDataAllNodes(actor)

      toJsonResponse(None, JString("Data for all nodes, for all configured data sources are going to be updated"))
    }

    case Get(Nil, req) => {
      response(apiService.getSources(), req, "Could not get data sources", None)("getAllDataSources")
    }

    case Get(sourceId :: Nil, req) => {
      response(apiService.getSource(DataSourceId(sourceId)), req, s"Could not get data sources from '${sourceId}'", None)("getDataSource")
    }

    case Delete(sourceId :: Nil, req) => {
      response(apiService.deleteSource(DataSourceId(sourceId)), req, s"Could not delete data sources '${sourceId}'", None)("deteteDataSource")
    }

    case Put(Nil, req) => {
        response(apiService.createSource(req), req, "Could not create data source", None)("createDataSource")
    }

    case Post(sourceId :: Nil, req) => {
      response(apiService.updateSource(DataSourceId(sourceId),req), req, s"Could not update data source '${sourceId}'", None)("updateDataSource")
    }

  }
}
