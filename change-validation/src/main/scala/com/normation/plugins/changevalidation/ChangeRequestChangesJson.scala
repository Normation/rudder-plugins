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

import cats.data.NonEmptyList
import com.normation.cfclerk.domain.Technique
import com.normation.errors.Accumulated
import com.normation.errors.AccumulateErrors
import com.normation.errors.Inconsistency
import com.normation.errors.IOResult
import com.normation.errors.PureResult
import com.normation.errors.RudderError
import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.plugins.changevalidation.Action.ChangeLogEvent
import com.normation.plugins.changevalidation.Action.ResourceChangeEvent
import com.normation.plugins.changevalidation.ActionChangeJson.create
import com.normation.plugins.changevalidation.ActionChangeJson.delete
import com.normation.plugins.changevalidation.ActionChangeJson.modify
import com.normation.plugins.changevalidation.GroupChangeJson.GroupCreateChangeJson
import com.normation.plugins.changevalidation.GroupChangeJson.GroupDeleteChangeJson
import com.normation.plugins.changevalidation.GroupChangeJson.GroupModifyChangeJson
import com.normation.rudder.domain.eventlog.AddChangeRequest
import com.normation.rudder.domain.eventlog.ChangeRequestEventLog
import com.normation.rudder.domain.eventlog.DeleteChangeRequest
import com.normation.rudder.domain.eventlog.ModifyChangeRequest
import com.normation.rudder.domain.eventlog.WorkflowStepChanged
import com.normation.rudder.domain.nodes.AddNodeGroupDiff
import com.normation.rudder.domain.nodes.DeleteNodeGroupDiff
import com.normation.rudder.domain.nodes.ModifyToNodeGroupDiff
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies
import com.normation.rudder.domain.policies.AddDirectiveDiff
import com.normation.rudder.domain.policies.AddRuleDiff
import com.normation.rudder.domain.policies.CompositeRuleTarget
import com.normation.rudder.domain.policies.DeleteDirectiveDiff
import com.normation.rudder.domain.policies.DeleteRuleDiff
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.domain.policies.ModifyToDirectiveDiff
import com.normation.rudder.domain.policies.ModifyToRuleDiff
import com.normation.rudder.domain.policies.NonGroupRuleTarget
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.domain.policies.TargetComposition
import com.normation.rudder.domain.policies.TargetExclusion
import com.normation.rudder.domain.policies.TargetIntersection
import com.normation.rudder.domain.policies.TargetUnion
import com.normation.rudder.domain.properties.AddGlobalParameterDiff
import com.normation.rudder.domain.properties.DeleteGlobalParameterDiff
import com.normation.rudder.domain.properties.ModifyToGlobalParameterDiff
import com.normation.rudder.domain.workflows.Change
import com.normation.rudder.domain.workflows.ChangeItem
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.DirectiveChangeItem
import com.normation.rudder.domain.workflows.DirectiveChanges
import com.normation.rudder.domain.workflows.GlobalParameterChangeItem
import com.normation.rudder.domain.workflows.GlobalParameterChanges
import com.normation.rudder.domain.workflows.NodeGroupChangeItem
import com.normation.rudder.domain.workflows.NodeGroupChanges
import com.normation.rudder.domain.workflows.RuleChangeItem
import com.normation.rudder.domain.workflows.RuleChanges
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.facts.nodes.NodeFactRepository
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.repository.RoDirectiveRepository
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.services.eventlog.EventLogDetailsService
import com.normation.rudder.services.modification.DiffService
import com.normation.utils.DateFormaterService
import com.normation.zio.UnsafeRun
import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.partial
import io.scalaland.chimney.partial.Result
import io.scalaland.chimney.partial.syntax.asResult
import io.scalaland.chimney.partial.syntax.orErrorAsResult
import io.scalaland.chimney.syntax.*
import org.joda.time.DateTime
import zio.Chunk
import zio.json.DeriveJsonEncoder
import zio.json.JsonEncoder
import zio.json.jsonDiscriminator
import zio.json.jsonExclude
import zio.json.jsonField

final case class SimpleChangeRequestJson(
    id:                                 ChangeRequestId,
    displayName:                        String,
    status:                             WorkflowNodeId,
    @jsonField("created by") createdBy: String,
    isMergeable:                        Boolean,
    description:                        String
)

