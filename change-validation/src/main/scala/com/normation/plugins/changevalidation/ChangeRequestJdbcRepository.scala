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

import cats.Show
import cats.data.NonEmptyList
import cats.implicits.toBifunctorOps
import cats.syntax.applicativeError.*
import cats.syntax.functor.*
import cats.syntax.reducible.*
import com.normation.errors.*
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.rudder.db.Doobie
import com.normation.rudder.db.Doobie.*
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ChangeRequestInfo
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.DirectiveChanges
import com.normation.rudder.domain.workflows.GlobalParameterChanges
import com.normation.rudder.domain.workflows.NodeGroupChanges
import com.normation.rudder.domain.workflows.RuleChanges
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.services.marshalling.ChangeRequestChangesSerialisation
import com.normation.rudder.services.marshalling.ChangeRequestChangesUnserialisation
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.fragments
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import org.joda.time.DateTime
import scala.xml.Elem
import zio.interop.catz.*
import zio.syntax.*

/**
 * Change request from the database does not have a trivial mapping to the `ChangeRequest` structure :
 * there is the XML content that needs to be validated, and in some cases (some APIs) we decide to
 * return the valid ones and ignore invalid ones.
 *
 * So, the type here is used to create a Read that does not fail but contains the failure.
 */
sealed trait DbChangeRequest
case class ChangeRequestWithSuccessXml(cr: ChangeRequest)  extends DbChangeRequest
case class ChangeRequestWithInvalidXml(error: RudderError) extends DbChangeRequest

trait RoChangeRequestJdbcRepositorySQL {

  final val ruleIdXPath:        String   = "/changeRequest/rules/rule/@id"
  final val ruleIdXPathFr:      Fragment = Fragment.const(s"'${ruleIdXPath}'")
  final val directiveIdXPath:   String   = "/changeRequest/directives/directive/@id"
  final val directiveIdXPathFr: Fragment = Fragment.const(s"'${directiveIdXPath}'")
  final val groupIdXPath:       String   = "/changeRequest/groups/group/@id"
  final val groupIdXPathFr:     Fragment = Fragment.const(s"'${groupIdXPath}'")

  val changeRequestMapper: ChangeRequestMapper

  import changeRequestMapper.*

  implicit val wfRead: Read[WorkflowNodeId] = Read[String].map(x => WorkflowNodeId(x))

  implicit val ChangeRequestReadOpt: Read[DbChangeRequest] = {
    Read[CR].map {
      case (id, name, description, content, modId) =>
        crcUnserialiser
          .unserialise(content)
          .chainError(s"Error when trying to get the content of the change request ${id}") match {
          case Right((directivesMaps, nodesMaps, ruleMaps, paramMaps)) =>
            ChangeRequestWithSuccessXml(
              ConfigurationChangeRequest(
                ChangeRequestId(id),
                modId.map(ModificationId.apply),
                ChangeRequestInfo(
                  name.getOrElse(""),
                  description.getOrElse("")
                ),
                directivesMaps,
                nodesMaps,
                ruleMaps,
                paramMaps
              )
            )

          case Left(err) =>
            ChangeRequestWithInvalidXml(err)
        }
    }
  }

  def getAllSQL: Query0[DbChangeRequest] =
    sql"SELECT id, name, description, content, modificationId FROM ChangeRequest".query[DbChangeRequest]

  def getSQL(changeRequestId: ChangeRequestId): Query0[ChangeRequest] = {
    sql"SELECT id, name, description, content, modificationId FROM ChangeRequest where id = ${changeRequestId.value}"
      .query[ChangeRequest]
  }

  def getRawCRSQL(changeRequestId: ChangeRequestId): Query0[DbChangeRequest] = {
    sql"SELECT id, name, description, content, modificationId FROM ChangeRequest where id = ${changeRequestId.value}"
      .query[DbChangeRequest]
  }

  def getByContributorSQL(actor: EventActor): Query0[ChangeRequest] = {
    val actorName = Array(actor.name)
    sql"SELECT id, name, description, content, modificationId FROM ChangeRequest where cast( xpath('//firstChange/change/actor/text()',content) as character varying[]) = ${actorName}"
      .query[ChangeRequest]
  }

  def getChangeRequestsByXpathContentSQL(
      xpath:        Fragment,
      shouldEquals: String,
      onlyPending:  Boolean
  ): Query0[ChangeRequest] = {
    val param = Array(shouldEquals)
    if (onlyPending) {
      sql"""SELECT CR.id, name, description, content, modificationId FROM changeRequest CR LEFT JOIN workflow W on CR.id = W.id where cast( xpath(${xpath}, content) as character varying[]) = ${param} and state like 'Pending%'"""
        .query[ChangeRequest]
    } else {
      sql"""SELECT id, name, description, content, modificationId FROM ChangeRequest where cast( xpath(${xpath}, content) as character varying[]) = ${param}"""
        .query[ChangeRequest]
    }
  }

