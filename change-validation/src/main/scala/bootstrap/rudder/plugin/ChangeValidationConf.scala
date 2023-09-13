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
import bootstrap.liftweb.RudderConfig.commitAndDeployChangeRequest
import bootstrap.liftweb.RudderConfig.doobie
import bootstrap.liftweb.RudderConfig.restDataSerializer
import bootstrap.liftweb.RudderConfig.restExtractorService
import bootstrap.liftweb.RudderConfig.techniqueRepository
import bootstrap.liftweb.RudderConfig.workflowLevelService

import com.normation.box._
import com.normation.eventlog.EventActor
import com.normation.plugins.PluginStatus
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.changevalidation.ChangeRequestMapper
import com.normation.plugins.changevalidation.ChangeValidationPluginDef
import com.normation.plugins.changevalidation.CheckRudderPluginEnableImpl
import com.normation.plugins.changevalidation.EmailNotificationService
import com.normation.plugins.changevalidation.NodeGroupValidationNeeded
import com.normation.plugins.changevalidation.NotificationService
import com.normation.plugins.changevalidation.RoChangeRequestJdbcRepository
import com.normation.plugins.changevalidation.RoChangeRequestRepository
import com.normation.plugins.changevalidation.RoValidatedUserJdbcRepository
import com.normation.plugins.changevalidation.RoValidatedUserRepository
import com.normation.plugins.changevalidation.RoWorkflowJdbcRepository
import com.normation.plugins.changevalidation.TopBarExtension
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl
import com.normation.plugins.changevalidation.UnsupervisedTargetsRepository
import com.normation.plugins.changevalidation.ValidatedUserMapper
import com.normation.plugins.changevalidation.ValidationNeeded
import com.normation.plugins.changevalidation.WoChangeRequestJdbcRepository
import com.normation.plugins.changevalidation.WoChangeRequestRepository
import com.normation.plugins.changevalidation.WoValidatedUserJdbcRepository
import com.normation.plugins.changevalidation.WoValidatedUserRepository
import com.normation.plugins.changevalidation.WoWorkflowJdbcRepository
import com.normation.plugins.changevalidation.api.ChangeRequestApi
import com.normation.plugins.changevalidation.api.ChangeRequestApiImpl
import com.normation.plugins.changevalidation.api.SupervisedTargetsApi
import com.normation.plugins.changevalidation.api.SupervisedTargetsApiImpl
import com.normation.plugins.changevalidation.api.ValidatedUserApi
import com.normation.plugins.changevalidation.api.ValidatedUserApiImpl
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.services.workflows.DirectiveChangeRequest
import com.normation.rudder.services.workflows.GlobalParamChangeRequest
import com.normation.rudder.services.workflows.NodeGroupChangeRequest
import com.normation.rudder.services.workflows.RuleChangeRequest
import com.normation.rudder.services.workflows.WorkflowLevelService
import com.normation.rudder.services.workflows.WorkflowService

import java.nio.file.Paths
import net.liftweb.common.Box
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full

/*
 * The validation workflow level
 */
