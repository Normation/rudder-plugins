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

package com.normation.plugins.datasources

import bootstrap.liftweb.Boot
import bootstrap.rudder.plugin.DatasourcesConf
import com.normation.plugins.PluginName
import com.normation.plugins.PluginVersion
import com.normation.plugins.RudderPluginDef
import com.normation.rudder.authorization.Read
import com.normation.rudder.domain.logger.PluginLogger
import java.util.Properties
import net.liftweb.common.Loggable
import net.liftweb.http.ClasspathTemplates
import net.liftweb.http.LiftRules
import net.liftweb.http.ResourceServer
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.sitemap.Loc.Template
import net.liftweb.sitemap.Loc.TestAccess
import net.liftweb.sitemap.LocPath.stringToLocPath
import net.liftweb.sitemap.Menu
import scala.xml.NodeSeq

class DataSourcesPluginDef() extends RudderPluginDef with Loggable {

  val properties = {
    val props = new Properties
    props.load(this.getClass.getClassLoader.getResourceAsStream("build.conf"))
    import scala.collection.JavaConverters._
    props.asScala
  }

  override val basePackage = "com.normation.plugins.datasources"
  override val name = PluginName(properties("plugin-id"))
  override val displayName = properties("plugin-name")
  override val version = {
    PluginVersion.from(properties("plugin-version")).getOrElse(
      //a version name that indicate an erro
      PluginVersion(0,0,1, "ERROR-PARSING-VERSION")
    )
  }

  override val description : NodeSeq  =
    <div>
     Data source plugin allows to get node properties from third parties provider accessible via a REST API.
     Configuration can be done in <a href="/secure/administration/dataSourceManagement">the dedicated management page</a>
    </div>

  def init = {
    PluginLogger.info(s"loading '${properties("plugin-id")}' plugin")
    LiftRules.statelessDispatch.append(DatasourcesConf.dataSourceApi9)
    // resources in src/main/resources/toserve must be allowed:
    ResourceServer.allow{
      case "datasources" :: _ => true
    }
  }

  def oneTimeInit : Unit = {}

  val configFiles = Seq()

  override def updateSiteMap(menus:List[Menu]) : List[Menu] = {
    val datasourceMenu = (
      Menu("dataSourceManagement", <span>Data sources</span>) /
        "secure" / "administration" / "dataSourceManagement"
        >> LocGroup("administrationGroup")
        >> TestAccess ( () => Boot.userIsAllowed("/secure/administration/policyServerManagement",Read("administration")) )
        >> Template(() => ClasspathTemplates( "template" :: "dataSourceManagement" :: Nil ) openOr <div>Template not found</div>)
    )

    menus.map {
      case m@Menu(l, _* ) if(l.name == "AdministrationHome") =>
        Menu(l , m.kids.toSeq :+ datasourceMenu:_* )
      case m => m
    }
  }

}
