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
import com.normation.cfclerk.domain.SectionSpec
import com.normation.cfclerk.domain.Technique
import com.normation.cfclerk.domain.TechniqueName
import com.normation.cfclerk.domain.TechniqueVersion
import com.normation.errors.*
import com.normation.inventory.domain.NodeId
import com.normation.rudder.apidata.JsonResponseObjects.*
import com.normation.rudder.apidata.implicits.*
import com.normation.rudder.domain.nodes.AddNodeGroupDiff
import com.normation.rudder.domain.nodes.ChangeRequestNodeGroupDiff
import com.normation.rudder.domain.nodes.DeleteNodeGroupDiff
import com.normation.rudder.domain.nodes.ModifyNodeGroupDiff
import com.normation.rudder.domain.nodes.ModifyToNodeGroupDiff
import com.normation.rudder.domain.nodes.NodeGroup
import com.normation.rudder.domain.nodes.NodeGroupCategoryId
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.AddDirectiveDiff
import com.normation.rudder.domain.policies.AddRuleDiff
import com.normation.rudder.domain.policies.ChangeRequestDirectiveDiff
import com.normation.rudder.domain.policies.ChangeRequestRuleDiff
import com.normation.rudder.domain.policies.DeleteDirectiveDiff
import com.normation.rudder.domain.policies.DeleteRuleDiff
import com.normation.rudder.domain.policies.Directive
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.domain.policies.ModifyDirectiveDiff
import com.normation.rudder.domain.policies.ModifyRuleDiff
import com.normation.rudder.domain.policies.ModifyToDirectiveDiff
import com.normation.rudder.domain.policies.ModifyToRuleDiff
import com.normation.rudder.domain.policies.Rule
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.domain.policies.SectionVal
import com.normation.rudder.domain.policies.SimpleDiff
import com.normation.rudder.domain.properties.AddGlobalParameterDiff
import com.normation.rudder.domain.properties.ChangeRequestGlobalParameterDiff
import com.normation.rudder.domain.properties.DeleteGlobalParameterDiff
import com.normation.rudder.domain.properties.GenericProperty
import com.normation.rudder.domain.properties.GlobalParameter
import com.normation.rudder.domain.properties.GroupProperty
import com.normation.rudder.domain.properties.InheritMode
import com.normation.rudder.domain.properties.ModifyGlobalParameterDiff
import com.normation.rudder.domain.properties.ModifyToGlobalParameterDiff
import com.normation.rudder.domain.properties.PropertyProvider
import com.normation.rudder.domain.queries.Query
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ChangeRequestInfo
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.DirectiveChange
import com.normation.rudder.domain.workflows.DirectiveChanges
import com.normation.rudder.domain.workflows.GlobalParameterChange
import com.normation.rudder.domain.workflows.GlobalParameterChanges
import com.normation.rudder.domain.workflows.NodeGroupChange
import com.normation.rudder.domain.workflows.NodeGroupChanges
import com.normation.rudder.domain.workflows.RuleChange
import com.normation.rudder.domain.workflows.RuleChanges
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.services.modification.DiffService
import com.typesafe.config.ConfigValue
import io.scalaland.chimney.PartialTransformer
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.partial.Result
import io.scalaland.chimney.syntax.*
import scala.util.Try
import zio.Chunk
import zio.NonEmptyChunk
import zio.json.*
import zio.json.internal.Write

sealed trait SimpleDiffOrValueJson[T] {
  def map[U](f: T => U): SimpleDiffOrValueJson[U]
}
final case class SimpleDiffJson[T](
    @jsonField("from") oldValue: T,
    @jsonField("to") newValue:   T
) extends SimpleDiffOrValueJson[T] {
  def map[U](f: T => U): SimpleDiffJson[U] = SimpleDiffJson(f(oldValue), f(newValue))
}
final case class SimpleValueJson[T](value: T) extends SimpleDiffOrValueJson[T] {
  def map[U](f: T => U): SimpleValueJson[U] = SimpleValueJson(f(value))
}

object SimpleDiffOrValueJson {
  implicit def diffTransformer[T]:                 Transformer[SimpleDiff[T], SimpleDiffJson[T]] =
    Transformer.derive[SimpleDiff[T], SimpleDiffJson[T]]
  implicit def simpleValueEncoder[T: JsonEncoder]: JsonEncoder[SimpleValueJson[T]]               =
    JsonEncoder[T].contramap[SimpleValueJson[T]](_.value)
  implicit def simpleDiffEncoder[T: JsonEncoder]: JsonEncoder[SimpleDiffJson[T]] = DeriveJsonEncoder.gen[SimpleDiffJson[T]]

  // An encoder for both {from, to} json object and raw value json. This is a way to encode both different types of json
  implicit def encoder[T: JsonEncoder]: JsonEncoder[SimpleDiffOrValueJson[T]] = new JsonEncoder[SimpleDiffOrValueJson[T]] {
    override def unsafeEncode(a: SimpleDiffOrValueJson[T], indent: Option[Int], out: Write): Unit = {
      a match {
        case SimpleDiffJson(from, to) => simpleDiffEncoder[T].unsafeEncode(SimpleDiffJson(from, to), indent, out)
        case SimpleValueJson(value)   => simpleValueEncoder[T].unsafeEncode(SimpleValueJson(value), indent, out)
      }
    }
  }

  def withDefault[T](diff: Option[SimpleDiff[T]], defaultValue: T): SimpleDiffOrValueJson[T] = {
    diff match {
      case Some(SimpleDiff(from, to)) => SimpleDiffJson(from, to)
      case None                       => SimpleValueJson(defaultValue)
    }
  }
}

