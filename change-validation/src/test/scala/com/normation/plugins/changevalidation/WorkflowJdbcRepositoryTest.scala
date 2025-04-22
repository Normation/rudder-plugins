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
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Cancelled
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Deployment
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Validation
import com.normation.rudder.db.DBCommon
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.zio.UnsafeRun
import doobie.Transactor
import doobie.specs2.analysisspec.IOChecker
import doobie.syntax.all.*
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import zio.interop.catz.*

@RunWith(classOf[JUnitRunner])
class WorkflowJdbcRepositoryTest extends Specification with DBCommon with IOChecker {
  sequential

  override def initDb() = {
    super.initDb()
    // initialize some change requests to setup change requests for workflow
    doobie.transactRunEither(xa => {
      (
        // id: 1
        sql"insert into ChangeRequest (name, description, creationTime, content, modificationId) values ('a change request', 'a change request description', '2023-01-01T00:00:00', '', '11111111-1111-1111-1111-111111111111')".update.run *>

        // insert a change request that has status "Pending validation"
        sql"insert into ChangeRequest (name, description, creationTime, content, modificationId) values ('pendingValidation1', 'a change request description', '2025-01-01T00:00:00', '', '11111111-1111-1111-1111-111111111111')".update.run *>

        // insert two change requests that both have status "Pending deployment"
        sql"insert into ChangeRequest (name, description, creationTime, content, modificationId) values ('pendingDeployment2', 'a change request description', '2025-01-01T00:00:00', '', '11111111-1111-1111-1111-111111111111')".update.run *>
        sql"insert into ChangeRequest (name, description, creationTime, content, modificationId) values ('pendingDeployment3', 'a change request description', '2025-01-01T00:00:00', '', '11111111-1111-1111-1111-111111111111')".update.run *>

        // insert ids 2, 3 and 4 in the workflow table with their corresponding status
        sql"insert into Workflow (id, state) values (2, 'Pending validation')".update.run *>
        sql"insert into Workflow (id, state) values (3, 'Pending deployment')".update.run *>
        sql"insert into Workflow (id, state) values (4, 'Pending deployment')".update.run
      ).transact(xa)
    }) match {
      case Right(_) => ()
      case Left(ex) => throw ex
    }
  }

  override def transactor: Transactor[IO] = doobie.xaio

  private lazy val roWorkflowJdbcRepository = new RoWorkflowJdbcRepository(doobie)
  private lazy val woWorkflowJdbcRepository = new WoWorkflowJdbcRepository(doobie)

  val changeRequestId  = ChangeRequestId(1)
  val changeRequestId2 = ChangeRequestId(2)
  val changeRequestId3 = ChangeRequestId(3)
  val changeRequestId4 = ChangeRequestId(4)

  "WorkflowJdbcRepository" should {

    "type-check queries" in {
      check(WorkflowJdbcRepositorySQL.getAllByStateSQL(WorkflowNodeId("foo")))
      check(WorkflowJdbcRepositorySQL.getCountByStateSQL(NonEmptyList.of(WorkflowNodeId("foo"))))
      check(WorkflowJdbcRepositorySQL.getStateOfChangeRequestSQL(changeRequestId))
      check(WorkflowJdbcRepositorySQL.getAllChangeRequestsStateSQL)
      check(WorkflowJdbcRepositorySQL.createWorkflowSQL(changeRequestId, WorkflowNodeId("foo")))
      check(WorkflowJdbcRepositorySQL.updateStateSQL(changeRequestId, WorkflowNodeId("foo"), WorkflowNodeId("foo")))
    }

    val firstWorkflowNodeId  = WorkflowNodeId("first")
    val secondWorkflowNodeId = WorkflowNodeId("second")
    "create a workflow" in {
      woWorkflowJdbcRepository.createWorkflow(changeRequestId, firstWorkflowNodeId).runNow must beEqualTo(firstWorkflowNodeId)
    }

    "update a workflow" in {
      woWorkflowJdbcRepository.updateState(changeRequestId, firstWorkflowNodeId, secondWorkflowNodeId).runNow must beEqualTo(
        secondWorkflowNodeId
      )
    }

    "get all change requests by state" in {
      roWorkflowJdbcRepository.getAllByState(firstWorkflowNodeId).runNow must beEqualTo(Seq.empty)
      roWorkflowJdbcRepository.getAllByState(secondWorkflowNodeId).runNow must beEqualTo(Vector(changeRequestId))

    }

    "get state of change request" in {
      roWorkflowJdbcRepository.getStateOfChangeRequest(changeRequestId).runNow must beEqualTo(secondWorkflowNodeId)
    }

    "get all change requests state" in {
      roWorkflowJdbcRepository.getAllChangeRequestsState().runNow must beEqualTo(
        Map(
          changeRequestId  -> secondWorkflowNodeId,
          changeRequestId2 -> Validation.id,
          changeRequestId3 -> Deployment.id,
          changeRequestId4 -> Deployment.id
        )
      )
    }

    "get the count of pending change requests for " in {

      "both the 'Pending validation' and 'Pending deployment' states" in {
        roWorkflowJdbcRepository.getCountByState(NonEmptyList.of(Validation.id, Deployment.id)).runNow must beEqualTo(
          Map(
            Validation.id -> 1,
            Deployment.id -> 2
          )
        )
      }

      "the 'Pending validation' state" in {
        roWorkflowJdbcRepository.getCountByState(NonEmptyList.of(Validation.id)).runNow must beEqualTo(
          Map(
            Validation.id -> 1
          )
        )
      }

      "the 'Pending deployment' state" in {
        roWorkflowJdbcRepository.getCountByState(NonEmptyList.of(Deployment.id)).runNow must beEqualTo(
          Map(
            Deployment.id -> 2
          )
        )
      }
    }

    "return 0 for the number of change requests in a given state if no change request is in that state" in {

      (woWorkflowJdbcRepository.updateState(changeRequestId2, Validation.id, Cancelled.id) *>

      roWorkflowJdbcRepository.getCountByState(NonEmptyList.of(Validation.id, Deployment.id))).runNow must beEqualTo(
        Map(
          Validation.id -> 0,
          Deployment.id -> 2
        )
      )
    }
  }
}
