/*
*************************************************************************************
* Copyright 2018 Normation SAS
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

package bootstrap.rudder.plugin

import bootstrap.liftweb.RudderConfig
import com.normation.eventlog.EventActor
import com.normation.plugins.PluginStatus
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.changevalidation.ChangeValidationPluginDef
import com.normation.plugins.changevalidation.CheckRudderPluginEnableImpl
import com.normation.plugins.changevalidation.FiveFirstTargetForChangeRequest
import com.normation.plugins.changevalidation.NodeGroupValidationNeeded
import com.normation.plugins.changevalidation.TopBarExtension
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl
import com.normation.plugins.changevalidation.ValidationNeeded
import com.normation.rudder.services.workflows.DirectiveChangeRequest
import com.normation.rudder.services.workflows.GlobalParamChangeRequest
import com.normation.rudder.services.workflows.NodeGroupChangeRequest
import com.normation.rudder.services.workflows.RuleChangeRequest
import com.normation.rudder.services.workflows.WorkflowLevelService
import com.normation.rudder.services.workflows.WorkflowService
import net.liftweb.common.Box



/*
 * The validation workflow level
 */
class ChangeValidationWorkflowLevelService(
    status                   : PluginStatus
  , defaultWorkflowService   : WorkflowService
  , validationWorkflowService: WorkflowService
  , validationNeeded         : ValidationNeeded
  , workflowEnabledByUser    : () => Box[Boolean]
) extends WorkflowLevelService {


  override def workflowLevelAllowsEnable: Boolean = status.isEnabled()

  override def workflowEnabled: Boolean = {
    workflowLevelAllowsEnable && workflowEnabledByUser().getOrElse(false)
  }
  override def name: String = "Change Request Validation Workflows"

  override def getWorkflowService(): WorkflowService = if(workflowEnabled) validationWorkflowService else defaultWorkflowService

  override def getForRule(actor: EventActor, change: RuleChangeRequest): Box[WorkflowService] = {
    for {
      need <- validationNeeded.forRule(actor, change)
    } yield {
      if(need && workflowEnabled) {
        validationWorkflowService
      } else {
        defaultWorkflowService
      }
    }
  }

  override def getForDirective(actor: EventActor, change: DirectiveChangeRequest): Box[WorkflowService] = ???
  override def getForNodeGroup(actor: EventActor, change: NodeGroupChangeRequest): Box[WorkflowService] = ???
  override def getForGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): Box[WorkflowService] = ???
}

/*
 * Actual configuration of the plugin logic
 */
object ChangeValidationConf extends RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl()


  lazy val validationWorkflowService = new TwoValidationStepsWorkflowServiceImpl(
      RudderConfig.workflowEventLogService
    , RudderConfig.commitAndDeployChangeRequest
    , RudderConfig.roWorkflowRepository
    , RudderConfig.woWorkflowRepository
    , RudderConfig.asyncWorkflowInfo
    , RudderConfig.configService.rudder_workflow_self_validation _
    , RudderConfig.configService.rudder_workflow_self_deployment _
  )

  // other service instanciation / initialization
  RudderConfig.workflowLevelService.overrideLevel(
    new ChangeValidationWorkflowLevelService(
        pluginStatusService
      , RudderConfig.workflowLevelService.defaultWorkflowService
      , new TwoValidationStepsWorkflowServiceImpl(
            RudderConfig.workflowEventLogService
          , RudderConfig.commitAndDeployChangeRequest
          , RudderConfig.roWorkflowRepository
          , RudderConfig.woWorkflowRepository
          , RudderConfig.asyncWorkflowInfo
          , RudderConfig.configService.rudder_workflow_self_validation _
          , RudderConfig.configService.rudder_workflow_self_deployment _
        )
      , new NodeGroupValidationNeeded(
            new FiveFirstTargetForChangeRequest(RudderConfig.roNodeGroupRepository)
          , RudderConfig.roChangeRequestRepository
          , RudderConfig.roRuleRepository
        )
      , RudderConfig.configService.rudder_workflow_enabled _
    )
  )

  lazy val pluginDef = new ChangeValidationPluginDef(pluginStatusService)

  RudderConfig.snippetExtensionRegister.register(new TopBarExtension(pluginStatusService))
}
