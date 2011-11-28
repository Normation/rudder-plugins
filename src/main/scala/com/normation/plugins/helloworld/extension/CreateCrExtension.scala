package com.normation.plugins.helloworld.extension

import com.normation.plugins.{SnippetExtensionKey,SnippetExtensionPoint}
import com.normation.rudder.web.components.popup.CreateConfigurationRulePopup
import scala.xml.NodeSeq
import com.normation.rudder.web.snippet.configuration.ConfigurationRuleManagement
import com.normation.rudder.web.components.ConfigurationRuleEditForm
import net.liftweb.util._
import net.liftweb.util.Helpers._
import com.normation.rudder.domain.policies.ConfigurationRule
import com.normation.rudder.services.policies.PolicyInstanceTargetService
import bootstrap.liftweb.LiftSpringApplicationContext.inject
import net.liftweb.common._
import com.normation.plugins.helloworld.service.LogAccessInDb
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.joda.time.DateTime
import com.normation.rudder.web.components.DateFormaterService

class CreateCrExtension extends SnippetExtensionPoint[ConfigurationRuleManagement] with Loggable {

  val extendsAt = SnippetExtensionKey(classOf[ConfigurationRuleManagement].getSimpleName)

  def compose(snippet:ConfigurationRuleManagement) : Map[String, NodeSeq => NodeSeq] = Map(
      "head" -> display _
    , "viewConfigurationRules" -> viewConfigurationRules _
  )
  
  
  def display(xml:NodeSeq) = {
    logger.info("display: I'm called !!!!!!!!!!" )
    xml
  }
  
  def viewConfigurationRules(xml:NodeSeq) : NodeSeq = {
    logger.info("viewConfigurationRules: I'm called !!!!!!!!!!" )
    xml
  }

}


class CreateCrEditFormExtension(
    targetInfoService:PolicyInstanceTargetService,
    dbLogService: LogAccessInDb
  ) extends SnippetExtensionPoint[ConfigurationRuleEditForm] with Loggable {

  val extendsAt = SnippetExtensionKey(classOf[ConfigurationRuleEditForm].getSimpleName)

  def compose(snippet:ConfigurationRuleEditForm) : Map[String, NodeSeq => NodeSeq] = Map(
      "showForm" -> addAnOtherTab(snippet) _
  )
  
  /**
   * Add a tab: 
   * - add an li in ul with id=configRuleDetailsTabMenu
   * - add the actual tab after the div with id=configRuleDetailsEditTab
   */
  def addAnOtherTab(snippet:ConfigurationRuleEditForm)(xml:NodeSeq) = {
    logger.info("I'm called !!!!!!!!!!" )
    
    //add a log entry
    dbLogService.logAccess(SecurityContextHolder.getContext.getAuthentication.getPrincipal match {
      case u:UserDetails =>  u.getUsername 
      case x => "unknown user"
    })
    
    (
      "#configRuleDetailsTabMenu *" #> { x => x ++  (
        <li><a href="#anOtherTab">An other tab, add by a plugin</a></li> 
        <li><a href="#aLogTab">Access log for that cr</a></li> 
      )} &
      "#configRuleDetailsEditTab" #> { x => x ++
          tabContent(snippet.configurationRule)(myXml) ++
          logTabContent(logXml) 
      }
    )(xml)
  }

  def tabContent(cr:ConfigurationRule) = {
    "#cfInfos" #> cr.name &
    "#nodeListTableForCr" #> { xml:NodeSeq =>
      cr.target match {
        case None => <span>No target is defined for that configuration rule</span>
        case Some(target) => targetInfoService.getNodeIds(target) match {
          case e:EmptyBox => 
            logger.error("Error when trying to find node ids for target: " + target, e) 
            <span class="error">Error when trying to find target</span>
          case Full(ids) => (".nodeId" #> ids.map(s => <tr><td>{s.value}</td></tr>))(xml)
        }
      }
    }
  }

  private val myXml = 
    <div id="anOtherTab">
      <h3>This tab list all the node on wich that configuration rule is applied</h3>
      <br/>
      <p>This is an other tab which is provided by a plugin. Cool, isn't it ?</p>
      <br/>
      <div>
        We are currently processing <span id="cfInfos"/>
      </div>
      <table id="nodeListTableForCr">
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