package com.normation.plugins.scaleoutrelay

import com.normation.cfclerk.domain.TechniqueVersion
import com.normation.errors.IOResult
import com.normation.errors._
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.logger.PluginLogger
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.policies._
import com.normation.rudder.domain.queries._
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



class ScaleOutRelayService(
    nodeInfosService              : NodeInfoService
  , woLDAPNodeGroupRepository     : WoNodeGroupRepository
  , woLDAPNodeRepository          : WoNodeRepository
  , woDirectiveRepository         : WoDirectiveRepository
  , woRuleRepository              : WoRuleRepository
  , uuidGen                       : StringUuidGenerator
  , policyServerManagementService : PolicyServerManagementService
) {
  val SYSTEM_GROUPS = "SystemGroups"
  val DISTRIBUTE_POLICY = "distributePolicy"
  val COMMON = "common"

  def promoteNodeToRelay(uuid: NodeId, actor: EventActor, reason:Option[String]): ZIO[Any, RudderError, NodeInfo] = {
    val modId = ModificationId(uuidGen.newUuid)
    for {
      nodeInfos   <- getNodeToPromote(uuid)
      updatedNode =  NodeToPolicyServer(nodeInfos)
      _           <- woLDAPNodeRepository.deleteNode(nodeInfos.node, modId, actor, reason).chainError(s"Remove ${nodeInfos.node.id.value} to update it to policy server failed")
      newRelay    <- createRelayFromNode(updatedNode, modId, actor, reason).chainError(s"Promote ${nodeInfos.node.id} to relay failed")
    } yield {
      newRelay
    }
  }

  private[scaleoutrelay] def createRelayFromNode(nodeInf: NodeInfo, modId: ModificationId, actor: EventActor, reason:Option[String])= {
    for {
      commonDirective <- createCommonDirective(nodeInf).toIO

      directDistribPolicy = createDirectiveDistributePolicy(nodeInf.node.id)
      ruleTarget = createPolicyServer(nodeInf.node.id)
      nodeGroup = createNodeGroup(nodeInf.node.id)
      ruleDistribPolicy = createRuleDistributePolicy(nodeInf.node.id)
      ruleSetup = createRuleSetup(nodeInf.node.id)

      categoryId = NodeGroupCategoryId(SYSTEM_GROUPS)
      activeTechniqueId = ActiveTechniqueId(DISTRIBUTE_POLICY)
      activeTechniqueIdCommon = ActiveTechniqueId(COMMON)

      _ <- woLDAPNodeRepository.createNode(nodeInf.node, modId, actor, reason).catchAll { err =>
             val e = Chained(s"Trying to restore initial node configuration", err)
             PluginLogger.error(e)
             val old = PolicyServerToNode(nodeInf)
             woLDAPNodeRepository.createNode(old.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : restaure ${nodeInf.node} configuration failed")
           }
      _ <- woLDAPNodeGroupRepository.createPolicyServerTarget(ruleTarget, modId, actor, reason).catchAll{ err =>
            val e = Chained(s"Trying to remove residual object : policy server rule ${ruleTarget}", err)
            PluginLogger.error(e)
            for {
              _ <- woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed")
              _ <- woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
            } yield ()
           }
      _ <- woLDAPNodeGroupRepository.create(nodeGroup, categoryId, modId, actor, reason).catchAll { err =>
           val e = Chained(s"Trying to remove residual object : node group ${nodeGroup.id.value}", err)
           PluginLogger.error(e)
           for {
              _ <- woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed")
              _ <- woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
              _ <- woLDAPNodeGroupRepository.delete(nodeGroup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed node group ${nodeGroup.id.value} failed")
           } yield ()
      }
      _ <- woDirectiveRepository.saveSystemDirective(activeTechniqueId, directDistribPolicy, modId, actor, reason).catchAll { err =>
        val e = Chained(s"Trying to remove residual object : system directive ${activeTechniqueId.value}", err)
        PluginLogger.error(e)
        for {
          _ <- woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed")
          _ <- woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
          _ <- woLDAPNodeGroupRepository.delete(nodeGroup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed node group ${nodeGroup.id.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueId, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueId.value} failed")
        } yield ()
      }
      _ <- woDirectiveRepository.saveSystemDirective(activeTechniqueIdCommon, commonDirective, modId, actor, reason).catchAll { err =>
        val e = Chained(s"Trying to remove residual object : system directive ${activeTechniqueIdCommon.value}", err)
        PluginLogger.error(e)
        for {
          _ <- woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed")
          _ <- woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
          _ <- woLDAPNodeGroupRepository.delete(nodeGroup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed node group ${nodeGroup.id.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueId, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueId.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueIdCommon, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueIdCommon.value} failed")
        } yield ()
      }
      _ <- woRuleRepository.create(ruleSetup, modId, actor, reason).catchAll { err =>
        val e = Chained(s"Trying to remove residual object : rule ${ruleSetup.id.value}", err)
        PluginLogger.error(e)
        for {
          _ <- woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed")
          _ <- woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
          _ <- woLDAPNodeGroupRepository.delete(nodeGroup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed node group ${nodeGroup.id.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueId, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueId.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueIdCommon, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueIdCommon.value} failed")
          _ <- woRuleRepository.delete(ruleSetup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed rule  ${ruleSetup.id.value} failed")
        } yield ()
      }
      _ <- woRuleRepository.create(ruleDistribPolicy, modId, actor, reason).catchAll { err =>
        val e = Chained(s"Trying to remove residual object : rule ${ruleDistribPolicy.id.value}", err)
        PluginLogger.error(e)
        for {
          _ <- woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, reason).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed")
          _ <- woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
          _ <- woLDAPNodeGroupRepository.delete(nodeGroup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed node group ${nodeGroup.id.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueId, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueId.value} failed")
          _ <- woDirectiveRepository.deleteActiveTechnique(activeTechniqueIdCommon, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueIdCommon.value} failed")
          _ <- woRuleRepository.delete(ruleSetup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed rule  ${ruleSetup.id.value} failed")
          _ <- woRuleRepository.delete(ruleDistribPolicy.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed rule  ${ruleDistribPolicy.id.value} failed")
        } yield ()
      }
    } yield {
      nodeInf
    }
  }

//  private[scaleoutrelay] def rollbackFromLevel(level : Int, err : RudderError,ruleTarget:  PolicyServerTarget,ruleSetup : Rule,ruleDistribPolicy: Rule,nodeInf: NodeInfo, nodeGroup: NodeGroup, activeTechniqueId: ActiveTechniqueId, activeTechniqueIdCommon: ActiveTechniqueId,modId: ModificationId, actor: EventActor,) = {
//    val e = Chained(s"Error when promoting '${nodeInf.node.id.value}' to relay, trying to restore initial node configuration", err)
//    PluginLogger.error(e)
//    for {
//      _ <- if(level >= 1) woLDAPNodeRepository.deleteNode(nodeInf.node, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed ${nodeInf.node} failed"))
//      _ <- if(level >= 2) woLDAPNodeGroupRepository.deletePolicyServerTarget(ruleTarget).chainError(s"Rollback promote to relay failed : removed rule target ${ruleTarget.target} failed")
//      _ <- if(level >= 3) woLDAPNodeGroupRepository.delete(nodeGroup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed node group ${nodeGroup.id.value} failed")
//      _ <- if(level >= 4) woDirectiveRepository.deleteActiveTechnique(activeTechniqueId, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueId.value} failed")
//      _ <- if(level >= 5) woDirectiveRepository.deleteActiveTechnique(activeTechniqueIdCommon, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed active technique ${activeTechniqueIdCommon.value} failed")
//      _ <- if(level >= 6) woRuleRepository.delete(ruleSetup.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed rule  ${ruleSetup.id.value} failed")
//      _ <- if(level >= 7) woRuleRepository.delete(ruleDistribPolicy.id, modId, actor, Some("Promote node to relay rollback")).chainError(s"Rollback promote to relay failed : removed rule  ${ruleDistribPolicy.id.value} failed")
//    } yield ()
//  }

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
      , Some(
          Query(
              NodeAndPolicyServerReturnType
            , And
            , List(CriterionLine(objectType, attribute, comparator, value)
            , CriterionLine(objectType, attribute2, comparator, value2))
          )
      )
      , true
      , Set()
      , true
      , true
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
      , true
      , true
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
         , true
         , true
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
      , true
      , true
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
      , true
      , true
    )
  }
}
