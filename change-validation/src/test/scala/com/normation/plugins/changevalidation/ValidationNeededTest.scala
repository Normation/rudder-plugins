package com.normation.plugins.changevalidation

import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.{MockNodeGroups, MockNodes}
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.services.workflows.{DGModAction, NodeGroupChangeRequest}
import net.liftweb.common.Full
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner


@RunWith(classOf[JUnitRunner])
class ValidationNeededTest extends Specification {

  val restTestSetUp: RestTestSetUp = RestTestSetUp.newEnv
  val mockNodeGroups: MockNodeGroups = restTestSetUp.mockNodeGroups
  val mockNodes: MockNodes = restTestSetUp.mockNodes

  val nodeGrpValNdd = new NodeGroupValidationNeeded(
    () => IOResult.attempt(Set.from(List(GroupTarget(mockNodeGroups.g0.id)))),
    null,
    null,
    mockNodeGroups.groupsRepo,
    mockNodes.nodeFactRepo)

  "ValidationNeeded" should {

    "return true when modifying a GlobalParam" in {

      val actor = EventActor("myActor")
      val nodeGrp = mockNodeGroups.g0
      val nodeGrpChangeReq = NodeGroupChangeRequest(DGModAction.CreateSolo, nodeGrp, None, None)

      nodeGrpValNdd.forNodeGroup(actor, nodeGrpChangeReq) must beEqualTo(Full(true))
    }
  }

}

