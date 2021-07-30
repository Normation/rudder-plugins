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
import com.normation.rudder.services.servers.PolicyServerConfigurationObjects
import com.normation.rudder.services.servers.PolicyServerManagementService
import com.normation.utils.StringUuidGenerator
import com.softwaremill.quicklens._
import zio.ZIO
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

  def promoteNodeToRelay(uuid: NodeId, actor: EventActor, reason:Option[String]): ZIO[Any, RudderError, NodeInfo] = {
    val modId = ModificationId(uuidGen.newUuid)
    for {
      nodeInfos  <- getNodeToPromote(uuid)
      targetedNode <-
                    if (!nodeInfos.isPolicyServer) {
                      val updatedNode = NodeToPolicyServer(nodeInfos)
                      for {
                         _        <- woLDAPNodeRepository.deleteNode(nodeInfos.node, modId, actor, reason)
                                       .chainError(s"Remove ${nodeInfos.node.id.value} to update it to policy server failed")
                         newRelay <- createRelayFromNode(updatedNode, modId, actor, reason)
                      } yield {
                        newRelay
                      }
                    } else {
                      nodeInfos.succeed
                    }
    } yield {
      asyncDeploymentAgent ! AutomaticStartDeployment(modId, actor)
      targetedNode
    }
  }

  private[scaleoutrelay] def createRelayFromNode(nodeInf: NodeInfo, modId: ModificationId, actor: EventActor, reason:Option[String]) = {

    val objects = PolicyServerConfigurationObjects.getConfigurationObject(nodeInf.id, nodeInf.hostname, nodeInf.policyServerId)

    val promote = for {
      _ <- woLDAPNodeRepository.createNode(nodeInf.node, modId, actor, reason)
      _ <- ZIO.foreach(objects.targets){ t =>
             woLDAPNodeGroupRepository.createPolicyServerTarget(t, modId, actor, reason)
           }
      _ <- ZIO.foreach(objects.groups) { g =>
             woLDAPNodeGroupRepository.create(g, NodeGroupCategoryId(SYSTEM_GROUPS), modId, actor, reason)
           }
      _ <- ZIO.foreach(objects.directives.toList) { case (t, d) =>
             woDirectiveRepository.saveSystemDirective(ActiveTechniqueId(t.value), d, modId, actor, reason)
           }
      _ <- ZIO.foreach(objects.rules) { r =>
             woRuleRepository.create(r, modId, actor, reason)
           }
      _ <- actionLogger.savePromoteToRelay(modId,actor,nodeInf, reason)
    } yield {
      nodeInf
    }

    promote.catchAll {
      err =>
        for {
          _ <- demoteRelay(nodeInf, objects, modId, actor)
          _ <- ScaleOutRelayLoggerPure.error(s"Promote node ${nodeInf.node.id.value} have failed, cause by : ${err.fullMsg}")
        } yield {
           nodeInf
        }
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
        .chainError(s"Demote relay failed : removing node group '${g.id.value}' failed").unit
    )
    val directives = objects.directives.toList.map(d =>
      woDirectiveRepository.deleteSystemDirective(d._2.id.uid, modId, actor, reason)
        .chainError(s"Demote relay failed : removing directive '${d._2.id.debugString}' failed").unit
    )
    val rules = objects.rules.map(r =>
      woRuleRepository.deleteSystemRule(r.id, modId, actor, reason).catchAll {
        err => ScaleOutRelayLoggerPure.info(s"Trying to remove residual object Rule ${r.id.value} : ${err.fullMsg}")
      }.unit
    )

    (nPromoted :: nBeforePromoted :: targets ::: groups ::: directives ::: rules).accumulate(identity)
  }

  private[scaleoutrelay] def getNodeToPromote(uuid: NodeId): IOResult[NodeInfo] = {
    nodeInfosService.getNodeInfo(uuid).notOptional(s"Node with UUID ${uuid.value} is missing and can not be upgraded to relay")
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
