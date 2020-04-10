package com.normation.plugins.openscappolicies.extension

import com.normation.plugins.openscappolicies.services.{OpenScapReportReader, ReportSanitizer}
import com.normation.plugins.{PluginExtensionPoint, PluginStatus}
import com.normation.rudder.web.components.ShowNodeDetailsFromNode
import net.liftweb.common.{EmptyBox, Full, Loggable}

import scala.reflect.ClassTag
import scala.xml.NodeSeq
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._
import com.normation.inventory.domain.NodeId
import net.liftweb.http.S

class OpenScapNodeDetailsExtension(
    val status: PluginStatus
  , openScapReader: OpenScapReportReader
  , reportSanitizer: ReportSanitizer)(implicit val ttag: ClassTag[ShowNodeDetailsFromNode]) extends PluginExtensionPoint[ShowNodeDetailsFromNode] with Loggable {

  def pluginCompose(snippet: ShowNodeDetailsFromNode) : Map[String, NodeSeq => NodeSeq] = Map(
      "popupDetails" -> addOpenScapReportTab(snippet) _
    , "mainDetails"  -> addOpenScapReportTab(snippet) _
  )

  /**
   * Add a tab:
   * - add an li in ul with id=openScapExtensionTab
   */
  def addOpenScapReportTab(snippet: ShowNodeDetailsFromNode)(xml: NodeSeq): NodeSeq = {
    // Actually extend
    def display(): NodeSeq = {
      val content = openScapReader.checkOpenScapReportExistence(snippet.nodeId) match {
        case eb: EmptyBox =>
          val e = eb ?~! "Can not display OpenScap report for that node"
          (<div class="error">
            {e.messageChain}
          </div>)
        case Full(existence) =>
          existence match {
            case false =>
              <div id="openscap_reports">
                <p>That tab gives access to OpenScap reports configured for that node</p>
                <div class="error">No OpenScap report available for node
                  {snippet.nodeId.value}
                </div>
              </div>
            case true =>
              frameContent(snippet.nodeId)(openScapExtensionXml)
          }
      }

      val tabTitle = "OpenSCAP"

      status.isEnabled() match {
        case false =>
          <div class="error">Plugin is disabled</div>
        case true =>
          (
            "#NodeDetailsTabMenu *" #> { (x: NodeSeq) =>
              x ++ (
                <li>
                  <a href="#openscap_reports">
                    {tabTitle}
                  </a>
                </li>
                  <li>
                    {content}
                  </li>
                )
            } &
              "#node_logs" #> <div>test</div>
            ) (xml)
      }
    }

    openScapReader.checkifOpenScapApplied(snippet.nodeId) match {
      case Full(true) => display()
      case _ => xml
    }

  }

  def frameContent(nodeId : NodeId): CssSel = {

        "iframe [src]"      #> s"${S.hostAndPath}/secure/api/openscap/sanitized/${nodeId.value}"

  }

  private def openScapExtensionXml =
    <div id="openscap_reports">
      <div id="openScapReports">
        <br />
        <div class="col-xs-12">
          <div class="page-title" id="agentPolicyMode">OpenSCAP reporting</div>
          <div class="col-xs-12 callout-fade callout-info">
            <div class="marker">
              <span class="glyphicon glyphicon-info-sign"></span>
            </div>
            <p>That tab gives access to OpenSCAP report configured for that node</p>
          </div>
          <iframe width="100%" height="500"></iframe>
        </div>


      </div>
    </div>
}