final case class ChangeRequestJson(
    id:                                 ChangeRequestId,
    displayName:                        String,
    status:                             WorkflowNodeId,
    @jsonField("created by") createdBy: String,
    acceptable:                         Boolean,
    description:                        String,
    changes:                            Option[ConfigurationChangeRequestJson]
)

object ChangeRequestJson {
  implicit val idEncoder:     JsonEncoder[ChangeRequestId]   = JsonEncoder[Int].contramap[ChangeRequestId](_.value)
  implicit val statusEncoder: JsonEncoder[WorkflowNodeId]    = JsonEncoder[String].contramap[WorkflowNodeId](_.value)
  implicit val encoder:       JsonEncoder[ChangeRequestJson] = DeriveJsonEncoder.gen[ChangeRequestJson]

  implicit def transformErrorsToRudderError: Transformer[Result.Errors, RudderError] = {
    case Result.Errors(errors) =>
      Accumulated(
        NonEmptyList
          .fromListUnsafe(errors.toList) // This is safe because errors is non-empty
          .map(e => Inconsistency(s"Error while serializing change request at ${e.path.asString} : ${e.message.asString}"))
      )
  }

  // Entrypoint to convert the whole tree of ChangeRequest + some context to a serializable object ChangeRequestJson
  def from(cr: ChangeRequest, status: WorkflowNodeId, isAcceptable: Boolean)(implicit
      techniqueByDirective: Map[DirectiveId, Technique],
      diffService:          DiffService
  ): PureResult[ChangeRequestJson] = {
    val changesJson: PureResult[Option[ConfigurationChangeRequestJson]] = cr match {
      case cr: ConfigurationChangeRequest => {
        cr.transformIntoPartial[ConfigurationChangeRequestJson].map(Some(_)).asEither.left.map(_.transformInto[RudderError])
      }
      case _ => Right(None)
    }

    changesJson.map(
      ChangeRequestJson(
        cr.id,
        cr.info.name,
        status,
        cr.owner,
        isAcceptable,
        cr.info.description,
        _
      )
    )
  }
}

final case class ChangeRequestInfoJson(
    name:        Option[String],
    description: Option[String]
) {

  def updateCrInfo(crInfo: ChangeRequestInfo): ChangeRequestInfo = {
    crInfo.copy(
      name = name.getOrElse(crInfo.name),
      description = description.getOrElse(crInfo.description)
    )
  }
}

object ChangeRequestInfoJson {
  implicit val decoder: JsonDecoder[ChangeRequestInfoJson] = DeriveJsonDecoder.gen[ChangeRequestInfoJson]
}

/**
 * Class that represents the number of change requests that are currently in a "pending" status, i.e.
 * "Pending validation" and "Pending deployment" respectively.
 * Both fields are optional : either field will be present if and only if the user who made the request has
 * the required authorization type, i.e. Validator.Read, and Deployer.Read authorizations respectively.
 *
 * @param pendingValidation the current number of change requests that have the "Pending validation" status
 * @param pendingDeployment the current number of change requests that have the "Pending deployment" status
 */
final case class PendingCountJson(
    pendingValidation: Option[Long],
    pendingDeployment: Option[Long]
)

object PendingCountJson {
  implicit val encoder: JsonEncoder[PendingCountJson] = DeriveJsonEncoder.gen[PendingCountJson]

  def from(map: Map[WorkflowNodeId, Long]): PendingCountJson = {
    PendingCountJson(
      map.get(TwoValidationStepsWorkflowServiceImpl.Validation.id),
      map.get(TwoValidationStepsWorkflowServiceImpl.Deployment.id)
    )
  }
}

@jsonDiscriminator("action") sealed trait ActionChangeJson {
  def name: String = this match {
    case ActionChangeJson.create => "create"
    case ActionChangeJson.modify => "modify"
    case ActionChangeJson.delete => "delete"
  }
}
object ActionChangeJson                                    {
  case object create extends ActionChangeJson
  case object modify extends ActionChangeJson
  case object delete extends ActionChangeJson

  implicit val encoder: JsonEncoder[ActionChangeJson] = JsonEncoder[String].contramap[ActionChangeJson](_.name)
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// DIRECTIVES
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// The discriminator is not needed for serialization so we leave hints as case class names
@jsonDiscriminator("type") sealed trait DirectiveChangeJson
object DirectiveChangeJson {

  final case class DirectiveCreateChangeJson(
      change: JRDirective
  ) extends DirectiveChangeJson

  final case class DirectiveDeleteChangeJson(
      change: JRDirective
  ) extends DirectiveChangeJson

  final case class DirectiveModifyChangeJson(
      change: ModifyDirectiveJson
  ) extends DirectiveChangeJson

  final case class SectionValJson(
      section: SectionValContentJson
  )
  final case class SectionValContentJson(
      name:     String = SectionVal.ROOT_SECTION_NAME,
      vars:     Option[NonEmptyChunk[SectionVarJson]],
      sections: Option[NonEmptyChunk[SectionValContentJson]]
  )
  object SectionValContentJson {
    implicit val sectionNameValueEncoder: JsonEncoder[SectionNameValueJson]  = DeriveJsonEncoder.gen[SectionNameValueJson]
    implicit val sectionVarEncoder:       JsonEncoder[SectionVarJson]        = DeriveJsonEncoder.gen[SectionVarJson]
    implicit lazy val encoder:            JsonEncoder[SectionValContentJson] = DeriveJsonEncoder.gen[SectionValContentJson]
  }
  final case class SectionVarJson(
      `var`: SectionNameValueJson
  )
  final case class SectionNameValueJson(
      name:  String,
      value: String
  )

