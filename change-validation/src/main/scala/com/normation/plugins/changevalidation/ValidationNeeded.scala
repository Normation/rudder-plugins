/*
 *************************************************************************************
 * Copyright 2025 Normation SAS
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

import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.policies.Rule
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.facts.nodes.NodeFactRepository
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.repository.RoRuleRepository
import com.normation.rudder.services.workflows.DirectiveChangeRequest
import com.normation.rudder.services.workflows.GlobalParamChangeRequest
import com.normation.rudder.services.workflows.NodeGroupChangeRequest
import com.normation.rudder.services.workflows.RuleChangeRequest
import scala.collection.MapView
import zio.ZIO
import zio.syntax.ToZio

object bddMock {
  val USER_AUTH_NEEDED = Map(
    "admin" -> false,
    "Jean"  -> true
  )
}

/**
 * Check is an external validation is needed for the change, given some
 * arbitrary rules defined in implementation.
 *
 * Validated user will be checked directly in `combine` method, it will not follow this logic
 * (see https://issues.rudder.io/issues/22188#note-5)
 */
trait ValidationNeeded {
  def forRule(actor:        EventActor, change: RuleChangeRequest):        IOResult[Boolean]
  def forDirective(actor:   EventActor, change: DirectiveChangeRequest):   IOResult[Boolean]
  def forNodeGroup(actor:   EventActor, change: NodeGroupChangeRequest):   IOResult[Boolean]
  def forGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): IOResult[Boolean]
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
 * Note that a validated user will always bypass this validation (see https://issues.rudder.io/issues/22188#note-5)
 */
class NodeGroupValidationNeeded(
    supervisedTargets: () => IOResult[Set[SimpleTarget]],
    repos:             RoChangeRequestRepository,
    ruleLib:           RoRuleRepository,
    groupLib:          RoNodeGroupRepository,
    nodeFactRepo:      NodeFactRepository
) extends ValidationNeeded {

  /*
   * A rule need external validation if any of its current or future nodes
   * are also in a monitored group.
   * We can't check only for group target by the rule, because of (for ex)
   * that case:
   * - A is a node in Group1 (supervised) and Group2 (not supervised)
   * - rule R is changed to add Group2 in its target (or opposite change: Group2 removed)
   * - the change must be validated.
   */
  override def forRule(actor: EventActor, change: RuleChangeRequest): IOResult[Boolean] = {
    for {
      start           <- com.normation.zio.currentTimeMillis
      groups          <- groupLib.getFullGroupLibrary()
      // I think it's ok to have that, it will need a deeper change when we will want to have per-tenant change validation
      arePolicyServer <- nodeFactRepo.getAll()(QueryContext.systemQC)
      supervised      <- supervisedTargets()
      targets          = Set(change.newRule) ++ change.previousRule.toSet
      res              = checkNodeTargetByRule(groups, arePolicyServer.mapValues(_.rudderSettings.isPolicyServer), supervised, targets)
      end             <- com.normation.zio.currentTimeMillis
      _               <- {
        ChangeValidationLoggerPure.Metrics.debug(
          s"Check rule '${change.newRule.name}' [${change.newRule.id.serialize}]" +
          s"change requestion need for validation in ${end - start}ms"
        )
      }
    } yield {
      res
    }
  }

  /**
   * This method checks if at least one of the nodes belonging to rule targets for the change
   * is supervised.
   */
  def checkNodeTargetByRule(
      groups:          FullNodeGroupCategory,
      arePolicyServer: MapView[NodeId, Boolean],
      monitored:       Set[SimpleTarget],
      rules:           Set[Rule]
  ): Boolean = {
    val monitoredNodes = groups.getNodeIds(monitored.map(identity), arePolicyServer)
    val changes        = rules.flatMap(_.targets)
    val exists         = groups.getNodeIds(changes, arePolicyServer).exists(nodeId => monitoredNodes.contains(nodeId))
    // we want to let the log knows why the change request need validation
    if (exists && ChangeValidationLogger.isDebugEnabled) {
      rules.foreach { rule =>
        groups.getNodeIds(rule.targets, arePolicyServer).find(nodeId => monitoredNodes.contains(nodeId)).foreach { node =>
          ChangeValidationLogger.debug(
            s"Node '${node.value}' belongs to both a supervised group and is a target of rule '${rule.name}' [${rule.id.serialize}]"
          )
        }
      }
    }
    exists
  }

  /*
   * We want to check if the supervised node are the same before and after the change to avoid the case:
   * - the group (not supervised) does not contain any supervised nodes,
   * - the group is applied to a directive,
   * - the group (still not supervised) is modified to include supervised nodes (because they also
   *   belong to an other group)
   * - now the rule is applied to supervised node, but no validation was done.
   */
  override def forNodeGroup(actor: EventActor, change: NodeGroupChangeRequest): IOResult[Boolean] = {
    // Here we need to test the future content of the group, and not the current one.
    // So we need to know:
    // - the list of supervised node in the group before the change,
    // => non empty means validation needed,
    // - the list of supervised node in the group after change,
    // => non empty means validation needed

    for {
      start      <- com.normation.zio.currentTimeMillis
      groups     <- groupLib.getFullGroupLibrary()
      nodeFacts  <- nodeFactRepo.getAll()(QueryContext.systemQC)
      supervised <- supervisedTargets()
      targetNodes = change.newGroup.serverList ++ change.previousGroup.map(_.serverList).getOrElse(Set())
      exists      = groups
                      .getNodeIds(supervised.map(identity), nodeFacts.mapValues(_.rudderSettings.isPolicyServer))
                      .find(nodeId => targetNodes.contains(nodeId))
      res        <-
        // we want to let the log know why the change request needs validation
        ZIO
          .foreach(exists) { nodeId =>
            ChangeValidationLoggerPure
              .debug(
                s"Node '${nodeId.value}' belongs to both a supervised group and to group '${change.newGroup.name}' [${change.newGroup.id.serialize}]"
              )
          }
          .map(_.nonEmpty)

      end <- com.normation.zio.currentTimeMillis
      _   <- {
        ChangeValidationLoggerPure.Metrics.debug(
          s"Check group '${change.newGroup.name}' [${change.newGroup.id.serialize}] " +
          s"change requestion need for validation in ${end - start}ms"
        )
      }
    } yield {
      res
    }
  }

  /*
   * A directive need a validation if any rule using it need a validation.
   */
  override def forDirective(actor: EventActor, change: DirectiveChangeRequest): IOResult[Boolean] = {
    for {
      start      <- com.normation.zio.currentTimeMillis
      // in a change, the old directive id and the new one is the same.
      directiveId = change.newDirective.id
      rules      <- ruleLib.getAll(includeSytem = true).map(_.filter(r => r.directiveIds.contains(directiveId)))
      // we need to add potentially new rules applied to that directive that the previous request does not cover
      newRules    = change.updatedRules
      supervised <- supervisedTargets()
      groups     <- groupLib.getFullGroupLibrary()
      nodeFacts  <- nodeFactRepo.getAll()(QueryContext.systemQC)
      res         =
        checkNodeTargetByRule(groups, nodeFacts.mapValues(_.rudderSettings.isPolicyServer), supervised, (rules ++ newRules).toSet)
      end        <- com.normation.zio.currentTimeMillis
      _          <- {
        ChangeValidationLoggerPure.Metrics.debug(
          s"Check directive '${change.newDirective.name}' [${change.newDirective.id.uid.serialize}]" +
          s"change requestion need for validation in ${end - start}ms"
        )
      }
    } yield {
      res
    }
  }

  /*
   * For a global parameter, we just answer "yes"
   */
  override def forGlobalParam(actor: EventActor, change: GlobalParamChangeRequest): IOResult[Boolean] = {
    true.succeed
  }
}
