package com.normation.plugins.changevalidation

import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.policies.Rule
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.repository.RoRuleRepository
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.workflows.DirectiveChangeRequest
import com.normation.rudder.services.workflows.GlobalParamChangeRequest
import com.normation.rudder.services.workflows.NodeGroupChangeRequest
import com.normation.rudder.services.workflows.RuleChangeRequest
import net.liftweb.common.Box
import net.liftweb.common.Full

import com.normation.box._

object bddMock {
  val USER_AUTH_NEEDED = Map(
    "admin" -> false,
    "Jean" -> true
  )
}

/**
 * Check is an external validation is needed for the change, given some
 * arbitrary rules defined in implementation.
 */
trait ValidationNeeded {
  def forRule       (actor: EventActor, change: RuleChangeRequest       ): Box[Boolean]
  def forDirective  (actor: EventActor, change: DirectiveChangeRequest  ): Box[Boolean]
  def forNodeGroup  (actor: EventActor, change: NodeGroupChangeRequest  ): Box[Boolean]
  def forGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): Box[Boolean]
}

class UserValidationNeeded(repo: RoValidatedUserRepository) extends ValidationNeeded {

  override def forDirective(actor: EventActor, change: DirectiveChangeRequest): Box[Boolean] = {
    repo.get(actor) match {
      case Full(ea) =>
        ea match {
          case Some(_) => Full(false)
          case None => Full(true)
        }
      case _ => Full(true)
    }
  }

  override def forGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): Box[Boolean] = {
    repo.get(actor) match {
      case Full(ea) =>
        ea match {
          case Some(_) => Full(false)
          case None => Full(true)
        }
      case _ => Full(true)
    }
  }

  override def forNodeGroup(actor: EventActor, change: NodeGroupChangeRequest): Box[Boolean] = {
    repo.get(actor) match {
      case Full(ea) =>
        ea match {
          case Some(_) => Full(false)
          case None => Full(true)
        }
      case _ => Full(true)
    }
  }
  override def forRule(actor: EventActor, change: RuleChangeRequest): Box[Boolean] = {
    repo.get(actor) match {
      case Full(ea) =>
        ea match {
          case Some(_) => Full(false)
          case None => Full(true)
        }
      case _ => Full(true)
    }
  }
}


/*
 * A version of the "validationNeeded" plugin which bases its oracle on a list
 * of group. The list of group is used to mark nodes.
 *
 * Any modification that impacts a marked node triggers a validation:
 * - a modification in a GlobalParam is always validated (because can be used in CFEngine code)
 * - a modification in a node group is validated only if it's one of the supervised one
 * - a modification in a rule is validated only if:
 *    - one of the modification add or remove a monitored group
 *    - intersection of the set of nodes targeted by the rule with the set of marked nodes is non empty
 * - a modification in a directive is validated if it as at least configured in one rule where modification
 *   are supervised.
 *
 */
