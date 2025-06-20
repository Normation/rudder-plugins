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

import com.normation.cfclerk.domain.Technique
import com.normation.eventlog.EventActor
import com.normation.plugins.changevalidation.Action.ChangeLogEvent
import com.normation.plugins.changevalidation.Action.ResourceChangeEvent
import com.normation.plugins.changevalidation.ActionChangeJson.create
import com.normation.plugins.changevalidation.ActionChangeJson.delete
import com.normation.plugins.changevalidation.ActionChangeJson.modify
import com.normation.rudder.domain.eventlog.AddChangeRequest
import com.normation.rudder.domain.eventlog.ChangeRequestEventLog
import com.normation.rudder.domain.eventlog.DeleteChangeRequest
import com.normation.rudder.domain.eventlog.ModifyChangeRequest
import com.normation.rudder.domain.eventlog.WorkflowStepChanged
import com.normation.rudder.domain.nodes.AddNodeGroupDiff
import com.normation.rudder.domain.nodes.DeleteNodeGroupDiff
import com.normation.rudder.domain.nodes.ModifyToNodeGroupDiff
import com.normation.rudder.domain.policies.AddDirectiveDiff
import com.normation.rudder.domain.policies.AddRuleDiff
import com.normation.rudder.domain.policies.DeleteDirectiveDiff
import com.normation.rudder.domain.policies.DeleteRuleDiff
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.ModifyToDirectiveDiff
import com.normation.rudder.domain.policies.ModifyToRuleDiff
import com.normation.rudder.domain.properties.AddGlobalParameterDiff
import com.normation.rudder.domain.properties.DeleteGlobalParameterDiff
import com.normation.rudder.domain.properties.ModifyToGlobalParameterDiff
import com.normation.rudder.domain.workflows.Change
import com.normation.rudder.domain.workflows.ChangeItem
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.DirectiveChangeItem
import com.normation.rudder.domain.workflows.GlobalParameterChangeItem
import com.normation.rudder.domain.workflows.NodeGroupChangeItem
import com.normation.rudder.domain.workflows.RuleChangeItem
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.services.eventlog.EventLogDetailsService
import com.normation.rudder.services.modification.DiffService
import com.normation.utils.DateFormaterService
import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.partial.Result
import io.scalaland.chimney.syntax.*
import org.joda.time.DateTime
import zio.Chunk
import zio.json.DeriveJsonEncoder
import zio.json.JsonEncoder
import zio.json.jsonDiscriminator
import zio.json.jsonField

final case class SimpleChangeRequestJson(
    id:                                 ChangeRequestId,
    displayName:                        String,
    status:                             WorkflowNodeId,
    @jsonField("created by") createdBy: String,
    isMergeable:                        Boolean,
    description:                        String
)

object SimpleChangeRequestJson {
  implicit val idEncoder:     JsonEncoder[ChangeRequestId]         = JsonEncoder[Int].contramap[ChangeRequestId](_.value)
  implicit val statusEncoder: JsonEncoder[WorkflowNodeId]          = JsonEncoder[String].contramap[WorkflowNodeId](_.value)
  implicit val encoder:       JsonEncoder[SimpleChangeRequestJson] = DeriveJsonEncoder.gen[SimpleChangeRequestJson]

  def from(cr: ChangeRequest, status: WorkflowNodeId, isMergeable: Boolean)(implicit
      techniqueByDirective: Map[DirectiveId, Technique],
      diffService:          DiffService
  ): SimpleChangeRequestJson = {
    SimpleChangeRequestJson(
      cr.id,
      cr.info.name,
      status,
      cr.owner,
      isMergeable,
      cr.info.description
    )
  }
}

final case class ChangeRequestChangesJson(
    changes: Option[ConfigurationChangesJson]
)

object ChangeRequestChangesJson {
  implicit val encoder: JsonEncoder[ChangeRequestChangesJson] = DeriveJsonEncoder.gen[ChangeRequestChangesJson]

}

final case class ConfigurationChangesJson(
    directives: Chunk[DirectiveChangeActionJson],
    rules:      Chunk[RuleChangeActionJson],
    groups:     Chunk[GroupChangeActionJson],
    parameters: Chunk[GlobalParameterChangeActionJson]
)

object ConfigurationChangesJson {
  implicit val encoder: JsonEncoder[ConfigurationChangesJson] = DeriveJsonEncoder.gen[ConfigurationChangesJson]
}

@jsonDiscriminator("resourceType") sealed trait ResourceType {
  def name: String = this match {
    case ResourceType.directive   => "directive"
    case ResourceType.nodeGroup   => "node group"
    case ResourceType.rule        => "rule"
    case ResourceType.globalParam => "global parameter"
  }
}

