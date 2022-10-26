/*
 *************************************************************************************
 * Copyright 2013 Normation SAS
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

import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.rudder.db.Doobie
import com.normation.rudder.db.Doobie._
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ChangeRequestInfo
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.services.marshalling.ChangeRequestChangesSerialisation
import com.normation.rudder.services.marshalling.ChangeRequestChangesUnserialisation
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import net.liftweb.common._
import net.liftweb.common.Loggable
import org.joda.time.DateTime
import scala.xml.Elem
import zio.interop.catz._

class RoChangeRequestJdbcRepository(
    doobie: Doobie,
    mapper: ChangeRequestMapper
) extends RoChangeRequestRepository with Loggable {

  import doobie._
  import mapper.ChangeRequestRead

  val SELECT_SQL = "SELECT id, name, description, content, modificationId FROM ChangeRequest"

  val SELECT_SQL_JOIN_WORKFLOW =
    "SELECT CR.id, name, description, content, modificationId FROM changeRequest CR LEFT JOIN workflow W on CR.id = W.id"

  // utility method which correctly transform Doobie types towards Box[Vector[ChangeRequest]]
  private[this] def execQuery(q: Query0[Box[ChangeRequest]]): Box[Vector[ChangeRequest]] = {
    transactRunBox(xa => {
      q.to[Vector]
        .map(
          // we are just ignoring change request with unserialisation
          // error. Does not seem the best.
          _.flatten.toVector
        )
        .transact(xa)
    })
  }

  override def getAll(): Box[Vector[ChangeRequest]] = {
    val q = query[Box[ChangeRequest]](SELECT_SQL)
    execQuery(q)
  }

  override def get(changeRequestId: ChangeRequestId): Box[Option[ChangeRequest]] = {
    val q = Query[ChangeRequestId, Box[ChangeRequest]](SELECT_SQL + " where id = ?", None).toQuery0(changeRequestId)
    transactRunBox(xa => q.option.map(_.flatMap(_.toOption)).transact(xa))

  }

  // Get every change request where a user add a change
  override def getByContributor(actor: EventActor): Box[Vector[ChangeRequest]] = {

    val actorName = Array(actor.name)
    val q         = (Fragment.const(
      SELECT_SQL
    ) ++ sql"""where cast( xpath('//firstChange/change/actor/text()',content) as character varying[]) = ${actorName}""")
      .query[Box[ChangeRequest]]
    execQuery(q)
  }

  override def getByDirective(id: DirectiveUid, onlyPending: Boolean): Box[Vector[ChangeRequest]] = {
    getChangeRequestsByXpathContent(
      "/changeRequest/directives/directive/@id",
      id.value,
      s"could not fetch change request for directive with id ${id.value}",
      onlyPending
    )
  }

  override def getByNodeGroup(id: NodeGroupId, onlyPending: Boolean): Box[Vector[ChangeRequest]] = {
    getChangeRequestsByXpathContent(
      "/changeRequest/groups/group/@id",
      id.value,
      s"could not fetch change request for group with id ${id.value}",
      onlyPending
    )
  }

  override def getByRule(id: RuleUid, onlyPending: Boolean): Box[Vector[ChangeRequest]] = {
    getChangeRequestsByXpathContent(
      "/changeRequest/rules/rule/@id",
      id.value,
      s"could not fetch change request for rule with id ${id.value}",
      onlyPending
    )
  }

  /**
   * Retrieve a sequence of change request based on one XML
   * element value.
   * The xpath query must match only one element.
   * We want to be able to find only pending change request without having to request the state of each change request
   * Maybe this function should be in Workflow repository/service instead
   */
  private[this] def getChangeRequestsByXpathContent(
      xpath:        String,
      shouldEquals: String,
      errorMessage: String,
      onlyPending:  Boolean
  ): Box[Vector[ChangeRequest]] = {

    val param = Array(shouldEquals)
    val q     = {
      if (onlyPending) {
        (Fragment.const(
          s"""${SELECT_SQL_JOIN_WORKFLOW} where cast( xpath('${xpath}', content) as character varying[])"""
        ) ++ sql""" = ${param} and state like 'Pending%'""").query[Box[ChangeRequest]]
      } else {
        (Fragment.const(
          s"""${SELECT_SQL} where cast( xpath('${xpath}', content) as character varying[])"""
        ) ++ sql""" = ${param}""").query[Box[ChangeRequest]]
      }
    }
    execQuery(q)
  }

}

