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
import com.normation.rudder.AuthorizationType.Administration
import com.normation.plugins._
import net.liftweb.http.ClasspathTemplates
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.sitemap.Loc.Template
import net.liftweb.sitemap.Loc.TestAccess
import net.liftweb.sitemap.LocPath.stringToLocPath
import net.liftweb.sitemap.Menu
import com.normation.plugins.PluginStatus
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.lift.LiftApiModuleProvider

class DataSourcesPluginDef(override val status: PluginStatus) extends DefaultPluginDef {

  override val basePackage = "com.normation.plugins.datasources"

  override def apis: Option[LiftApiModuleProvider[_ <: EndpointSchema]] = Some(DatasourcesConf.dataSourceApi9)

  def init = {}

  def oneTimeInit : Unit = {}

  val configFiles = Seq()

  override def updateSiteMap(menus:List[Menu]) : List[Menu] = {
    val datasourceMenu = (
      Menu("dataSourceManagement", <span>Data sources</span>) /
        "secure" / "administration" / "dataSourceManagement"
        >> LocGroup("administrationGroup")
        >> TestAccess ( () => Boot.userIsAllowed("/secure/administration/policyServerManagement", Administration.Read) )
        >> Template(() => ClasspathTemplates( "template" :: "dataSourceManagement" :: Nil ) openOr <div>Template not found</div>)
    )

    menus.map {
      case m@Menu(l, _* ) if(l.name == "AdministrationHome") =>
        Menu(l , m.kids.toSeq :+ datasourceMenu:_* )
      case m => m
    }
  }

}
