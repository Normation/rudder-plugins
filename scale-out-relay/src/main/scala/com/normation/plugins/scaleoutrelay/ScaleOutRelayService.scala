package com.normation.plugins.scaleoutrelay

import com.normation.errors.*
import com.normation.inventory.domain.NodeId
import com.normation.ldap.sdk.LDAPConnectionProvider
import com.normation.ldap.sdk.RwLDAPConnection
import com.normation.rudder.domain.NodeDit
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeKind
import com.normation.rudder.domain.policies.*
import com.normation.rudder.facts.nodes.ChangeContext
import com.normation.rudder.facts.nodes.CoreNodeFact
import com.normation.rudder.facts.nodes.NodeFactRepository
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.repository.EventLogRepository
import com.normation.rudder.repository.WoDirectiveRepository
import com.normation.rudder.repository.WoNodeGroupRepository
import com.normation.rudder.repository.WoRuleRepository
import com.normation.rudder.services.servers.PolicyServer
import com.normation.rudder.services.servers.PolicyServerConfigurationObjects
import com.normation.rudder.services.servers.PolicyServerManagementService
import com.softwaremill.quicklens.*
import zio.*
import zio.syntax.*

/*
 * We need a low level tool to delete the LDAP entry under ou=Nodes,cn=rudder-configuration because we need
 * to change one of its structural type in a way that is not liked by ldap and causes:
 *   BackendException: Error when doing action 'modify' with and LDIF change request: structural object class
 *   modification from 'rudderNode' to 'rudderPolicyServer' not allowed
 */
trait DeleteNodeEntryService {
  def delete(nodeId: NodeId): IOResult[Unit]
}

class LdapDeleteNodeEntryService(
    ldap: LDAPConnectionProvider[RwLDAPConnection],
    dit:  NodeDit
) extends DeleteNodeEntryService {
  override def delete(nodeId: NodeId): IOResult[Unit] = {
    val dn = dit.NODES.NODE.dn(nodeId.value)
    for {
      con <- ldap
      _   <- con.delete(dn).chainError(s"Error when trying to delete node '${nodeId.value}'")
    } yield ()
  }
}

