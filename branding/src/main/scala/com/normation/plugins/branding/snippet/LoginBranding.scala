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


package com.normation.plugins.branding.snippet

import bootstrap.rudder.plugin.BrandingPluginConf
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.plugins.PluginVersion
import com.normation.rudder.web.snippet.Login
import net.liftweb.common.{Full, Loggable}
import net.liftweb.util.Helpers._

import scala.reflect.ClassTag
import scala.xml.NodeSeq


class LoginBranding(val status: PluginStatus, version: PluginVersion)(implicit val ttag: ClassTag[Login]) extends PluginExtensionPoint[Login] with Loggable {

  def pluginCompose(snippet:Login) : Map[String, NodeSeq => NodeSeq] = Map(
    "display" -> guard(display(_))
  )


  private [this] val confRepo = BrandingPluginConf.brandingConfService
  def display(xml:NodeSeq) = {
    val data = confRepo.getConf
    val legend =
      <p class="legend col-xs-12">
        <img src="/images/login/logo-rudder.svg" data-lift="with-cached-resource" alt="Rudder"></img>
        <span>{version.prefix.replaceAll("-", "")}</span>
      </p>
    val bar = data match {
      case Full(data) if (data.displayBarLogin) =>
        <div id="headerBar">
          <div class="background">
            <span>{if (data.displayLabel) data.labelText}</span>
          </div>
          <style>
            #login-page form p.legend {{margin-bottom: 0px; }}
            #headerBar {{background-color: #fff; float: left;height: 30px;width:100%;margin-bottom: 30px; position: relative;}}
            #headerBar > .background {{background-color: {data.barColor.toRgba}; color: {data.labelColor.toRgba}; font-size:20px; text-align:center; font-weight: 700; position: absolute; ;top: 0;bottom: 0;left: 0;right: 0;}}
          </style>
        </div>
      case _ => NodeSeq.Empty
    }
    val legendBar = legend ++ bar
    val motd = data match {
      case Full(data) if (data.displayMotd) =>
       data.motd
      case _ => "Welcome, please sign in:"
    }
    ( ".legend"    #> legendBar &
      ".welcome *" #> motd
    ) (xml)
  }
}
