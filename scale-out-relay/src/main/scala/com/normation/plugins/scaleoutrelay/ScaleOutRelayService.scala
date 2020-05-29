package com.normation.plugins.scaleoutrelay

import com.normation.NamedZioLogger
import com.normation.cfclerk.domain.TechniqueVersion
import com.normation.errors._
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.rudder.batch.AsyncDeploymentActor
import com.normation.rudder.batch.AutomaticStartDeployment
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.policies._
import com.normation.rudder.domain.queries._
import com.normation.rudder.repository.EventLogRepository
import com.normation.rudder.repository.WoDirectiveRepository
import com.normation.rudder.repository.WoNodeGroupRepository
import com.normation.rudder.repository.WoNodeRepository
import com.normation.rudder.repository.WoRuleRepository
import com.normation.rudder.rule.category.RuleCategoryId
import com.normation.rudder.services.nodes.NodeInfoService
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
  val DISTRIBUTE_POLICY = "distributePolicy"
  val COMMON = "common"
  val logger = NamedZioLogger("plugin.scaleoutrelay")

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
    val directDistribPolicy     = createDirectiveDistributePolicy(nodeInf.node.id)
    val ruleTarget              = createPolicyServer(nodeInf.node.id)
    val nodeGroup               = createNodeGroup(nodeInf.node.id)
    val ruleDistribPolicy       = createRuleDistributePolicy(nodeInf.node.id)
    val ruleSetup               = createRuleSetup(nodeInf.node.id)
    val categoryId              = NodeGroupCategoryId(SYSTEM_GROUPS)
    val activeTechniqueId       = ActiveTechniqueId(DISTRIBUTE_POLICY)
    val activeTechniqueIdCommon = ActiveTechniqueId(COMMON)

    val promote = for {
      commonDirective <- createCommonDirective(nodeInf).toIO

      _ <- woLDAPNodeRepository.createNode(nodeInf.node, modId, actor, reason)
      _ <- woLDAPNodeGroupRepository.createPolicyServerTarget(ruleTarget, modId, actor, reason)
      _ <- woLDAPNodeGroupRepository.create(nodeGroup, categoryId, modId, actor, reason)
      _ <- woDirectiveRepository.saveSystemDirective(activeTechniqueId, directDistribPolicy, modId, actor, reason)
      _ <- woDirectiveRepository.saveSystemDirective(activeTechniqueIdCommon, commonDirective, modId, actor, reason)
      _ <- woRuleRepository.create(ruleSetup, modId, actor, reason)
      _ <- woRuleRepository.create(ruleDistribPolicy, modId, actor, reason)
      _ <- actionLogger.savePromoteToRelay(modId,actor,nodeInf, reason)
    } yield {
      nodeInf
    }

    promote.catchAll {
      err =>
        for {
          _ <- demoteRelay(nodeInf, modId, actor)
          _ <- logger.error(s"Promote node ${nodeInf.node.id.value} have failed, cause by : ${err.fullMsg}")
        } yield {
           nodeInf
        }
    }
  }

  private[scaleoutrelay] def demoteRelay(
     newInfo        : NodeInfo
    , modId         : ModificationId
    , actor         : EventActor
  ): ZIO[Any, Accumulated[RudderError], List[Any]] = {

    val ruleTarget              = PolicyServerTarget(newInfo.node.id)
    val nodeGroup               = NodeGroupId(s"hasPolicyServer-${newInfo.node.id.value}")
    val ruleDistribPolicy       = RuleId(s"${newInfo.node.id.value}-DP")
    val ruleSetup               = RuleId(s"hasPolicyServer-${newInfo.node.id.value}")
    val activeTechniqueId       = ActiveTechniqueId(DISTRIBUTE_POLICY)
    val activeTechniqueIdCommon = ActiveTechniqueId(COMMON)
    val common                  = DirectiveId(s"common-${newInfo.node.id.value}")
    val distribPolicy           = DirectiveId(s"${newInfo.node.id.value}-distributePolicy")

    val old = PolicyServerToNode(newInfo)
    val reason = Some("Demote relay")

    val nPromoted = woLDAPNodeRepository.deleteNode(newInfo.node, modId, actor, reason)

    val nBeforePromoted = woLDAPNodeRepository.createNode(old.node, modId, actor, reason)
             .chainError(s"Demote relay failed : restore '${newInfo.node}' configuration failed")
    val policyServer  = woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget)
             .chainError(s"Demote relay failed : removing '${newInfo.node}' configuration failed")
    val nGroupSystem = woLDAPNodeGroupRepository.delete(nodeGroup, modId, actor, reason)
             .chainError(s"Demote relay failed : removing node group '${nodeGroup.value}' failed")
    val distributePolicy = woDirectiveRepository.deleteSystemDirective(distribPolicy, modId, actor, reason)
             .chainError(s"Demote relay failed : removing active technique '${activeTechniqueId.value}' failed")
    val commonDirective = woDirectiveRepository.deleteSystemDirective(common,modId, actor, reason)
             .chainError(s"Demote relay failed : removing active technique '${activeTechniqueIdCommon.value}' failed")

    // Rules deletion return an error if there is no such ID
    val f = woRuleRepository.deleteSystemRule(ruleSetup, modId, actor, reason) catchAll {
      err =>
        logger.info(s"Trying to remove residual object Rule ${ruleSetup.value} : ${err.fullMsg}")
    }
    val g = woRuleRepository.deleteSystemRule(ruleDistribPolicy, modId, actor, reason) catchAll {
      err =>
        logger.info(s"Trying to remove residual object Rule ${ruleDistribPolicy.value} : ${err.fullMsg}")
    }
    List(nPromoted,nBeforePromoted,policyServer,nGroupSystem,distributePolicy,commonDirective,f,g).accumulate(x => x)
  }

  private[scaleoutrelay] def getNodeToPromote(uuid: NodeId): IOResult[NodeInfo] = {
    nodeInfosService.getNodeInfo(uuid).toIO.notOptional(s"Node with UUID ${uuid.value} is missing and can not be upgraded to relay")
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

  private[scaleoutrelay] def createNodeGroup(uuid: NodeId) = {
    val objectType = ObjectCriterion("node", Seq(Criterion("policyServerId", StringComparator, None),Criterion("agentName", AgentComparator, None)))
    val attribute = Criterion("agentName", StringComparator)
    val comparator = Equals
    val value = "cfengine"
    val attribute2 = Criterion("policyServerId", StringComparator)
    val value2 = uuid.value
    NodeGroup(
        NodeGroupId(s"hasPolicyServer-${uuid.value}")
      , s"All classic Nodes managed by ${uuid.value} policy server"
      , s"All classic Nodes known by Rudder directly connected to the ${uuid.value} server. This group exists only as internal purpose and should not be used to configure Nodes."
      , Nil
      , Some(
          Query(
              NodeAndPolicyServerReturnType
            , And
            , List(CriterionLine(objectType, attribute, comparator, value)
            , CriterionLine(objectType, attribute2, comparator, value2))
          )
      )
      , isDynamic = true
      , Set()
      , _isEnabled = true
      , isSystem = true
    )
  }

  private[scaleoutrelay] def createDirectiveDistributePolicy(uuid: NodeId) = {
    Directive(
        DirectiveId(s"${uuid.value}-distributePolicy")
      , TechniqueVersion("1.0")
      , Map()
      , s"${uuid.value}-Distribute Policy"
      , "Distribute policy - Technical"
      , None
      , ""
      ,  0
      , _isEnabled = true
      , isSystem = true
      , Tags(Set.empty)
    )
  }

  private[scaleoutrelay] def createCommonDirective(nodeInf: NodeInfo) = {
     for {
      authorizedNetworks <- policyServerManagementService.getAuthorizedNetworks(nodeInf.policyServerId)
    } yield {
       val parameters =
         Map (
            "OWNER"              -> Seq("${rudder.node.admin}")
          , "UUID"               -> Seq("${rudder.node.id}")
          , "POLICYSERVER"       -> Seq(nodeInf.hostname)
          , "POLICYSERVER_ID"    -> Seq(nodeInf.policyServerId.value)
          , "POLICYSERVER_ADMIN" -> Seq("root")
          , "ALLOWEDNETWORK"     -> authorizedNetworks
        )
       Directive(
           DirectiveId(s"common-${nodeInf.node.id.value}")
         , TechniqueVersion("1.0")
         , parameters
         , s"Common-${nodeInf.node.id.value}"
         , "Common - Technical"
         , None
         , ""
         , 0
         , _isEnabled = true
         , isSystem = true
         , Tags(Set.empty)
       )
    }
  }

  private[scaleoutrelay] def createRuleDistributePolicy(uuid: NodeId) = {
    Rule(
        RuleId(s"${uuid.value}-DP")
      , s"${uuid.value}-distributePolicy"
      , RuleCategoryId("rootRuleCategory")
      , Set(PolicyServerTarget(uuid))
      , Set(DirectiveId(s"${uuid.value}-distributePolicy"))
      , "Distribute Policy - Technical"
      , "This rule allows to distribute policies to nodes"
      , isEnabledStatus = true
      , isSystem = true
    )
  }

  private[scaleoutrelay] def createRuleSetup(uuid: NodeId) = {
    Rule(
      RuleId(s"hasPolicyServer-${uuid.value}")
      , s"Rudder system policy: basic setup (common)-${uuid.value}"
      , RuleCategoryId("rootRuleCategory")
      , Set(GroupTarget(NodeGroupId(s"hasPolicyServer-${uuid.value}")))
      , Set(DirectiveId(s"common-${uuid.value}"))
      , "Common - Technical"
      , "This is the basic system rule which all nodes must have."
      , isEnabledStatus = true
      , isSystem = true
    )
  }
}
