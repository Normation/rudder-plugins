package com.normation.plugins.scaleoutrelay

import com.normation.errors.*
import com.normation.eventlog.EventActor
import com.normation.eventlog.EventLog
import com.normation.eventlog.EventLogFilter
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.ldap.ldif.LDIFNoopChangeRecord
import com.normation.rudder.domain.nodes.*
import com.normation.rudder.domain.policies.PolicyServerTarget
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.repository.EventLogRepository
import com.normation.rudder.repository.WoNodeGroupRepository
import com.normation.rudder.services.eventlog.EventLogFactory
import com.normation.rudder.services.servers.PolicyServer
import com.normation.rudder.services.servers.PolicyServerManagementService
import com.normation.rudder.services.servers.PolicyServers
import com.normation.rudder.services.servers.PolicyServersUpdateCommand
import com.normation.rudder.tenants.ChangeContext
import com.normation.zio.UnsafeRun
import com.unboundid.ldap.sdk.DN
import com.unboundid.ldif.LDIFChangeRecord
import doobie.Fragment
import zio.Ref
import zio.ZIO
import zio.syntax.*

class MockServices(nodeGroups: Map[NodeGroupId, NodeGroup]) {

  private val nodeGroupsRef: Ref[Map[NodeGroupId, NodeGroup]] = {
    Ref.Synchronized.make(nodeGroups).runNow
  }

  object woLDAPNodeGroupRepository extends WoNodeGroupRepository {

    override def create(group: NodeGroup, into: NodeGroupCategoryId)(implicit cc: ChangeContext): IOResult[AddNodeGroupDiff] = {
      nodeGroupsRef.update(_ + (group.id -> group)).as(AddNodeGroupDiff(group))
    }

    override def createPolicyServerTarget(target: PolicyServerTarget)(implicit cc: ChangeContext): IOResult[LDIFChangeRecord] = {
      LDIFNoopChangeRecord(DN.NULL_DN).succeed
    }

    override def delete(id: NodeGroupId)(implicit cc: ChangeContext): IOResult[DeleteNodeGroupDiff] = {
      nodeGroupsRef.get
        .map(_.get(id))
        .flatMap(_.notOptional(s"Cannot delete node group $id because it was not created in mock"))
        .map(DeleteNodeGroupDiff(_))
    }

    override def deletePolicyServerTarget(
        policyServer: PolicyServerTarget
    )(implicit cc: ChangeContext): IOResult[PolicyServerTarget] = {
      policyServer.succeed
    }

    override def update(group: NodeGroup)(implicit cc: ChangeContext): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def updateDiffNodes(
        group:  NodeGroupId,
        add:    List[NodeId],
        delete: List[NodeId]
    )(implicit cc: ChangeContext): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def updateSystemGroup(
        group: NodeGroup
    )(implicit cc: ChangeContext): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def updateDynGroupNodes(
        group: NodeGroup
    )(implicit cc: ChangeContext): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def move(
        group:       NodeGroupId,
        containerId: NodeGroupCategoryId
    )(implicit cc: ChangeContext): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def addGroupCategoryToCategory(that: NodeGroupCategory, into: NodeGroupCategoryId)(implicit
        cc: ChangeContext
    ): IOResult[NodeGroupCategory] = {
      ???
    }
    override def saveGroupCategory(
        category: NodeGroupCategory
    )(implicit cc: ChangeContext): IOResult[NodeGroupCategory] = ???
    override def saveGroupCategory(
        category:    NodeGroupCategory,
        containerId: NodeGroupCategoryId
    )(implicit cc: ChangeContext): IOResult[NodeGroupCategory] = ???
    override def delete(
        id:         NodeGroupCategoryId,
        checkEmpty: Boolean
    )(implicit cc: ChangeContext): IOResult[NodeGroupCategoryId] = ???
  }

  object policyServerManagementService extends PolicyServerManagementService {
    private val fakePolicyServers = PolicyServers(PolicyServer(NodeId("root"), List.empty), List.empty)
    override def savePolicyServers(policyServers: PolicyServers): IOResult[PolicyServers] = {
      fakePolicyServers.succeed
    }

    override def getPolicyServers(): IOResult[PolicyServers] = {
      fakePolicyServers.succeed
    }

    override def updatePolicyServers(
        commands: List[PolicyServersUpdateCommand],
        modId:    ModificationId,
        actor:    EventActor
    ): IOResult[PolicyServers] = ???
    override def deleteRelaySystemObjects(policyServerId: NodeId): IOResult[Unit] = ???

  }

  object eventLogRepo extends EventLogRepository {

    override def savePromoteToRelay(
        modId:        ModificationId,
        principal:    EventActor,
        promotedNode: NodeInfo,
        reason:       Option[String]
    ): IOResult[EventLog] = {
      ZIO.succeed(null)
    }

    override def saveDemoteToNode(
        modId:        ModificationId,
        principal:    EventActor,
        demotedRelay: NodeInfo,
        reason:       Option[String]
    ): IOResult[EventLog] = {
      ZIO.succeed(null)
    }

    override def eventLogFactory: EventLogFactory = ???
    override def getLastEventByChangeRequest(
        xpath:           String,
        eventTypeFilter: List[EventLogFilter]
    ): IOResult[Map[ChangeRequestId, EventLog]] = ???
    override def saveEventLog(modId: ModificationId, eventLog: EventLog): IOResult[EventLog] = ???
    override def getEventLogByCriteria(
        criteria:       Option[Fragment],
        limit:          Option[Int],
        orderBy:        List[Fragment],
        extendedFilter: Option[Fragment]
    ): IOResult[Seq[EventLog]] = ???
    override def getEventLogById(id: Long): IOResult[EventLog] = ???
    override def getEventLogCount(criteria:       Option[Fragment], extendedFilter: Option[Fragment]): IOResult[Long] = ???
    override def getEventLogByChangeRequest(
        changeRequest:   ChangeRequestId,
        xpath:           String,
        optLimit:        Option[Int],
        orderBy:         Option[String],
        eventTypeFilter: List[EventLogFilter]
    ): IOResult[Vector[EventLog]] = ???
    override def getEventLogWithChangeRequest(id: Int): IOResult[Option[(EventLog, Option[ChangeRequestId])]] = ???
  }
}
