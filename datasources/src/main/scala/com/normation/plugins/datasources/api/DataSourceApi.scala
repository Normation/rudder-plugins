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

import com.normation.rudder.api.HttpAction._
import com.normation.rudder.rest._
import com.normation.rudder.rest.EndpointSchema.syntax._
import sourcecode.Line

sealed trait DataSourceApi extends EndpointSchema with GeneralApi with SortIndex
object DataSourceApi       extends ApiModuleProvider[DataSourceApi] {

  /* Avoiding POST unreachable endpoint:
   * (note: datasource must not have id "reload")
   *
   * POST /datasources/reload/node/
   * POST /datasources/reload/node/$nodeid
   * POST /datasources/reload/$datasourceid
   * POST /datasources/reload/$datasourceid/node/$nodeid
   * POST /datasources/reload
   * POST /datasources/clear/$datasourceid/
   * POST /datasources/clear/$datasourceid/node/$nodeid
   *
   * And then the simpler on datasource CRUD
   */

  final case object ReloadAllDatasourcesAllNodes extends DataSourceApi with ZeroParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Reload all datasources for all nodes"
    val (action, path) = POST / "datasources" / "reload" / "node"

    override def dataContainer: Option[String] = None
  }

  final case object ReloadAllDatasourcesOneNode extends DataSourceApi with OneParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Reload all datasources for the given node"
    val (action, path) = POST / "datasources" / "reload" / "node" / "{nodeid}"

    override def dataContainer: Option[String] = None
  }

  final case object ReloadOneDatasourceAllNodes extends DataSourceApi with OneParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Reload this given datasources for all nodes"
    val (action, path) = POST / "datasources" / "reload" / "{datasourceid}"

    override def dataContainer: Option[String] = None
  }

  final case object ReloadOneDatasourceOneNode extends DataSourceApi with TwoParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Reload the given datasource for the given node"
    val (action, path) = POST / "datasources" / "reload" / "{datasourceid}" / "node" / "{nodeid}"

    override def dataContainer: Option[String] = None
  }

  final case object ClearValueOneDatasourceAllNodes extends DataSourceApi with OneParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Clear node property values on all nodes for given datasource"
    val (action, path) = POST / "datasources" / "clear" / "{datasourceid}"

    override def dataContainer: Option[String] = None
  }

  final case object ClearValueOneDatasourceOneNode extends DataSourceApi with TwoParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Clear node property value set by given datasource on given node"
    val (action, path) = POST / "datasources" / "clear" / "{datasourceid}" / "node" / "{nodeid}"

    override def dataContainer: Option[String] = None
  }

  final case object GetAllDataSources extends DataSourceApi with ZeroParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Get the list of all defined datasources"
    val (action, path) = GET / "datasources"

    override def dataContainer: Option[String] = None
  }

  final case object GetDataSource extends DataSourceApi with OneParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Get information about the given datasource"
    val (action, path) = GET / "datasources" / "{datasourceid}"

    override def dataContainer: Option[String] = None
  }

  final case object DeleteDataSource extends DataSourceApi with OneParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Delete given datasource"
    val (action, path) = DELETE / "datasources" / "{datasourceid}"

    override def dataContainer: Option[String] = None
  }

  final case object CreateDataSource extends DataSourceApi with ZeroParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Create given datasource"
    val (action, path) = PUT / "datasources"

    override def dataContainer: Option[String] = None
  }

  final case object UpdateDataSource extends DataSourceApi with OneParam with StartsAtVersion9 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Update information about the given datasource"
    val (action, path) = POST / "datasources" / "{datasourceid}"

    override def dataContainer: Option[String] = None
  }

  def endpoints = ca.mrvisser.sealerate.values[DataSourceApi].toList.sortBy(_.z)
}
