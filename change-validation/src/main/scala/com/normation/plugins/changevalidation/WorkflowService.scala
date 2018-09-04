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
import com.normation.rudder.domain.workflows._
import net.liftweb.common._
import com.normation.rudder.repository._
import com.normation.rudder.services.eventlog.WorkflowEventLogService
import com.normation.rudder.batch.AsyncWorkflowInfo
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.parameters.ParameterName
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.domain.policies.Rule
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.domain.policies.TargetExclusion
import com.normation.rudder.domain.policies.TargetIntersection
import com.normation.rudder.domain.policies.TargetUnion
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.services.workflows.NoWorkflowAction
import com.normation.rudder.services.workflows.RuleChangeRequest
import com.normation.rudder.services.workflows.WorkflowAction
import com.normation.rudder.services.workflows.WorkflowService
import com.normation.rudder.services.workflows.WorkflowUpdate


/**
 * A proxy workflow service based on a runtime choice
 */
class EitherWorkflowService(cond: () => Box[Boolean], whenTrue: WorkflowService, whenFalse: WorkflowService) extends WorkflowService {

  //TODO: handle ERRORS for config!

  val name = "choose-active-validation-workflow"

  def current: WorkflowService = if(cond().getOrElse(false)) whenTrue else whenFalse

  override def startWorkflow(changeRequestId: ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] =
    current.startWorkflow(changeRequestId, actor, reason)
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


trait ValidationNeeded {

  def forRule(actor: EventActor, change: RuleChangeRequest): Box[Boolean]
}





trait TargetForChangeRequest {

  def getAll(): Box[Set[SimpleTarget]]
}

class FiveFirstTargetForChangeRequest(repos: RoNodeGroupRepository) extends TargetForChangeRequest {

  val first5 : Box[Set[SimpleTarget]] = {
    for {
      groups <- repos.getAll()
    } yield {
      val first5 = groups.sortBy( _.id.value ).take(5)

      first5.foreach(g =>
        ChangeValidationLogger.info(s"Change validation required: '${g.name}' [${g.id.value}]")
      )

      first5.map(g => GroupTarget(g.id)).toSet
    }
  }

