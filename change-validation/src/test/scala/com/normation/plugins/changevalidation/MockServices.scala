/*
 *************************************************************************************
 * Copyright 2023 Normation SAS
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

import better.files.File
import cats.data.NonEmptyList
import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.eventlog.EventLog
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Rights
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.config.StatelessUserPropertyService
import com.normation.rudder.config.UserPropertyService
import com.normation.rudder.domain.eventlog.ChangeRequestDiff
import com.normation.rudder.domain.eventlog.ChangeRequestEventLog
import com.normation.rudder.domain.eventlog.WorkflowStepChanged
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupCategory
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.WorkflowNode
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.domain.workflows.WorkflowStepChange
import com.normation.rudder.facts.nodes.ChangeContext
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.repository.CategoryAndNodeGroup
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.services.eventlog.ChangeRequestEventLogService
import com.normation.rudder.services.eventlog.WorkflowEventLogService
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.users.AuthenticatedUser
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.UserPassword
import com.normation.rudder.users.UserService
import com.normation.zio.UnsafeRun
import scala.collection.immutable.SortedMap
import zio.Chunk
import zio.Ref
import zio.ZIO
import zio.syntax.*

class MockSupervisedTargets(unsupervisedDir: File, unsupervisedFilename: String, fullNodeGroupCategory: FullNodeGroupCategory) {

  val unsupervisedRepo = new UnsupervisedTargetsRepository(unsupervisedDir.path, unsupervisedFilename)

  object nodeGroupRepo extends RoNodeGroupRepository {

    override def getFullGroupLibrary():                                       IOResult[FullNodeGroupCategory]                    = {
      fullNodeGroupCategory.succeed
    }

    override def categoryExists(
        id: com.normation.rudder.domain.nodes.NodeGroupCategoryId
    ): com.normation.errors.IOResult[Boolean] = ???
    override def getNodeGroupOpt(id: NodeGroupId)(implicit qc: QueryContext): IOResult[Option[(NodeGroup, NodeGroupCategoryId)]] =
      ???
    override def getNodeGroupCategory(id: NodeGroupId): IOResult[NodeGroupCategory] = ???
    override def getAll(): IOResult[Seq[NodeGroup]] = ???
    override def getAllByIds(ids: Seq[NodeGroupId]): IOResult[Seq[com.normation.rudder.domain.nodes.NodeGroup]] = ???
    override def getAllNodeIds():      IOResult[Map[NodeGroupId, Set[NodeId]]]   = ???
    override def getAllNodeIdsChunk(): IOResult[Map[NodeGroupId, Chunk[NodeId]]] = ???
    override def getGroupsByCategory(
        includeSystem: Boolean
    )(implicit qc: QueryContext): IOResult[SortedMap[List[NodeGroupCategoryId], CategoryAndNodeGroup]] = ???
    override def findGroupWithAnyMember(nodeIds: Seq[NodeId]): IOResult[Seq[NodeGroupId]] = ???
    override def findGroupWithAllMember(nodeIds: Seq[NodeId]): IOResult[Seq[NodeGroupId]] = ???
    override def getRootCategory():     NodeGroupCategory                                                 = ???
    override def getRootCategoryPure(): IOResult[NodeGroupCategory]                                       = ???
    override def getCategoryHierarchy:  IOResult[SortedMap[List[NodeGroupCategoryId], NodeGroupCategory]] = ???
    override def getAllGroupCategories(includeSystem: Boolean):             IOResult[Seq[NodeGroupCategory]]  = ???
    override def getGroupCategory(id:                 NodeGroupCategoryId): IOResult[NodeGroupCategory]       = ???
    override def getParentGroupCategory(id:           NodeGroupCategoryId): IOResult[NodeGroupCategory]       = ???
    override def getParents_NodeGroupCategory(id:     NodeGroupCategoryId): IOResult[List[NodeGroupCategory]] = ???
    override def getAllNonSystemCategories(): IOResult[Seq[NodeGroupCategory]] = ???
  }

}

class MockValidatedUsers(users: Map[EventActor, WorkflowUsers]) {

  object validatedUserRepo extends RoValidatedUserRepository with WoValidatedUserRepository {

    private val cache: Ref[Map[EventActor, WorkflowUsers]] = Ref.Synchronized.make(users).runNow

    override def getUsers(): IOResult[Set[WorkflowUsers]] = {
      cache.get.map(_.values.toSet)
    }

    override def deleteUser(actor: EventActor): IOResult[EventActor] = {
      cache.update(_.removed(actor)).as(actor)
    }

    override def saveWorkflowUsers(actor: List[EventActor]): IOResult[Set[WorkflowUsers]] = {
      cache
        .updateAndGet(currentUsers => actor.map(a => a -> WorkflowUsers(a, true, currentUsers.values.toList.contains(a))).toMap)
        .map(_.values.toSet)
    }

    override def getValidatedUsers(): IOResult[Seq[EventActor]] = ???
    override def get(actor:        EventActor): IOResult[Option[EventActor]] = ???
    override def createUser(newVU: EventActor): IOResult[EventActor]         = ???
  }

}

class MockServices(changeRequestsByStatus: Map[WorkflowNodeId, List[ChangeRequest]] = Map.empty) {

  object changeRequestRepository extends RoChangeRequestRepository with WoChangeRequestRepository {

    override def getByFilter(filter: ChangeRequestFilter): IOResult[Vector[(ChangeRequest, WorkflowNodeId)]] = {
      import ChangeRequestFilter.*
      changeRequestsByStatus.view
        .filterKeys(status => filter.status.forall(_.contains(status)))
        .toVector
        .flatMap {
          case (state, crs) =>
            val checkNoFilter = filter.by.isEmpty
            val checkDirective: ConfigurationChangeRequest => Boolean = filter.by match {
              case Some(ByDirective(directiveId)) => _.directives.keySet.map(_.uid).contains(directiveId)
              case _                              => _ => true
            }
            val checkRule:      ConfigurationChangeRequest => Boolean = filter.by match {
              case Some(ByRule(ruleId)) => _.rules.keySet.map(_.uid).contains(ruleId)
              case _                    => _ => true
            }
            val checkNodeGroup: ConfigurationChangeRequest => Boolean = filter.by match {
              case Some(ByNodeGroup(nodeGroupId)) => _.nodeGroups.keySet.map(_.uid).contains(nodeGroupId)
              case _                              => _ => true
            }
            crs.collect {
              case cr: ConfigurationChangeRequest if checkNoFilter || checkDirective(cr) || checkRule(cr) || checkNodeGroup(cr) =>
                (cr, state)
            }
        }
        .succeed
    }

    override def get(changeRequestId: ChangeRequestId): IOResult[Option[ChangeRequest]] = {
      changeRequestsByStatus.values.flatten.find(_.id == changeRequestId) match {
        case Some(cr) => Some(cr).succeed
        case None     => None.succeed
      }
    }

    override def updateChangeRequest(
        changeRequest: ChangeRequest,
        actor:         EventActor,
        reason:        Option[String]
    ): IOResult[ChangeRequest] = {
      changeRequest.succeed
    }

    override def getAll(): IOResult[Vector[ChangeRequest]] = ???

    override def getByDirective(id: DirectiveUid, onlyPending: Boolean): IOResult[Vector[ChangeRequest]] = ???

    override def getByNodeGroup(id: NodeGroupId, onlyPending: Boolean): IOResult[Vector[ChangeRequest]] = ???

    override def getByRule(id: RuleUid, onlyPending: Boolean): IOResult[Vector[ChangeRequest]] = ???

    override def getByContributor(actor: EventActor): IOResult[Vector[ChangeRequest]] = ???

    override def createChangeRequest(
        changeRequest: ChangeRequest,
        actor:         EventActor,
        reason:        Option[String]
    ): IOResult[ChangeRequest] = ???

    override def deleteChangeRequest(
        changeRequestId: ChangeRequestId,
        actor:           EventActor,
        reason:          Option[String]
    ): IOResult[ChangeRequest] = ???

  }

  object workflowRepository extends RoWorkflowRepository with WoWorkflowRepository {
    override def getStateOfChangeRequest(crId: ChangeRequestId): IOResult[WorkflowNodeId] = {
      changeRequestsByStatus.find(_._2.exists(_.id == crId)).map(_._1) match {
        case Some(state) => state.succeed
        case None        => WorkflowNodeId("unknown").succeed
      }
    }

    override def updateState(crId: ChangeRequestId, from: WorkflowNodeId, state: WorkflowNodeId): IOResult[WorkflowNodeId] = {
      state.succeed
    }

    override def getCountByState(filter: NonEmptyList[WorkflowNodeId]): IOResult[Map[WorkflowNodeId, Long]] = {
      changeRequestsByStatus.flatMap {
        case (k, v) =>
          filter.find(_ == k).map({ _ => (k, v.size.toLong) })
      }.succeed
    }

    override def getAllByState(state: WorkflowNodeId): IOResult[Seq[ChangeRequestId]] = {
      ???
    }

    override def createWorkflow(crId: ChangeRequestId, state: WorkflowNodeId): IOResult[WorkflowNodeId] = ???

    override def getAllChangeRequestsState(): IOResult[Map[ChangeRequestId, WorkflowNodeId]] = ???

  }

  object commitAndDeployChangeRequest extends CommitAndDeployChangeRequestService {

    def save(changeRequest: ChangeRequest)(implicit cc: ChangeContext): IOResult[ChangeRequest] = changeRequest.succeed

    def isMergeable(changeRequest: ChangeRequest)(implicit qc: QueryContext): Boolean = {
      // can depend on "mergeable" changeRequest by their id to vary test cases
      true
    }

  }

  object workflowEventLogService extends WorkflowEventLogService {
    override def saveEventLog(stepChange: WorkflowStepChange, actor: EventActor, reason: Option[String]): IOResult[EventLog] = {
      val res: EventLog = null
      res.succeed
    }

    override def getChangeRequestHistory(id: ChangeRequestId): IOResult[Seq[WorkflowStepChanged]]    = ???
    override def getLastLog(id:              ChangeRequestId): IOResult[Option[WorkflowStepChanged]] = ???
    override def getLastWorkflowEvents(): IOResult[Map[ChangeRequestId, EventLog]] = ???

  }

  object changeRequestEventLogService extends ChangeRequestEventLogService {

    override def saveChangeRequestLog(
        modId:     ModificationId,
        principal: EventActor,
        diff:      ChangeRequestDiff,
        reason:    Option[String]
    ): IOResult[EventLog] = {
      val res: EventLog = null
      res.succeed
    }

    override def getChangeRequestHistory(id: ChangeRequestId): IOResult[Seq[ChangeRequestEventLog]]    = ???
    override def getFirstLog(id:             ChangeRequestId): IOResult[Option[ChangeRequestEventLog]] = ???
    override def getLastLog(id:              ChangeRequestId): IOResult[Option[ChangeRequestEventLog]] = ???
    override def getLastCREvents: IOResult[Map[ChangeRequestId, EventLog]] = ???
  }

  object notificationService extends NotificationService {
    override def sendNotification(step: WorkflowNode, cr: ChangeRequest): IOResult[Unit] = ZIO.unit
  }

  val userPropertyService: UserPropertyService = new StatelessUserPropertyService(
    getEnable = () => true.succeed,
    getMandatory = () => false.succeed,
    getExplanation = () => "Test property service".succeed
  )

  object userService extends UserService {
    val user:                    AuthenticatedUser         = new AuthenticatedUser {
      override val account:   RudderAccount       = RudderAccount.User("admin", UserPassword.fromSecret("admin"))
      override val authz:     Rights              = Rights.AnyRights
      override val apiAuthz:  ApiAuthorization    = ApiAuthorization.RW
      override val nodePerms: NodeSecurityContext = NodeSecurityContext.All

      override def checkRights(auth: AuthorizationType): Boolean = true
    }
    override val getCurrentUser: Option[AuthenticatedUser] = Some(user)
  }
}
