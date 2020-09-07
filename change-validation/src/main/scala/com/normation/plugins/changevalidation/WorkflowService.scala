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

package com.normation.plugins.changevalidation

import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.rudder.batch.AsyncWorkflowInfo
import com.normation.rudder.domain.eventlog.AddChangeRequestDiff
import com.normation.rudder.domain.eventlog.ChangeRequestDiff
import com.normation.rudder.domain.eventlog.DeleteChangeRequestDiff
import com.normation.rudder.domain.eventlog.ModifyToChangeRequestDiff
import com.normation.rudder.domain.workflows._
import com.normation.rudder.services.eventlog.ChangeRequestEventLogService
import com.normation.rudder.services.eventlog.WorkflowEventLogService
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.services.workflows.NoWorkflowAction
import com.normation.rudder.services.workflows.WorkflowAction
import com.normation.rudder.services.workflows.WorkflowService
import com.normation.rudder.services.workflows.WorkflowUpdate
import com.normation.utils.StringUuidGenerator
import net.liftweb.common._
import com.normation.box._
import com.normation.errors._
import zio.UIO



/**
 * A proxy workflow service based on a runtime choice
 */
class EitherWorkflowService(cond: () => Box[Boolean], whenTrue: WorkflowService, whenFalse: WorkflowService) extends WorkflowService {

  //TODO: handle ERRORS for config!

  val name = "choose-active-validation-workflow"

  def current: WorkflowService = if(cond().getOrElse(false)) whenTrue else whenFalse

  override def startWorkflow(changeRequest: ChangeRequest, actor:EventActor, reason: Option[String]) : Box[ChangeRequestId] =
    current.startWorkflow(changeRequest, actor, reason)
  override def openSteps :List[WorkflowNodeId] =
    current.openSteps
  override def closedSteps :List[WorkflowNodeId] =
    current.closedSteps
  override def stepsValue :List[WorkflowNodeId] =
    current.stepsValue
  override def findNextSteps(currentUserRights: Seq[String], currentStep: WorkflowNodeId, isCreator: Boolean) : WorkflowAction =
    current.findNextSteps(currentUserRights, currentStep, isCreator)
  override def findBackSteps(currentUserRights: Seq[String], currentStep: WorkflowNodeId, isCreator: Boolean) : Seq[(WorkflowNodeId,(ChangeRequestId,EventActor, Option[String]) => Box[WorkflowNodeId])] =
    current.findBackSteps(currentUserRights, currentStep, isCreator)
  override def findStep(changeRequestId: ChangeRequestId) : Box[WorkflowNodeId] =
    current.findStep(changeRequestId)
  override def getAllChangeRequestsStep() : Box[Map[ChangeRequestId,WorkflowNodeId]] =
    current.getAllChangeRequestsStep
  override def isEditable(currentUserRights: Seq[String], currentStep: WorkflowNodeId, isCreator: Boolean): Boolean =
    current.isEditable(currentUserRights, currentStep, isCreator)
  override def isPending(currentStep:WorkflowNodeId): Boolean =
    current.isPending(currentStep)
  override def needExternalValidation(): Boolean = current.needExternalValidation()
}


object TwoValidationStepsWorkflowServiceImpl {
  case object Validation extends WorkflowNode {
    val id = WorkflowNodeId("Pending validation")
  }

  case object Deployment extends WorkflowNode {
    val id = WorkflowNodeId("Pending deployment")
  }

  case object Deployed extends WorkflowNode {
    val id = WorkflowNodeId("Deployed")
  }

  case object Cancelled extends WorkflowNode {
    val id = WorkflowNodeId("Cancelled")
  }

  val steps:List[WorkflowNode] = List(Validation,Deployment,Deployed,Cancelled)
}