class ChangeValidationWorkflowLevelService(
    status:                    PluginStatus,
    defaultWorkflowService:    WorkflowService,
    validationWorkflowService: TwoValidationStepsWorkflowServiceImpl,
    validationNeeded:          Seq[ValidationNeeded],
    workflowEnabledByUser:     () => Box[Boolean],
    validatedUserRepo:         RoValidatedUserRepository
) extends WorkflowLevelService {

  override def workflowLevelAllowsEnable: Boolean = status.isEnabled()

  override def workflowEnabled: Boolean = {
    workflowLevelAllowsEnable && workflowEnabledByUser().getOrElse(false)
  }
  override def name:            String  = "Change Request Validation Workflows"

  override def getWorkflowService(): WorkflowService = if (workflowEnabled) validationWorkflowService else defaultWorkflowService

  /*
   * return the correct workflow given the "needed" check. Also check
   * for the actual status of workflow to decide what workflow to use.
   */
  private[this] def getWorkflow(shouldBeNeeded: Box[Boolean]): Box[WorkflowService] = {
    for {
      need <- shouldBeNeeded
    } yield {
      if (need && workflowEnabled) {
        validationWorkflowService
      } else {
        defaultWorkflowService
      }
    }
  }

  /**
   * Methode to use to combine several validationNeeded check.
   * Note that a validated user will prevent workflow to be performed, no other validationNeeded check will be executed
   */
  def combine[T](
      checkFn: (ValidationNeeded, EventActor, T) => Box[Boolean],
      checks:  Seq[ValidationNeeded],
      actor:   EventActor,
      change:  T
  ): Box[WorkflowService] = {
    def getWorkflowAux = {
      getWorkflow(validationNeeded.foldLeft(Full(false): Box[Boolean]) {
        case (shouldValidate, nextCheck) =>
          shouldValidate.flatMap {
            // logic is "or": if previous should validate is true, don't check following
            case true  => Full(true)
            case false => checkFn(nextCheck, actor, change)
          }
      })
    }

    /*
     * Here we check if there is a validated user that should not be subject to any validation workflow
     * if there is no validated user, we iterate over `checks: Seq[ValidationNeeded]` to verify if there
     * there is a specific workflow.
     *
     * Check why we decided to separate the validated user logic from `ValidationNeeded` objects :
     * https://issues.rudder.io/issues/22188#note-5
     */
    validatedUserRepo.get(actor) match {
      case Full(Some(e)) => getWorkflow(Full(false))
      case Full(None)    => getWorkflowAux
      case eb: EmptyBox => eb ?~ s"Could get user from validated user list when checking validation workflow"
    }
  }

  override def getForRule(actor: EventActor, change: RuleChangeRequest):               Box[WorkflowService] = {
    combine[RuleChangeRequest]((v, a, c) => v.forRule(a, c), validationNeeded, actor, change)
  }
  override def getForDirective(actor: EventActor, change: DirectiveChangeRequest):     Box[WorkflowService] = {
    combine[DirectiveChangeRequest]((v, a, c) => v.forDirective(a, c), validationNeeded, actor, change)
  }
  override def getForNodeGroup(actor: EventActor, change: NodeGroupChangeRequest):     Box[WorkflowService] = {
    combine[NodeGroupChangeRequest]((v, a, c) => v.forNodeGroup(a, c), validationNeeded, actor, change)
  }
  override def getForGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): Box[WorkflowService] = {
    combine[GlobalParamChangeRequest]((v, a, c) => v.forGlobalParam(a, c), validationNeeded, actor, change)
  }

  override def getByDirective(uid: DirectiveUid, onlyPending: Boolean): Box[Vector[ChangeRequest]] = {
    if (workflowEnabled) {
      validationWorkflowService.roChangeRequestRepository.getByDirective(uid, onlyPending)
    } else {
      Full(Vector())
    }
  }

  override def getByNodeGroup(id: NodeGroupId, onlyPending: Boolean): Box[Vector[ChangeRequest]] = {
    if (workflowEnabled) {
      validationWorkflowService.roChangeRequestRepository.getByNodeGroup(id, onlyPending)
    } else {
      Full(Vector())
    }
  }

  override def getByRule(id: RuleUid, onlyPending: Boolean): Box[Vector[ChangeRequest]] = {
    if (workflowEnabled) {
      validationWorkflowService.roChangeRequestRepository.getByRule(id, onlyPending)
    } else {
      Full(Vector())
    }
  }
}

/*
 * Actual configuration of the plugin logic
 */
object ChangeValidationConf extends RudderPluginModule {

  val migration = new MigrateSupervisedGroups(RudderConfig.roNodeGroupRepository, unsupervisedTargetRepo)
  // look if we were using supervised groups and need to use unsupervised ones.
  // can be removed in Rudder 8.0 since Rudder 7.3 only knows about unsupervised groups.
  migration.migrate()

  lazy val notificationService = new NotificationService(
    new EmailNotificationService(),
    RudderConfig.linkUtil,
    "/opt/rudder/etc/plugins/change-validation.conf"
  )
  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService = new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val roWorkflowRepository = new RoWorkflowJdbcRepository(RudderConfig.doobie)
  lazy val woWorkflowRepository = new WoWorkflowJdbcRepository(RudderConfig.doobie)

