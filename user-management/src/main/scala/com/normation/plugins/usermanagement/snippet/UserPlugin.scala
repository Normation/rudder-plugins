/*
 *************************************************************************************
 * Copyright 2018 Normation SAS
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

package com.normation.plugins.usermanagement.snippet

import better.files.Resource
import bootstrap.liftweb.RudderConfig
import bootstrap.rudder.plugin.UserManagementConf
import com.normation.plugins.usermanagement.Serialisation._
import com.normation.rudder.RudderRoles
import com.normation.zio._
import net.liftweb.http.DispatchSnippet
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import scala.xml.NodeSeq
import zio.json._

class UserPlugin extends DispatchSnippet {

  private[this] val userService         = RudderConfig.rudderUserListProvider
  private[this] val authProviderManager = RudderConfig.authenticationProviders
  private[this] val userManagementApi   = UserManagementConf.api

  def dispatch = { case "getAuthzConfig" => _ => getAuthzConfig }

  val t = Resource.getAsString("demo-rudder-users.xml")

  def getAuthzConfig: NodeSeq = {
    val allRoles = RudderRoles.getAllRoles.runNow.values.toSet
    Script(
      JsRaw(
        s"""var authzConfig = ${userManagementApi.serialize(userService.authConfig, allRoles)(authProviderManager).toJson};"""
      )
    )
  }
}
