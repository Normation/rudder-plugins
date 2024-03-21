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
import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.eventlog.EventLog
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiAuthorization
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
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.repository.CategoryAndNodeGroup
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.services.eventlog.ChangeRequestEventLogService
import com.normation.rudder.services.eventlog.WorkflowEventLogService
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.users.AuthenticatedUser
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.UserService
import com.normation.rudder.web.services.StatelessUserPropertyService
import com.normation.rudder.web.services.UserPropertyService
import com.normation.zio.UnsafeRun
import net.liftweb.common.Box
import net.liftweb.common.Full
import scala.collection.immutable.SortedMap
import zio.Chunk
import zio.Ref
import zio.ZIO
import zio.syntax.*

class MockSupervisedTargets(unsupervisedDir: File, unsupervisedFilename: String, fullNodeGroupCategory: FullNodeGroupCategory) {

  val unsupervisedRepo = new UnsupervisedTargetsRepository(unsupervisedDir.path, unsupervisedFilename)

  object nodeGroupRepo extends RoNodeGroupRepository {

    override def getFullGroupLibrary(): IOResult[FullNodeGroupCategory] = {
      fullNodeGroupCategory.succeed
    }

    override def getNodeGroupOpt(id:      NodeGroupId): IOResult[Option[(NodeGroup, NodeGroupCategoryId)]] = ???
    override def getNodeGroupCategory(id: NodeGroupId): IOResult[NodeGroupCategory]                        = ???
    override def getAll(): IOResult[Seq[NodeGroup]] = ???
    override def getAllByIds(ids: Seq[NodeGroupId]): IOResult[Seq[com.normation.rudder.domain.nodes.NodeGroup]] = ???
    override def getAllNodeIds():      IOResult[Map[NodeGroupId, Set[NodeId]]]   = ???
    override def getAllNodeIdsChunk(): IOResult[Map[NodeGroupId, Chunk[NodeId]]] = ???
    override def getGroupsByCategory(
        includeSystem: Boolean
    ): IOResult[SortedMap[List[NodeGroupCategoryId], CategoryAndNodeGroup]] = ???
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

    override def get(changeRequestId: ChangeRequestId): Box[Option[ChangeRequest]] = {
      changeRequestsByStatus.values.flatten.find(_.id == changeRequestId) match {
        case Some(cr) => Full(Some(cr))
        case None     => Full(None)
      }
    }

    override def updateChangeRequest(
        changeRequest: ChangeRequest,
        actor:         EventActor,
        reason:        Option[String]
    ): Box[ChangeRequest] = {
      Full(changeRequest)
    }

    override def getAll(): Box[Vector[ChangeRequest]] = ???

    override def getByDirective(id: DirectiveUid, onlyPending: Boolean): Box[Vector[ChangeRequest]] = ???

    override def getByNodeGroup(id: NodeGroupId, onlyPending: Boolean): Box[Vector[ChangeRequest]] = ???

    override def getByRule(id: RuleUid, onlyPending: Boolean): Box[Vector[ChangeRequest]] = ???

    override def getByContributor(actor: EventActor): Box[Vector[ChangeRequest]] = ???

    override def createChangeRequest(
        changeRequest: ChangeRequest,
        actor:         EventActor,
        reason:        Option[String]
    ): Box[ChangeRequest] = ???

    override def deleteChangeRequest(
        changeRequestId: ChangeRequestId,
        actor:           EventActor,
        reason:          Option[String]
    ): Box[ChangeRequest] = ???

  }

  object workflowRepository extends RoWorkflowRepository with WoWorkflowRepository {
    override def getStateOfChangeRequest(crId: ChangeRequestId): Box[WorkflowNodeId] = {
      changeRequestsByStatus.find(_._2.exists(_.id == crId)).map(_._1) match {
        case Some(state) => Full(state)
        case None        => Full(WorkflowNodeId("unknown"))
      }
    }

    override def updateState(crId: ChangeRequestId, from: WorkflowNodeId, state: WorkflowNodeId): Box[WorkflowNodeId] = {
      Full(state)
    }

    override def getAllByState(state: WorkflowNodeId): Box[Seq[ChangeRequestId]] = {
      ???
    }

    override def createWorkflow(crId: ChangeRequestId, state: WorkflowNodeId): Box[WorkflowNodeId] = ???

    override def getAllChangeRequestsState(): Box[Map[ChangeRequestId, WorkflowNodeId]] = ???

  }

  object commitAndDeployChangeRequest extends CommitAndDeployChangeRequestService {

    override def save(changeRequest: ChangeRequest, actor: EventActor, reason: Option[String]): Box[ChangeRequest] = Full(
      changeRequest
    )

    override def isMergeable(changeRequest: ChangeRequest): Boolean = {
      // can depend on "mergeable" changeRequest by their id to vary test cases
      true
    }

  }

  object workflowEventLogService extends WorkflowEventLogService {
    override def saveEventLog(stepChange: WorkflowStepChange, actor: EventActor, reason: Option[String]): Box[EventLog] = Full(
      null
    )

    override def getChangeRequestHistory(id: ChangeRequestId): Box[Seq[WorkflowStepChanged]]    = ???
    override def getLastLog(id:              ChangeRequestId): Box[Option[WorkflowStepChanged]] = ???
    override def getLastWorkflowEvents(): Box[Map[ChangeRequestId, EventLog]] = ???

  }

  object changeRequestEventLogService extends ChangeRequestEventLogService {

    override def saveChangeRequestLog(
        modId:     ModificationId,
        principal: EventActor,
        diff:      ChangeRequestDiff,
        reason:    Option[String]
    ): Box[EventLog] = Full(null)

    override def getChangeRequestHistory(id: ChangeRequestId): Box[Seq[ChangeRequestEventLog]]    = ???
    override def getFirstLog(id:             ChangeRequestId): Box[Option[ChangeRequestEventLog]] = ???
    override def getLastLog(id:              ChangeRequestId): Box[Option[ChangeRequestEventLog]] = ???
    override def getLastCREvents: Box[Map[ChangeRequestId, EventLog]] = ???
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
    val user = new AuthenticatedUser {
      val account:   RudderAccount.User  = RudderAccount.User("admin", "admin")
      def nodePerms: NodeSecurityContext = NodeSecurityContext.All
      def checkRights(auth: AuthorizationType) = true
      def getApiAuthz: ApiAuthorization = ApiAuthorization.RW
    }
    val getCurrentUser: AuthenticatedUser = user
  }
}