  lazy val validationWorkflowService = new TwoValidationStepsWorkflowServiceImpl(
    RudderConfig.workflowEventLogService,
    RudderConfig.commitAndDeployChangeRequest,
    roWorkflowRepository,
    woWorkflowRepository,
    RudderConfig.asyncWorkflowInfo,
    RudderConfig.stringUuidGenerator,
    RudderConfig.changeRequestEventLogService,
    roChangeRequestRepository,
    woChangeRequestRepository,
    notificationService,
    () => Full(RudderConfig.workflowLevelService.workflowEnabled),
    () => RudderConfig.configService.rudder_workflow_self_validation().toBox,
    () => RudderConfig.configService.rudder_workflow_self_deployment().toBox
  )

  lazy val unsupervisedTargetRepo = new UnsupervisedTargetsRepository(
    directory = Paths.get("/var/rudder/plugin-resources/" + pluginDef.shortName),
    filename = "unsupervised-targets.json"
  )
  lazy val roChangeRequestRepository: RoChangeRequestRepository = {
    new RoChangeRequestJdbcRepository(doobie, changeRequestMapper)
  }

  lazy val woChangeRequestRepository: WoChangeRequestRepository = {
    new WoChangeRequestJdbcRepository(doobie, changeRequestMapper, roChangeRequestRepository)
  }

  lazy val roValidatedUserRepository: RoValidatedUserJdbcRepository = {
    new RoValidatedUserJdbcRepository(doobie, validatedUserMapper)
  }

  lazy val woValidatedUserRepository: WoValidatedUserRepository = {
    new WoValidatedUserJdbcRepository(doobie, validatedUserMapper, roValidatedUserRepository)
  }

  val loadSupervisedTargets = () => {
    for {
      u <- unsupervisedTargetRepo.load()
      g <- RudderConfig.roNodeGroupRepository.getFullGroupLibrary().toBox
    } yield UnsupervisedTargetsRepository.invertTargets(u, g)
  }

  RudderConfig.workflowLevelService.overrideLevel(
    new ChangeValidationWorkflowLevelService(
      pluginStatusService,
      RudderConfig.workflowLevelService.defaultWorkflowService,
      validationWorkflowService,
      Seq(
        new NodeGroupValidationNeeded(
          loadSupervisedTargets,
          roChangeRequestRepository,
          RudderConfig.roRuleRepository,
          RudderConfig.roNodeGroupRepository,
          RudderConfig.nodeInfoService
        )
      ),
      () => RudderConfig.configService.rudder_workflow_enabled().toBox,
      roValidatedUserRepository
    )
  )

  lazy val changeRequestMapper =
    new ChangeRequestMapper(RudderConfig.changeRequestChangesUnserialisation, RudderConfig.changeRequestChangesSerialisation)

  lazy val validatedUserMapper = new ValidatedUserMapper()

  lazy val pluginDef = new ChangeValidationPluginDef(pluginStatusService)

  lazy val api = {
    val api1 = new SupervisedTargetsApiImpl(
      RudderConfig.restExtractorService,
      unsupervisedTargetRepo,
      RudderConfig.roNodeGroupRepository
    )
    val api2 = new ChangeRequestApiImpl(
      restExtractorService,
      roChangeRequestRepository,
      woChangeRequestRepository,
      roWorkflowRepository,
      woWorkflowRepository,
      techniqueRepository,
      workflowLevelService,
      commitAndDeployChangeRequest,
      restDataSerializer
    )
    val api3 = new ValidatedUserApiImpl(
      restExtractorService,
      roValidatedUserRepository,
      woValidatedUserRepository,
      restDataSerializer
    )
    new LiftApiModuleProvider[EndpointSchema] {
      override def schemas = new ApiModuleProvider[EndpointSchema] {
        override def endpoints = ValidatedUserApi.endpoints ::: SupervisedTargetsApi.endpoints ::: ChangeRequestApi.endpoints
      }

      override def getLiftEndpoints(): List[LiftApiModule] =
        api1.getLiftEndpoints() ::: api2.getLiftEndpoints() ::: api3.getLiftEndpoints()
    }
  }

  RudderConfig.snippetExtensionRegister.register(new TopBarExtension(pluginStatusService))
}