  def getByFiltersSQL(
      statuses:       Option[NonEmptyList[WorkflowNodeId]],
      xpathWithValue: Option[(Fragment, String)] // (xpath, value)
  ): Query0[(ChangeRequest, WorkflowNodeId)] = {
    (fr"SELECT CR.id, CR.name, CR.description, CR.content, CR.modificationId, W.state FROM ChangeRequest CR LEFT JOIN workflow W on CR.id = W.id" ++
    fragments.whereAndOpt(
      statuses.map(nel => fragments.in(fr"state", nel.map(_.value))),
      xpathWithValue.map {
        case (xpath, value) =>
          val param = Array(value)
          fr"cast( xpath(${xpath}, content) as character varying[]) = ${param}"
      }
    )).query[(ChangeRequest, WorkflowNodeId)]
  }

}

trait WoChangeRequestJdbcRepositorySQL {
  def createChangeRequestSQL(name: Option[String], desc: Option[String], xml: Elem, modId: Option[String]): Update0 = {
    sql"""insert into ChangeRequest (name, description, creationTime, content, modificationId)
          values (${name}, ${desc}, ${DateTime.now}, ${xml}, ${modId})
       """.update
  }

  def updateChangeRequestSQL(
      name:  Option[String],
      desc:  Option[String],
      xml:   Elem,
      modId: Option[String],
      id:    ChangeRequestId
  ): Update0 = sql"""update ChangeRequest set name = ${name}, description = ${desc}, content = ${xml}, modificationId = ${modId}
                                 where id = ${id.value}""".update
}

object WoChangeRequestJdbcRepositorySQL extends WoChangeRequestJdbcRepositorySQL

class RoChangeRequestJdbcRepository(
    doobie:                           Doobie,
    override val changeRequestMapper: ChangeRequestMapper
) extends RoChangeRequestRepository with RoChangeRequestJdbcRepositorySQL with Loggable {

  import doobie.*

  // utility method which correctly transform Doobie types towards Box[Vector[ChangeRequest]]
  private def execQuery(errMsg: String, q: Query0[ChangeRequest]): IOResult[Vector[ChangeRequest]] = {
    transactIOResult(errMsg)(xa => { q.to[Vector].transact(xa) })
  }

  override def getAll(): IOResult[Vector[ChangeRequest]] = {
    transactIOResult("errMsg")(xa => {
      getAllSQL
        .to[List]
        .transact(xa)
        .flatMap(vector => {
          val (errors, success) = vector.partitionMap {
            case ChangeRequestWithInvalidXml(err) => Left(err)
            case ChangeRequestWithSuccessXml(suc) => Right(suc)
          }

          NonEmptyList
            .fromList(errors)
            .succeed
            .tapSome {
              case Some(errs) =>
                ChangeValidationLoggerPure
                  .warn(
                    Chained("There are some errors getting all change requests", Accumulated(errs)).fullMsg
                  )
            }
            .as(success.toVector)
        })
    })
  }

  override def get(changeRequestId: ChangeRequestId): IOResult[Option[ChangeRequest]] = {
    transactIOResult(s"Could not get change request with id ${changeRequestId} in database")(xa =>
      getSQL(changeRequestId).option.transact(xa)
    )
  }

  // Get every change request where a user add a change
  override def getByContributor(actor: EventActor): IOResult[Vector[ChangeRequest]] = {
    execQuery(s"Could not get change requests that were modified by ${actor.name} in database", getByContributorSQL(actor))
  }

  override def getByDirective(id: DirectiveUid, onlyPending: Boolean): IOResult[Vector[ChangeRequest]] = {
    execQuery(
      s"Could not get change requests for directive with id ${id.value} in database",
      getChangeRequestsByXpathContentSQL(
        directiveIdXPathFr,
        id.value,
        onlyPending
      )
    )
  }

  override def getByNodeGroup(id: NodeGroupId, onlyPending: Boolean): IOResult[Vector[ChangeRequest]] = {
    execQuery(
      s"Could not get change requests for group with id ${id.serialize} in database",
      getChangeRequestsByXpathContentSQL(
        groupIdXPathFr,
        id.serialize,
        onlyPending
      )
    )
  }

  override def getByRule(id: RuleUid, onlyPending: Boolean): IOResult[Vector[ChangeRequest]] = {
    execQuery(
      s"Could not get change requests for rule with id ${id.value} in database",
      getChangeRequestsByXpathContentSQL(
        ruleIdXPathFr,
        id.value,
        onlyPending
      )
    )
  }

  override def getByFilter(filter: ChangeRequestFilter): IOResult[Vector[(ChangeRequest, WorkflowNodeId)]] = {

    import com.normation.plugins.changevalidation.ChangeRequestFilter.*
    val errorMsg = s"Could not get change request by filter ${filter}"

    def getXPathWithValue(by: ByFilter): (Fragment, String) = by match {
      case ByRule(ruleId)         => (ruleIdXPathFr, ruleId.value)
      case ByDirective(directive) => (directiveIdXPathFr, directive.value)
      case ByNodeGroup(groupId)   => (groupIdXPathFr, groupId.value)
    }

    val filteredQuery = filter match {
      case ChangeRequestFilter(statuses, by) =>
        getByFiltersSQL(statuses.map(_.toNonEmptyList), by.map(getXPathWithValue))
    }

    transactIOResult(errorMsg)(filteredQuery.to[Vector].transact(_))
  }

}