/**
 * Class that represents a Change request's main details
 * Equivalent to the ChangeRequestJson format, only without the changes field.
 *
 */
object SimpleChangeRequestJson {
  implicit val idEncoder:     JsonEncoder[ChangeRequestId]         = JsonEncoder[Int].contramap[ChangeRequestId](_.value)
  implicit val statusEncoder: JsonEncoder[WorkflowNodeId]          = JsonEncoder[String].contramap[WorkflowNodeId](_.value)
  implicit val encoder:       JsonEncoder[SimpleChangeRequestJson] = DeriveJsonEncoder.gen[SimpleChangeRequestJson]

  def from(cr: ChangeRequest, status: WorkflowNodeId, isMergeable: Boolean): SimpleChangeRequestJson = {
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

  implicit def transformErrorsToRudderError: Transformer[Result.Errors, RudderError] = {
    case Result.Errors(errors) =>
      Accumulated(
        NonEmptyList
          .fromListUnsafe(errors.toList) // This is safe because errors is non-empty
          .map(e => Inconsistency(s"Error while serializing change request at ${e.path.asString} : ${e.message.asString}"))
      )
  }

  def from(cr: ChangeRequest)(implicit
      techniqueByDirective:  Map[DirectiveId, Technique],
      diffService:           DiffService,
      qc:                    QueryContext,
      nodeFactRepository:    NodeFactRepository,
      directiveRepository:   RoDirectiveRepository,
      roNodeGroupRepository: RoNodeGroupRepository
  ): PureResult[ChangeRequestChangesJson] = {
    cr match {
      case cr: ConfigurationChangeRequest => {
        cr.transformIntoPartial[ConfigurationChangesJson](ConfigurationChangesJson.transformer)
          .map(c => ChangeRequestChangesJson(Some(c)))
          .asEither
          .left
          .map(_.transformInto[RudderError])
      }
      case _ =>
        Right(ChangeRequestChangesJson(None))
    }
  }
}

final case class ConfigurationChangesJson(
    directives: Chunk[DirectiveChangeActionJson],
    rules:      Chunk[ExtendedRuleChangeJson],
    groups:     Chunk[ExtendedGroupChangeJson],
    parameters: Chunk[GlobalParameterChangeActionJson]
)

object ConfigurationChangesJson {

  implicit def transformer(implicit
      techniqueByDirective:  Map[DirectiveId, Technique],
      diffService:           DiffService,
      qc:                    QueryContext,
      nodeFactRepository:    NodeFactRepository,
      roDirectiveRepository: RoDirectiveRepository,
      roNodeGroupRepository: RoNodeGroupRepository
  ): PartialTransformer[ConfigurationChangeRequest, ConfigurationChangesJson] = {
    PartialTransformer
      .define[ConfigurationChangeRequest, ConfigurationChangesJson]
      .withFieldComputedPartial(
        _.directives,
        cr => {
          Result.traverse[Chunk[DirectiveChangeActionJson], (DirectiveId, DirectiveChanges), DirectiveChangeActionJson](
            cr.directives.iterator,
            {
              case (directiveId, changes) =>
                for {
                  technique <- Result.fromOptionOrString(
                                 techniqueByDirective.get(directiveId),
                                 s"Error while fetching technique for directive ${directiveId}"
                               )
                  res       <- {
                    implicit val foundTechnique = technique
                    changes.changes.transformIntoPartial[DirectiveChangeActionJson]
                  }

                } yield res
            },
            failFast = false
          )
        }
      )
      .withFieldComputedPartial(
        _.rules,
        cr => {
          Result.traverse[Chunk[ExtendedRuleChangeJson], (RuleId, RuleChanges), ExtendedRuleChangeJson](
            cr.rules.iterator,
            {
              case (directiveId, changes) =>
                changes.changes
                  .transformIntoPartial[RuleChangeActionJson]
                  .flatMap(_.transformIntoPartial[ExtendedRuleChangeJson])
            },
            failFast = false
          )
        }
      )
      .withFieldComputedPartial(
        _.groups,
        cr => {
          Result
            .traverse[Chunk[ExtendedGroupChangeJson], (NodeGroupId, NodeGroupChanges), ExtendedGroupChangeJson](
              cr.nodeGroups.iterator,
              {
                case (groupId, changes) =>
                  changes.changes
                    .transformIntoPartial[GroupChangeActionJson]
                    .flatMap(_.transformIntoPartial[ExtendedGroupChangeJson])
              },
              failFast = false
            )

        }
      )
      .withFieldComputedPartial(
        _.parameters,
        cr => {
          Result
            .traverse[Chunk[GlobalParameterChangeActionJson], (String, GlobalParameterChanges), GlobalParameterChangeActionJson](
              cr.globalParams.iterator,
              {
                case (parameterName, changes) =>
                  changes.changes.transformIntoPartial[GlobalParameterChangeActionJson]
              },
              failFast = false
            )
        }
      )
      .buildTransformer
  }
  implicit val encoder: JsonEncoder[ConfigurationChangesJson] = DeriveJsonEncoder.gen[ConfigurationChangesJson]
}

final case class ExtendedGroupChangeJson(
    action:   ActionChangeJson,
    change:   GroupChangeJson,
    nodeInfo: Chunk[NodeIdent]
)

object ExtendedGroupChangeJson {

