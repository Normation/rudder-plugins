package com.normation.plugins.changevalidation

import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.AuthorizationType
import com.normation.rudder.users.CurrentUser
import com.normation.rudder.web.snippet.CommonLayout
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers.*
import scala.reflect.ClassTag
import scala.xml.NodeSeq

/**
 * Load the change-validation scripts, css and async comet actor : to display it in the top bar
 */
class TopBarExtension(val status: PluginStatus)(implicit val ttag: ClassTag[CommonLayout])
    extends PluginExtensionPoint[CommonLayout] with Loggable {

  def pluginCompose(snippet: CommonLayout): Map[String, NodeSeq => NodeSeq] = Map(
    "display" -> render
  )

  /**
   * User must have either or both of the Validator.Read and Deployer.Read authorizations
   * in order to display this.
   */
  def render(xml: NodeSeq) = {
    val isValidator = CurrentUser.checkRights(AuthorizationType.Validator.Read)
    val isDeployer  = CurrentUser.checkRights(AuthorizationType.Deployer.Read)
    if (isValidator || isDeployer) {
      (
        "#rudder-navbar -*" #>
        <head_merge>
          <script type="text/javascript" data-lift="with-cached-resource" src="/toserve/changevalidation/rudder-workflowinformation.js"></script>
          <link rel="stylesheet" type="text/css" href="/toserve/changevalidation/change-validation.css" media="screen" data-lift="with-cached-resource" />
        </head_merge>
        & "#rudder-navbar -*" #>
        <li class="lift:comet?type=WorkflowInformation" name="workflowInfo" ></li>
      ).apply(xml)
    } else xml
  }

}
