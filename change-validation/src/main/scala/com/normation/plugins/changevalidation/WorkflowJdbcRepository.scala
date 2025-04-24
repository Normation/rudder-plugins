/*
 *************************************************************************************
 * Copyright 2011-2013 Normation SAS
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
import cats.implicits.*
import com.normation.errors.IOResult
import com.normation.rudder.db.Doobie
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.WorkflowNodeId
import doobie.*
import doobie.implicits.*
import doobie.util.fragments
import net.liftweb.common.Loggable
import zio.ZIO
import zio.interop.catz.*

/**
 * Repository to manage the Workflow part
 */
trait RoWorkflowRepository {
  def getAllByState(state: WorkflowNodeId): IOResult[Seq[ChangeRequestId]]

  def getCountByState(filter: NonEmptyList[WorkflowNodeId]): IOResult[Map[WorkflowNodeId, Long]]

  def getStateOfChangeRequest(crId: ChangeRequestId): IOResult[WorkflowNodeId]

  def getAllChangeRequestsState(): IOResult[Map[ChangeRequestId, WorkflowNodeId]]
}

trait WoWorkflowRepository {
  def createWorkflow(crId: ChangeRequestId, state: WorkflowNodeId): IOResult[WorkflowNodeId]

  def updateState(crId: ChangeRequestId, from: WorkflowNodeId, state: WorkflowNodeId): IOResult[WorkflowNodeId]
}

trait RoWorkflowJdbcRepositorySQL {
  def getAllByStateSQL(state: WorkflowNodeId): Query0[ChangeRequestId] = {
    sql"select id from workflow where state = $state".query[ChangeRequestId]
  }

  def getCountByStateSQL(filter: NonEmptyList[WorkflowNodeId]): Query0[(WorkflowNodeId, Long)] = {
    val f = fragments.in(fr"state", filter)
    sql"select state,count(distinct(id)) from workflow where ${f} group by state"
      .query[(WorkflowNodeId, Long)]
  }

  def getStateOfChangeRequestSQL(crId: ChangeRequestId): Query0[WorkflowNodeId] = {
    sql"select state from workflow where id = $crId".query[WorkflowNodeId]
  }

  def getAllChangeRequestsStateSQL: Query0[(ChangeRequestId, WorkflowNodeId)] = {
    sql"select id, state from workflow".query[(ChangeRequestId, WorkflowNodeId)]
  }
}

trait WoWorkflowJdbcRepositorySQL {
  def createWorkflowSQL(crId: ChangeRequestId, state: WorkflowNodeId): Update0 = {
    sql"insert into workflow (id, state) values ($crId, $state)".update
  }

  def updateStateSQL(crId: ChangeRequestId, from: WorkflowNodeId, state: WorkflowNodeId): Update0 = {
    sql"update workflow set state = $state where id = $crId".update
  }
}
object WorkflowJdbcRepositorySQL extends RoWorkflowJdbcRepositorySQL with WoWorkflowJdbcRepositorySQL {}

class RoWorkflowJdbcRepository(doobie: Doobie) extends RoWorkflowRepository with Loggable {
  import WorkflowJdbcRepositorySQL.*
  import doobie.*

  def getAllByState(state: WorkflowNodeId): IOResult[Seq[ChangeRequestId]] = {
    transactIOResult(s"Could not get change request with state ${state.value}")(xa =>
      getAllByStateSQL(state).to[Vector].transact(xa)
    )
  }

  /**
   * Returns the number of change requests for each state in the given filter.
   * If there are no existing change requests for a given state in the filter, the count for this state will be 0.
   * @param filter
   * @return
   */
  override def getCountByState(filter: NonEmptyList[WorkflowNodeId]): IOResult[Map[WorkflowNodeId, Long]] = {
    transactIOResult("Could not get total count of change requests in each state")(xa => {
      for {
        req    <- getCountByStateSQL(filter).to[Vector].transact(xa).map(_.toMap)
        initMap = filter.map((_, 0L)).toList.toMap
        res     = req.combine(initMap)
      } yield {
        res
      }
    })
  }

  def getStateOfChangeRequest(crId: ChangeRequestId): IOResult[WorkflowNodeId] = {
    transactIOResult(s"Could not get state of change request with id ${crId.value}")(xa =>
      getStateOfChangeRequestSQL(crId).unique.transact(xa)
    )
  }

  def getAllChangeRequestsState(): IOResult[Map[ChangeRequestId, WorkflowNodeId]] = {
    transactIOResult("Could not get states of all change requests")(xa => getAllChangeRequestsStateSQL.to[Vector].transact(xa))
      .map(_.toMap)
  }
}

class WoWorkflowJdbcRepository(doobie: Doobie) extends WoWorkflowRepository with Loggable {
  import WorkflowJdbcRepositorySQL.*
  import doobie.*

  def createWorkflow(crId: ChangeRequestId, state: WorkflowNodeId): IOResult[WorkflowNodeId] = {
    val process = {
      for {
        exists <- getStateOfChangeRequestSQL(crId).option
        _      <- exists match {
                    case None    => createWorkflowSQL(crId, state).run.attempt
                    case Some(s) =>
                      val msg =
                        s"Cannot start a workflow for Change Request id ${crId.value}, as it is already part of a workflow in state '${s}'"
                      ChangeValidationLogger.error(msg)
                      (Left(msg)).pure[ConnectionIO]
                  }
      } yield {
        state
      }
    }
    transactIOResult(s"Could not create workflow with id ${crId.value} and state ${state.value}")(xa => process.transact(xa))
  }

  def updateState(crId: ChangeRequestId, from: WorkflowNodeId, state: WorkflowNodeId): IOResult[WorkflowNodeId] = {
    val process: ConnectionIO[Either[String, Unit]] = {
      for {
        exists <- getStateOfChangeRequestSQL(crId).option
        update <- exists match {
                    case Some(s) =>
                      if (s == from) {
                        updateStateSQL(
                          crId,
                          from,
                          state
                        ).run.attempt.map(
                          _.bimap(
                            err => err.getMessage,
                            _ => ()
                          )
                        )
                      } else {
                        val msg = s"Cannot change status of ChangeRequest '${crId.value}': it has status '${s.value}' " +
                          s"but the expected status was '${from.value}'. Perhaps someone else changed it concurrently?"
                        Left(msg).pure[ConnectionIO]
                      }
                    case None    =>
                      val msg =
                        s"Cannot change a workflow for Change Request id ${crId.value}, as it is not part of any workflow yet"
                      Left(msg).pure[ConnectionIO]
                  }
      } yield {
        update
      }
    }

    for {
      res <- transactIOResult(
               s"Could not update state of change request with id ${crId.value} from ${from.value} to ${state.value}"
             )(xa => process.transact(xa))
      _   <- ZIO.whenCase(res) { case Left(err) => ChangeValidationLoggerPure.error(err) }
    } yield {
      state
    }
  }
}