  object SectionValJson {
    implicit val transformer: Transformer[SectionVal, SectionValJson] = sv => {
      def serializeSectionVal(sv: SectionVal, sectionName: String = SectionVal.ROOT_SECTION_NAME): SectionValContentJson = {
        val variables = {
          sv.variables.toSeq.sortBy(_._1).map {
            case (variable, value) =>
              SectionVarJson(SectionNameValueJson(variable, value))
          }
        }
        val sections  = {
          sv.sections.toSeq.sortBy(_._1).flatMap {
            case (sectionName, sectionIterations) =>
              sectionIterations.map(sectionValue => serializeSectionVal(sectionValue, sectionName))
          }
        }
        SectionValContentJson(
          sectionName,
          NonEmptyChunk.fromIterableOption(variables),
          NonEmptyChunk.fromIterableOption(sections)
        )
      }
      SectionValJson(serializeSectionVal(sv))
    }

    implicit val encoder: JsonEncoder[SectionValJson] = DeriveJsonEncoder.gen[SectionValJson]
  }

  final case class ModifyDirectiveJson(
      id:                                                 DirectiveId,
      @jsonField("displayName") modName:                  SimpleDiffOrValueJson[String],
      @jsonField("shortDescription") modShortDescription: SimpleDiffOrValueJson[String],
      @jsonField("longDescription") modLongDescription:   SimpleDiffOrValueJson[String],
      techniqueName:                                      TechniqueName,
      @jsonField("techniqueVersion") modTechniqueVersion: SimpleDiffOrValueJson[TechniqueVersion],
      @jsonField("parameters") modParameters:             SimpleDiffOrValueJson[SectionValJson],
      @jsonField("priority") modPriority:                 SimpleDiffOrValueJson[Int],
      @jsonField("enabled") modIsActivated:               SimpleDiffOrValueJson[Boolean],
      system:                                             Boolean
  )

  object ModifyDirectiveJson {
    // encoders in this nested scope need to be lazy
    implicit lazy val directiveIdEncoder:      JsonEncoder[DirectiveId]         = JsonEncoder[String].contramap[DirectiveId](_.serialize)
    implicit lazy val techniqueNameEncoder:    JsonEncoder[TechniqueName]       = JsonEncoder[String].contramap[TechniqueName](_.value)
    implicit lazy val techniqueVersionEncoder: JsonEncoder[TechniqueVersion]    =
      JsonEncoder[String].contramap[TechniqueVersion](_.serialize)
    implicit lazy val encoder:                 JsonEncoder[ModifyDirectiveJson] =
      DeriveJsonEncoder.gen[ModifyDirectiveJson]

    def from(
        diff:               ModifyDirectiveDiff,
        initialState:       Directive,
        technique:          Technique,
        initialRootSection: SectionSpec
    ): Either[String, ModifyDirectiveJson] = {
      import io.scalaland.chimney.dsl.*
      // This is in a try/catch because directiveValToSectionVal may fail
      Try(
        ModifyDirectiveJson(
          initialState.id,
          SimpleDiffOrValueJson.withDefault(diff.modName, initialState.name),
          SimpleDiffOrValueJson.withDefault(diff.modShortDescription, initialState.shortDescription),
          SimpleDiffOrValueJson.withDefault(diff.modLongDescription, initialState.longDescription),
          technique.id.name,
          SimpleDiffOrValueJson.withDefault(diff.modTechniqueVersion, initialState.techniqueVersion),
          SimpleDiffOrValueJson
            .withDefault(
              diff.modParameters.map(_.transformInto[SimpleDiff[SectionValJson]]),
              SectionVal
                .directiveValToSectionVal(initialRootSection, initialState.parameters)
                .transformInto[SectionValJson]
            ),
          SimpleDiffOrValueJson.withDefault(diff.modPriority, initialState.priority),
          SimpleDiffOrValueJson.withDefault(diff.modIsActivated, initialState.isEnabled),
          initialState.isSystem
        )
      ).toEither.left.map(e => s"Error in directive sections : ${e.getMessage}")
    }
  }

  implicit def createTransformer(implicit
      technique: Technique
  ): Transformer[AddDirectiveDiff, DirectiveCreateChangeJson] = {
    case AddDirectiveDiff(techniqueName, directive) =>
      DirectiveCreateChangeJson(JRDirective.fromDirective(technique, directive, None))
  }

  implicit def deleteTransformer(implicit
      technique: Technique
  ): Transformer[DeleteDirectiveDiff, DirectiveDeleteChangeJson] = {
    case DeleteDirectiveDiff(_, directive) =>
      DirectiveDeleteChangeJson(JRDirective.fromDirective(technique, directive, None))
  }

  // Technique is needed to put default values when there is no diff in any given directive field
  // This return an error result when no technique is passed
  implicit def modifyTransformer(implicit
      change:      DirectiveChange,
      technique:   Technique,
      diffService: DiffService
  ): PartialTransformer[ModifyToDirectiveDiff, DirectiveModifyChangeJson] = {
    case (ModifyToDirectiveDiff(_, directive, rootSection), _) =>
      val result = change.initialState match {
        case Some((techniqueName, initialState, section @ Some(initialRootSection))) =>
          val diff = diffService.diffDirective(initialState, section, directive, rootSection, techniqueName)

          ModifyDirectiveJson
            .from(diff, initialState, technique, initialRootSection)
            .map(DirectiveModifyChangeJson(_))

        case _ => Left(s"Error while fetching initial state of change request.")
      }

      Result.fromEitherString(result)
  }

