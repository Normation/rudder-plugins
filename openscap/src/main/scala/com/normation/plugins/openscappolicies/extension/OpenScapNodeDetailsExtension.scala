package com.normation.plugins.openscappolicies.extension

import com.normation.inventory.domain.NodeId
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.plugins.openscappolicies.services.OpenScapReportReader
import com.normation.plugins.openscappolicies.services.ReportSanitizer
import com.normation.rudder.web.components.ShowNodeDetailsFromNode
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class OpenScapNodeDetailsExtension(
    val status:      PluginStatus,
    openScapReader:  OpenScapReportReader,
    reportSanitizer: ReportSanitizer
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
      val content = openScapReader.checkOpenScapReportExistence(nodeId) match {
        case eb: EmptyBox =>
          val e = eb ?~! "Can not display OpenScap report for that node"
          (<div class="error">
            {e.messageChain}
          </div>)
        case Full(existence) =>
          existence match {
            case false =>
              <div id="openScap" class="inner-portlet">
                  <h3 class="page-title">OpenSCAP reporting</h3>
                  <div class="col-xs-12 callout-fade callout-info">
                    <div class="marker">
                      <span class="glyphicon glyphicon-info-sign"></span>
                    </div>
                    <p>That tab gives access to OpenSCAP report configured for that node.</p>
                    <br/>
                    <div class="error">There are no OpenScap report available yet for node
                      {snippet.nodeId.value}
                    </div>
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
        case true  =>
          (
            "#NodeDetailsTabMenu *" #> { (x: NodeSeq) =>
              x ++ (
                <li>
                  <a href="#openscap_reports" class="ui-tabs-panel ui-corner-bottom">
                    {tabTitle}
                  </a>
                </li>
              )
            } &
            "#node_logs" #> { (x: NodeSeq) =>
              x ++ (<div id="openscap_reports">
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

  def frameContent(nodeId: NodeId): CssSel = {

    "iframe [src]" #> s"/secure/api/openscap/report/${nodeId.value}" &
    "a [href]" #> s"/secure/api/openscap/report/${nodeId.value}"

  }

  private def openScapExtensionXml = {
    <div id="openScap" class="inner-portlet">
        <h3 class="page-title">OpenSCAP reporting</h3>
        <div class="col-xs-12 callout-fade callout-info">
          <div class="marker">
            <span class="glyphicon glyphicon-info-sign"></span>
          </div>
          <p>That tab gives access to OpenSCAP report configured for that node. Below is the raw report as sent by the node.</p>
          <br/>
          <p><b><a href="">You can also download this report here</a></b></p>
        </div>
        <iframe width="100%" height="600"></iframe>
      </div>
  }
}
