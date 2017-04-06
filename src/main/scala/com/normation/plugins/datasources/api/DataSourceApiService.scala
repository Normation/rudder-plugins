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
import com.normation.plugins.datasources.DataSourceRepository
import com.normation.plugins.datasources.DataSourceRunParameters
import com.normation.plugins.datasources.DataSourceSchedule
import com.normation.plugins.datasources.DataSourceType
import com.normation.plugins.datasources.DataSourceUpdateCallbacks
import com.normation.plugins.datasources.HttpMethod
import com.normation.plugins.datasources.HttpRequestMode
import com.normation.plugins.datasources.MissingNodeBehavior
import com.normation.rudder.web.rest.RestDataSerializer
import com.normation.rudder.web.rest.RestExtractorService
import com.normation.rudder.web.rest.RestUtils

import net.liftweb.common._
import net.liftweb.http.Req
import net.liftweb.json.JsonAST.JValue

class DataSourceApiService(
    dataSourceRepo     : DataSourceRepository with DataSourceUpdateCallbacks
  , restDataSerializer : RestDataSerializer
  , restExtractor      : RestExtractorService
) extends Loggable {
  import restExtractor._
  import com.normation.plugins.datasources.DataSourceExtractor.OptionnalJson._
  import net.liftweb.json.JsonDSL._

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
              name = name.getOrElse(base.name)
            , sourceType = getOrElse(sourceType.map(_.withBase(base.sourceType)),base.sourceType)
            , description = description.getOrElse(base.description)
            , enabled = enabled.getOrElse(base.enabled)
            , updateTimeOut = timeOut.getOrElse(base.updateTimeOut)
            , runParam = getOrElse(runParam.map(_.withBase(base.runParam)),base.runParam)
          )
        }
    }
  }

  type ActionType = RestUtils.ActionType
  def getSources() : Box[JValue] = {
    for {
      sources <- dataSourceRepo.getAll
      data = sources.values.map(DataSourceJsonSerializer.serialize(_))
    } yield {
      data
    }
  }

  def getSource(id : DataSourceId) : Box[JValue] = {
    for {
      optSource <- dataSourceRepo.get(id)
      source <- Box(optSource) ?~! s"Data source ${id} does not exist."
    } yield {
      DataSourceJsonSerializer.serialize(source) :: Nil
    }
  }

  def deleteSource(id : DataSourceId) : Box[JValue] = {
    for {
      source <- dataSourceRepo.delete(id)
    } yield {
      (( "id" -> id.value) ~ ("message" -> s"Data source ${id.value} deleted")) :: Nil
    }
  }

  def createSource(request : Req) : Box[JValue] = {

    val defaultDuration = DataSource.defaultDuration
    val baseSourceType = DataSourceType.HTTP("", Map(), HttpMethod.GET, Map(), false,"", HttpRequestMode.OneRequestByNode, defaultDuration, MissingNodeBehavior.Delete)
    val baseRunParam  = DataSourceRunParameters.apply(DataSourceSchedule.NoSchedule(defaultDuration), false, false)
    for {
      sourceId <- restExtractor.extractId(request){ a => val id = DataSourceId(a); Full(id)}.flatMap( Box(_) ?~! "You need to define datasource id to create it via api")
      base = DataSource.apply(sourceId, DataSourceName(""), baseSourceType, baseRunParam, "", false, defaultDuration)
      source <- extractReqDataSource(request, base)
      _ <- dataSourceRepo.save(source)
      data = DataSourceJsonSerializer.serialize(source)
    } yield {
      data :: Nil
    }
  }

  def updateSource(sourceId : DataSourceId, request: Req ) : Box[JValue] = {
    for {
      base <- dataSourceRepo.get(sourceId).flatMap { Box(_) ?~! s"Cannot update data source '${sourceId.value}', because it does not exist" }
      updated <- extractReqDataSource(request, base)
      _ <- dataSourceRepo.save(updated)
      data = DataSourceJsonSerializer.serialize(updated)
    } yield {
      data :: Nil
    }
  }

  def reloadDataAllNodes(actor: EventActor): Unit = {
    dataSourceRepo.onUserAskUpdateAllNodes(actor)
  }

  def reloadDataAllNodesFor(actor: EventActor, datasourceId: DataSourceId): Unit = {
    dataSourceRepo.onUserAskUpdateAllNodesFor(actor, datasourceId)
  }

  def reloadDataOneNode(actor: EventActor, nodeId: NodeId): Unit = {
    dataSourceRepo.onUserAskUpdateNode(actor, nodeId)
  }

  def reloadDataOneNodeFor(actor: EventActor, nodeId: NodeId, datasourceId: DataSourceId): Unit = {
    dataSourceRepo.onUserAskUpdateNodeFor(actor, nodeId, datasourceId)
  }
}