  implicit def transformDirectiveDiff(implicit
      change:      DirectiveChange,
      technique:   Technique,
      diffService: DiffService
  ): PartialTransformer[ChangeRequestDirectiveDiff, DirectiveChangeJson] = {
    PartialTransformer
      .define[ChangeRequestDirectiveDiff, DirectiveChangeJson]
      .withSealedSubtypeHandled[AddDirectiveDiff](_.transformInto[DirectiveCreateChangeJson])
      .withSealedSubtypeHandled[DeleteDirectiveDiff](_.transformInto[DirectiveDeleteChangeJson])
      .withSealedSubtypeHandledPartial[ModifyToDirectiveDiff](_.transformIntoPartial[DirectiveModifyChangeJson])
      .buildTransformer
  }

  // We need to remove the wrapping when serializing to directly serialize the directive or directive diff
  implicit val createEncoder: JsonEncoder[DirectiveCreateChangeJson] =
    JsonEncoder[JRDirective].contramap[DirectiveCreateChangeJson](_.change)
  implicit val deleteEncoder: JsonEncoder[DirectiveDeleteChangeJson] =
    JsonEncoder[JRDirective].contramap[DirectiveDeleteChangeJson](_.change)
  implicit val modifyEncoder: JsonEncoder[DirectiveModifyChangeJson] =
    JsonEncoder[ModifyDirectiveJson].contramap[DirectiveModifyChangeJson](_.change)
  implicit val encoder:       JsonEncoder[DirectiveChangeJson]       = DeriveJsonEncoder.gen[DirectiveChangeJson]

}

final case class DirectiveChangeActionJson(
    action: ActionChangeJson,
    change: DirectiveChangeJson
)
object DirectiveChangeActionJson {
  import DirectiveChangeJson.*

  implicit def transformer(implicit
      technique:   Technique,
      diffService: DiffService
  ): PartialTransformer[DirectiveChange, DirectiveChangeActionJson] = {
    case (source, _) =>
      implicit val change = source
      source.change
        .map(_.diff)
        .map(_.transformIntoPartial[DirectiveChangeJson].map {
          case d: DirectiveCreateChangeJson => DirectiveChangeActionJson(ActionChangeJson.create, d)
          case d: DirectiveDeleteChangeJson => DirectiveChangeActionJson(ActionChangeJson.delete, d)
          case d: DirectiveModifyChangeJson => DirectiveChangeActionJson(ActionChangeJson.modify, d)
        }) match {
        case Left(err)    =>
          Result.fromErrorString(
            s"Error while serializing directives from CR ${change.firstChange.diff.directive.id.serialize}: ${err.msg}"
          )
        case Right(value) => value
      }
  }

  implicit val encoder: JsonEncoder[DirectiveChangeActionJson] = DeriveJsonEncoder.gen[DirectiveChangeActionJson]
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// RULES
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@jsonDiscriminator("type") sealed trait RuleChangeJson
object RuleChangeJson {

  final case class RuleCreateChangeJson(
      rule: JRRule
  ) extends RuleChangeJson

  final case class RuleDeleteChangeJson(
      rule: JRRule
  ) extends RuleChangeJson

  final case class RuleModifyChangeJson(
      change: ModifyRuleJson
  ) extends RuleChangeJson

  final case class ModifyRuleJson(
      id:                                                 RuleId,
      @jsonField("displayName") modName:                  SimpleDiffOrValueJson[String],
      @jsonField("shortDescription") modShortDescription: SimpleDiffOrValueJson[String],
      @jsonField("longDescription") modLongDescription:   SimpleDiffOrValueJson[String],
      @jsonField("directives") modDirectiveIds:           SimpleDiffOrValueJson[Set[DirectiveId]],
      @jsonField("targets") modTarget:                    SimpleDiffOrValueJson[Set[RuleTarget]],
      @jsonField("enabled") modIsActivatedStatus:         SimpleDiffOrValueJson[Boolean]
  )

  object ModifyRuleJson {
    implicit lazy val ruleIdEncoder:      JsonEncoder[RuleId]         = JsonEncoder[String].contramap[RuleId](_.serialize)
    implicit lazy val directiveIdEncoder: JsonEncoder[DirectiveId]    = JsonEncoder[String].contramap[DirectiveId](_.serialize)
    implicit lazy val ruleTargetEncoder:  JsonEncoder[RuleTarget]     = JsonEncoder[String].contramap[RuleTarget](_.target)
    implicit lazy val encoder:            JsonEncoder[ModifyRuleJson] =
      DeriveJsonEncoder.gen[ModifyRuleJson]