class WoChangeRequestJdbcRepository(
    doobie:                           Doobie,
    override val changeRequestMapper: ChangeRequestMapper,
    roRepo:                           RoChangeRequestRepository
) extends WoChangeRequestRepository with Loggable with WoChangeRequestJdbcRepositorySQL with RoChangeRequestJdbcRepositorySQL {

  import doobie.*

  // get the different part from a change request: name, description, content, modId
  private def getAtom(cr: ChangeRequest): (Option[String], Option[String], Elem, Option[String]) = {
    val xml   = changeRequestMapper.crcSerialiser.serialise(cr)
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
  def createChangeRequest(changeRequest: ChangeRequest, actor: EventActor, reason: Option[String]): IOResult[ChangeRequest] = {

    val (name, desc, xml, modId) = getAtom(changeRequest)

    transactIOResult[Option[ChangeRequest]](s"Could not create change request with id ${changeRequest.id} in database")(xa => {
      (for {
        id <- createChangeRequestSQL(name, desc, xml, modId).withUniqueGeneratedKeys[Int]("id")
        cr <- getSQL(ChangeRequestId(id)).option
      } yield {
        cr
      }).transact(xa)
    })
      .notOptional(s"The new change request cannot be saved in database")
      .tapError(err => ChangeValidationLoggerPure.error(err.fullMsg))
  }

  /**
   * Delete a change request.
   * (whatever the read/write mode is).
   */
  def deleteChangeRequest(
      changeRequestId: ChangeRequestId,
      actor:           EventActor,
      reason:          Option[String]
  ): IOResult[ChangeRequest] = {
    // we should update it rather, shouldn't we ?
    throw new IllegalArgumentException(
      "This a developer error. Please contact rudder developer, saying that they call unemplemented deleteChangeRequest"
    )
  }

  /**
   * Update a change request. The change request must exist.
   */
  def updateChangeRequest(changeRequest: ChangeRequest, actor: EventActor, reason: Option[String]): IOResult[ChangeRequest] = {
    // no transaction between steps, because we don't actually use anything in the existing change request
    val process = {
      for {
        exists <- getRawCRSQL(changeRequest.id).option
        _      <- exists match {
                    case None    =>
                      val msg =
                        s"Change Request with id ${changeRequest.id.value} was not found in database"
                      new IllegalArgumentException(msg).raiseError[ConnectionIO, Unit]
                    case Some(_) =>
                      val (name, desc, xml, modId) = getAtom(changeRequest)
                      updateChangeRequestSQL(name, desc, xml, modId, changeRequest.id).run.void
                  }
      } yield {
        changeRequest
      }
    }

    transactIOResult(
      s"Could not update change request with id ${changeRequest.id.value} in database"
    )(xa => process.transact(xa))
      .tapError(err => ChangeValidationLoggerPure.error(err.fullMsg))
  }
}

// doobie mapping, must be done here because of TechniqueRepo in ChangeRequestChangesUnserialisation impl
class ChangeRequestMapper(
    val crcUnserialiser: ChangeRequestChangesUnserialisation,
    val crcSerialiser:   ChangeRequestChangesSerialisation
) extends Loggable {

  // id, name, description, content, modificationId
  type CR = (Int, Option[String], Option[String], Elem, Option[String])

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

  type ElemXml = (
      Map[DirectiveId, DirectiveChanges],
      Map[NodeGroupId, NodeGroupChanges],
      Map[RuleId, RuleChanges],
      Map[String, GlobalParameterChanges]
  )

  implicit val ElemShow: Show[Elem] = {
    Show.show(e => e.toString())
  }

  implicit val ElemXmlGet: Get[ElemXml] = XmlMeta.get.temap(content => {
    crcUnserialiser
      .unserialise(content)
      .leftMap(err => Chained("Could not get content column from change request ", err).fullMsg)
  })

  implicit val ChangeRequestShow: Show[CR] = {
    Show.show(cr => s"( id : ${cr._1}, name : ${cr._2}, description : ${cr._3}, content : ${cr._4}, modificationId : ${cr._5} ")
  }

  implicit val ChangeRequestRead: Read[ChangeRequest] = {

    Read[(Int, Option[String], Option[String], ElemXml, Option[String])].map {

      case (id, name, description, (directivesMaps, nodesMaps, ruleMaps, paramMaps), modId) =>
        ConfigurationChangeRequest(
          ChangeRequestId(id),
          modId.map(ModificationId.apply),
          ChangeRequestInfo(
            name.getOrElse(""),
            description.getOrElse("")
          ),
          directivesMaps,
          nodesMaps,
          ruleMaps,
          paramMaps
        )
    }
  }
}
