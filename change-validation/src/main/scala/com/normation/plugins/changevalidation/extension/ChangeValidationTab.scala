/*
 *************************************************************************************
 * Copyright 2024 Normation SAS
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

package com.normation.plugins.changevalidation.extension

import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.web.ChooseTemplate
import com.normation.rudder.web.snippet.TabUtils.AddTabMode
import com.normation.rudder.web.snippet.TabUtils.AppendTab
import com.normation.rudder.web.snippet.administration.Settings
import net.liftweb.util.CssSel
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class ChangeValidationTab(val status: PluginStatus)(implicit val ttag: ClassTag[Settings])
    extends PluginExtensionPoint[Settings] {

  private val template = ChooseTemplate("template" :: "ChangeValidationManagement" :: Nil, "component-body")

  override def pluginCompose(snippet: Settings): Map[String, NodeSeq => NodeSeq] = Map(
    "body" -> addTabWithId("changeValidationTab", "changeValidationLinkTab", "Change validation", template)
  )

  def addTabWithId(tabId: String, linkId: String, name: String, content: NodeSeq, mode: AddTabMode = AppendTab): CssSel =
    mode(Settings.tabMenuId)(tabMenuWithId(tabId, linkId, name)) & Settings.addTabContent(tabId, content, mode)

  private def tabMenuWithId(tabId: String, linkId: String, name: String) = {
    <li class="nav-item" role="presentation">
      <button class="nav-link" data-bs-toggle="tab" id={linkId} data-bs-target={
      "#" + tabId
    } type="button" role="tab" aria-controls={tabId}>{name}</button>
    </li>
  }
}
