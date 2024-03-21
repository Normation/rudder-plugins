/*
 *************************************************************************************
 * Copyright 2018 Normation SAS
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

import com.normation.NamedZioLogger
import com.normation.eventlog.EventActor
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.FullRuleTargetInfo
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.repository.FullNodeGroupCategory
import io.scalaland.chimney.Transformer
import net.liftweb.common.Logger
import org.slf4j.LoggerFactory
import scala.annotation.nowarn
import scala.collection.immutable.SortedSet
import zio.NonEmptyChunk
import zio.json.*

/**
 * Applicative log of interest for Rudder ops.
 */
object ChangeValidationLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("change-validation")

  object Metrics extends Logger {
    override protected def _logger = LoggerFactory.getLogger("change-validation.metrics")
  }
}

object ChangeValidationLoggerPure extends NamedZioLogger {

  override def loggerName: String = "change-validation"

  object Metrics extends NamedZioLogger {
    override def loggerName: String = "change-validation.metrics"
  }
}

/**
  * Case class used for serializing and deserializing the list of supervised targets from
  * the old file format.
  */
final case class OldFileFormat(supervised: List[String])

object OldFileFormat {
  implicit val decoder: JsonDecoder[OldFileFormat] = DeriveJsonDecoder.gen[OldFileFormat]
  implicit val encoder: JsonEncoder[OldFileFormat] = DeriveJsonEncoder.gen[OldFileFormat]

  implicit val transformer: Transformer[OldFileFormat, SupervisedSimpleTargets] = old =>
    SupervisedSimpleTargets(old.supervised.flatMap(s => RuleTarget.unser(s).collect { case t: SimpleTarget => t }).toSet)

}

final case class ChangeRequestFilter(
    status: Option[NonEmptyChunk[WorkflowNodeId]],
    by:     Option[ChangeRequestFilter.ByFilter]
)

object ChangeRequestFilter {
  sealed trait ByFilter
  final case class ByRule(ruleId: RuleUid)                extends ByFilter
  final case class ByDirective(directiveId: DirectiveUid) extends ByFilter
  final case class ByNodeGroup(nodeGroupId: NodeGroupUid) extends ByFilter
}

/**
 * The supervised simple targets.
 * This is also used to decode API requests.
 */
final case class SupervisedSimpleTargets(supervised: Set[SimpleTarget])

/*
 * What is a group in the API ?
 */
final case class JsonTarget(
    id:          String // this a target id, so either group:groupid or special:specialname
    ,
    name:        String,
    description: String,
    supervised:  Boolean
)

/*
 * The JSON class saved in /var/rudder/plugins/...
 * with the list of targets
 */
final case class UnsupervisedTargetIds(
    unsupervised: SortedSet[SimpleTarget]
)

/*
 * The JSON sent to client side
 */
final case class JsonCategory(
    name:       String,
    categories: List[JsonCategory],
    targets:    List[JsonTarget]
)

/**
  * Case class used to represent the state of a user in the workflow of validation.
  * @param isValidated indicates if a user is validated i.e. stored in validated user list
  * @param userExists indicates if a user is present in user file description
  * WARNING: this class is used to serialize data to JSON, so changing fields may break API compatibility
  */
case class WorkflowUsers(@jsonField("username") actor: EventActor, isValidated: Boolean, userExists: Boolean)

final case class JsonValidatedUsers(validatedUsers: List[EventActor])

trait EventActorJsonCodec {
  implicit val eventActorEncoder: JsonEncoder[EventActor] = JsonEncoder[String].contramap(_.name)
  implicit val eventActorDecoder: JsonDecoder[EventActor] = JsonDecoder[String].map(EventActor.apply(_))
}

trait WorkflowUsersJsonCodec extends EventActorJsonCodec {
  implicit val workflowUsersEncoder:  JsonEncoder[WorkflowUsers]      = DeriveJsonEncoder.gen[WorkflowUsers]
  implicit val validatedUsersDecoder: JsonDecoder[JsonValidatedUsers] = DeriveJsonDecoder.gen[JsonValidatedUsers]
}

trait TargetJsonCodec {
  implicit val jsonTargetEncoder:        JsonEncoder[JsonTarget]   = DeriveJsonEncoder.gen[JsonTarget]
  implicit lazy val jsonCategoryEncoder: JsonEncoder[JsonCategory] = DeriveJsonEncoder.gen[JsonCategory]

  implicit val simpleTargetEncoder: JsonEncoder[SimpleTarget] = JsonEncoder[String].contramap(_.target)

  // provides a natural ordering for SimpleTarget in order to derive codec for ordered collections
  implicit val orderedSimpleTarget: Ordering[SimpleTarget] = Ordering.by(_.target)

  implicit val unsupervisedTargetIdsEncoder: JsonEncoder[UnsupervisedTargetIds] = DeriveJsonEncoder.gen[UnsupervisedTargetIds]
  implicit val unsupervisedTargetIdsDecoder: JsonDecoder[UnsupervisedTargetIds] = {
    @nowarn implicit val simpleTargetDecoder: JsonDecoder[SimpleTarget] = JsonDecoder[String].mapOrFail(s => {
      RuleTarget
        .unser(s)
        .collect { case t: SimpleTarget => t }
        .toRight(s"Error: the string '${s}' can not parsed as a valid rule target")
    })
    DeriveJsonDecoder.gen[UnsupervisedTargetIds]
  }

  implicit val supervisedSimpleTargetsDecoder: JsonDecoder[SupervisedSimpleTargets] = {
    @nowarn implicit val loggedSimpleTargetDecoder: JsonDecoder[SimpleTarget] = {
      JsonDecoder[String].mapOrFail(s => {
        RuleTarget
          .unser(s)
          .collect { case t: SimpleTarget => t }
          .toRight(s"Error: the string '${s}' can not parsed as a valid rule target")
          .left
          .map(err => {
            ChangeValidationLogger.error(err)
            err
          })
      })
    }

    DeriveJsonDecoder.gen[SupervisedSimpleTargets]
  }
}

/*
 * Mapping between Rudder category/group and json one.
 * Defines extension methods to convert to json, and provides json codecs for data types.
 */
object RudderJsonMapping extends TargetJsonCodec with WorkflowUsersJsonCodec {

  implicit class TargetToJson(target: FullRuleTargetInfo) {

    /**
     * We only know how to map SimpleTarget, so just map that.
     */
    def toJson(supervisedSet: Set[SimpleTarget]): Option[JsonTarget] = {

      target.target.target match {
        case st: SimpleTarget =>
          Some(
            JsonTarget(
              st.target,
              target.name,
              target.description,
              supervisedSet.contains(st)
            )
          )
        case _ => None
      }
    }
  }

  implicit class CatToJson(cat: FullNodeGroupCategory) {
    def toJson(supervisedSet: Set[SimpleTarget]): JsonCategory = JsonCategory(
      cat.name,
      cat.subCategories.map(_.toJson(supervisedSet)).sortBy(_.name),
      cat.targetInfos.flatMap(_.toJson(supervisedSet)).sortBy(_.name)
    )
  }

}