    def from(
        modifyRuleDiff: ModifyRuleDiff,
        initialState:   Rule
    ): ModifyRuleJson = {
      ModifyRuleJson(
        modifyRuleDiff.id,
        SimpleDiffOrValueJson.withDefault(modifyRuleDiff.modName, initialState.name),
        SimpleDiffOrValueJson.withDefault(modifyRuleDiff.modShortDescription, initialState.shortDescription),
        SimpleDiffOrValueJson.withDefault(modifyRuleDiff.modLongDescription, initialState.longDescription),
        SimpleDiffOrValueJson.withDefault(modifyRuleDiff.modDirectiveIds, initialState.directiveIds),
        SimpleDiffOrValueJson.withDefault(modifyRuleDiff.modTarget, initialState.targets),
        SimpleDiffOrValueJson.withDefault(modifyRuleDiff.modIsActivatedStatus, initialState.isEnabledStatus)
      )
    }
  }
  implicit val jrRuleTransformer: Transformer[Rule, JRRule] = Transformer
    .define[Rule, JRRule]
    .enableBeanGetters
    .withFieldComputed(_.id, _.id.serialize)
    .withFieldRenamed(_.name, _.displayName)
    .withFieldComputed(_.categoryId, _.categoryId.value)
    .withFieldComputed(_.directives, _.directiveIds.map(_.serialize).toList.sorted)
    .withFieldComputed(_.targets, _.targets.toList.sortBy(_.target).map(t => JRRuleTarget(t)))
    .withFieldRenamed(_.isEnabledStatus, _.enabled)
    .withFieldComputed(_.tags, x => JRTags.fromTags(x.tags))
    .withFieldConst(_.policyMode, None)      // not needed
    .withFieldConst(_.status, None)          // not needed
    .withFieldConst(_.changeRequestId, None) // not needed
    .buildTransformer
  implicit val createTransformer: Transformer[AddRuleDiff, RuleCreateChangeJson]    =
    Transformer.derive[AddRuleDiff, RuleCreateChangeJson]
  implicit val deleteTransformer: Transformer[DeleteRuleDiff, RuleDeleteChangeJson] =
    Transformer.derive[DeleteRuleDiff, RuleDeleteChangeJson]
  implicit def modifyTransformer(implicit
      change:      RuleChange,
      diffService: DiffService
  ): PartialTransformer[ModifyToRuleDiff, RuleModifyChangeJson] = {
    case (ModifyToRuleDiff(rule), _) =>
      val result = change.initialState match {
        case Some(init) =>
          val diff = diffService.diffRule(init, rule)
          Right(RuleModifyChangeJson(ModifyRuleJson.from(diff, init)))
        case _          => Left(s"Error while fetching initial state of change request.")
      }
      Result.fromEitherString(result)
  }

  implicit def transformer(implicit
      change:      RuleChange,
      diffService: DiffService
  ): PartialTransformer[ChangeRequestRuleDiff, RuleChangeJson] = {
    PartialTransformer
      .define[ChangeRequestRuleDiff, RuleChangeJson]
      .withSealedSubtypeHandled[AddRuleDiff](_.transformInto[RuleCreateChangeJson])
      .withSealedSubtypeHandled[DeleteRuleDiff](_.transformInto[RuleDeleteChangeJson])
      .withSealedSubtypeHandledPartial[ModifyToRuleDiff](_.transformIntoPartial[RuleModifyChangeJson])
      .buildTransformer
  }

  implicit val createEncoder: JsonEncoder[RuleCreateChangeJson] = JsonEncoder[JRRule].contramap[RuleCreateChangeJson](_.rule)
  implicit val deleteEncoder: JsonEncoder[RuleDeleteChangeJson] = JsonEncoder[JRRule].contramap[RuleDeleteChangeJson](_.rule)
  implicit val modifyEncoder: JsonEncoder[RuleModifyChangeJson] =
    JsonEncoder[ModifyRuleJson].contramap[RuleModifyChangeJson](_.change)
  implicit val encoder:       JsonEncoder[RuleChangeJson]       = DeriveJsonEncoder.gen[RuleChangeJson]
}

final case class RuleChangeActionJson(
    action: ActionChangeJson,
    change: RuleChangeJson
)
object RuleChangeActionJson {
  import RuleChangeJson.*

  implicit def transformer(implicit
      diffService: DiffService
  ): PartialTransformer[RuleChange, RuleChangeActionJson] = {
    case (source, _) =>
      implicit val change = source
      source.change
        .map(_.diff)
        .map(_.transformIntoPartial[RuleChangeJson].map {
          case d: RuleCreateChangeJson => RuleChangeActionJson(ActionChangeJson.create, d)
          case d: RuleDeleteChangeJson => RuleChangeActionJson(ActionChangeJson.delete, d)
          case d: RuleModifyChangeJson => RuleChangeActionJson(ActionChangeJson.modify, d)
        }) match {
        case Left(err)    =>
          Result.fromErrorString(
            s"Error while serializing rules from CR ${change.firstChange.diff.rule.id.serialize}: ${err.msg}"
          )
        case Right(value) => value
      }
  }

  implicit val encoder: JsonEncoder[RuleChangeActionJson] = DeriveJsonEncoder.gen[RuleChangeActionJson]
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// GROUPS
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@jsonDiscriminator("type") sealed trait GroupChangeJson
object GroupChangeJson {

  // Group json representation for creating or deleting a group
  final case class GroupJson(
      id:                               NodeGroupId,
      @jsonField("displayName") name:   String,
      description:                      String,
      query:                            Option[JRQuery],
      @jsonField("nodeIds") serverList: Set[NodeId],
      dynamic:                          Boolean,
      enabled:                          Boolean,
      groupClass:                       List[String],
      properties:                       PropertiesJson,
      target:                           GroupTarget,
      @jsonField("system") isSystem:    Boolean
  )

  object GroupJson {
    implicit val serverListEncoder: JsonEncoder[Set[NodeId]] =
      JsonEncoder[List[String]].contramap[Set[NodeId]](_.toList.map(_.value).sorted)
    implicit val targetEncoder:     JsonEncoder[GroupTarget] = JsonEncoder[String].contramap[GroupTarget](_.target)
    implicit val encoder:           JsonEncoder[GroupJson]   = DeriveJsonEncoder.gen[GroupJson]
  }

