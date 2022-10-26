/*
 *************************************************************************************
 * Copyright 2014 Normation SAS
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

package com.normation.plugins.nodeexternalreports

import bootstrap.liftweb.Boot
import bootstrap.liftweb.ClassPathResource
import bootstrap.liftweb.MenuUtils
import com.normation.plugins._
import com.normation.plugins.PluginStatus
import com.normation.plugins.nodeexternalreports.service.NodeExternalReportApi
import com.normation.rudder.AuthorizationType
import net.liftweb.common.Loggable
import net.liftweb.http.ClasspathTemplates
import net.liftweb.http.LiftRules
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.sitemap.Loc.Template
import net.liftweb.sitemap.Loc.TestAccess
import net.liftweb.sitemap.LocPath.stringToLocPath
import net.liftweb.sitemap.Menu

class NodeExternalReportsPluginDef(api: NodeExternalReportApi, override val status: PluginStatus)
    extends DefaultPluginDef with Loggable {

  val basePackage = "com.normation.plugins.nodeexternalreports"

  val configFiles = Seq(ClassPathResource("demo-config-1.properties"), ClassPathResource("demo-config-2.properties"))

  def init = {
    LiftRules.statelessDispatch.append(api)
  }

  def oneTimeInit: Unit = {}

  override def pluginMenuEntry: Option[Menu] = {
    Some(
      Menu("160-nodeExternalReportInfo", <span>Node External Reports</span>) / "secure" / "plugins" / "nodeexternalreports" >>
      LocGroup("pluginsGroup") >>
      Template(() => {
        ClasspathTemplates("nodeExternalReports" :: Nil) openOr
        <div>Template not found</div>
      })
    )
  }

  override def pluginMenuParent: Option[Menu] = {
    Some(
      Menu(MenuUtils.nodeManagementMenu, <span>Node management</span>) /
      "secure" / "nodeManager" / "index" >> TestAccess(() => Boot.userIsAllowed("/secure/index", AuthorizationType.Node.Read))
    )
  }
}
