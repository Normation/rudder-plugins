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

package com.normation.plugins.nodeexternalreports.extension

import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.plugins.nodeexternalreports.service.NodeExternalReport
import com.normation.plugins.nodeexternalreports.service.ReadExternalReports
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.users.CurrentUser
import com.normation.rudder.web.components.ShowNodeDetailsFromNode
import com.normation.rudder.web.snippet.TabUtils
import net.liftweb.common.*
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers.*
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class CreateNodeDetailsExtension(externalReport: ReadExternalReports, val status: PluginStatus)(implicit
    val ttag: ClassTag[ShowNodeDetailsFromNode]
) extends PluginExtensionPoint[ShowNodeDetailsFromNode] with TabUtils {

  def pluginCompose(snippet: ShowNodeDetailsFromNode): Map[String, NodeSeq => NodeSeq] = Map(
    "mainDetails" -> addExternalReportTab(snippet) _
  )

  override def tabMenuId: String = "NodeDetailsTabMenu"

  override def tabContentId: String = "NodeDetailsTabContent"

  private val tabId = "externalReportTab"

  private def addExternalReportTab(snippet: ShowNodeDetailsFromNode)(xml: NodeSeq): NodeSeq = {
    implicit val qc: QueryContext = CurrentUser.queryContext // bug https://issues.rudder.io/issues/26605
    (externalReport.getExternalReports(snippet.nodeId) match {
      case eb: EmptyBox =>
        val e = eb ?~! "Can not display external reports for that node"
        addTab(tabId, "External reports", <div class="error">{e.messageChain}</div>)
      case Full(config) =>
        addTab(tabId, config.tabTitle, tabContent(config.reports)(myXml))
    })(xml)
  }

  def tabContent(reports: Map[String, NodeExternalReport]): CssSel = {

    ".nodeReports" #> reports.map {
      case (key, report) =>
        (
          ".reportTitle *" #> report.title
          & ".reportDescription *" #> report.description
          & ".reportLink" #> (report.fileName match {
            case None    =>
              <span>No report of that type is available</span>
            case Some(f) =>
              <a href={
                s"/secure/nodeManager/externalInformation/${urlEncode(key)}/${urlEncode(f)}/raw"
              } target="_blank">Display report in a new window</a>
          })
        )
    }
  }

  private def myXml = {
    <div class="inner-portlet">
      <h3>External reports configured for that node</h3>

      <div class="nodeReports">
        <br />
        <h3 class="reportTitle">[title]</h3>
        <div class="intro reportDescription">[description]</div>
        <p class="reportLink">[the link to report</p>
      </div>
    </div>
  }

}
