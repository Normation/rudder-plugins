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

import com.normation.cfclerk.domain.SectionSpec
import com.normation.cfclerk.domain.TechniqueName
import com.normation.eventlog.EventActor
import com.normation.rudder.MockDirectives
import com.normation.rudder.MockGitConfigRepo
import com.normation.rudder.MockGlobalParam
import com.normation.rudder.MockNodeGroups
import com.normation.rudder.MockNodes
import com.normation.rudder.MockRules
import com.normation.rudder.MockTechniques
import com.normation.rudder.domain.policies.ActiveTechniqueId
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.services.workflows.DGModAction
import com.normation.rudder.services.workflows.DirectiveChangeRequest
import com.normation.rudder.services.workflows.GlobalParamChangeRequest
import com.normation.rudder.services.workflows.GlobalParamModAction
import com.normation.rudder.services.workflows.NodeGroupChangeRequest
import com.normation.rudder.services.workflows.RuleChangeRequest
import com.normation.rudder.services.workflows.RuleModAction
import net.liftweb.common.Full
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import zio.syntax.ToZio

@RunWith(classOf[JUnitRunner])
class ValidationNeededTest extends Specification {

  val mockNodes:      MockNodes      = new MockNodes()
  val mockNodeGroups: MockNodeGroups = new MockNodeGroups(mockNodes, new MockGlobalParam())
  val actor:          EventActor     = EventActor("myActor")
  val mockRules:      MockRules      = new MockRules()

  val nodeGrpValNdd = new NodeGroupValidationNeeded(
    () => Set[SimpleTarget](GroupTarget(mockNodeGroups.g0.id)).succeed,
    null,
    mockRules.ruleRepo,
    mockNodeGroups.groupsRepo,
    mockNodes.nodeFactRepo
  )

  "ValidationNeeded" should {

    "always validate a modification in a GlobalParam" in {
      val globalParamChangeReq = GlobalParamChangeRequest(GlobalParamModAction.Create, None)
      nodeGrpValNdd.forGlobalParam(actor, globalParamChangeReq) must beEqualTo(Full(true))
    }

    "validate a modification in a node group if that group is supervised" in {
      val nodeGrp          = mockNodeGroups.g0
      val nodeGrpChangeReq = NodeGroupChangeRequest(DGModAction.CreateSolo, nodeGrp, None, None)
      nodeGrpValNdd.forNodeGroup(actor, nodeGrpChangeReq) must beEqualTo(Full(true))
    }

    "not validate a modification in a node group if that group is not supervised" in {
      val nodeGrp          = mockNodeGroups.g1
      val nodeGrpChangeReq = NodeGroupChangeRequest(DGModAction.CreateSolo, nodeGrp, None, None)
      nodeGrpValNdd.forNodeGroup(actor, nodeGrpChangeReq) must beEqualTo(Full(false))
    }

    "if a modification concerns a rule that" in {

      "targets a supervised node, the modification must be validated" in {
        // defaultRule targets all nodes
        val rule = mockRules.rules.defaultRule

        val ruleChangeReq = RuleChangeRequest(
          RuleModAction.Create,
          rule,
          None
        )
        nodeGrpValNdd.forRule(actor, ruleChangeReq) must beEqualTo(Full(true))
      }

      "doesn't target a supervised node, the modification mustn't be validated" in {
        // copyGitFileRule targets the nodeGroup g1 which is empty
        val rule = mockRules.rules.copyGitFileRule

        val ruleChangeReq = RuleChangeRequest(
          RuleModAction.Create,
          rule,
          None
        )
        nodeGrpValNdd.forRule(actor, ruleChangeReq) must beEqualTo(Full(false))
      }
    }

    val mockGitRepo    = new MockGitConfigRepo("")
    val mockTechniques = MockTechniques(mockGitRepo)
    val mockDirectives = new MockDirectives(mockTechniques)
    val sectionSpec    = SectionSpec("ROOT")
    val technique      = mockDirectives.directives.commonTechnique

    "validate a modification in a directive if any rule that uses it requires validation" in {

      val dirChangeReq = DirectiveChangeRequest(
        DGModAction.Delete,
        TechniqueName(technique.name),
        ActiveTechniqueId("myId"),
        sectionSpec,
        mockDirectives.directives.pkgDirective, // used by defaultRule
        Some(mockDirectives.directives.commonDirective),
        List(),
        List(mockRules.rules.defaultRule)       // targets all nodes, including node group g0
      )

      nodeGrpValNdd.forDirective(actor, dirChangeReq) must beEqualTo(Full(true))
    }

    "not validate a modification in a directive if no rule that uses it requires validation" in {

      val dirChangeReq = DirectiveChangeRequest(
        DGModAction.Delete,
        TechniqueName(technique.name),
        ActiveTechniqueId("myId"),
        sectionSpec,
        mockDirectives.directives.gvdDirective1, // unused by copyGitFileRule
        None,
        List(),
        List(mockRules.rules.copyGitFileRule)    // targets node group g1 which is empty

      )

      nodeGrpValNdd.forDirective(actor, dirChangeReq) must beEqualTo(Full(false))
    }

  }

}
