package com.normation.plugins.scaleoutrelay

import com.normation.errors._
import com.normation.eventlog.{EventActor, EventLog, EventLogFilter, ModificationId}
import com.normation.inventory.domain.NodeId
import com.normation.ldap.ldif.LDIFNoopChangeRecord
import com.normation.rudder.domain.nodes._
import com.normation.rudder.domain.policies.PolicyServerTarget
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.repository.{EventLogRepository, WoNodeGroupRepository}
import com.normation.rudder.services.eventlog.EventLogFactory
import com.normation.rudder.services.servers.{PolicyServer, PolicyServerManagementService, PolicyServers, PolicyServersUpdateCommand}
import com.normation.zio.UnsafeRun
import com.unboundid.ldap.sdk.DN
import com.unboundid.ldif.LDIFChangeRecord
import doobie.Fragment
import zio.syntax._
import zio.{Ref, ZIO}

class MockServices(nodeGroups: Map[NodeGroupId, NodeGroup]) {

  private val  nodeGroupsRef:  Ref[Map[NodeGroupId, NodeGroup]] = {
    Ref.Synchronized.make(nodeGroups).runNow
  }

  object woLDAPNodeGroupRepository extends WoNodeGroupRepository {

    override def create(
        group: NodeGroup,
        into:  NodeGroupCategoryId,
        modId: ModificationId,
        actor: EventActor,
        why:   Option[String]
    ): IOResult[AddNodeGroupDiff] = {
      nodeGroupsRef.update(_ + (group.id -> group)).as(AddNodeGroupDiff(group))
    }

    override def createPolicyServerTarget(
        target: PolicyServerTarget,
        modId:  ModificationId,
        actor:  EventActor,
        reason: Option[String]
    ): IOResult[LDIFChangeRecord] = {
      LDIFNoopChangeRecord(DN.NULL_DN).succeed
    }

    override def delete(
        id:             NodeGroupId,
        modId:          ModificationId,
        actor:          EventActor,
        whyDescription: Option[String]
    ): IOResult[DeleteNodeGroupDiff] = {
      nodeGroupsRef.get
        .map(_.get(id))
        .flatMap(_.notOptional(s"Cannot delete node group $id because it was not created in mock"))
        .map(DeleteNodeGroupDiff(_))
    }

    override def deletePolicyServerTarget(policyServer: PolicyServerTarget): IOResult[PolicyServerTarget] = {
      policyServer.succeed
    }

    override def update(
        group:          NodeGroup,
        modId:          ModificationId,
        actor:          EventActor,
        whyDescription: Option[String]
    ): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def updateDiffNodes(
        group:          NodeGroupId,
        add:            List[NodeId],
        delete:         List[NodeId],
        modId:          ModificationId,
        actor:          EventActor,
        whyDescription: Option[String]
    ): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def updateSystemGroup(
        group:  NodeGroup,
        modId:  ModificationId,
        actor:  EventActor,
        reason: Option[String]
    ): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def updateDynGroupNodes(
        group:          NodeGroup,
        modId:          ModificationId,
        actor:          EventActor,
        whyDescription: Option[String]
    ): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def move(
        group:          NodeGroupId,
        containerId:    NodeGroupCategoryId,
        modId:          ModificationId,
        actor:          EventActor,
        whyDescription: Option[String]
    ): IOResult[Option[ModifyNodeGroupDiff]] = ???
    override def addGroupCategorytoCategory(
        that:           NodeGroupCategory,
        into:           NodeGroupCategoryId,
        modificationId: ModificationId,
        actor:          EventActor,
        reason:         Option[String]
    ): IOResult[NodeGroupCategory] = ???
    override def saveGroupCategory(
        category:       NodeGroupCategory,
        modificationId: ModificationId,
        actor:          EventActor,
        reason:         Option[String]
    ): IOResult[NodeGroupCategory] = ???
    override def saveGroupCategory(
        category:       NodeGroupCategory,
        containerId:    NodeGroupCategoryId,
        modificationId: ModificationId,
        actor:          EventActor,
        reason:         Option[String]
    ): IOResult[NodeGroupCategory] = ???
    override def delete(
        id:             NodeGroupCategoryId,
        modificationId: ModificationId,
        actor:          EventActor,
        reason:         Option[String],
        checkEmpty:     Boolean
    ): IOResult[NodeGroupCategoryId] = ???
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
