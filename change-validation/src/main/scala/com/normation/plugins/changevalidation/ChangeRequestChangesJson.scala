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
import com.normation.cfclerk.domain.SectionSpec
import com.normation.cfclerk.domain.Technique
import com.normation.cfclerk.domain.TechniqueName
import com.normation.errors.Accumulated
import com.normation.errors.Inconsistency
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
import com.normation.rudder.domain.eventlog.*
import com.normation.rudder.domain.nodes.AddNodeGroupDiff
import com.normation.rudder.domain.nodes.DeleteNodeGroupDiff
import com.normation.rudder.domain.nodes.ModifyToNodeGroupDiff
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies
import com.normation.rudder.domain.policies.*
import com.normation.rudder.domain.properties.AddGlobalParameterDiff
import com.normation.rudder.domain.properties.DeleteGlobalParameterDiff
import com.normation.rudder.domain.properties.ModifyToGlobalParameterDiff
import com.normation.rudder.domain.workflows.*
import com.normation.rudder.rule.category.RuleCategory
import com.normation.rudder.rule.category.RuleCategoryId
import com.normation.rudder.rule.category.RuleCategoryService
import com.normation.rudder.services.eventlog.EventLogDetailsService
import com.normation.rudder.services.modification.DiffService
import com.normation.utils.DateFormaterService
import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.partial.Result
import io.scalaland.chimney.syntax.*
import org.joda.time.DateTime
import scala.collection.MapView
import zio.Chunk
import zio.json.DeriveJsonEncoder
import zio.json.JsonEncoder
import zio.json.jsonDiscriminator
import zio.json.jsonField
import zio.json.jsonHint

/**
 * Simplified representation of a change request's details. Equivalent to the ChangeRequestJson format,
 * except for the changes field, which only includes the names of the modified resources.
 */
final case class SimpleChangeRequestJson(
    id:             ChangeRequestId,
    displayName:    String,
    status:         WorkflowNodeId,
    createdBy:      String,
    isMergeable:    Boolean,
    description:    String,
    changesSummary: ChangesSummaryJson
)

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
      cr.info.description,
      cr match {
        case c: ConfigurationChangeRequest => c.transformInto[ChangesSummaryJson]
        case _ => ChangesSummaryJson.empty
      }
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
      techniqueByDirective: Map[DirectiveId, Technique],
      diffService:          DiffService,
      nodeGroups:           MapView[NodeGroupId, String],
      directives:           MapView[DirectiveId, Directive],
      nodes:                MapView[NodeId, String],
      allTargets:           MapView[RuleTarget, FullRuleTargetInfo],
      ruleCategoryService:  RuleCategoryService,
      rootCategory:         RuleCategory
  ): PureResult[ChangeRequestChangesJson] = {
    cr match {
      case cr: ConfigurationChangeRequest =>
        cr.transformIntoPartial[ConfigurationChangesJson](ConfigurationChangesJson.transformer)
          .map(c => ChangeRequestChangesJson(Some(c)))
          .asEither
          .left
          .map(_.transformInto[RudderError])
      case _ =>
        Right(ChangeRequestChangesJson(None))
    }
  }
}

/**
 * Json format that contains the same information as ConfigurationChangeRequestJson, as well as additional
 * information that is needed by the Elm app that displays a given change request's list of changes, i.e. :
 * 
 *    - the parameter diff of a given directive change, in string format;
 *    - an (id, name) pair for each directive of a given rule change;
 *    - an (id, name) pair for each node target of a given rule change;
 *    - the category name diff of a given rule change;
 *    - an (id, name) pair for each node of a given group change.
 *    
 * This additional info is used in order to display the links in the "diff" tab and the "history" table of a given
 * change request's page.
 * 
 * @param directives List of directive changes
 * @param rules List of rule changes
 * @param groups List of group changes
 * @param parameters List of parameter changes ; does not contain additional information
 */
final case class ConfigurationChangesJson(
    directives: Chunk[ExtendedDirectiveChangeJson],
    rules:      Chunk[ExtendedRuleChangeJson],
    groups:     Chunk[ExtendedGroupChangeJson],
    parameters: Chunk[GlobalParameterChangeActionJson]
)

object ConfigurationChangesJson {

