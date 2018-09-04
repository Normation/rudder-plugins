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
import com.normation.plugins._
import com.normation.rudder.AuthorizationType
import com.normation.rudder.AuthorizationType.Administration
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
  }

  def oneTimeInit : Unit = {}

  val configFiles = Seq()

  override def pluginMenuEntry: Option[Menu] = {
    val changeMenu = (
      Menu("changeValidationManagement", <span>Change Validation</span>) /
      "secure" / "plugins" / "changeValidationManagement"
      >> LocGroup("pluginsGroup")
      >> TestAccess ( () => Boot.userIsAllowed("/secure/index", Administration.Read))
      >> Template(() => ClasspathTemplates("template" :: "ChangeValidationManagement" :: Nil ) openOr <div>Template not found</div>)
    ) submenus (
        Menu("changeRequests", <span>Change requests</span>) /
          "secure" / "plugins" / "changes" / "changeRequests"
          >> LocGroup("utilitiesGroup")
          >> Template(() => ClasspathTemplates("template" :: "changeRequests" :: Nil ) openOr <div>Template not found</div>)
          >> TestAccess ( () =>
            if (status.isEnabled() && (CurrentUser.checkRights(AuthorizationType.Validator.Read) || CurrentUser.checkRights(AuthorizationType.Deployer.Read)))
              Empty
            else
              Full(RedirectWithState("/secure/utilities/eventLogs", redirection ) )
            )

      , Menu("changeRequest", <span>Change request</span>) /
          "secure" / "plugins" / "changes" / "changeRequest"
          >> Hidden
          >> Template(() => ClasspathTemplates("template" :: "changeRequest" :: Nil ) openOr <div>Template not found</div>)
          >> TestAccess ( () =>
            if (status.isEnabled() && (CurrentUser.checkRights(AuthorizationType.Validator.Read) || CurrentUser.checkRights(AuthorizationType.Deployer.Read)))
              Empty
            else
              Full(RedirectWithState("/secure/utilities/eventLogs", redirection) )
            )
    )

    Some(changeMenu)


  }

}