  implicit def transformer(implicit
      nodeFactRepo: NodeFactRepository,
      qc:           QueryContext
  ): PartialTransformer[GroupChangeActionJson, ExtendedGroupChangeJson] = {

    def nodeIdtoIdent(id: NodeId): Result[NodeIdent] = {
      (for {
        node <- nodeFactRepo.get(id).notOptional(s"Node with id ${id.value} not found.")
      } yield {
        (id, node.fqdn)
      }).either.runNow match {
        case Left(err)       => partial.Result.fromErrorString(s"Node with id ${id.value} not found.")
        case Right(id, name) => partial.Result.fromValue(NodeIdent(id, name))
      }
    }

    PartialTransformer
      .define[GroupChangeActionJson, ExtendedGroupChangeJson]
      .withFieldComputedPartial(
        _.nodeInfo,
        _.change match {
          case c: GroupCreateChangeJson =>
            Result.traverse[Chunk[NodeIdent], NodeId, NodeIdent](
              c.group.serverList.iterator,
              nodeIdtoIdent,
              failFast = false
            )

          case d: GroupDeleteChangeJson =>
            Result.traverse[Chunk[NodeIdent], NodeId, NodeIdent](
              d.group.serverList.iterator,
              nodeIdtoIdent,
              failFast = false
            )
          case m: GroupModifyChangeJson =>
            Result.traverse[Chunk[NodeIdent], NodeId, NodeIdent](
              m.change.modNodeList match {
                case SimpleValueJson(value)             => value.iterator
                case SimpleDiffJson(oldValue, newValue) => Set.from(oldValue ++ newValue).iterator
              },
              nodeIdtoIdent,
              failFast = false
            )
        }
      )
      .buildTransformer
  }

  implicit val encoder: JsonEncoder[ExtendedGroupChangeJson] = DeriveJsonEncoder.gen[ExtendedGroupChangeJson]
}

final case class NodeIdent(
    id:   NodeId,
    name: String
)

object NodeIdent {
  implicit val idEncoder: JsonEncoder[NodeId]    = JsonEncoder[String].contramap[NodeId](_.value)
  implicit val encoder:   JsonEncoder[NodeIdent] = DeriveJsonEncoder.gen[NodeIdent]
}

final case class ExtendedRuleChangeJson(
    action:         ActionChangeJson,
    change:         RuleChangeJson,
    ruleTargetInfo: Chunk[RuleTargetExtended],
    directiveInfo:  Chunk[DirectiveIdent]
)

object ExtendedRuleChangeJson {