object ResourceType {
  case object directive   extends ResourceType
  case object nodeGroup   extends ResourceType
  case object rule        extends ResourceType
  case object globalParam extends ResourceType

  implicit val encoder: JsonEncoder[ResourceType] = JsonEncoder[String].contramap[ResourceType](_.name)
}

@jsonDiscriminator("type") sealed trait Action

object Action {

  final case class ResourceChangeEvent(
      resourceType: ResourceType,
      resourceName: String,
      resourceId:   String,
      action:       ActionChangeJson
  ) extends Action

  implicit def changeToAction[A, B, C <: ChangeItem[B]]: Transformer[Change[A, B, C], Action] = {
    Transformer.derive[Change[A, B, C], Action]
  }

  object ResourceChangeEvent {
    implicit val encoder: JsonEncoder[ResourceChangeEvent] = DeriveJsonEncoder.gen[ResourceChangeEvent]

    implicit val directiveChangeTransform: Transformer[DirectiveChangeItem, ResourceChangeEvent] = {
      Transformer
        .define[DirectiveChangeItem, ResourceChangeEvent]
        .withFieldConst(_.resourceType, ResourceType.directive)
        .withFieldComputed(_.resourceName, _.diff.directive.name)
        .withFieldComputed(_.resourceId, _.diff.directive.id.serialize)
        .withFieldComputed(
          _.action,
          _.diff match {
            case a: AddDirectiveDiff      => create
            case d: DeleteDirectiveDiff   => delete
            case m: ModifyToDirectiveDiff => modify
          }
        )
        .buildTransformer
    }

    implicit val nodeGroupChangeTransform: Transformer[NodeGroupChangeItem, ResourceChangeEvent] = {
      Transformer
        .define[NodeGroupChangeItem, ResourceChangeEvent]
        .withFieldConst(_.resourceType, ResourceType.nodeGroup)
        .withFieldComputed(_.resourceName, _.diff.group.name)
        .withFieldComputed(_.resourceId, _.diff.group.id.serialize)
        .withFieldComputed(
          _.action,
          _.diff match {
            case a: AddNodeGroupDiff      => create
            case d: DeleteNodeGroupDiff   => delete
            case m: ModifyToNodeGroupDiff => modify
          }
        )
        .buildTransformer
    }

    implicit val ruleChangeTransform: Transformer[RuleChangeItem, ResourceChangeEvent] = {
      Transformer
        .define[RuleChangeItem, ResourceChangeEvent]
        .withFieldConst(_.resourceType, ResourceType.rule)
        .withFieldComputed(_.resourceName, _.diff.rule.name)
        .withFieldComputed(_.resourceId, _.diff.rule.id.serialize)
        .withFieldComputed(
          _.action,
          _.diff match {
            case a: AddRuleDiff      => create
            case d: DeleteRuleDiff   => delete
            case m: ModifyToRuleDiff => modify
          }
        )
        .buildTransformer
    }

    implicit val globalParameterChangeTransform: Transformer[GlobalParameterChangeItem, ResourceChangeEvent] = {
      Transformer
        .define[GlobalParameterChangeItem, ResourceChangeEvent]
        .withFieldConst(_.resourceType, ResourceType.globalParam)
        .withFieldComputed(_.resourceName, _.diff.parameter.name)
        .withFieldComputed(_.resourceId, _.diff.parameter.name)
        .withFieldComputed(
          _.action,
          _.diff match {
            case a: AddGlobalParameterDiff      => create
            case d: DeleteGlobalParameterDiff   => delete
            case m: ModifyToGlobalParameterDiff => modify
          }
        )
        .buildTransformer
    }

  }

  final case class ChangeLogEvent(
      action: String
  ) extends Action
  object ChangeLogEvent {
    implicit val encoder: JsonEncoder[ChangeLogEvent] = DeriveJsonEncoder.gen[ChangeLogEvent]
  }

  implicit val encoder: JsonEncoder[Action] = DeriveJsonEncoder.gen[Action]
}

/**
 * Class that represents an event log, whether it is a
 *  - workflow event log
 *  - change request event log
 *  - resource change event log.
 *
 * @param action the description of the event
 * @param actor the name of the actor who triggered the event
 * @param reason the reason of the event, which may be empty
 * @param date the date of the event
 */
final case class EventLogJson(
    action: Action,
    actor:  EventActor,
    reason: Option[String],
    date:   DateTime
)

object EventLogJson {

  implicit val actorEncoder:    JsonEncoder[EventActor] = JsonEncoder[String].contramap(_.name)
  implicit val dateTimeEncoder: JsonEncoder[DateTime]   =
    JsonEncoder[String].contramap(date => DateFormaterService.getDisplayDate(date))