  final case class PropertiesJson( // we sort the properties by name on serialization
      value: List[JRProperty]
  ) extends AnyVal

  object PropertiesJson      {
    implicit val propertyTransformer: Transformer[GroupProperty, JRProperty]           = JRProperty.fromGroupProp _
    implicit val transformer:         Transformer[List[GroupProperty], PropertiesJson] =
      Transformer.derive[List[GroupProperty], PropertiesJson]

    implicit val encoder: JsonEncoder[PropertiesJson] =
      JsonEncoder[List[JRProperty]].contramap[PropertiesJson](_.value.sortBy(_.name))
  }

  final case class GroupCreateChangeJson(
      group: GroupJson
  ) extends GroupChangeJson

  final case class GroupDeleteChangeJson(
      group: GroupJson
  ) extends GroupChangeJson

  final case class GroupModifyChangeJson(
      change: ModifyGroupJson
  ) extends GroupChangeJson

  final case class GroupPropertyJson(
      name:        String,
      value:       ConfigValue,
      provider:    Option[PropertyProvider],
      inheritMode: Option[InheritMode]
  )
  final case class GroupPropertiesJson(
      value: List[GroupPropertyJson]
  ) extends AnyVal {
    def sorted: GroupPropertiesJson = GroupPropertiesJson(value.sortBy(_.name))
  }
  object GroupPropertyJson   {
    implicit val transformer: Transformer[GroupProperty, GroupPropertyJson] = property => {
      GroupPropertyJson( // chimney does not like this kind of inheritance
        property.name,
        property.value,
        property.provider,
        property.inheritMode
      )
    }

    implicit val propertyProviderEncoder: JsonEncoder[PropertyProvider]  = JsonEncoder[String].contramap[PropertyProvider](_.value)
    implicit val inheritModeEncoder:      JsonEncoder[InheritMode]       = JsonEncoder[String].contramap[InheritMode](_.value)
    implicit val encoder:                 JsonEncoder[GroupPropertyJson] = DeriveJsonEncoder.gen[GroupPropertyJson]
  }
  object GroupPropertiesJson {
    // We avoid using automatic derivation because we want to sort the properties by name so we define explicitly the transformers required elsewhere
    implicit val transformer:               Transformer[List[GroupProperty], GroupPropertiesJson]                         =
      Transformer.derive[List[GroupProperty], GroupPropertiesJson]
    implicit val simpleDiffListTransformer: Transformer[SimpleDiff[List[GroupProperty]], SimpleDiff[GroupPropertiesJson]] =
      Transformer.derive[SimpleDiff[List[GroupProperty]], SimpleDiff[GroupPropertiesJson]]

    implicit val encoder: JsonEncoder[GroupPropertiesJson] = {
      JsonEncoder[List[GroupPropertyJson]].contramap(g => g.value.sortBy(_.name))
    }
  }
  final case class ModifyGroupJson(
      id:                                       NodeGroupId,
      @jsonField("displayName") modName:        SimpleDiffOrValueJson[String],
      @jsonField("description") modDescription: SimpleDiffOrValueJson[String],
      @jsonField("category") modCategory:       Option[SimpleDiffJson[NodeGroupCategoryId]], // category is optional
      @jsonField("query") modQuery:             SimpleDiffOrValueJson[Option[Query]],
      @jsonField("properties") modProperties:   SimpleDiffOrValueJson[GroupPropertiesJson],
      @jsonField("nodeIds") modNodeList:        SimpleDiffOrValueJson[Set[NodeId]],
      @jsonField("dynamic") modIsDynamic:       SimpleDiffOrValueJson[Boolean],
      @jsonField("enabled") modIsActivated:     SimpleDiffOrValueJson[Boolean]
  )

  object ModifyGroupJson {
    implicit lazy val nodeIdEncoder: JsonEncoder[NodeId]          = JsonEncoder[String].contramap[NodeId](_.value)
    implicit lazy val queryEncoder:  JsonEncoder[Query]           = JsonEncoder[String].contramap[Query](_.toString)
    implicit lazy val encoder:       JsonEncoder[ModifyGroupJson] =
      DeriveJsonEncoder.gen[ModifyGroupJson]

    def from(
        modifyGroupDiff: ModifyNodeGroupDiff,
        initialState:    NodeGroup
    ): ModifyGroupJson = {
      ModifyGroupJson(
        modifyGroupDiff.id,
        SimpleDiffOrValueJson.withDefault(modifyGroupDiff.modName, initialState.name),
        SimpleDiffOrValueJson.withDefault(modifyGroupDiff.modDescription, initialState.description),
        modifyGroupDiff.modCategory.map(_.transformInto[SimpleDiffJson[NodeGroupCategoryId]]),
        SimpleDiffOrValueJson.withDefault(modifyGroupDiff.modQuery, initialState.query),
        SimpleDiffOrValueJson.withDefault(
          modifyGroupDiff.modProperties.map(_.transformInto[SimpleDiff[GroupPropertiesJson]]),
          initialState.properties.transformInto[GroupPropertiesJson]
        ),
        SimpleDiffOrValueJson.withDefault(modifyGroupDiff.modNodeList, initialState.serverList),
        SimpleDiffOrValueJson.withDefault(modifyGroupDiff.modIsDynamic, initialState.isDynamic),
        SimpleDiffOrValueJson.withDefault(modifyGroupDiff.modIsActivated, initialState.isEnabled)
      )
    }
  }