  implicit def transformer(implicit
      roDirectiveRepo:       RoDirectiveRepository,
      roNodeGroupRepository: RoNodeGroupRepository,
      qc:                    QueryContext
  ): PartialTransformer[RuleChangeActionJson, ExtendedRuleChangeJson] = {

    implicit val ruleTargetTransformer: Transformer[RuleTarget, RuleTargetExtended] = RuleTargetExtended.transformer

    def directiveIdToIdent(uid: DirectiveUid): Result[DirectiveIdent] = {
      (for {
        directive <- roDirectiveRepo.getDirective(uid).notOptional(s"Directive with id ${uid} not found.")
      } yield {
        (uid, directive.name)
      }).either.runNow match {
        case Left(err)        => partial.Result.fromErrorString(s"Directive with id ${uid} not found.")
        case Right(uid, name) => partial.Result.fromValue(DirectiveIdent(uid, name))
      }
    }

    PartialTransformer
      .define[RuleChangeActionJson, ExtendedRuleChangeJson]
      .withFieldComputedPartial(
        _.ruleTargetInfo,
        _.change match {
          case RuleChangeJson.RuleCreateChangeJson(rule)   =>
            partial.Result.fromValue(Chunk.from(rule.targets.map(_.toRuleTarget.transformInto[RuleTargetExtended])))
          case RuleChangeJson.RuleDeleteChangeJson(rule)   =>
            partial.Result.fromValue(Chunk.from(rule.targets.map(_.toRuleTarget.transformInto[RuleTargetExtended])))
          case RuleChangeJson.RuleModifyChangeJson(change) =>
            change.modTarget match {
              case SimpleDiffJson(oldValue, newValue) =>
                partial.Result.fromValue(
                  Chunk.from(oldValue.map(_.transformInto[RuleTargetExtended]))
                  ++ Chunk.from(newValue.map(_.transformInto[RuleTargetExtended]))
                )

              case SimpleValueJson(value) =>
                partial.Result.fromValue(Chunk.from(value.map(_.transformInto[RuleTargetExtended])))
            }
        }
      )
      .withFieldComputedPartial(
        _.directiveInfo,
        _.change match {
          case RuleChangeJson.RuleCreateChangeJson(rule) =>
            Result.traverse[Chunk[DirectiveIdent], DirectiveUid, DirectiveIdent](
              rule.directives.map(DirectiveUid(_)).iterator,
              directiveIdToIdent,
              failFast = false
            )

          case RuleChangeJson.RuleDeleteChangeJson(rule)   =>
            Result.traverse[Chunk[DirectiveIdent], DirectiveUid, DirectiveIdent](
              rule.directives.map(DirectiveUid(_)).iterator,
              directiveIdToIdent,
              failFast = false
            )
          case RuleChangeJson.RuleModifyChangeJson(change) =>
            Result.traverse[Chunk[DirectiveIdent], DirectiveUid, DirectiveIdent](
              change.modDirectiveIds match {
                case SimpleDiffJson(oldValue, newValue) => Set.from(oldValue ++ newValue).map(_.uid).iterator
                case SimpleValueJson(value)             => value.map(_.uid).iterator
              },
              directiveIdToIdent,
              failFast = false
            )
        }
      )
      .buildTransformer
  }

  implicit val encoder:                  JsonEncoder[ExtendedRuleChangeJson]        = DeriveJsonEncoder.gen[ExtendedRuleChangeJson]
  implicit val targetEncoder:            JsonEncoder[RuleTargetExtended]            = DeriveJsonEncoder.gen[RuleTargetExtended]
  implicit val compositionTargetEncoder: JsonEncoder[CompositionRuleTargetExtended] =
    DeriveJsonEncoder.gen[CompositionRuleTargetExtended]
  implicit val simpleTargetEncoder:      JsonEncoder[SimpleRuleTargetExtended]      = DeriveJsonEncoder.gen[SimpleRuleTargetExtended]
}

@jsonDiscriminator("type") sealed trait RuleTargetExtended {}
sealed trait CompositionRuleTargetExtended extends RuleTargetExtended {}
sealed trait SimpleRuleTargetExtended      extends RuleTargetExtended {}

final case class GroupTargetExtended(
    id:   NodeGroupId,
    name: String
) extends SimpleRuleTargetExtended

object GroupTargetExtended {
  implicit val idEncoder: JsonEncoder[NodeGroupId]         = JsonEncoder[String].contramap[NodeGroupId](_.serialize)
  implicit val encoder:   JsonEncoder[GroupTargetExtended] = DeriveJsonEncoder.gen[GroupTargetExtended]
}
final case class NonGroupTargetExtended(r: String) extends SimpleRuleTargetExtended
final case class ComposedTarget(
    include: CompositionRuleTargetExtended,
    exclude: CompositionRuleTargetExtended
) extends RuleTargetExtended
@jsonExclude final case class OrComposition(or: Chunk[RuleTargetExtended]) extends CompositionRuleTargetExtended
@jsonExclude final case class AndComposition(and: Chunk[RuleTargetExtended]) extends CompositionRuleTargetExtended

object RuleTargetExtended {