class WoChangeRequestJdbcRepository(
    doobie: Doobie,
    mapper: ChangeRequestMapper,
    roRepo: RoChangeRequestRepository
) extends WoChangeRequestRepository with Loggable {

  import doobie._

  // get the different part from a change request: name, description, content, modId
  private[this] def getAtom(cr: ChangeRequest): (Option[String], Option[String], Elem, Option[String]) = {
    val xml   = mapper.crcSerialiser.serialise(cr)
    val name  = cr.info.name match {
      case "" => None
      case x  => Some(x)
    }
    val desc  = cr.info.description match {
      case "" => None
      case x  => Some(x)
    }
    val modId = cr.modId.map(_.value)

    (name, desc, xml, modId)
  }

  /**
   * Save a new change request in the back-end.
   * The id is ignored, and a new one will be attributed
   * to the change request.
   */
  def createChangeRequest(changeRequest: ChangeRequest, actor: EventActor, reason: Option[String]): Box[ChangeRequest] = {

    val (name, desc, xml, modId) = getAtom(changeRequest)

    val q = sql"""insert into ChangeRequest (name, description, creationTime, content, modificationId)
          values (${name}, ${desc}, ${DateTime.now}, ${xml}, ${modId})
       """.update.withUniqueGeneratedKeys[Int]("id")

    for {
      id <- transactRunBox(xa => q.transact(xa))
      cr <- roRepo.get(ChangeRequestId(id)).flatMap {
              case None    =>
                val msg = s"The newly saved change request with ID ${id} was not found back in data base"
                ChangeValidationLogger.error(msg)
                Failure(msg)
              case Some(x) => Full(x)
            }
    } yield {
      cr
    }
  }

  /**
   * Delete a change request.
   * (whatever the read/write mode is).
   */
  def deleteChangeRequest(changeRequestId: ChangeRequestId, actor: EventActor, reason: Option[String]): Box[ChangeRequest] = {
    // we should update it rather, shouldn't we ?
    throw new IllegalArgumentException(
      "This a developer error. Please contact rudder developer, saying that they call unemplemented deleteChangeRequest"
    )
  }

  /**
   * Update a change request. The change request must exists.
   */
  def updateChangeRequest(changeRequest: ChangeRequest, actor: EventActor, reason: Option[String]): Box[ChangeRequest] = {
    // no transaction between steps, because we don't actually use anything in the existing change request

    for {
      cr      <- roRepo.get(changeRequest.id)
      ok      <- cr match {
                   case None    =>
                     val msg = s"Cannot update non-existent Change Request with id ${changeRequest.id.value}"
                     ChangeValidationLogger.warn(msg)
                     Failure(msg)
                   case Some(x) => Full("ok")
                 }
      update  <- {
        val (name, desc, xml, modId) = getAtom(changeRequest)
        val q                        = sql"""update ChangeRequest set name = ${name}, description = ${desc}, content = ${xml}, modificationId = ${modId}
                                 where id = ${changeRequest.id}"""
        transactRunBox(xa => q.update.run.transact(xa))
      }
      updated <- roRepo.get(changeRequest.id).flatMap {
                   case None    =>
                     val msg = s"Couldn't find the updated entry when updating Change Request ${changeRequest.id.value}"
                     ChangeValidationLogger.error(msg)
                     Failure(msg)
                   case Some(x) => Full(x)
                 }
    } yield {
      updated
    }
  }

}

// doobie mapping, must be done here because of TechniqueRepo in ChangeRequestChangesUnserialisation impl
class ChangeRequestMapper(
    val crcUnserialiser: ChangeRequestChangesUnserialisation,
    val crcSerialiser:   ChangeRequestChangesSerialisation
) extends Loggable {

  // id, name, description, content, modificationId
  type CR = (Int, Option[String], Option[String], Elem, Option[String])

  // unserialize the XML.
  // If it fails, produce a failure
  // directives map is boxed because some Exception could be launched
  def unserialize(
      id:          Int,
      name:        Option[String],
      description: Option[String],
      content:     Elem,
      modId:       Option[String]
  ): Box[ChangeRequest] = {
    crcUnserialiser.unserialise(content) match {
      case Full((directivesMaps, nodesMaps, ruleMaps, paramMaps)) =>
        directivesMaps match {
          case Full(map) =>
            Full(
              ConfigurationChangeRequest(
                ChangeRequestId(id),
                modId.map(ModificationId.apply),
                ChangeRequestInfo(
                  name.getOrElse(""),
                  description.getOrElse("")
                ),
                map,
                nodesMaps,
                ruleMaps,
                paramMaps
              )
            )

          case eb: EmptyBox =>
            val fail = eb ?~! s"could not deserialize directive change of change request #${id} cause is: ${eb}"
            ChangeValidationLogger.error(fail)
            fail
        }

      case eb: EmptyBox =>
        val fail = eb ?~! s"Error when trying to get the content of the change request ${id} : ${eb}"
        ChangeValidationLogger.error(fail.msg)
        fail
    }
  }

  def serialize(optCR: Box[ChangeRequest]): CR = {
    optCR match {
      case Full(cr) =>
        val elem = crcSerialiser.serialise(cr)
        val name = cr.info.name match {
          case "" => None
          case x  => Some(x)
        }
        val desc = cr.info.description match {
          case "" => None
          case x  => Some(x)
        }
        (cr.id.value, name, desc, elem, cr.modId.map(_.value))

      case _ =>
        ChangeValidationLogger.error(s"We can only serialize Full(ChangeRequest), not a ${optCR}")
        throw new IllegalArgumentException(s"We can only serialize Full(ChangeRequest)")
    }
  }

  implicit val ChangeRequestRead: Read[Box[ChangeRequest]] = {
    Read[CR].map((t: CR) => unserialize(t._1, t._2, t._3, t._4, t._5))
  }
}