  implicit def transformer(implicit
      techniqueByDirective: Map[DirectiveId, Technique],
      diffService:          DiffService,
      nodeGroups:           MapView[NodeGroupId, String],
      directives:           MapView[DirectiveId, Directive],
      nodes:                MapView[NodeId, String],
      allTargets:           MapView[RuleTarget, FullRuleTargetInfo],
      ruleCategoryService:  RuleCategoryService,
      rootCategory:         RuleCategory
  ): PartialTransformer[ConfigurationChangeRequest, ConfigurationChangesJson] = {
    PartialTransformer
      .define[ConfigurationChangeRequest, ConfigurationChangesJson]
      .withFieldComputedPartial(
        _.directives,
        cr => {
          Result.traverse[Chunk[ExtendedDirectiveChangeJson], (DirectiveId, DirectiveChanges), ExtendedDirectiveChangeJson](
            cr.directives.iterator,
            {
              case (directiveId, changes) =>
                for {
                  technique <- Result.fromOptionOrString(
                                 techniqueByDirective.get(directiveId),
                                 s"Error while fetching technique for directive ${directiveId}"
                               )
                  directive  = directives(directiveId)
                  res       <- {
                    implicit val foundTechnique = technique
                    changes.changes
                      .transformIntoPartial[DirectiveChangeActionJson]
                      .map(json => ExtendedDirectiveChangeJson.toExtended(directive, json))
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
              case (ruleId, changes) =>
                changes.changes
                  .transformIntoPartial[RuleChangeActionJson]
                  .map(ExtendedRuleChangeJson.toExtended)
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
                    .map(ExtendedGroupChangeJson.toExtended)
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

@jsonDiscriminator("type") sealed trait ExtendedDirectiveChangeJson

final case class DirectiveChangeDefaultJson(
    action:     ActionChangeJson,
    change:     DirectiveChangeJson,
    parameters: String
) extends ExtendedDirectiveChangeJson

final case class DirectiveChangeDiffJson(
    action:     ActionChangeJson,
    change:     DirectiveChangeJson,
    parameters: SimpleDiffOrValueJson[String]
) extends ExtendedDirectiveChangeJson

object ExtendedDirectiveChangeJson {

  def toExtended(
      directive:           Directive,
      change:              DirectiveChangeActionJson
  )(implicit
      directiveTechniques: Map[DirectiveId, Technique],
      diffService:         DiffService
  ): ExtendedDirectiveChangeJson = {

    val xmlPretty = new scala.xml.PrettyPrinter(80, 2)

    def sectionXml(id: DirectiveId): String = {
      val sectionSpecOpt = directiveTechniques.get(id).map(_.rootSection)

      xmlPretty.format(
        SectionVal.toOptionnalXml(sectionSpecOpt.map(SectionVal.directiveValToSectionVal(_, directive.parameters)))
      )
    }

    change.change match {
      case c: DirectiveChangeJson.DirectiveCreateChangeJson =>
        DirectiveChangeDefaultJson(change.action, c, sectionXml(directive.id))
      case d: DirectiveChangeJson.DirectiveDeleteChangeJson =>
        DirectiveChangeDefaultJson(change.action, d, sectionXml(directive.id))
      case m: DirectiveChangeJson.DirectiveModifyChangeJson =>
        val section = m.change.modParameters match {
          case SimpleDiffJson(oldValue, newValue) =>
            SimpleDiffJson(xmlPretty.format(SectionVal.toXml(oldValue)), xmlPretty.format(SectionVal.toXml(newValue)))

          case SimpleValueJson(_) => SimpleValueJson(sectionXml(directive.id))

        }

        DirectiveChangeDiffJson(change.action, m, section)

    }

  }

  implicit val encoder: JsonEncoder[ExtendedDirectiveChangeJson] = DeriveJsonEncoder.gen[ExtendedDirectiveChangeJson]

}

@jsonDiscriminator("type") sealed trait ExtendedGroupChangeJson

final case class GroupChangeDefaultJson(
    action:   ActionChangeJson,
    change:   GroupChangeJson,
    nodeInfo: Chunk[NodeIdent]
) extends ExtendedGroupChangeJson

final case class GroupChangeDiffJson(
    action:   ActionChangeJson,
    change:   GroupChangeJson,
    nodeInfo: SimpleDiffOrValueJson[Chunk[NodeIdent]]
) extends ExtendedGroupChangeJson

object ExtendedGroupChangeJson {

  implicit def toExtended(g: GroupChangeActionJson)(implicit nodes: MapView[NodeId, String]): ExtendedGroupChangeJson = {
    implicit def nodeIdentTransformer: Transformer[NodeId, NodeIdent] = NodeIdent.transformer

    g.change match {
      case c: GroupCreateChangeJson =>
        GroupChangeDefaultJson(g.action, c, Chunk.from(c.group.serverList.map(_.transformInto[NodeIdent])))
      case d: GroupDeleteChangeJson =>
        GroupChangeDefaultJson(g.action, d, Chunk.from(d.group.serverList.map(_.transformInto[NodeIdent])))
      case m: GroupModifyChangeJson =>
        GroupChangeDiffJson(
          g.action,
          g.change,
          m.change.modNodeList.map(v => Chunk.from(v.map(_.transformInto[NodeIdent])))
        )
    }
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

  implicit def transformer(implicit nodes: MapView[NodeId, String]): Transformer[NodeId, NodeIdent] = {
    case (id: NodeId) => NodeIdent(id, nodes.getOrElse(id, "unknown"))
  }
}

@jsonDiscriminator("type") sealed trait ExtendedRuleChangeJson

final case class RuleChangeDefaultJson(
    action:         ActionChangeJson,
    change:         RuleChangeJson,
    ruleTargetInfo: Chunk[RuleTargetExtended],
    directiveInfo:  Chunk[DirectiveIdent],
    categoryName:   String
) extends ExtendedRuleChangeJson

final case class RuleChangeDiffJson(
    action:         ActionChangeJson,
    change:         RuleChangeJson,
    ruleTargetInfo: SimpleDiffOrValueJson[Chunk[RuleTargetExtended]],
    directiveInfo:  SimpleDiffOrValueJson[Chunk[DirectiveIdent]],
    categoryName:   SimpleDiffOrValueJson[RuleCategoryId]
) extends ExtendedRuleChangeJson

object ExtendedRuleChangeJson {

  def toExtended(r: RuleChangeActionJson)(implicit
      nodeGroups:          MapView[NodeGroupId, String],
      directives:          MapView[DirectiveId, Directive],
      allTargets:          MapView[RuleTarget, FullRuleTargetInfo],
      ruleCategoryService: RuleCategoryService,
      rootCategory:        RuleCategory
  ): ExtendedRuleChangeJson = {

    implicit val d: Transformer[DirectiveId, DirectiveIdent] = DirectiveIdent.transformer

    implicit def iterableToChunkTransformer[A, B](implicit transformer: Transformer[A, B]): Transformer[Iterable[A], Chunk[B]] = {
      case (a: Iterable[A]) => Chunk.fromIterable(a.map(transformer.transform))
    }

    r.change match {
      case c: RuleChangeJson.RuleCreateChangeJson =>
        RuleChangeDefaultJson(
          r.action,
          c,
          c.rule.targets
            .map(_.toRuleTarget)
            .transformInto[Chunk[RuleTargetExtended]](iterableToChunkTransformer),
          c.rule.directives
            .map(id => DirectiveId(DirectiveUid(id)))
            .transformInto[Chunk[DirectiveIdent]](iterableToChunkTransformer),
          ruleCategoryService.shortFqdn(rootCategory, RuleCategoryId(c.rule.categoryId)).getOrElse(c.rule.categoryId)
        )
      case d: RuleChangeJson.RuleDeleteChangeJson =>
        RuleChangeDefaultJson(
          r.action,
          d,
          d.rule.targets
            .map(_.toRuleTarget)
            .transformInto[Chunk[RuleTargetExtended]](iterableToChunkTransformer),
          d.rule.directives
            .map(id => DirectiveId(DirectiveUid(id)))
            .transformInto[Chunk[DirectiveIdent]](iterableToChunkTransformer),
          ruleCategoryService.shortFqdn(rootCategory, RuleCategoryId(d.rule.categoryId)).getOrElse(d.rule.categoryId)
        )
      case m: RuleChangeJson.RuleModifyChangeJson =>
        RuleChangeDiffJson(
          r.action,
          m,
          m.change.modTarget.map(_.transformInto[Chunk[RuleTargetExtended]](iterableToChunkTransformer)),
          m.change.modDirectiveIds.map(_.transformInto[Chunk[DirectiveIdent]](iterableToChunkTransformer)),
          m.change.modCategoryId
        )
    }
  }

  implicit val encoder:                  JsonEncoder[ExtendedRuleChangeJson]        = DeriveJsonEncoder.gen[ExtendedRuleChangeJson]
  implicit val targetEncoder:            JsonEncoder[RuleTargetExtended]            = DeriveJsonEncoder.gen[RuleTargetExtended]
  implicit val compositionTargetEncoder: JsonEncoder[CompositionRuleTargetExtended] =
    DeriveJsonEncoder.gen[CompositionRuleTargetExtended]
  implicit val simpleTargetEncoder:      JsonEncoder[SimpleRuleTargetExtended]      = DeriveJsonEncoder.gen[SimpleRuleTargetExtended]
  implicit val idEncoder:                JsonEncoder[NodeGroupUid]                  = JsonEncoder[String].contramap[NodeGroupUid](_.value)
  implicit val categoryEncoder:          JsonEncoder[RuleCategoryId]                = JsonEncoder[String].contramap[RuleCategoryId](_.value)

}

@jsonDiscriminator("type") sealed trait RuleTargetExtended {}
@jsonDiscriminator("type") sealed trait CompositionRuleTargetExtended extends RuleTargetExtended {}

@jsonDiscriminator("type") sealed trait SimpleRuleTargetExtended(id: String, name: String) extends RuleTargetExtended

object SimpleRuleTargetExtended {
  @jsonHint("groupId") case class GroupTarget(id: String, name: String)       extends SimpleRuleTargetExtended(id, name)
  @jsonHint("target") case class NonGroupRuleTarget(id: String, name: String) extends SimpleRuleTargetExtended(id, name)

  implicit val simpleTargetEncoder: JsonEncoder[SimpleRuleTargetExtended] = DeriveJsonEncoder.gen[SimpleRuleTargetExtended]
}

final case class ComposedTarget(
    @jsonField("include") includedTarget: CompositionRuleTargetExtended,
    @jsonField("exclude") excludedTarget: CompositionRuleTargetExtended
) extends RuleTargetExtended

final case class OrComposition(or: Chunk[RuleTargetExtended])   extends CompositionRuleTargetExtended
final case class AndComposition(and: Chunk[RuleTargetExtended]) extends CompositionRuleTargetExtended

object RuleTargetExtended {

  implicit private def composedTransformer(implicit
      nodeGroups: MapView[NodeGroupId, String],
      allTargets: MapView[RuleTarget, FullRuleTargetInfo]
  ): Transformer[TargetExclusion, ComposedTarget] = {
    Transformer.derive[TargetExclusion, ComposedTarget]
  }

  implicit private def compositionTransformer(implicit
      nodeGroups: MapView[NodeGroupId, String],
      allTargets: MapView[RuleTarget, FullRuleTargetInfo]
  ): Transformer[TargetComposition, CompositionRuleTargetExtended] = {
    Transformer
      .define[TargetComposition, CompositionRuleTargetExtended]
      .withSealedSubtypeHandled[TargetUnion](u => u.transformInto[OrComposition])
      .withSealedSubtypeHandled[TargetIntersection](i => i.transformInto[AndComposition])
      .buildTransformer
  }

  implicit private def orTransformer(implicit
      nodeGroups: MapView[NodeGroupId, String],
      allTargets: MapView[RuleTarget, FullRuleTargetInfo]
  ): Transformer[TargetUnion, OrComposition] = {
    Transformer
      .define[TargetUnion, OrComposition]
      .withFieldComputed(_.or, union => Chunk.from(union.targets.map(_.transformInto[RuleTargetExtended])))
      .buildTransformer
  }

  implicit private def andTransformer(implicit
      nodeGroups: MapView[NodeGroupId, String],
      allTargets: MapView[RuleTarget, FullRuleTargetInfo]
  ): Transformer[TargetIntersection, AndComposition] = {
    Transformer
      .define[TargetIntersection, AndComposition]
      .withFieldComputed(_.and, inter => Chunk.from(inter.targets.map(_.transformInto[RuleTargetExtended])))
      .buildTransformer
  }

  implicit private def simpleTransformer(implicit
      nodeGroups: MapView[NodeGroupId, String],
      allTargets: MapView[RuleTarget, FullRuleTargetInfo]
  ): Transformer[SimpleTarget, SimpleRuleTargetExtended] = {
    Transformer
      .define[SimpleTarget, SimpleRuleTargetExtended]
      .withSealedSubtypeHandled[GroupTarget] {
        case GroupTarget(groupId) =>
          SimpleRuleTargetExtended.GroupTarget(groupId.serialize, nodeGroups.getOrElse(groupId, "unknown"))
      }
      .withSealedSubtypeHandled[NonGroupRuleTarget](target =>
        SimpleRuleTargetExtended.NonGroupRuleTarget(target.target, allTargets.get(target).map(_.name).getOrElse("unknown"))
      )
      .buildTransformer
  }

  implicit def transformer(implicit
      nodeGroups: MapView[NodeGroupId, String],
      allTargets: MapView[RuleTarget, FullRuleTargetInfo]
  ): Transformer[RuleTarget, RuleTargetExtended] = {

    Transformer
      .define[RuleTarget, RuleTargetExtended]
      .withSealedSubtypeHandled[CompositeRuleTarget] {
        case e: TargetExclusion   => e.transformInto[ComposedTarget]
        case c: TargetComposition => c.transformInto[CompositionRuleTargetExtended]
      }
      .withSealedSubtypeHandled[SimpleTarget](simple => simple.transformInto[SimpleRuleTargetExtended])
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

  implicit def transformer(implicit directives: MapView[DirectiveId, Directive]): Transformer[DirectiveId, DirectiveIdent] = {
    Transformer
      .define[DirectiveId, DirectiveIdent]
      .withFieldRenamed(_.uid, _.id)
      .withFieldComputed(_.name, id => directives.get(id).map(_.name).getOrElse("unknown"))
      .buildTransformer
  }

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
 * as well as its full event log history and a summary of the node group, directive, rule, and parameter changes.
 */
final case class ChangeRequestMainDetailsJson(
    changeRequest: SimpleChangeRequestJson,
    isPending:     Boolean,
    eventLogs:     Chunk[EventLogJson],
    backStatus:    Option[WorkflowNodeId],
    nextStatus:    Option[WorkflowNodeId]
)

object ChangeRequestMainDetailsJson {
  implicit val encoder:               JsonEncoder[ChangeRequestMainDetailsJson] = DeriveJsonEncoder.gen[ChangeRequestMainDetailsJson]
  implicit val actorEncoder:          JsonEncoder[EventActor]                   = EventLogJson.actorEncoder
  implicit val dateTimeEncoder:       JsonEncoder[DateTime]                     = EventLogJson.dateTimeEncoder
  implicit val eventLogEncoder:       JsonEncoder[EventLogJson]                 = DeriveJsonEncoder.gen[EventLogJson]
  implicit val workflowNodeIdEncoder: JsonEncoder[WorkflowNodeId]               = JsonEncoder[String].contramap[WorkflowNodeId](_.value)

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
      crLogs:       Seq[ChangeRequestEventLog],
      backStatus:   Option[WorkflowNodeId],
      nextStatus:   Option[WorkflowNodeId]
  )(implicit eventLogDetailsService: EventLogDetailsService): ChangeRequestMainDetailsJson = {

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

    ChangeRequestMainDetailsJson(
      simpleCrJson,
      isPending,
      eventLogs,
      backStatus,
      nextStatus
    )
  }
}

final case class ChangesSummaryJson(
    directives: Chunk[String],
    rules:      Chunk[String],
    groups:     Chunk[String],
    parameters: Chunk[String]
)

object ChangesSummaryJson {

  implicit val encoder: JsonEncoder[ChangesSummaryJson] = DeriveJsonEncoder.gen[ChangesSummaryJson]

  implicit val transformer: Transformer[ConfigurationChangeRequest, ChangesSummaryJson] = {
    Transformer
      .define[ConfigurationChangeRequest, ChangesSummaryJson]
      .withFieldComputed(_.directives, cr => Chunk.from(cr.directives.map(_._2.changes.firstChange.diff.directive.name)))
      .withFieldComputed(_.rules, cr => Chunk.from(cr.rules.map(_._2.changes.firstChange.diff.rule.name)))
      .withFieldComputed(_.groups, cr => Chunk.from(cr.nodeGroups.map(_._2.changes.firstChange.diff.group.name)))
      .withFieldComputed(_.parameters, cr => Chunk.from(cr.globalParams.map(_._2.changes.firstChange.diff.parameter.name)))
      .buildTransformer
  }

  def empty: ChangesSummaryJson = {
    ChangesSummaryJson(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
  }
}