  private def nodeGroupIdToIdent(
      id: NodeGroupId
  )(implicit groupRepository: RoNodeGroupRepository, qc: QueryContext): Result[GroupTargetExtended] = {
    (for {
      group <- groupRepository.getNodeGroup(id)
    } yield {
      (id, group._1.name)
    }).either.runNow match {
      case Left(err)       => partial.Result.fromErrorString(s"Node group with id ${id.serialize} not found.")
      case Right(id, name) => partial.Result.fromValue(GroupTargetExtended(id, name))
    }
  }

  implicit def composedTransformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[TargetExclusion, ComposedTarget] = {
    Transformer
      .define[TargetExclusion, ComposedTarget]
      .withFieldComputed(_.include, _.includedTarget.transformInto[CompositionRuleTargetExtended])
      .withFieldComputed(_.exclude, _.excludedTarget.transformInto[CompositionRuleTargetExtended])
      .buildTransformer
  }

  implicit def compositionTransformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[TargetComposition, CompositionRuleTargetExtended] = {
    Transformer
      .define[TargetComposition, CompositionRuleTargetExtended]
      .withSealedSubtypeHandled[TargetUnion](u => u.transformInto[OrComposition])
      .withSealedSubtypeHandled[TargetIntersection](i => i.transformInto[AndComposition])
      .buildTransformer
  }

  implicit def orTransformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[TargetUnion, OrComposition] = {
    Transformer
      .define[TargetUnion, OrComposition]
      .withFieldComputed(_.or, union => Chunk.from(union.targets.map(_.transformInto[RuleTargetExtended])))
      .buildTransformer
  }

  implicit def andTransformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[TargetIntersection, AndComposition] = {
    Transformer
      .define[TargetIntersection, AndComposition]
      .withFieldComputed(_.and, inter => Chunk.from(inter.targets.map(_.transformInto[RuleTargetExtended])))
      .buildTransformer
  }

  implicit def groupTransformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[GroupTarget, GroupTargetExtended] = {
    Transformer
      .define[GroupTarget, GroupTargetExtended]
      .withFieldComputed(_.id, _.groupId)
      .withFieldComputed(_.name, _ => "TODO")
      .buildTransformer
  }

  implicit def nonGroupTransformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[NonGroupRuleTarget, NonGroupTargetExtended] = {
    Transformer
      .define[NonGroupRuleTarget, NonGroupTargetExtended]
      .withFieldComputed(_.r, _.target)
      .buildTransformer
  }

  implicit def transformer(implicit
      groupRepository: RoNodeGroupRepository,
      qc:              QueryContext
  ): Transformer[RuleTarget, RuleTargetExtended] = {

    Transformer
      .define[RuleTarget, RuleTargetExtended]
      .withSealedSubtypeHandled[CompositeRuleTarget] {
        case e: TargetExclusion   => e.transformInto[ComposedTarget]
        case c: TargetComposition => c.transformInto[CompositionRuleTargetExtended]
      }
      .withSealedSubtypeHandled[SimpleTarget] {
        case g: policies.GroupTarget => g.transformInto[GroupTargetExtended]
        case n: NonGroupRuleTarget   => n.transformInto[NonGroupTargetExtended]
      }
      .buildTransformer
  }
}

final case class DirectiveIdent(
    id:   DirectiveUid,
    name: String
)

object DirectiveIdent {
  implicit val idEncoder: JsonEncoder[DirectiveUid]   = JsonEncoder[String].contramap[DirectiveUid](_.serialize)
  implicit val encoder:   JsonEncoder[DirectiveIdent] = DeriveJsonEncoder.gen[DirectiveIdent]
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
    changeRequest: SimpleChangeRequestJson,
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
      cr:           ChangeRequest,
      simpleCrJson: SimpleChangeRequestJson,
      isPending:    Boolean,
      wfLogs:       Seq[WorkflowStepChanged],
      crLogs:       Seq[ChangeRequestEventLog]
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
      simpleCrJson,
      isPending,
      eventLogs
    )
  }
}