class NodeGroupValidationNeeded(
    monitoredTargets: () => Box[Set[SimpleTarget]]
  , repos           : RoChangeRequestRepository
  , ruleLib         : RoRuleRepository
  , groupLib        : RoNodeGroupRepository
  , nodeInfoService : NodeInfoService
) extends ValidationNeeded {

  /*
   * A rule need external validation if any of its current or futur nodes
   * are also in a monitored group.
   * We can't check only for group target by the rule, because of (for ex)
   * that case:
   * - A is a node in Group1 (supervised) and Group2 (not supervised)
   * - rule R is changed to add Group2 in its target (or oposite change: Group2 removed)
   * - the change must be validated.
   */
  override def forRule(actor: EventActor, change: RuleChangeRequest): Box[Boolean] = {
    val start = System.currentTimeMillis()
    val res = for {
      groups    <- groupLib.getFullGroupLibrary().toBox
      nodeInfo  <- nodeInfoService.getAll()
      monitored <- monitoredTargets()
    } yield {
      val targets = Set(change.newRule) ++ change.previousRule.toSet
      checkNodeTargetByRule(groups, nodeInfo, monitored, targets)
    }
    ChangeValidationLogger.Metrics.debug(s"Check rule '${change.newRule.name}' [${change.newRule.id.value}] change requestion need for validation in ${System.currentTimeMillis() - start}ms")
    res
  }

  /**
   * This method checks if at least one of the nodes belonging to rule targets for the change
   * is supervised.
   */
  def checkNodeTargetByRule(groups: FullNodeGroupCategory, allNodeInfo: Map[NodeId, NodeInfo], monitored: Set[SimpleTarget], rules: Set[Rule]): Boolean = {
    val monitoredNodes = groups.getNodeIds(monitored.map(identity), allNodeInfo)
    val changes = rules.flatMap(_.targets)
    val exists = groups.getNodeIds(changes, allNodeInfo).exists(nodeId => monitoredNodes.contains(nodeId))
    // we want to let the log knows why the change request need validation
    if(exists && ChangeValidationLogger.isDebugEnabled) {
      rules.foreach { rule =>
        groups.getNodeIds(rule.targets, allNodeInfo).find(nodeId => monitoredNodes.contains(nodeId)).foreach { node =>
           ChangeValidationLogger.debug(s"Node '${node.value}' belongs to both a supervised group and is a target of rule '${rule.name}' [${rule.id.value}]")
        }
      }
    }
    exists
  }

  /*
   * We want to check if the supervised node are the same before and after the change to avoid the case:
   * - the group (not supervised) does not contain any sypervised nodes,
   * - the group is applied to a directive,
   * - the group (still not supervised) is modified to include supervised nodes (because they also
   *   belong to an other group)
   * - now the rule is applied to supervised node, but no validation was done.
   */
  override def forNodeGroup(actor: EventActor, change: NodeGroupChangeRequest): Box[Boolean] = {
    // Here we need to test the future content of the group, and not the current one.
    // So we need to know:
    // - the list of supervised node in the group before the change,
    // => non empty means validation needed,
    // - the list of supervised node in the group after change,
    // => non empty means validation needed

    val start = System.currentTimeMillis()

    val res = for {
      groups      <- groupLib.getFullGroupLibrary().toBox
      allNodeInfo <- nodeInfoService.getAll()
      monitored   <- monitoredTargets()
    } yield {
      val targetNodes = change.newGroup.serverList ++ change.previousGroup.map(_.serverList).getOrElse(Set())
      val exists = groups.getNodeIds(monitored.map(identity), allNodeInfo).find(nodeId => targetNodes.contains(nodeId))

      // we want to let the log knows why the change request need validation
      exists.foreach { nodeId =>
        ChangeValidationLogger.debug(s"Node '${nodeId.value}' belongs to both a supervised group and to group '${change.newGroup.name}' [${change.newGroup.id.value}]")
      }
      exists.nonEmpty
    }
    ChangeValidationLogger.Metrics.debug(s"Check group '${change.newGroup.name}' [${change.newGroup.id.value}] change requestion need for validation in ${System.currentTimeMillis() - start}ms")
    res
  }

  /*
   * A directive need a validation if any rule using it need a validation.
   */
  override def forDirective(actor: EventActor, change: DirectiveChangeRequest): Box[Boolean] = {
    //in a change, the old directive id and the new one is the same.
    val directiveId = change.newDirective.id
    val start = System.currentTimeMillis()
    val res = for {
      rules     <- ruleLib.getAll(includeSytem = true).map( _.filter(r => r.directiveIds.contains(directiveId))).toBox
      // we need to add potentially new rules applied to that directive that the previous request does not cover
      newRules  =  change.updatedRules
      monitored <- monitoredTargets()
      groups    <- groupLib.getFullGroupLibrary().toBox
      nodeInfo  <- nodeInfoService.getAll()
    } yield {
      checkNodeTargetByRule(groups, nodeInfo, monitored, (rules++newRules).toSet)
    }
    ChangeValidationLogger.Metrics.debug(s"Check directive '${change.newDirective.name}' [${change.newDirective.id.value}] change requestion need for validation in ${System.currentTimeMillis() - start}ms")
    res
  }

  /*
   * For a global parameter, we just answer "yes"
   */
  override def forGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): Box[Boolean] = {
    Full(true)
  }
}
