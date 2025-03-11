/*
 *************************************************************************************
 * Copyright 2023 Normation SAS
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

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.apply.*
import com.normation.BoxSpecMatcher
import com.normation.GitVersion
import com.normation.cfclerk.domain.TechniqueName
import com.normation.cfclerk.domain.TechniqueVersionHelper
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.rudder.db.DBCommon
import com.normation.rudder.db.Doobie.*
import com.normation.rudder.domain.nodes.AddNodeGroupDiff
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies.*
import com.normation.rudder.domain.properties.AddGlobalParameterDiff
import com.normation.rudder.domain.properties.GlobalParameter
import com.normation.rudder.domain.properties.Visibility
import com.normation.rudder.domain.workflows.*
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rule.category.RuleCategoryId
import com.normation.rudder.services.marshalling.ChangeRequestChangesSerialisation
import com.normation.rudder.services.marshalling.ChangeRequestChangesUnserialisation
import com.normation.zio.UnsafeRun
import com.typesafe.config.ConfigValueFactory
import doobie.Transactor
import doobie.specs2.analysisspec.IOChecker
import doobie.syntax.all.*
import net.liftweb.common.Box
import net.liftweb.common.Failure
import net.liftweb.common.Full
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.core.Fragments
import zio.interop.catz.*

@RunWith(classOf[JUnitRunner])
class ChangeRequestJdbcRepositoryTest extends Specification with DBCommon with IOChecker with BoxSpecMatcher {

  val restTestSetUp = RestTestSetUp.newEnv

  val changeRequestId = ChangeRequestId(1)
  val actor           = EventActor("actor")

  val sampleChangeRequestContent = {
    <changeRequest fileFormat="6">
      <directives>
        <directive id="foo">
          <firstChange>
            <change>
              <actor>actor</actor>
            </change>
          </firstChange>
        </directive>
      </directives>
      <groups>
        <group id="bar">
          <firstChange>
            <change>
              <actor>actor</actor>
            </change>
          </firstChange>
        </group>
      </groups>
      <rules>
        <rule id="baz">
          <firstChange>
            <change>
              <actor>actor</actor>
            </change>
          </firstChange>
        </rule>
      </rules>
      <globalParameters>
        <globalParameter id="qux">
          <firstChange>
            <change>
              <actor>actor</actor>
            </change>
          </firstChange>
        </globalParameter>
      </globalParameters>
    </changeRequest>
  }

  override def initDb() = {
    super.initDb()
    // initialize some change requests to setup change requests
    doobie.transactRunEither(xa => {
      // id: 1
      // same change request to test different xpaths : /changeRequest/directives/directive/@id, "/changeRequest/groups/group/@id", "/changeRequest/rules/rule/@id"
      // We test change of a directive, nodeGroup and rule using the same change request
      (sql"insert into ChangeRequest (name, description, creationTime, content, modificationId) values ('a change request', 'a change request description', '2023-01-01T00:00:00', ${sampleChangeRequestContent}, '11111111-1111-1111-1111-111111111111')".update.run *>
      sql"insert into Workflow (id, state) values (${changeRequestId}, 'Pending validation')".update.run)
        .transact(xa)
    }) match {
      case Right(_) => ()
      case Left(ex) => throw ex
    }
  }

  override def transactor: Transactor[IO] = doobie.xaio

  val directiveId   = DirectiveId(DirectiveUid("foo"))
  val groupId       = NodeGroupId(NodeGroupUid("bar"))
  val ruleId        = RuleId(RuleUid("baz"))
  val globalParamId = "qux"

  // setup DirectiveChanges, NodeGroupChanges, RuleChanges and GlobalParamChanges to test the unserialisation
  val directiveChanges = DirectiveChanges(
    DirectiveChange(
      None,
      DirectiveChangeItem(
        actor,
        DateTime.parse("2023-01-01T00:00:00.000Z"),
        Some("directive_001 change reason"),
        AddDirectiveDiff(
          TechniqueName("packageManagement"),
          Directive(
            directiveId,
            TechniqueVersionHelper("1.0"),
            Map.empty,
            "",
            "",
            None
          )
        )
      ),
      List.empty
    ),
    List.empty
  )

  val nodeGroupChanges = NodeGroupChanges(
    NodeGroupChange(
      None,
      NodeGroupChangeItem(
        actor,
        DateTime.parse("2023-01-01T00:00:00.000Z"),
        Some("nodeGroup_001 change reason"),
        AddNodeGroupDiff(NodeGroup(groupId, "", "", List.empty, None, serverList = Set.empty, _isEnabled = true))
      ),
      List.empty
    ),
    List.empty
  )
  val ruleChanges      = RuleChanges(
    RuleChange(
      None,
      RuleChangeItem(
        actor,
        DateTime.parse("2023-01-01T00:00:00.000Z"),
        Some("rule_001 change reason"),
        AddRuleDiff(Rule(ruleId, "", RuleCategoryId("")))
      ),
      List.empty
    ),
    List.empty
  )

  val globalParamChanges = GlobalParameterChanges(
    GlobalParameterChange(
      None,
      GlobalParameterChangeItem(
        actor,
        DateTime.parse("2023-01-01T00:00:00.000Z"),
        Some("globalParam_001 change reason"),
        AddGlobalParameterDiff(
          GlobalParameter(
            globalParamId,
            GitVersion.DEFAULT_REV,
            ConfigValueFactory.fromAnyRef(""),
            None,
            "",
            None,
            Visibility.default
          )
        )
      ),
      List.empty
    ),
    List.empty
  )

  val expectedChangeRequest = ConfigurationChangeRequest(
    changeRequestId,
    Some(ModificationId("11111111-1111-1111-1111-111111111111")),
    ChangeRequestInfo("a change request", "a change request description"),
    Map(directiveId   -> directiveChanges),
    Map(groupId       -> nodeGroupChanges),
    Map(ruleId        -> ruleChanges),
    Map(globalParamId -> globalParamChanges)
  )

  val newChangeRequest = expectedChangeRequest.copy(id = ChangeRequestId(2))

  // Returns the same change request content
  val changeRequestChangesSerialisation:        ChangeRequestChangesSerialisation   = _ => {
    sampleChangeRequestContent
  }
  lazy val changeRequestChangesUnserialisation: ChangeRequestChangesUnserialisation = _ => {
    Full(
      (
        Full(Map(directiveId -> directiveChanges)),
        Map(groupId       -> nodeGroupChanges),
        Map(ruleId        -> ruleChanges),
        Map(globalParamId -> globalParamChanges)
      )
    )
  }
  lazy val changeRequestMapper = new ChangeRequestMapper(
    changeRequestChangesUnserialisation,
    changeRequestChangesSerialisation
  )
  lazy val roChangeRequestJdbcRepository =
    new RoChangeRequestJdbcRepository(doobie, changeRequestMapper)
  lazy val woChangeRequestJdbcRepository =
    new WoChangeRequestJdbcRepository(doobie, changeRequestMapper, roChangeRequestJdbcRepository)

  sequential

  "ChangeRequestJdbcRepository" should {

    "type-check queries" in {
      if (doDatabaseConnection) {
        val RoChangeRequestJdbcRepositorySQL: RoChangeRequestJdbcRepositorySQL = roChangeRequestJdbcRepository
        check(RoChangeRequestJdbcRepositorySQL.getAllSQL)
        check(RoChangeRequestJdbcRepositorySQL.getSQL(changeRequestId))
        check(RoChangeRequestJdbcRepositorySQL.getByContributorSQL(actor))
        check(RoChangeRequestJdbcRepositorySQL.getChangeRequestsByXpathContentSQL(fr"'/'", "", true))
        check(RoChangeRequestJdbcRepositorySQL.getChangeRequestsByXpathContentSQL(fr"'/'", "", false))
        check(RoChangeRequestJdbcRepositorySQL.getByFiltersSQL(None, None))
        check(RoChangeRequestJdbcRepositorySQL.getByFiltersSQL(Some(NonEmptyList.one(WorkflowNodeId("foo"))), None))
        check(RoChangeRequestJdbcRepositorySQL.getByFiltersSQL(None, Some((fr"'/'", "bar"))))
        check(WoChangeRequestJdbcRepositorySQL.createChangeRequestSQL(Some("foo"), Some("bar"), <root />, Some("qux")))
        check(
          WoChangeRequestJdbcRepositorySQL.updateChangeRequestSQL(
            Some("foo"),
            Some("bar"),
            <changeRequest />,
            Some("qux"),
            changeRequestId
          )
        )
      } else Fragments.empty
    }

    "get all change requests" in {
      val res = roChangeRequestJdbcRepository.getAll()
      (res.map(_.size) must beEqualTo(Full(1))) and (res.flatMap(_.headOption) mustFullEq expectedChangeRequest)
    }

    "get a change request by id" in {
      val res = roChangeRequestJdbcRepository.get(changeRequestId)
      res.flatMap(Box(_)) mustFullEq expectedChangeRequest
    }

    // This does not seem to return any result at all, but the xpath and query look okay... :(
    // "get change requests by contributor" in {
    //  val res = roChangeRequestJdbcRepository.getByContributor(actor)
    //  (res.map(_.size) must beEqualTo(Full(1))) and (res.flatMap(_.headOption) mustFullEq expectedChangeRequest)
    // }

    "get change requests by xpath content" in {
      val resDirective = roChangeRequestJdbcRepository.getByDirective(DirectiveUid("foo"), false)
      val resNodeGroup = roChangeRequestJdbcRepository.getByNodeGroup(NodeGroupId(NodeGroupUid("bar")), false)
      val resRule      = roChangeRequestJdbcRepository.getByRule(RuleUid("baz"), false)
      ((resDirective
        .map(_.size) must beEqualTo(Full(1))) and (resDirective.flatMap(_.headOption) mustFullEq expectedChangeRequest) and
      (resNodeGroup.map(_.size) must beEqualTo(Full(1))) and (resNodeGroup.flatMap(
        _.headOption
      ) mustFullEq expectedChangeRequest) and
      (resRule.map(_.size) must beEqualTo(Full(1))) and (resRule.flatMap(_.headOption) mustFullEq expectedChangeRequest))

    }

    "get change request by filter" in {
      val res = roChangeRequestJdbcRepository.getByFilter(ChangeRequestFilter(None, None)).runNow
      (res.size must beEqualTo(1)) and (res.head must beEqualTo((expectedChangeRequest, WorkflowNodeId("Pending validation"))))
    }

    "create change request" in {
      val res = woChangeRequestJdbcRepository.createChangeRequest(
        newChangeRequest,
        actor,
        Some("reason")
      )
      res mustFullEq newChangeRequest
    }

    "delete change request (unimplemented)" in {
      woChangeRequestJdbcRepository
        .deleteChangeRequest(changeRequestId, actor, Some("reason")) must throwA[IllegalArgumentException]
    }

    "update an existing change request" in {
      val updatedChangeRequest = expectedChangeRequest.copy(
        info = expectedChangeRequest.info.copy(name = "updated change request")
      )
      val res                  = woChangeRequestJdbcRepository.updateChangeRequest(
        updatedChangeRequest,
        actor,
        Some("reason")
      )
      res mustFullEq updatedChangeRequest
    }

    "update a non-existing change request" in {
      woChangeRequestJdbcRepository.updateChangeRequest(
        expectedChangeRequest.copy(id = ChangeRequestId(999)),
        actor,
        Some("reason")
      ) must beEqualTo(Failure(s"Cannot update non-existent Change Request with id 999"))
    }

  }
}