  override def getAll(): Box[Set[SimpleTarget]] = first5
}

/*
 * A version of the "validationNeeded" plugin which bases its oracle on a list
 * of group. If at least one node on the group is impacted by the change, then
 * the change must be validated with a change request.
 *
 * - a modification in a GlobalParam is always validated (because can be used in CFEngine code)
 * - a modification in a node group is validated only if it's one of the monitored one
 * - a modification in a rule is validated only if:
 *    - one of the modification add or remove a monitored group
 *    - one of its target is a special group intersecting with the list of monitored groups
 *    - one of its target group, either in the "in" or in the "not in", is in the list of monitored one.
 *      We must take group in both "in" and "not in" because adding or removing a node from the target of
 *      a rule need the same validation
 * - a modification in a directive is validated if it as at least configured in one rule where modification
 *   are supervised.
 *
 */
class NodeGroupValidationNeeded(
    monitoredTargets: TargetForChangeRequest
  , repos           : RoChangeRequestRepository
  , ruleLib         : RoRuleRepository
) extends ValidationNeeded {


  /*
   * from a list of RuleTarget, get all the SimpleTarget implied
   */
  def getAllSimpleTarget(target: RuleTarget): Set[SimpleTarget] = {
    target match {
      case st: SimpleTarget       => Set(st)
      case TargetUnion(ts)        => ts.flatMap(getAllSimpleTarget)
      case TargetIntersection(ts) => ts.flatMap(getAllSimpleTarget)
      case TargetExclusion(a, b)  => getAllSimpleTarget(a) ++ getAllSimpleTarget(b)
    }
  }


  def checkGlobalParams(monitored: Set[SimpleTarget], parameterNames: Set[ParameterName]): Boolean = parameterNames.nonEmpty

  // return true if at least one of the groups in nodeGroups is monitored
  def checkNodeGroups(monitored: Set[SimpleTarget], nodeGroups: Set[NodeGroupId]): Boolean = {
    val groups = monitored.collect { case GroupTarget(id) => id }
    (nodeGroups.intersect(groups)).nonEmpty
  }

  def checkRule(monitored: Set[SimpleTarget], rules: Map[RuleId, RuleChanges]): Boolean = {
    val compositeTargets = rules.values.flatMap(c =>
         (c.changes.initialState.map( _.targets).getOrElse(Set())
      ++ (c.changes.firstChange+:c.changes.nextChanges).flatMap( _.diff.rule.targets)).toSet
    ).toSet

      val targets = compositeTargets.flatMap(getAllSimpleTarget)

      monitored.intersect(targets).nonEmpty
  }

  def checkDirectives(rules: Set[Rule], monitored: Set[SimpleTarget], directives: Set[DirectiveId]): Boolean = {
    monitored.intersect(rules.filter(_.directiveIds.intersect(directives).nonEmpty).flatMap(_.targets).flatMap(getAllSimpleTarget)).nonEmpty
  }

  override def forRule(actor: EventActor, change: RuleChangeRequest): Box[Boolean] = {
    val targets = change.newRule.targets.flatMap(getAllSimpleTarget) ++ change.previousRule.map( _.targets.flatMap(getAllSimpleTarget)).getOrElse(Set())
    for {
      monitored <- monitoredTargets.getAll()
    } yield {
      monitored.intersect(targets).nonEmpty
    }
  }
}



class TwoValidationStepsWorkflowServiceImpl(
    workflowLogger  : WorkflowEventLogService
  , commit          : CommitAndDeployChangeRequestService
  , roWorkflowRepo  : RoWorkflowRepository
  , woWorkflowRepo  : WoWorkflowRepository
  , workflowComet   : AsyncWorkflowInfo
  , selfValidation  : () => Box[Boolean]
  , selfDeployment  : () => Box[Boolean]
) extends WorkflowService {

  val name = "two-steps-validation-workflow"

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

  def getItemsInStep(stepId: WorkflowNodeId) : Box[Seq[ChangeRequestId]] = roWorkflowRepo.getAllByState(stepId)

  val openSteps : List[WorkflowNodeId] = List(Validation.id,Deployment.id)
  val closedSteps : List[WorkflowNodeId] = List(Cancelled.id,Deployed.id)
  val stepsValue = steps.map(_.id)

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
        ChangeValidationLogger.warn(s"An unknow workflow state was reached with ID: '${x}'. It is likely to be a bug, please report it")
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
        ChangeValidationLogger.warn(s"An unknow workflow state was reached with ID: '${x}'. It is likely to be a bug, please report it")
        false
    }
  }
  def findStep(changeRequestId: ChangeRequestId) : Box[WorkflowNodeId] = {
    roWorkflowRepo.getStateOfChangeRequest(changeRequestId)
  }


  def getAllChangeRequestsStep : Box[Map[ChangeRequestId,WorkflowNodeId]] = {
    roWorkflowRepo.getAllChangeRequestsState
  }

  private[this] def changeStep(
      from           : WorkflowNode
    , to             : WorkflowNode
    , changeRequestId: ChangeRequestId
    , actor          : EventActor
    , reason         : Option[String]
  ) : Box[WorkflowNodeId] = {
    (for {
      state <- woWorkflowRepo.updateState(changeRequestId,from.id, to.id)
      workflowStep = WorkflowStepChange(changeRequestId,from.id,to.id)
      log   <- workflowLogger.saveEventLog(workflowStep,actor,reason)
    } yield {
      workflowComet ! WorkflowUpdate
      state
    }) match {
      case Full(state) => Full(state)
      case e:Failure => ChangeValidationLogger.error(s"Error when changing step in workflow for Change Request ${changeRequestId.value} : ${e.msg}")
                        e
      case Empty => ChangeValidationLogger.error(s"Error when changing step in workflow for Change Request ${changeRequestId.value} : no reason given")
                    Empty
    }
  }

  private[this] def toFailure(from: WorkflowNode, changeRequestId: ChangeRequestId, actor: EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    changeStep(from, Cancelled,changeRequestId,actor,reason)
  }

  def startWorkflow(changeRequestId: ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    startTwoStepWorkflow(changeRequestId, actor, reason)
  }

  private[this] def startTwoStepWorkflow(changeRequestId: ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    ChangeValidationLogger.debug(s"${name}: start workflow for change request '${changeRequestId.value}'")
    for {
      workflow <- woWorkflowRepo.createWorkflow(changeRequestId, Validation.id)
    } yield {
      workflowComet ! WorkflowUpdate
      workflow
    }
  }

//  private[this] def startAutodeployWorkflow(changeRequestId: ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
//    ChangeValidationLogger.debug(s"${name}: automatically deploy change for change request '${changeRequestId.value}'")
//    for {
//      result <- commit.save(changeRequestId, actor, reason)
//    } yield {
//      // and return a no workflow
//      Deployed.id
//    }
//  }

  private[this] def onSuccessWorkflow(from: WorkflowNode, changeRequestId: ChangeRequestId, actor:EventActor, reason: Option[String]) : Box[WorkflowNodeId] = {
    ChangeValidationLogger.debug(s"${name}: deploy change for change request '${changeRequestId.value}'")
    for {
      save  <- commit.save(changeRequestId, actor, reason)
      state <- changeStep(from,Deployed,changeRequestId,actor,reason)
    } yield {
      state
    }

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


