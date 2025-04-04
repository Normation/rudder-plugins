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
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.{MockGlobalParam, MockNodeGroups, MockNodes, MockRules}
import com.normation.rudder.services.workflows.{DGModAction, GlobalParamChangeRequest, GlobalParamModAction, NodeGroupChangeRequest, RuleChangeRequest, RuleModAction}
import net.liftweb.common.Full
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class ValidationNeededTest extends Specification {

  val mockGlobalParam: MockGlobalParam = new MockGlobalParam()
  val mockNodes: MockNodes = new MockNodes()
  val mockNodeGroups: MockNodeGroups = new MockNodeGroups(mockNodes, mockGlobalParam)
  val actor = EventActor("myActor")
  val mockRules = new MockRules()

  val nodeGrpValNdd = new NodeGroupValidationNeeded(
    () => IOResult.attempt(Set.apply(GroupTarget(mockNodeGroups.g0.id))),
    null,
    null,
    mockNodeGroups.groupsRepo,
    mockNodes.nodeFactRepo)

  "ValidationNeeded" should {

    "always validate a modification in a GlobalParam" in {
      val globalParamChangeReq = GlobalParamChangeRequest(GlobalParamModAction.Create, None)
      nodeGrpValNdd.forGlobalParam(actor, globalParamChangeReq) must beEqualTo(Full(true))
    }

    "validate a modification in a node group if that group is supervised" in {
      val nodeGrp = mockNodeGroups.g0
      val nodeGrpChangeReq = NodeGroupChangeRequest(DGModAction.CreateSolo, nodeGrp, None, None)
      nodeGrpValNdd.forNodeGroup(actor, nodeGrpChangeReq) must beEqualTo(Full(true))
    }

    "not validate a modification in a node group if that group is not supervised" in {
      val nodeGrp = mockNodeGroups.g1
      val nodeGrpChangeReq = NodeGroupChangeRequest(DGModAction.CreateSolo, nodeGrp, None, None)
      nodeGrpValNdd.forNodeGroup(actor, nodeGrpChangeReq) must beEqualTo(Full(false))
    }


    "if the rule" in {

      "targets a supervised node, the modification must be validated" in {
        // defaultRule targets all nodes
        val rule = mockRules.rules.defaultRule

        val ruleChangeReq = RuleChangeRequest(
          RuleModAction.Create, rule, None
        )
        nodeGrpValNdd.forRule(actor, ruleChangeReq) must beEqualTo(Full(true))
      }

      "doesn't target a supervised node, the modification mustn't be validated" in {
        // copyGitFileRule targets the nodeGroup g1 which is empty
        val rule = mockRules.rules.copyGitFileRule

        val ruleChangeReq = RuleChangeRequest(
          RuleModAction.Create, rule, None
        )
        nodeGrpValNdd.forRule(actor, ruleChangeReq) must beEqualTo(Full(false))
      }
    }


    "validate a modification in a directive if it is configured " +
      "in at least one rule where modifications are supervised" in {
      //TODO
      true must beEqualTo(false)
    }

    "not validate a modification in a directive if it is not configured " +
      "in at least one rule where modifications are supervised" in {
      //TODO
      true must beEqualTo(false)
    }

  }

}

