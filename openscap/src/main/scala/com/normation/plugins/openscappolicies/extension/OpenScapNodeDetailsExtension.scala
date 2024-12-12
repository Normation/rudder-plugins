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

package com.normation.plugins.openscappolicies.extension

import com.normation.inventory.domain.NodeId
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.plugins.openscappolicies.services.OpenScapReportReader
import com.normation.rudder.web.components.ShowNodeDetailsFromNode
import com.normation.zio.*
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers.*
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class OpenScapNodeDetailsExtension(
    val status:     PluginStatus,
    openScapReader: OpenScapReportReader
)(implicit val ttag: ClassTag[ShowNodeDetailsFromNode])
    extends PluginExtensionPoint[ShowNodeDetailsFromNode] with Loggable {

  def pluginCompose(snippet: ShowNodeDetailsFromNode): Map[String, NodeSeq => NodeSeq] = Map(
    "popupDetails" -> addOpenScapReportTab(snippet) _,
    "mainDetails"  -> addOpenScapReportTab(snippet) _
  )

  /**
   * Add a tab:
   * - add an li in ul with id=openScapExtensionTab
   */
  def addOpenScapReportTab(snippet: ShowNodeDetailsFromNode)(xml: NodeSeq): NodeSeq = {
    // Actually extend
    def display(): NodeSeq = {
      val nodeId  = snippet.nodeId
      val content = openScapReader.getOpenScapReportFile(nodeId).either.runNow match {
        case Left(err) =>
          val e = s"Can not display OpenSCAP report for that node: ${err.fullMsg}"
          <div class="error">{e}</div>

        case Right(opt) =>
          opt match {
            case None =>
              <div id="openScap" class="inner-portlet">
                <h3 class="page-title mt-0">OpenSCAP reporting</h3>
                <div class="col-sm-12 callout-fade callout-info">
                  <div class="marker">
                    <span class="fa fa-info-circle"></span>
                  </div>
                  <p>That tab gives access to OpenSCAP report configured for that node.</p>
                  <br/>
                  <div class="error">There are no OpenSCAP report available yet for node
                    {snippet.nodeId.value}
                  </div>
                </div>
              </div>

            case Some((hostname, _)) =>
              frameContent(snippet.nodeId, hostname)(openScapExtensionXml)
          }
      }

      val tabTitle = "OpenSCAP"

      status.isEnabled() match {
        case false =>
          <div class="error">Plugin is disabled</div>
        case true  =>
          (
            "#NodeDetailsTabMenu *" #> { (x: NodeSeq) =>
              x ++ (
                <li class="nav-item">
                  <button class="nav-link" data-bs-toggle="tab" data-bs-target="#openscap_reports" type="button" role="tab" aria-controls="openscap_reports">
                    {tabTitle}
                  </button>
                </li>
              )
            } &
            "#node_logs" #> { (x: NodeSeq) =>
              x ++ (<div id="openscap_reports" class="tab-pane">
                    {content}
                   </div>)
            }
          ).apply(xml)
      }
    }

    openScapReader.checkifOpenScapApplied(snippet.nodeId) match {
      case Full(true) => display()
      case _          => xml
    }

  }

  def frameContent(nodeId: NodeId, hostname: String): CssSel = {

    "iframe [src]" #> s"/secure/api/openscap/sanitized/${nodeId.value}" &
    ".sanitized [href]" #> s"/secure/api/openscap/sanitized/${nodeId.value}" &
    ".original [href]" #> s"/secure/api/openscap/report/${nodeId.value}" &
    ".original [download]" #> s"OpenSCAP report for ${hostname} (${nodeId.value}).html"

  }

  private def openScapExtensionXml = {
    <div id="openScap" class="inner-portlet">
        <h3 class="page-title">OpenSCAP reporting</h3>
        <div class="col-xs-12 callout-fade callout-info">
          <div class="marker">
            <span class="fa fa-info-circle"></span>
          </div>
          <p>That tab gives access to OpenSCAP report configured for that node. Below is the raw report as sent by the node.</p>
          <br/>
          <p><b><a class="sanitized" href=""  target="_blank">Open sanitized report in a new tab</a></b></p>
          <p><a class="original" href="" download="">You can also download the original report, with JS enabled, here</a></p>
        </div>
        <iframe width="100%" height="600"></iframe>
      </div>
  }
}
