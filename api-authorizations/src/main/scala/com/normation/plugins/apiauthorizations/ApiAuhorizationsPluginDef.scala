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

package com.normation.plugins.apiauthorizations

import com.normation.plugins.{PluginName, PluginStatus, PluginVersion, RudderPluginDef}
import com.normation.rudder.domain.logger.PluginLogger
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Loggable

import scala.xml.NodeSeq

class ApiAuthorizationsPluginDef(info: PluginStatus) extends RudderPluginDef with Loggable {

  override val basePackage = "com.normation.plugins.apiauthorizations"

  //get properties name for the plugin from "build.conf" file
  //have default string for errors (and avoid "missing prop exception"):
  val defaults = List("plugin-id", "plugin-name", "plugin-version").map(p => s"$p=missing property with name '$p' in file 'build.conf'").mkString("\n")
  // ConfigFactory does not want the "/" at begining nor the ".conf" on the end
  val buildConfPath = basePackage.replaceAll("""\.""", "/") + "/build.conf"
  val buildConf = ConfigFactory.load(this.getClass.getClassLoader, buildConfPath).withFallback(ConfigFactory.parseString(defaults))


  override val name = PluginName(buildConf.getString("plugin-id"))
  override val displayName = buildConf.getString("plugin-name")
  override val version = {
    val versionString = buildConf.getString("plugin-version")
    PluginVersion.from(versionString).getOrElse(
      //a version name that indicate an erro
      PluginVersion(0,0,1, s"ERROR-PARSING-VERSION: ${versionString}")
    )
  }

  override def description : NodeSeq  = (
     <p>
     This plugin allows to use fine grained authorizations for APIs thanks to ACLs.
     </p>

  )

  val status: PluginStatus = info

  def init = {
    PluginLogger.info(s"loading '${buildConf.getString("plugin-id")}:${version.toString}' plugin")
  }

  def oneTimeInit : Unit = {}

  val configFiles = Seq()

}
