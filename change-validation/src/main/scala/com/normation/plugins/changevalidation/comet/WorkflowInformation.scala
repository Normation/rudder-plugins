/*
 *************************************************************************************
 * Copyright 2011-2013 Normation SAS
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

package com.normation.plugins.changevalidation.comet

import bootstrap.liftweb.RudderConfig
import com.normation.rudder.AuthorizationType
import com.normation.rudder.batch.AsyncWorkflowInfo
import com.normation.rudder.services.workflows.WorkflowUpdate
import com.normation.rudder.users.CurrentUser
import com.normation.zio.UnsafeRun
import net.liftweb.common.Empty
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.http.*
import scala.xml.*

class WorkflowInformation extends CometActor with CometListener with Loggable {

  private[this] val asyncWorkflow = RudderConfig.asyncWorkflowInfo

  private[this] val isValidator = CurrentUser.checkRights(AuthorizationType.Validator.Read)
  private[this] val isDeployer  = CurrentUser.checkRights(AuthorizationType.Deployer.Read)

  private[this] var workflowEnabledPrev = getWorkflowEnabled()
  private[this] var shouldLoadScript    = workflowEnabledPrev

  /** A user must have either or both of the Validator.Read and Deployer.Read authorizations
   * in order to display the pending change requests menu. */
  private[this] def hasRights()          = isValidator || isDeployer
  private[this] def getWorkflowEnabled() = {
    if (hasRights()) RudderConfig.configService.rudder_workflow_enabled().orElseSucceed(false).runNow else false
  }

  override def registerWith: AsyncWorkflowInfo = asyncWorkflow

  override val defaultHtml = NodeSeq.Empty

  def render: RenderOut = {

    if (!hasRights()) new RenderOut(NodeSeq.Empty)

    /* xml is a menu entry which looks like :

    <li class="nav-item dropdown notifications-menu" id="workflow-app">
      <a href="#" class="dropdown-toggle" data-bs-toggle="dropdown" role="button" aria-expanded="false">
        <span>CR</span>
        <span id="number" class="badge rudder-badge"></span>
      </a>
      <ul class="dropdown-menu" role="menu">
        <li>
          <ul class="menu">
            ... pending change requests by status here ...
          </ul>
        </li>
      </ul>
    </li>

    This menu is created by the WorkflowInformation Elm app.
     */

    val xml = {
      if (shouldLoadScript) {
        ("#workflow-app" #>
        <li id="workflow-app" class="nav-item dropdown notifications-menu">
          <script>
            //<![CDATA[
              // init elm app
              $(document).ready(function(){
                var node = document.getElementById("workflow-app");
                var initValues = {
                  contextPath : contextPath
                };
                var app  = Elm.WorkflowInformation.init({node: node, flags: initValues});

                app.ports.errorNotification.subscribe((errMsg) => {
                  createErrorNotification(errMsg)
                })
              });
              // ]]>
          </script>
        </li>).apply(<li class="nav-item dropdown notifications-menu" id="workflow-app"/>)
      } else NodeSeq.Empty
    }

    val fixedLayout = {
      <head_merge>
        <script type="text/javascript" data-lift="with-cached-resource" src="/toserve/changevalidation/rudder-workflowinformation.js"></script>
        <link rel="stylesheet" type="text/css" href="/toserve/changevalidation/change-validation.css" media="screen" data-lift="with-cached-resource" />
      </head_merge>
    }

    RenderOut(Full(xml), Full(fixedLayout), Empty, Empty, ignoreHtmlOnJs = false)
  }

  override def lowPriority = {
    case WorkflowUpdate =>
      if (!hasRights()) ()

      val workflowEnabled = getWorkflowEnabled()

      // The script should be loaded if the workflow_enabled setting has been enabled since the last render
      if ((!workflowEnabledPrev) && workflowEnabled) shouldLoadScript = true
      if (workflowEnabledPrev && (!workflowEnabled)) shouldLoadScript = false

      workflowEnabledPrev = workflowEnabled

      reRender()
  }
}
