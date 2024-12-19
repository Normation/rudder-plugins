/*
 *************************************************************************************
 * Copyright 2017 Normation SAS
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

import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.web.ChooseTemplate
import com.normation.rudder.web.snippet.administration.Settings
import net.liftweb.http.LiftRules
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class BrandingResources(val status: PluginStatus)(implicit val ttag: ClassTag[Settings]) extends PluginExtensionPoint[Settings] {

  private[this] def link(s: String) = s"/${LiftRules.resourceServerPath}/branding/${s}"

  private def template: NodeSeq = ChooseTemplate("template" :: "brandingManagement" :: Nil, "component-body")

  override def pluginCompose(snippet: Settings): Map[String, NodeSeq => NodeSeq] = Map(
    "body" -> Settings.addTab("brandingTab", "Branding configuration", template),
    "css"  -> ((_: NodeSeq) => <link type="text/css" rel="stylesheet" href={link("rudder-branding.css")} ></link>),
    "js"   -> ((_: NodeSeq) => <script type="text/javascript" src={link("rudder-branding.js")}></script>)
  )
}