class ScaleOutRelayService(
    woLDAPNodeGroupRepository:     WoNodeGroupRepository,
    nodeFactRepository:            NodeFactRepository,
    woDirectiveRepository:         WoDirectiveRepository,
    woRuleRepository:              WoRuleRepository,
    policyServerManagementService: PolicyServerManagementService,
    actionLogger:                  EventLogRepository,
    deleteNodeService:             DeleteNodeEntryService
) {
  val SYSTEM_GROUPS = "SystemGroups"

  def promoteNodeToRelay(nodeId: NodeId)(implicit cc: ChangeContext): IOResult[Unit] = {
    implicit val qr: QueryContext = cc.toQuery
    for {
      _        <- ScaleOutRelayLoggerPure.debug(s"Start promotion of node '${nodeId.value}' to relay")
      nodeInfo <- nodeFactRepository
                    .get(nodeId)
                    .notOptional(s"Node with UUID ${nodeId.value} is missing and can not be upgraded to relay")
      _        <- ZIO.when(!nodeInfo.rudderSettings.isPolicyServer) {
                    val updatedNode: CoreNodeFact = NodeToPolicyServer(nodeInfo)
                    createRelayFromNode(updatedNode)
                  }
    } yield () // policy generation will be started if needed by NodeFact event handler
  }

  private[scaleoutrelay] def createRelayFromNode(
      nodeInfo: CoreNodeFact
  )(implicit cc: ChangeContext): IOResult[Unit] = {

    val objects = PolicyServerConfigurationObjects.getConfigurationObject(nodeInfo.id)

    val promote = for {
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] delete node entry")
      _ <- deleteNodeService.delete(nodeInfo.id)
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create entry with relay status")
      // we are force to delete the node, then create it again, because we are allowed to change it's structural
      // class hierarchy in LDAP, which leads to error: BackendException: Error when doing action 'modify' with and LDIF change request:
      // structural object class modification from 'rudderNode' to 'rudderPolicyServer' not allowed

      _ <- nodeFactRepository.save(nodeInfo)
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new special targets")
      _ <-
        ZIO.foreach(objects.targets)(t => woLDAPNodeGroupRepository.createPolicyServerTarget(t, cc.modId, cc.actor, cc.message))
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new system groups")
      _ <- ZIO.foreach(objects.groups) { g =>
             woLDAPNodeGroupRepository.create(g, NodeGroupCategoryId(SYSTEM_GROUPS), cc.modId, cc.actor, cc.message)
           }
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new system directives")
      _ <- ZIO.foreach(objects.directives.toList) {
             case (t, d) =>
               woDirectiveRepository.saveSystemDirective(ActiveTechniqueId(t.value), d, cc.modId, cc.actor, cc.message)
           }
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new system rules")
      _ <- ZIO.foreach(objects.rules)(r => woRuleRepository.create(r, cc.modId, cc.actor, cc.message))

      // save the relay in the rudder_policy_servers entry in LDAP
      existingPolicyServers <- policyServerManagementService.getPolicyServers()
      newPolicyServers       = existingPolicyServers.copy(relays = PolicyServer(nodeInfo.id, Nil) :: existingPolicyServers.relays)
      _                     <- policyServerManagementService.savePolicyServers(newPolicyServers)

      _ <- actionLogger.savePromoteToRelay(cc.modId, cc.actor, nodeInfo.toNodeInfo, cc.message)
    } yield {}

    promote.catchAll { err =>
      val msg = s"Promote node ${nodeInfo.id.value} have failed. Change were reverted. Cause was: ${err.fullMsg}"
      (for {
        _ <- ScaleOutRelayLoggerPure.debug(s"[promote ${nodeInfo.id.value}] error, start reverting")
        _ <- demoteRelay(nodeInfo, objects).catchAll(err =>
               ScaleOutRelayLoggerPure.debug(s"Error when reverting node promotion: ${err.fullMsg}")
             )
        _ <- ScaleOutRelayLoggerPure.error(msg)
      } yield {}) *> Unexpected(msg).fail
    }
  }

  def demoteRelayToNode(nodeId: NodeId)(implicit cc: ChangeContext): IOResult[Unit] = {
    implicit val qr: QueryContext = cc.toQuery
    for {
      _        <- ScaleOutRelayLoggerPure.debug(s"Start demotion of relay '${nodeId.value}' to node")
      nodeInfo <- nodeFactRepository
                    .get(nodeId)
                    .notOptional(s"Relay with UUID ${nodeId.value} is missing and can not be demoted to node")
      _        <- if (nodeInfo.rudderSettings.isPolicyServer) {
                    val configObjects = PolicyServerConfigurationObjects.getConfigurationObject(nodeInfo.id)
                    demoteRelay(nodeInfo, configObjects).unit *>
                    actionLogger.saveDemoteToNode(cc.modId, cc.actor, nodeInfo.toNodeInfo, cc.message)
                  } else {
                    ScaleOutRelayLoggerPure.debug(s"Node '${nodeId.value}' is already a simple node, nothing to do.") *>
                    ZIO.unit
                  }
    } yield () // policy generation will be started if needed by NodeFact event handler

  }

  private[scaleoutrelay] def demoteRelay(
      newInfo: CoreNodeFact,
      objects: PolicyServerConfigurationObjects
  )(implicit cc: ChangeContext): ZIO[Any, Accumulated[RudderError], List[Unit]] = {

    val old = PolicyServerToNode(newInfo)

    val nPromoted       = deleteNodeService.delete(newInfo.id)
    val nBeforePromoted = nodeFactRepository
      .save(old)
      .chainError(s"Demote relay failed: Setting node '${newInfo.id.value}' from relay to node failed")
      .unit

    val targets    = objects.targets.map(t => {
      woLDAPNodeGroupRepository
        .deletePolicyServerTarget(t)
        .chainError(s"Demote relay failed: removing policy server target  for node '${newInfo.id.value}' failed")
        .unit
    })
    val groups     = objects.groups.map(g => {
      woLDAPNodeGroupRepository
        .delete(g.id, cc.modId, cc.actor, cc.message)
        .chainError(s"Demote relay failed: removing node group '${g.id.serialize}' failed")
        .unit
    })
    val directives = objects.directives.toList.map(d => {
      woDirectiveRepository
        .deleteSystemDirective(d._2.id.uid, cc.modId, cc.actor, cc.message)
        .chainError(s"Demote relay failed: removing directive '${d._2.id.debugString}' failed")
        .unit
    })
    val rules      = objects.rules.map(r => {
      woRuleRepository
        .deleteSystemRule(r.id, cc.modId, cc.actor, cc.message)
        .catchAll { err =>
          ScaleOutRelayLoggerPure.info(s"Trying to remove residual object rule ${r.id.serialize}: ${err.fullMsg}")
        }
        .unit
    })

    (nPromoted :: nBeforePromoted :: targets ::: groups ::: directives ::: rules).accumulate(identity)
  }

  private[scaleoutrelay] def NodeToPolicyServer(nodeInf: CoreNodeFact) = {
    nodeInf
      .modify(_.rudderSettings)
      .setTo(nodeInf.rudderSettings.modify(_.kind).setTo(NodeKind.Relay))
  }

  private[scaleoutrelay] def PolicyServerToNode(nodeInf: CoreNodeFact) = {
    nodeInf
      .modify(_.rudderSettings)
      .setTo(nodeInf.rudderSettings.modify(_.kind).setTo(NodeKind.Node))
  }

}
