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


package com.normation.plugins.authbackends.snippet

import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.plugins.PluginVersion
import com.normation.plugins.authbackends.LoginFormRendering
import com.normation.plugins.authbackends.RudderPropertyBasedOAuth2RegistrationDefinition
import com.normation.rudder.web.snippet.Login

import bootstrap.rudder.plugin.AuthBackendsConf
import net.liftweb.http.js._
import JsCmds._
import JE._
import net.liftweb.util.Helpers._

import scala.reflect.ClassTag
import scala.xml.NodeSeq

import com.normation.zio._


class Oauth2LoginBanner(val status: PluginStatus, version: PluginVersion, registrations: RudderPropertyBasedOAuth2RegistrationDefinition)(implicit val ttag: ClassTag[Login]) extends PluginExtensionPoint[Login] {


  /*
   * The url on which we need to redirect to get the authentication by oauth2 be triggered
   */
  def redirectUrl(registrationId: String): String = {
    "/oauth2/authorization/" + registrationId
  }

  def pluginCompose(snippet:Login) : Map[String, NodeSeq => NodeSeq] = Map(
    "display" -> guard(display(_))
  )

  def display(xml: NodeSeq): NodeSeq = {

    val rgs = registrations.registrations.get.runNow

    val oauth2providers = if(rgs.nonEmpty) {
      <div class="col-xs-12">
        <h4 class="welcome col-xs-12">Log using OAUTH2 provider:</h4>
        {
           rgs.map { case (id, r) =>
            <div class="form-group col-xs-12">
             <label for="valid" class="sr-only">Sign in with Okta provider</label>
             <div class="input-group col-xs-12">
               <a class="btn btn-warning-rudder col-xs-12" href={ redirectUrl(id) } role="button">{ s"${r.infoMsg} (${r.registration.getClientName})" }</a>
             </div>
           </div>
           }
        }
      </div>
    } else {
      <div id="oauth2providers">
        <div class="col-xs-12">
          <h4 class="welcome col-xs-12">OAUTH2 backend is enabled, but no providers are correctly configured</h4>
        </div>
      </div>
    }

    /*
     * The form can be
     */
    val selector = (
      "#contact"     #> ((x:NodeSeq) =>  AuthBackendsConf.loginFormRendering match {
                          case LoginFormRendering.Show   => x
                          case LoginFormRendering.Hide   =>  // indentation is strange because need to be saw as one xml
                            Script(OnLoad(JsRaw(""" $ ("#toggleLoginFormButton").click(function() {{ $("#toggleLoginForm").toggle(); }}); """))) ++
                            <button id="toggleLoginFormButton" class="btn btn-default btn-xs">show non-SSO login form
                            </button><div id="toggleLoginForm" style="display: none">{x}</div>
                          case LoginFormRendering.Remove => NodeSeq.Empty
                        }) &
      ".form-footer" #> ((x:NodeSeq) => oauth2providers ++ x)
    )

    selector(xml)
  }
}
