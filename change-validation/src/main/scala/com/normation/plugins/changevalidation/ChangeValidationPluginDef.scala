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

package com.normation.plugins.changevalidation

import bootstrap.liftweb.Boot
import bootstrap.liftweb.Boot.redirection
import bootstrap.rudder.plugin.ChangeValidationConf
import com.normation.plugins._
import com.normation.rudder.AuthorizationType
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.web.model.CurrentUser
import net.liftweb.common.Empty
import net.liftweb.common.Full
import net.liftweb.http.ClasspathTemplates
import net.liftweb.http.GetRequest
import net.liftweb.http.LiftRules
import net.liftweb.http.ParsePath
import net.liftweb.http.RedirectWithState
import net.liftweb.http.RewriteRequest
import net.liftweb.http.RewriteResponse
import net.liftweb.sitemap.Loc.Hidden
import net.liftweb.sitemap.Loc.LocGroup
import net.liftweb.sitemap.Loc.Template
import net.liftweb.sitemap.Loc.TestAccess
import net.liftweb.sitemap.LocPath.stringToLocPath
import net.liftweb.sitemap.Menu

class ChangeValidationPluginDef(override val status: PluginStatus) extends DefaultPluginDef {

  override val basePackage = "com.normation.plugins.changevalidation"

  override def init = {
    // URL rewrites
    LiftRules.statefulRewrite.append {
      case RewriteRequest(ParsePath("secure" :: "plugins" :: "changes" :: "changeRequests" :: filter :: Nil, _, _, _), GetRequest, _) => {
        RewriteResponse("secure" :: "plugins" :: "changes" :: "changeRequests" :: Nil, Map("filter" -> filter))
      }
      case RewriteRequest(ParsePath("secure" ::"plugins" :: "changes" :: "changeRequest" :: crId :: Nil, _, _, _), GetRequest, _) =>
        RewriteResponse("secure" :: "plugins" :: "changes" :: "changeRequest" :: Nil, Map("crId" -> crId))
    }

    // init directory to save JSON
    ChangeValidationConf.supervisedTargetRepo.checkPathAndInitRepos()
  }

  override def apis: Option[LiftApiModuleProvider[_ <: EndpointSchema]] = Some(ChangeValidationConf.api)

  def oneTimeInit : Unit = {}

  val configFiles = Seq()

  /*
   * Menu for change validation is a little bit more complex than
   * usual plugins. We have the default plugin menu with admin rights, and a
   * "change request management" menu, that is hidden, but has special rights
   */
  // manage change request
  val changeRequestValidationMenu: Menu = {
    def canViewPage = CurrentUser.checkRights(AuthorizationType.Validator.Read) ||
                      CurrentUser.checkRights(AuthorizationType.Deployer.Read)

    (Menu("changeRequests", <span>Change Requests</span>) /
      "secure" / "plugins" / "changes"
      >> LocGroup("changeValidation")
      >> Hidden
      >> TestAccess ( () =>
        if (status.isEnabled() && canViewPage)
          Empty
        else
          Full(RedirectWithState("/secure/utilities/eventLogs", redirection ) )
      )
    ).submenus(
        Menu("changeRequestsList", <span>Change requests</span>) /
          "secure" / "plugins" / "changes" / "changeRequests"
          >> Hidden
          >> Template(() => ClasspathTemplates("template" :: "changeRequests" :: Nil ) openOr <div>Template not found</div>)
      , Menu("changeRequestDetails", <span>Change request</span>) /
          "secure" / "plugins" / "changes" / "changeRequest"
          >> Hidden
          >> Template(() => ClasspathTemplates("template" :: "changeRequest" :: Nil ) openOr <div>Template not found</div>)
    )
  }

  override def pluginMenuEntry: Option[Menu] = {
    Some(
      Menu("changeValidationManagement", <span>Change validation</span>) /
      "secure" / "plugins" / "changeValidationManagement"
      >> LocGroup("pluginsGroup")
      >> TestAccess ( () => Boot.userIsAllowed("/secure/index", AuthorizationType.Administration.Read))
      >> Template(() => ClasspathTemplates("template" :: "ChangeValidationManagement" :: Nil ) openOr <div>Template not found</div>)
    )
  }

  override def updateSiteMap(menus: List[Menu]): List[Menu] = {
    super.updateSiteMap(menus) :+ changeRequestValidationMenu
  }
}