  implicit val jrGroupTransformer: Transformer[NodeGroup, GroupJson]                       = Transformer
    .define[NodeGroup, GroupJson]
    .enableBeanGetters
    .withFieldComputed(_.query, _.query.map(JRQuery.fromQuery(_)))
    .withFieldComputed(_.groupClass, x => List(x.id.serialize, x.name).map(RuleTarget.toCFEngineClassName _).sorted)
    .withFieldComputed(_.target, x => GroupTarget(x.id))
    .buildTransformer
  implicit val createTransformer:  Transformer[AddNodeGroupDiff, GroupCreateChangeJson]    =
    Transformer.derive[AddNodeGroupDiff, GroupCreateChangeJson]
  implicit val deleteTransformer:  Transformer[DeleteNodeGroupDiff, GroupDeleteChangeJson] =
    Transformer.derive[DeleteNodeGroupDiff, GroupDeleteChangeJson]
  implicit def modifyTransformer(implicit
      change:      NodeGroupChange,
      diffService: DiffService
  ): PartialTransformer[ModifyToNodeGroupDiff, GroupModifyChangeJson] = {
    case (ModifyToNodeGroupDiff(group), _) =>
      val result = change.initialState match {
        case Some(init) =>
          val diff = diffService.diffNodeGroup(init, group)
          Right(GroupModifyChangeJson(ModifyGroupJson.from(diff, init)))
        case _          => Left(s"Error while fetching initial state of change request.")
      }
      Result.fromEitherString(result)
  }

  implicit def transformer(implicit
      change:      NodeGroupChange,
      diffService: DiffService
  ): PartialTransformer[ChangeRequestNodeGroupDiff, GroupChangeJson] = {
    PartialTransformer
      .define[ChangeRequestNodeGroupDiff, GroupChangeJson]
      .withSealedSubtypeHandled[AddNodeGroupDiff](_.transformInto[GroupCreateChangeJson])
      .withSealedSubtypeHandled[DeleteNodeGroupDiff](_.transformInto[GroupDeleteChangeJson])
      .withSealedSubtypeHandledPartial[ModifyToNodeGroupDiff](_.transformIntoPartial[GroupModifyChangeJson])
      .buildTransformer
  }

  implicit val createEncoder: JsonEncoder[GroupCreateChangeJson] =
    JsonEncoder[GroupJson].contramap[GroupCreateChangeJson](_.group)
  implicit val deleteEncoder: JsonEncoder[GroupDeleteChangeJson] =
    JsonEncoder[GroupJson].contramap[GroupDeleteChangeJson](_.group)
  implicit val modifyEncoder: JsonEncoder[GroupModifyChangeJson] =
    JsonEncoder[ModifyGroupJson].contramap[GroupModifyChangeJson](_.change)
  implicit val encoder:       JsonEncoder[GroupChangeJson]       = DeriveJsonEncoder.gen[GroupChangeJson]
}

final case class GroupChangeActionJson(
    action: ActionChangeJson,
    change: GroupChangeJson
)
object GroupChangeActionJson {
  import GroupChangeJson.*

  implicit def transformer(implicit
      diffService: DiffService
  ): PartialTransformer[NodeGroupChange, GroupChangeActionJson] = {
    case (source, _) =>
      implicit val change = source
      source.change
        .map(_.diff)
        .map(_.transformIntoPartial[GroupChangeJson].map {
          case d: GroupCreateChangeJson => GroupChangeActionJson(ActionChangeJson.create, d)
          case d: GroupDeleteChangeJson => GroupChangeActionJson(ActionChangeJson.delete, d)
          case d: GroupModifyChangeJson => GroupChangeActionJson(ActionChangeJson.modify, d)
        }) match {
        case Left(err)    =>
          Result.fromErrorString(
            s"Error while serializing group from CR ${change.firstChange.diff.group.id.serialize}: ${err.msg}"
          )
        case Right(value) => value
      }
  }

  implicit val encoder: JsonEncoder[GroupChangeActionJson] = DeriveJsonEncoder.gen[GroupChangeActionJson]
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// GLOBAL PARAMETER
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@jsonDiscriminator("type") sealed trait GlobalParameterChangeJson
object GlobalParameterChangeJson {

  final case class GlobalParameterCreateChangeJson(
      parameter: JRGlobalParameter
  ) extends GlobalParameterChangeJson

  final case class GlobalParameterDeleteChangeJson(
      parameter: JRGlobalParameter
  ) extends GlobalParameterChangeJson

  final case class GlobalParameterModifyChangeJson(
      change: ModifyGlobalParameterJson
  ) extends GlobalParameterChangeJson

  final case class ModifyGlobalParameterJson(
      name:        String,
      description: SimpleDiffOrValueJson[String],
      value:       SimpleDiffOrValueJson[String]
  )

  object ModifyGlobalParameterJson {
    implicit lazy val encoder: JsonEncoder[ModifyGlobalParameterJson] =
      DeriveJsonEncoder.gen[ModifyGlobalParameterJson]

    def from(
        modifyGlobalParameterDiff: ModifyGlobalParameterDiff,
        initialState:              GlobalParameter
    ): ModifyGlobalParameterJson = {
      ModifyGlobalParameterJson(
        modifyGlobalParameterDiff.name,
        SimpleDiffOrValueJson.withDefault(modifyGlobalParameterDiff.modDescription, initialState.description),
        SimpleDiffOrValueJson
          .withDefault(modifyGlobalParameterDiff.modValue, initialState.value)
          .map(GenericProperty.serializeToHocon)
      )
    }
  }

