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

import cats.effect.IO
import com.normation.rudder.db.DBCommon
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.WorkflowNodeId
import doobie.Transactor
import doobie.specs2.analysisspec.IOChecker
import doobie.syntax.all.*
import net.liftweb.common.Full
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
      // id: 1
      sql"insert into ChangeRequest (name, description, creationTime, content, modificationId) values ('a change request', 'a change request description', '2023-01-01T00:00:00', '', '11111111-1111-1111-1111-111111111111')".update.run
        .transact(xa)
    }) match {
      case Right(_) => ()
      case Left(ex) => throw ex
    }
  }

  override def transactor: Transactor[IO] = doobie.xaio

  private lazy val roWorkflowJdbcRepository = new RoWorkflowJdbcRepository(doobie)
  private lazy val woWorkflowJdbcRepository = new WoWorkflowJdbcRepository(doobie)

  val changeRequestId = ChangeRequestId(1)

  "WorkflowJdbcRepository" should {

    "type-check queries" in {
      check(WorkflowJdbcRepositorySQL.getAllByStateSQL(WorkflowNodeId("foo")))
      check(WorkflowJdbcRepositorySQL.getStateOfChangeRequestSQL(changeRequestId))
      check(WorkflowJdbcRepositorySQL.getAllChangeRequestsStateSQL)
      check(WorkflowJdbcRepositorySQL.createWorkflowSQL(changeRequestId, WorkflowNodeId("foo")))
      check(WorkflowJdbcRepositorySQL.updateStateSQL(changeRequestId, WorkflowNodeId("foo"), WorkflowNodeId("foo")))
    }

    val firstWorkflowNodeId  = WorkflowNodeId("first")
    val secondWorkflowNodeId = WorkflowNodeId("second")
    "create a workflow" in {
      woWorkflowJdbcRepository.createWorkflow(changeRequestId, firstWorkflowNodeId) must beEqualTo(Full(firstWorkflowNodeId))
    }

    "update a workflow" in {
      woWorkflowJdbcRepository.updateState(changeRequestId, firstWorkflowNodeId, secondWorkflowNodeId) must beEqualTo(
        Full(secondWorkflowNodeId)
      )
    }

    "get all change requests by state" in {
      roWorkflowJdbcRepository.getAllByState(firstWorkflowNodeId) must beEqualTo(Full(Seq.empty))
      roWorkflowJdbcRepository.getAllByState(secondWorkflowNodeId) must beEqualTo(Full(Vector(changeRequestId)))

    }

    "get state of change request" in {
      roWorkflowJdbcRepository.getStateOfChangeRequest(changeRequestId) must beEqualTo(Full(secondWorkflowNodeId))
    }

    "get all change requests state" in {
      roWorkflowJdbcRepository.getAllChangeRequestsState() must beEqualTo(Full(Map(changeRequestId -> secondWorkflowNodeId)))
    }

  }
}
