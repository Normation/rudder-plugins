package com.normation.plugins.helloworld.extension

import com.normation.plugins.{SnippetExtensionKey,SnippetExtensionPoint}
import com.normation.rudder.web.components.popup.CreateOrCloneRulePopup
import scala.xml.NodeSeq
import com.normation.rudder.web.snippet.configuration.RuleManagement
import com.normation.rudder.web.components.RuleEditForm
import net.liftweb.util._
import net.liftweb.util.Helpers._
import com.normation.rudder.domain.policies.Rule
import com.normation.rudder.services.policies.RuleTargetService
import bootstrap.liftweb.LiftSpringApplicationContext.inject
import net.liftweb.common._
import com.normation.plugins.helloworld.service.LogAccessInDb
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.joda.time.DateTime
import com.normation.rudder.web.components.DateFormaterService

class CreateRuleExtension extends SnippetExtensionPoint[RuleManagement] with Loggable {

  val extendsAt = SnippetExtensionKey(classOf[RuleManagement].getSimpleName)

  def compose(snippet:RuleManagement) : Map[String, NodeSeq => NodeSeq] = Map(
      "head" -> display _
    , "viewRules" -> viewRules _
  )


  def display(xml:NodeSeq) = {
    logger.info("display: I'm called !!!!!!!!!!" )
    xml
  }

  def viewRules(xml:NodeSeq) : NodeSeq = {
    logger.info("viewRules: I'm called !!!!!!!!!!" )
    xml
  }

}


class CreateRuleEditFormExtension(
      targetInfoService :RuleTargetService
    , dbLogService      : LogAccessInDb
  ) extends SnippetExtensionPoint[RuleEditForm] with Loggable {

  val extendsAt = SnippetExtensionKey(classOf[RuleEditForm].getSimpleName)

  def compose(snippet:RuleEditForm) : Map[String, NodeSeq => NodeSeq] = Map(
      "showForm" -> addAnOtherTab(snippet) _
  )

  /**
   * Add a tab:
   * - add an li in ul with id=ruleDetailsTabMenu
   * - add the actual tab after the div with id=ruleDetailsEditTab
   */
  def addAnOtherTab(snippet:RuleEditForm)(xml:NodeSeq) = {
    logger.info("I'm called !!!!!!!!!!" )

    //add a log entry
    dbLogService.logAccess(SecurityContextHolder.getContext.getAuthentication.getPrincipal match {
      case u:UserDetails =>  u.getUsername
      case x => "unknown user"
    })

    (
      "#ruleDetailsTabMenu *" #> { (x:NodeSeq) => x ++  (
        <li><a href="#anOtherTab">An other tab, add by a plugin</a></li>
        <li><a href="#aLogTab">Access log for that cr</a></li>
      )} &
      "#ruleDetailsEditTab" #> { (x:NodeSeq) => x ++
          tabContent(snippet.rule)(myXml) ++
          logTabContent(logXml)
      }
    )(xml)
  }

  def tabContent(rule:Rule) = {
    "#ruleInfos" #> rule.name &
    "#nodeListTableForRule" #> { xml:NodeSeq =>
      (".nodeId" #> rule.targets.map(target => <tr><td>{target.target}</td></tr>)).apply(xml)
    }
  }

  private val myXml =
    <div id="anOtherTab">
      <h3>This tab list all the node on wich that rule is applied</h3>
      <br/>
      <p>This is an other tab which is provided by a plugin. Cool, isn't it ?</p>
      <br/>
      <div>
        We are currently processing <span id="ruleInfos"/>
      </div>
      <table id="nodeListTableForRule">
        <thead>
          <tr><th>Node ID</th></tr>
        </thead>
        <tbody>
          <tr class="nodeId">
            <td>[Here comes the node id]</td>
          </tr>
        </tbody>
      </table>
    </div>


  def logTabContent = {
    ".nodeId" #> dbLogService.getLog( (DateTime.now).withYear(2000), (DateTime.now).plusDays(1) ).map { log =>
      val date = DateFormaterService.getFormatedDate(new DateTime(log.date.getTime))
      <tr><td>{log.id.toString}</td><td>{log.user}</td><td>{date}</td></tr>
    }
  }

  private val logXml =
    <div id="aLogTab">
      <h3>This tab list all access date and time to that CR</h3>
      <br/>
      <table>
        <thead>
          <tr><th>id</th><th>user</th><th>date</th></tr>
        </thead>
        <tbody>
          <tr class="nodeId">
            <td>[Here comes the log id]</td>
            <td>[Here comes the log user]</td>
            <td>[Here comes the log date]</td>
          </tr>
        </tbody>
      </table>
    </div>

}