  implicit val jrGlobalParameterTransformer: Transformer[GlobalParameter, JRGlobalParameter]                         =
    JRGlobalParameter.fromGlobalParameter(_, None)
  implicit val createTransformer:            Transformer[AddGlobalParameterDiff, GlobalParameterCreateChangeJson]    =
    Transformer.derive[AddGlobalParameterDiff, GlobalParameterCreateChangeJson]
  implicit val deleteTransformer:            Transformer[DeleteGlobalParameterDiff, GlobalParameterDeleteChangeJson] =
    Transformer.derive[DeleteGlobalParameterDiff, GlobalParameterDeleteChangeJson]
  implicit def modifyTransformer(implicit
      change:      GlobalParameterChange,
      diffService: DiffService
  ): PartialTransformer[ModifyToGlobalParameterDiff, GlobalParameterModifyChangeJson] = {
    case (ModifyToGlobalParameterDiff(parameter), _) =>
      val result = change.initialState match {
        case Some(init) =>
          val diff = diffService.diffGlobalParameter(init, parameter)
          Right(GlobalParameterModifyChangeJson(ModifyGlobalParameterJson.from(diff, init)))
        case _          => Left(s"Error while fetching initial state of change request.")
      }
      Result.fromEitherString(result)
  }

  implicit def transformer(implicit
      change:      GlobalParameterChange,
      diffService: DiffService
  ): PartialTransformer[ChangeRequestGlobalParameterDiff, GlobalParameterChangeJson] = {
    PartialTransformer
      .define[ChangeRequestGlobalParameterDiff, GlobalParameterChangeJson]
      .withCoproductInstance[AddGlobalParameterDiff](_.transformInto[GlobalParameterCreateChangeJson])
      .withCoproductInstance[DeleteGlobalParameterDiff](_.transformInto[GlobalParameterDeleteChangeJson])
      .withCoproductInstancePartial[ModifyToGlobalParameterDiff](_.transformIntoPartial[GlobalParameterModifyChangeJson])
      .buildTransformer
  }

  implicit val createEncoder: JsonEncoder[GlobalParameterCreateChangeJson] =
    JsonEncoder[JRGlobalParameter].contramap[GlobalParameterCreateChangeJson](_.parameter)
  implicit val deleteEncoder: JsonEncoder[GlobalParameterDeleteChangeJson] =
    JsonEncoder[JRGlobalParameter].contramap[GlobalParameterDeleteChangeJson](_.parameter)
  implicit val modifyEncoder: JsonEncoder[GlobalParameterModifyChangeJson] =
    JsonEncoder[ModifyGlobalParameterJson].contramap[GlobalParameterModifyChangeJson](_.change)
  implicit val encoder:       JsonEncoder[GlobalParameterChangeJson]       = DeriveJsonEncoder.gen[GlobalParameterChangeJson]
}

final case class GlobalParameterChangeActionJson(
    action: ActionChangeJson,
    change: GlobalParameterChangeJson
)

object GlobalParameterChangeActionJson {
  import GlobalParameterChangeJson.*

  implicit def transformer(implicit
      diffService: DiffService
  ): PartialTransformer[GlobalParameterChange, GlobalParameterChangeActionJson] = {
    case (source, _) =>
      implicit val change = source
      source.change
        .map(_.diff)
        .map(_.transformIntoPartial[GlobalParameterChangeJson].map {
          case d: GlobalParameterCreateChangeJson => GlobalParameterChangeActionJson(ActionChangeJson.create, d)
          case d: GlobalParameterDeleteChangeJson => GlobalParameterChangeActionJson(ActionChangeJson.delete, d)
          case d: GlobalParameterModifyChangeJson => GlobalParameterChangeActionJson(ActionChangeJson.modify, d)
        }) match {
        case Left(err)    =>
          Result.fromErrorString(
            s"Error while serializing global parameter from CR ${change.firstChange.diff.parameter.name}: ${err.msg}"
          )
        case Right(value) => value
      }
  }

  implicit val encoder: JsonEncoder[GlobalParameterChangeActionJson] = DeriveJsonEncoder.gen[GlobalParameterChangeActionJson]
}

final case class ConfigurationChangeRequestJson(
    directives: Chunk[DirectiveChangeActionJson],
    rules:      Chunk[RuleChangeActionJson],
    groups:     Chunk[GroupChangeActionJson],
    parameters: Chunk[GlobalParameterChangeActionJson]
)

object ConfigurationChangeRequestJson {
  implicit def transformer(implicit
      techniqueByDirective: Map[DirectiveId, Technique],
      diffService:          DiffService
  ): PartialTransformer[ConfigurationChangeRequest, ConfigurationChangeRequestJson] = {
    PartialTransformer
      .define[ConfigurationChangeRequest, ConfigurationChangeRequestJson]
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
          Result.traverse[Chunk[RuleChangeActionJson], (RuleId, RuleChanges), RuleChangeActionJson](
            cr.rules.iterator,
            {
              case (directiveId, changes) =>
                changes.changes.transformIntoPartial[RuleChangeActionJson]
            },
            failFast = false
          )
        }
      )
      .withFieldComputedPartial(
        _.groups,
        cr => {
          Result.traverse[Chunk[GroupChangeActionJson], (NodeGroupId, NodeGroupChanges), GroupChangeActionJson](
            cr.nodeGroups.iterator,
            {
              case (groupId, changes) =>
                changes.changes.transformIntoPartial[GroupChangeActionJson]
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

  implicit val encoder: JsonEncoder[ConfigurationChangeRequestJson] = DeriveJsonEncoder.gen[ConfigurationChangeRequestJson]
}