class TwoValidationStepsWorkflowServiceImpl(
    workflowLogger                : WorkflowEventLogService
  , commit                        : CommitAndDeployChangeRequestService
  , roWorkflowRepo                : RoWorkflowRepository
  , woWorkflowRepo                : WoWorkflowRepository
  , workflowComet                 : AsyncWorkflowInfo
  , uuidGen                       : StringUuidGenerator
  , changeRequestEventLogService  : ChangeRequestEventLogService
  , val roChangeRequestRepository : RoChangeRequestRepository
  , woChangeRequestRepository     : WoChangeRequestRepository
  , notificationService           : NotificationService
  , workflowEnable                : () => Box[Boolean]
  , selfValidation                : () => Box[Boolean]
  , selfDeployment                : () => Box[Boolean]
) extends WorkflowService {
  import TwoValidationStepsWorkflowServiceImpl._

  val name = "two-steps-validation-workflow"


  def getItemsInStep(stepId: WorkflowNodeId) : Box[Seq[ChangeRequestId]] = roWorkflowRepo.getAllByState(stepId)

  val closedSteps : List[WorkflowNodeId] = List(Cancelled.id,Deployed.id)
  val openSteps : List[WorkflowNodeId] = List(Validation.id,Deployment.id)
  val stepsValue = steps.map(_.id)

  private[this] def saveAndLogChangeRequest(diff:ChangeRequestDiff,actor:EventActor,reason:Option[String]) = {
    val changeRequest = diff.changeRequest
    // We need to remap back to the original type to fetch the id of the CR created
    val save = diff match {
      case add:AddChangeRequestDiff         => woChangeRequestRepository.createChangeRequest(diff.changeRequest, actor, reason).map(AddChangeRequestDiff(_))
      case modify:ModifyToChangeRequestDiff => woChangeRequestRepository.updateChangeRequest(changeRequest, actor, reason).map(x => modify) // For modification the id is already correct
      case delete:DeleteChangeRequestDiff   => woChangeRequestRepository.deleteChangeRequest(changeRequest.id, actor, reason).map(DeleteChangeRequestDiff(_))
    }

    for {
      saved  <- save ?~! s"could not save change request ${changeRequest.info.name}"
      modId  =  ModificationId(uuidGen.newUuid)
      workflowEnable <- workflowEnable()
      logged <- if(workflowEnable) {
        changeRequestEventLogService.saveChangeRequestLog(modId, actor, saved, reason) ?~!
          s"could not save event log for change request ${saved.changeRequest.id} creation"
      } else {
        Full("OK, no workflow")
      }
    } yield { saved.changeRequest }
  }

  def updateChangeRequestInfo(
      oldChangeRequest : ChangeRequest
    , newInfo          : ChangeRequestInfo
    , actor            : EventActor
    , reason           : Option[String]
  ) : Box[ChangeRequest] = {
    val newCr = ChangeRequest.updateInfo(oldChangeRequest, newInfo)
    saveAndLogChangeRequest(ModifyToChangeRequestDiff(newCr,oldChangeRequest), actor, reason)
  }

  def findNextSteps(
      currentUserRights : Seq[String]
    , currentStep       : WorkflowNodeId
    , isCreator         : Boolean
  ) : WorkflowAction = {
    val authorizedRoles = currentUserRights.filter(role => (role == "validator" || role == "deployer"))
    //TODO: manage error for config !
    val canValid  = selfValidation().getOrElse(false) || !isCreator
    val canDeploy = selfDeployment().getOrElse(false) || !isCreator
    currentStep match {
      case Validation.id =>
        val validatorActions =
          if (authorizedRoles.contains("validator") && canValid)
            Seq((Deployment.id,stepValidationToDeployment _)) ++ {
              if(authorizedRoles.contains("deployer") && canDeploy)
                Seq((Deployed.id,stepValidationToDeployed _))
              else Seq()
            }
          else Seq()
        WorkflowAction("Validate",validatorActions )


      case Deployment.id =>
        val actions =
          if(authorizedRoles.contains("deployer") && canDeploy)
            Seq((Deployed.id,stepDeploymentToDeployed _))
          else Seq()
        WorkflowAction("Deploy",actions)
      case Deployed.id   => NoWorkflowAction
      case Cancelled.id  => NoWorkflowAction
      case WorkflowNodeId(x) =>
        ChangeValidationLogger.warn(s"An unknow workflow state was reached with ID: '${x}'. It is likely to be a bug, please report it")
        NoWorkflowAction
    }
  }

  def findBackSteps(
      currentUserRights : Seq[String]
    , currentStep       : WorkflowNodeId
    , isCreator         : Boolean
  ) : Seq[(WorkflowNodeId,(ChangeRequestId,EventActor, Option[String]) => Box[WorkflowNodeId])] = {
    val authorizedRoles = currentUserRights.filter(role => (role == "validator" || role == "deployer"))
    //TODO: manage error for config !
    val canValid  = selfValidation().getOrElse(false) || !isCreator
    val canDeploy = selfDeployment().getOrElse(false) || !isCreator
    currentStep match {
      case Validation.id =>
        if (authorizedRoles.contains("validator") && canValid) Seq((Cancelled.id,stepValidationToCancelled _)) else Seq()
      case Deployment.id => if (authorizedRoles.contains("deployer") && canDeploy)  Seq((Cancelled.id,stepDeploymentToCancelled _)) else Seq()
      case Deployed.id   => Seq()
      case Cancelled.id  => Seq()
      case WorkflowNodeId(x) =>
        ChangeValidationLogger.warn(s"An unknow workflow state was reached with ID: '${x}'. It is likely to be a bug, please report it")
        Seq()
    }
  }

  def isEditable(currentUserRights:Seq[String],currentStep:WorkflowNodeId, isCreator : Boolean): Boolean = {
    val authorizedRoles = currentUserRights.filter(role => (role == "validator" || role == "deployer"))
    currentStep match {
      case Validation.id => authorizedRoles.contains("validator") || isCreator
      case Deployment.id => authorizedRoles.contains("deployer")
      case Deployed.id   => false
      case Cancelled.id  => false
      case WorkflowNodeId(x) =>
        ChangeValidationLogger.warn(s"An unknown workflow state was reached with ID: '${x}'. It is likely to be a bug, please report it")
        false
    }
  }

  def isPending(currentStep:WorkflowNodeId): Boolean = {
    currentStep match {
      case Validation.id => true
      case Deployment.id => true
      case Deployed.id   => false
      case Cancelled.id  => false
      case WorkflowNodeId(x) =>
        ChangeValidationLogger.warn(s"An unknown workflow state was reached with ID: '${x}'. It is likely to be a bug, please report it")
        false
    }
  }
  def findStep(changeRequestId: ChangeRequestId) : Box[WorkflowNodeId] = {
    roWorkflowRepo.getStateOfChangeRequest(changeRequestId)
  }


  def getAllChangeRequestsStep() : Box[Map[ChangeRequestId,WorkflowNodeId]] = {
    roWorkflowRepo.getAllChangeRequestsState
  }


  def startWorkflow(changeRequest: ChangeRequest, actor:EventActor, reason: Option[String]) : Box[ChangeRequestId] = {
    ChangeValidationLogger.debug(s"${name}: start workflow for change request '${changeRequest.id.value}'")
    for {
      saved    <- saveAndLogChangeRequest(AddChangeRequestDiff(changeRequest), actor, reason)
      workflow <- woWorkflowRepo.createWorkflow(saved.id, Validation.id)
      _        =  notificationService.sendNotification(Validation, saved).catchEmailError("changeRequestCreated", Validation.id.value)
    } yield {
      workflowComet ! WorkflowUpdate
      saved.id
    }
  }

  private[this] def changeStep(
      from           : WorkflowNode
    , to             : WorkflowNode
    , changeRequestId: ChangeRequestId
    , actor          : EventActor
    , reason         : Option[String]
  ) : Box[WorkflowNodeId] = {
    (for {
      state        <- woWorkflowRepo.updateState(changeRequestId,from.id, to.id)
      workflowStep =  WorkflowStepChange(changeRequestId,from.id,to.id)
      log          <- workflowLogger.saveEventLog(workflowStep,actor,reason)
      _            =  sendEmail(from, to, changeRequestId).catchEmailError(from.id.value, to.id.value)
    } yield {
      workflowComet ! WorkflowUpdate
      state
    }) match {
      case Full(state) => Full(state)
      case eb:EmptyBox =>
        val e = eb ?~! s"Error when changing step in workflow for Change Request ${changeRequestId.value}"
        ChangeValidationLogger.error(e.messageChain)
        e
    }
  }



  /**
   *  Send an email notification. Failing to send email does not fail the method (ie: change validation is ok, no
   *  error displayed to user) BUT of course we log.
   */
  private[this] def sendEmail(from: WorkflowNode, to: WorkflowNode, changeRequestId: ChangeRequestId): IOResult[Unit] = {
    for {
      cr <- roChangeRequestRepository.get(changeRequestId).toIO.notOptional(
                 s"Change request with ID '${changeRequestId.value}' was not found in database"
               )
      _  <- (from,to) match {
                  case (Validation, Deployment) =>
                    notificationService.sendNotification(Deployment, cr)
                  case (_, Cancelled) =>
                    notificationService.sendNotification(Cancelled, cr)
                  case (_, Deployed) =>
                    notificationService.sendNotification(Deployed, cr)
                  case _ =>
                    ChangeValidationLoggerPure.debug(s"Not sending email for update from '${from.id.value}' to '${to.id.value}''") *>
                    UIO.unit
                }
    } yield ()
  }

  /**
   * We never want to have an email sending error lead to an error in the CR request creation: the CR is
   * already created, and it's ok. It's still important to let log about what goes wrong in the notification
   * for admin (because they are the one who can do something about it).
   */
  implicit class CatchEmailError(result: IOResult[Unit]) {
    def catchEmailError(from: String, to: String): Unit = {
      result.toBox match {
        case eb: EmptyBox =>
          val msg = (eb ?~! s"Error when trying to send email for change request status update from '${from}' to '${to}'").messageChain
          ChangeValidationLogger.error(msg)
        case Full(_) => // nothing
      }
    }
  }

  private[this] def onSuccessWorkflow(from: WorkflowNode, changeRequestId: ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    ChangeValidationLogger.debug(s"${name}: deploy change for change request '${changeRequestId.value}'")
    for {
      optcr  <- roChangeRequestRepository.get(changeRequestId)
      cr     <- Box(optcr) ?~! s"Change request with ID '${changeRequestId.value}' was not found in database"
      saved  <- commit.save(cr, actor, reason)
      repoOk <- woChangeRequestRepository.updateChangeRequest(saved, actor, reason)
      state  <- changeStep(from,Deployed,changeRequestId,actor,reason)
    } yield {
      state
    }
  }


  private[this] def toFailure(from: WorkflowNode, changeRequestId: ChangeRequestId, actor: EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    changeStep(from, Cancelled,changeRequestId,actor,reason)
  }

  //allowed workflow steps


  private[this] def stepValidationToDeployment(changeRequestId:ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    changeStep(Validation, Deployment,changeRequestId, actor, reason)
  }


  private[this] def stepValidationToDeployed(changeRequestId:ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    onSuccessWorkflow(Validation, changeRequestId, actor, reason)
  }

  private[this] def stepValidationToCancelled(changeRequestId:ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    toFailure(Validation, changeRequestId, actor, reason)
  }

  private[this] def stepDeploymentToDeployed(changeRequestId:ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    onSuccessWorkflow(Deployment, changeRequestId, actor, reason)
  }


  private[this] def stepDeploymentToCancelled(changeRequestId:ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    toFailure(Deployment, changeRequestId, actor, reason)
  }

  // this THE workflow that needs external validation.
  override def needExternalValidation(): Boolean = true
}
