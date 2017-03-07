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

package com.normation.rudder.datasources

import scala.concurrent.duration.FiniteDuration

import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.nodes.NodeProperty
import com.normation.rudder.domain.nodes.NodePropertyProvider

import org.joda.time.DateTime

import net.liftweb.json.JsonAST.JValue



final object DataSource {
  val defaultDuration = FiniteDuration(5,"minutes")

  val providerName = NodePropertyProvider("datasources")

  /*
   * Name used in both datasource id and "reload" place in
   * API, that must not be used as id.
   */
  val reservedIds = Map(
      DataSourceId("reload") -> "That id would conflict with API path for reloading datasources"
  )


  /**
   * A node property with the correct DataSource metadata
   */
  def nodeProperty(name: String, value: JValue) = NodeProperty(name, value, Some(providerName))
  def nodeProperty(name: String, value: String) = NodeProperty(name, value, Some(providerName))
}

sealed trait DataSourceType {
  def name : String
}

/*
 * For an HTTP datasource, how to contact the
 * foreign server?
 */
sealed trait HttpMethod { def name: String }
final object HttpMethod {

  final case object GET  extends HttpMethod { override val name = "GET"  }
  final case object POST extends HttpMethod { override val name = "POST" }

  def values = ca.mrvisser.sealerate.values[HttpMethod]
}

final object DataSourceType {

  final object HTTP {
    val name = "HTTP"
  }

  final case class HTTP (
      url                : String
    , headers            : Map[String,String]
    , httpMethod         : HttpMethod
    , params             : Map[String, String] // query params for GET, form params for POST
    , sslCheck           : Boolean
    , path               : String
    , requestMode        : HttpRequestMode
    , requestTimeOut     : FiniteDuration
    , missingNodeBehavior: MissingNodeBehavior
  ) extends DataSourceType {
    val name = HTTP.name
  }
}

/**
 * How to query the target API:
 * - do one request for each known nodes?
 * - do one request for all node in one go?
 */
sealed trait HttpRequestMode { def name : String }

final object HttpRequestMode {
  final case object OneRequestByNode extends HttpRequestMode {
    val name = "byNode"
  }

  final object OneRequestAllNodes {
    val name = "allNodes"
  }

  final case class OneRequestAllNodes(
      matchingPath  : String
    , nodeAttribute : String
  ) extends HttpRequestMode {
    val name = OneRequestAllNodes.name
  }
}

/**
 * Define the behavior to adopt when a node is
 * in Rudder but not found in the API request result
 * (i.e not on error, but on 404 for a "by node" request
 * mode, or actually missing id for a "on request all node".
 */
sealed trait MissingNodeBehavior { def name: String }

final object MissingNodeBehavior {
  // delete is the default behavior is not specified
  final case object Delete                      extends MissingNodeBehavior { val name = "delete"       }
  final case object NoChange                    extends MissingNodeBehavior { val name = "noChange"     }
  final object DefaultValue { val name = "defaultValue" }
  final case class  DefaultValue(value: JValue) extends MissingNodeBehavior { val name = DefaultValue.name }
}

final case class DataSourceName(value : String)
final case class DataSourceId  (value : String)

sealed trait DataSourceSchedule {
  def duration : FiniteDuration
}

final object DataSourceSchedule {
  final case class NoSchedule(
    savedDuration : FiniteDuration
  ) extends DataSourceSchedule {
    val duration = savedDuration
  }

  final case class Scheduled(
    duration : FiniteDuration
  ) extends DataSourceSchedule
}

final case class DataSourceRunParameters (
    schedule     : DataSourceSchedule
  , onGeneration : Boolean
  , onNewNode    : Boolean
)

final case class DataSourceStatus (
    lastRunDate : Option[DateTime]
  , nodesStatus : Map[NodeId, DataSourceUpdateStatus]
)

sealed trait DataSourceUpdateStatus{
  def state       : String
  def lastRunDate : DateTime
}

final case object DataSourceUpdateStatus {

  final case class Success(
    lastRunDate : DateTime
  ) extends DataSourceUpdateStatus {
    val state = "success"
  }

  final case class Failure(
      lastRunDate     : DateTime
    , message         : String
    , lastSuccessDate : Option[String]
  ) extends DataSourceUpdateStatus {
    val state = "failure"
  }
}

final case class DataSource (
    id            : DataSourceId
  , name          : DataSourceName
  , sourceType    : DataSourceType
  , runParam      : DataSourceRunParameters
  , description   : String
  , enabled       : Boolean
  , updateTimeOut : FiniteDuration
) {
  val scope = "all"
}
