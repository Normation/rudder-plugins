package com.normation.plugins.scaleoutrelay

import com.normation.errors._
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.rudder.batch.AsyncDeploymentActor
import com.normation.rudder.batch.AutomaticStartDeployment
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.policies._
import com.normation.rudder.repository.EventLogRepository
import com.normation.rudder.repository.WoDirectiveRepository
import com.normation.rudder.repository.WoNodeGroupRepository
import com.normation.rudder.repository.WoNodeRepository
import com.normation.rudder.repository.WoRuleRepository
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.servers.{PolicyServer, PolicyServerConfigurationObjects, PolicyServerManagementService}
import com.normation.utils.StringUuidGenerator
import com.softwaremill.quicklens._
import zio._
import zio.syntax._


class ScaleOutRelayService(
    nodeInfosService              : NodeInfoService
  , woLDAPNodeGroupRepository     : WoNodeGroupRepository
  , woLDAPNodeRepository          : WoNodeRepository
  , woDirectiveRepository         : WoDirectiveRepository
  , woRuleRepository              : WoRuleRepository
  , uuidGen                       : StringUuidGenerator
  , policyServerManagementService : PolicyServerManagementService
  , actionLogger                  : EventLogRepository
  , asyncDeploymentAgent          : AsyncDeploymentActor
) {
  val SYSTEM_GROUPS = "SystemGroups"

  def promoteNodeToRelay(nodeId: NodeId, actor: EventActor, reason:Option[String]): IOResult[NodeInfo] = {
    val modId = ModificationId(uuidGen.newUuid)
    for {
      _            <- ScaleOutRelayLoggerPure.debug(s"Start promotion of node '${nodeId.value}' to relay")
      nodeInfo     <- nodeInfosService.getNodeInfo(nodeId).notOptional(s"Node with UUID ${nodeId.value} is missing and can not be upgraded to relay")
      targetedNode <- if (!nodeInfo.isPolicyServer) {
                        val updatedNode = NodeToPolicyServer(nodeInfo)
                        for {
                           _        <- woLDAPNodeRepository.deleteNode(nodeInfo.node, modId, actor, reason)
                                         .chainError(s"Remove ${nodeInfo.node.id.value} to update it to policy server failed")
                           newRelay <- createRelayFromNode(updatedNode, modId, actor, reason)
                        } yield {
                          newRelay
                        }
                      } else {
                        ScaleOutRelayLoggerPure.debug(s"Node '${nodeId.value}' is already a policy server, nothing to do.") *>
                        nodeInfo.succeed
                      }
    } yield {
      asyncDeploymentAgent ! AutomaticStartDeployment(modId, actor)
      targetedNode
    }
  }

  private[scaleoutrelay] def createRelayFromNode(nodeInfo: NodeInfo, modId: ModificationId, actor: EventActor, reason:Option[String]) = {

    val objects = PolicyServerConfigurationObjects.getConfigurationObject(nodeInfo.id)

    val promote = for {
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create entry with relay status")
      _ <- woLDAPNodeRepository.createNode(nodeInfo.node, modId, actor, reason)
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new special targets")
      _ <- ZIO.foreach(objects.targets){ t =>
             woLDAPNodeGroupRepository.createPolicyServerTarget(t, modId, actor, reason)
           }
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new system groups")
      _ <- ZIO.foreach(objects.groups) { g =>
             woLDAPNodeGroupRepository.create(g, NodeGroupCategoryId(SYSTEM_GROUPS), modId, actor, reason)
           }
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new system directives")
      _ <- ZIO.foreach(objects.directives.toList) { case (t, d) =>
             woDirectiveRepository.saveSystemDirective(ActiveTechniqueId(t.value), d, modId, actor, reason)
           }
      _ <- ScaleOutRelayLoggerPure.trace(s"[promote ${nodeInfo.id.value}] create new system rules")
      _ <- ZIO.foreach(objects.rules) { r =>
             woRuleRepository.create(r, modId, actor, reason)
           }

      // save the relay in the rudder_policy_servers entry in LDAP
      existingPolicyServers <- policyServerManagementService.getPolicyServers()
      newPolicyServers      = existingPolicyServers.copy(relays = PolicyServer(nodeInfo.id, Nil) :: existingPolicyServers.relays)
      _                     <- policyServerManagementService.savePolicyServers(newPolicyServers)

      _ <- actionLogger.savePromoteToRelay(modId,actor,nodeInfo, reason)
    } yield {
      nodeInfo
    }

    promote.catchAll {
      err =>
        val msg = s"Promote node ${nodeInfo.node.id.value} have failed. Change were reverted. Cause was: ${err.fullMsg}"
        (for {
          _ <- ScaleOutRelayLoggerPure.debug(s"[promote ${nodeInfo.id.value}] error, start reverting")
          _ <- demoteRelay(nodeInfo, objects, modId, actor)
          _ <- ScaleOutRelayLoggerPure.error(msg)
        } yield {
           nodeInfo
        }) *> Unexpected(msg).fail
    }
  }

  def demoteRelayToNode(nodeId: NodeId, actor: EventActor, reason: Option[String]): IOResult[Unit] = {
    val modId = ModificationId(uuidGen.newUuid)
    for {
      _            <- ScaleOutRelayLoggerPure.debug(s"Start demotion of relay '${nodeId.value}' to node")
      nodeInfo     <- nodeInfosService.getNodeInfo(nodeId).notOptional(s"Relay with UUID ${nodeId.value} is missing and can not be demoted to node")
      targetedNode <- if (nodeInfo.isPolicyServer) {
                        val configObjects = PolicyServerConfigurationObjects.getConfigurationObject(nodeInfo.id)
                        demoteRelay(nodeInfo, configObjects, modId, actor).unit *>
                        actionLogger.saveDemoteToNode(modId, actor, nodeInfo, reason)
                      } else {
                        ScaleOutRelayLoggerPure.debug(s"Node '${nodeId.value}' is already a simple node, nothing to do.") *>
                        UIO.unit
                      }
    } yield {
      asyncDeploymentAgent ! AutomaticStartDeployment(modId, actor)
    }

  }


  private[scaleoutrelay] def demoteRelay(
      newInfo: NodeInfo
    , objects: PolicyServerConfigurationObjects
    , modId  : ModificationId
    , actor  : EventActor
  ): ZIO[Any, Accumulated[RudderError], List[Unit]] = {

    val old = PolicyServerToNode(newInfo)
    val reason = Some("Demote relay")

    val nPromoted = woLDAPNodeRepository.deleteNode(newInfo.node, modId, actor, reason).unit
    val nBeforePromoted = woLDAPNodeRepository.createNode(old.node, modId, actor, reason)
          .chainError(s"Demote relay failed : restore '${newInfo.node}' configuration failed").unit

    val targets = objects.targets.map(t =>
      woLDAPNodeGroupRepository.deletePolicyServerTarget(t)
        .chainError(s"Demote relay failed : removing '${newInfo.node}' configuration failed").unit
    )
    val groups = objects.groups.map(g =>
      woLDAPNodeGroupRepository.delete(g.id, modId, actor, reason)
        .chainError(s"Demote relay failed : removing node group '${g.id.serialize}' failed").unit
    )
    val directives = objects.directives.toList.map(d =>
      woDirectiveRepository.deleteSystemDirective(d._2.id.uid, modId, actor, reason)
        .chainError(s"Demote relay failed : removing directive '${d._2.id.debugString}' failed").unit
    )
    val rules = objects.rules.map(r =>
      woRuleRepository.deleteSystemRule(r.id, modId, actor, reason).catchAll {
        err => ScaleOutRelayLoggerPure.info(s"Trying to remove residual object Rule ${r.id.serialize} : ${err.fullMsg}")
      }.unit
    )

    (nPromoted :: nBeforePromoted :: targets ::: groups ::: directives ::: rules).accumulate(identity)
  }

  private[scaleoutrelay] def NodeToPolicyServer(nodeInf: NodeInfo) = {
    nodeInf
      .modify(_.node.isSystem).setTo(true)
      .modify(_.node.isPolicyServer).setTo(true)
  }

  private[scaleoutrelay] def PolicyServerToNode(nodeInf: NodeInfo) = {
    nodeInf
      .modify(_.node.isSystem).setTo(false)
      .modify(_.node.isPolicyServer).setTo(false)
  }

  private[scaleoutrelay] def createPolicyServer(uuid: NodeId) = {
    PolicyServerTarget(uuid)
  }

}