  implicit def workflowEventTransformer(implicit
      eventLogDetailsService: EventLogDetailsService
  ): Transformer[WorkflowStepChanged, EventLogJson] = {
    Transformer
      .define[WorkflowStepChanged, EventLogJson]
      .withFieldComputed(
        _.action,
        wfEvent => {
          val step   = eventLogDetailsService.getWorkflotStepChange(wfEvent.details)
          val action = step.map(step => s"Status changed from ${step.from} to ${step.to}").getOrElse("State changed")
          ChangeLogEvent(action)
        }
      )
      .withFieldRenamed(_.principal, _.actor)
      .withFieldRenamed(_.creationDate, _.date)
      .withFieldComputedFrom(_.eventDetails)(_.reason, _.reason)
      .buildTransformer
  }

  implicit def changeRequestEventTransformer: Transformer[ChangeRequestEventLog, EventLogJson] = {
    Transformer
      .define[ChangeRequestEventLog, EventLogJson]
      .withFieldComputed(
        _.action,
        {
          case _: AddChangeRequest    => ChangeLogEvent("Change request created")
          case _: ModifyChangeRequest => ChangeLogEvent("Change request details modified")
          case _: DeleteChangeRequest => ChangeLogEvent("Change request deleted")
        }
      )
      .withFieldRenamed(_.principal, _.actor)
      .withFieldRenamed(_.creationDate, _.date)
      .withFieldComputedFrom(_.eventDetails)(_.reason, _.reason)
      .buildTransformer

  }

}

/**
 * Class that represents the main details of a given change request (name, id, status, ...)
 * as well as its full event log history and all the node group, directive, rule, and parameter changes.
 */
final case class ChangeRequestWithHistoryJson(
    changeRequest: ChangeRequestJson,
    isPending:     Boolean,
    eventLogs:     Chunk[EventLogJson]
)

object ChangeRequestWithHistoryJson {
  implicit val encoder:         JsonEncoder[ChangeRequestWithHistoryJson] = DeriveJsonEncoder.gen[ChangeRequestWithHistoryJson]
  implicit val actorEncoder:    JsonEncoder[EventActor]                   = EventLogJson.actorEncoder
  implicit val dateTimeEncoder: JsonEncoder[DateTime]                     = EventLogJson.dateTimeEncoder
  implicit val eventLogEncoder: JsonEncoder[EventLogJson]                 = DeriveJsonEncoder.gen[EventLogJson]

  implicit def changeToEventLog[A, B, C <: ChangeItem[B]](implicit
      actionTransformer: Transformer[C, ResourceChangeEvent]
  ): Transformer[Change[A, B, C], EventLogJson] = {
    Transformer
      .define[Change[A, B, C], EventLogJson]
      .withFieldComputed(_.action, _.firstChange.transformInto[ResourceChangeEvent])
      .withFieldComputed(_.actor, _.firstChange.actor)
      .withFieldComputed(_.reason, _.firstChange.reason)
      .withFieldComputed(_.date, _.firstChange.creationDate)
      .buildTransformer
  }

  def from(
      cr:        ChangeRequest,
      crJson:    ChangeRequestJson,
      isPending: Boolean,
      wfLogs:    Seq[WorkflowStepChanged],
      crLogs:    Seq[ChangeRequestEventLog]
  )(implicit eventLogDetailsService: EventLogDetailsService): ChangeRequestWithHistoryJson = {

    val resourceChangeLogs = cr match {
      case cr: ConfigurationChangeRequest =>
        val directives   = cr.directives.values.map(_.changes).map(_.transformInto[EventLogJson](changeToEventLog))
        val nodeGroups   = cr.nodeGroups.values.map(_.changes).map(_.transformInto[EventLogJson](changeToEventLog))
        val rules        = cr.rules.values.map(_.changes).map(_.transformInto[EventLogJson](changeToEventLog))
        val globalParams = cr.globalParams.values.map(_.changes).map(_.transformInto[EventLogJson](changeToEventLog))

        Chunk.from(directives) ++ Chunk.from(nodeGroups) ++ Chunk.from(rules) ++ Chunk.from(globalParams)

      case _ =>
        Chunk.empty
    }

    val eventLogs = {
      Chunk.from(crLogs.map(_.transformInto[EventLogJson]))
      ++ Chunk.from(wfLogs.map(_.transformInto[EventLogJson]))
      ++ resourceChangeLogs
    }

    ChangeRequestWithHistoryJson(
      crJson,
      isPending,
      eventLogs
    )
  }
}
