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

package com.normation.plugins.changevalidation.api

import com.normation.box.*
import com.normation.cfclerk.domain.Technique
import com.normation.cfclerk.domain.TechniqueId
import com.normation.cfclerk.services.TechniqueRepository
import com.normation.errors.*
import com.normation.plugins.changevalidation.ChangeRequestFilter
import com.normation.plugins.changevalidation.ChangeRequestJson
import com.normation.plugins.changevalidation.RoChangeRequestRepository
import com.normation.plugins.changevalidation.RoWorkflowRepository
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.*
import com.normation.plugins.changevalidation.WoChangeRequestRepository
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.DELETE
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax.*
import com.normation.rudder.rest.GeneralApi
import com.normation.rudder.rest.OneParam
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.StartsAtVersion3
import com.normation.rudder.rest.ZeroParam
import com.normation.rudder.rest.data.APIChangeRequestInfo
import com.normation.rudder.rest.implicits.*
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.services.modification.DiffService
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.services.workflows.WorkflowLevelService
import com.normation.rudder.users.UserService
import com.normation.rudder.web.services.ReasonBehavior
import com.normation.rudder.web.services.UserPropertyService
import enumeratum.*
import net.liftweb.common.Box
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line
import zio.*
import zio.json.*
import zio.syntax.*

sealed trait ChangeRequestApi extends EnumEntry with EndpointSchema with GeneralApi with SortIndex
object ChangeRequestApi       extends Enum[ChangeRequestApi] with ApiModuleProvider[ChangeRequestApi] {

  final case object ListChangeRequests     extends ChangeRequestApi with ZeroParam with StartsAtVersion3 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "List all change requests"
    val (action, path) = GET / "changeRequests"

    override def authz:         List[AuthorizationType] = List(AuthorizationType.Deployer.Read, AuthorizationType.Validator.Read)
    override def dataContainer: Option[String]          = None
  }
  final case object ChangeRequestsDetails  extends ChangeRequestApi with OneParam with StartsAtVersion3 with SortIndex  {
    val z              = implicitly[Line].value
    val description    = "Get information about given change request"
    val (action, path) = GET / "changeRequests" / "{id}"

    override def authz:         List[AuthorizationType] = List(AuthorizationType.Deployer.Read, AuthorizationType.Validator.Read)
    override def dataContainer: Option[String]          = Some("changeRequests")
    override def name:          String                  = "changeRequestDetails"
  }
  final case object DeclineRequestsDetails extends ChangeRequestApi with OneParam with StartsAtVersion3 with SortIndex  {
    val z              = implicitly[Line].value
    val description    = "Decline given change request"
    val (action, path) = DELETE / "changeRequests" / "{id}"

    override def authz:         List[AuthorizationType] = List(
      AuthorizationType.Deployer.Write,
      AuthorizationType.Deployer.Edit,
      AuthorizationType.Validator.Write,
      AuthorizationType.Validator.Edit
    )
    override def dataContainer: Option[String]          = Some("changeRequests")
    override def name:          String                  = "declineChangeRequest"
  }
  final case object AcceptRequestsDetails  extends ChangeRequestApi with OneParam with StartsAtVersion3 with SortIndex  {
    val z              = implicitly[Line].value
    val description    = "Accept given change request"
    val (action, path) = POST / "changeRequests" / "{id}" / "accept"

    override def authz:         List[AuthorizationType] = List(
      AuthorizationType.Deployer.Write,
      AuthorizationType.Deployer.Edit,
      AuthorizationType.Validator.Write,
      AuthorizationType.Validator.Edit
    )
    override def dataContainer: Option[String]          = Some("changeRequests")
    override def name:          String                  = "acceptChangeRequest"
  }
  final case object UpdateRequestsDetails  extends ChangeRequestApi with OneParam with StartsAtVersion3 with SortIndex  {
    val z              = implicitly[Line].value
    val description    = "Update information about given change request"
    val (action, path) = POST / "changeRequests" / "{id}"

    override def authz:         List[AuthorizationType] = List(
      AuthorizationType.Deployer.Write,
      AuthorizationType.Deployer.Edit,
      AuthorizationType.Validator.Write,
      AuthorizationType.Validator.Edit
    )
    override def dataContainer: Option[String]          = Some("changeRequests")
    override def name:          String                  = "updateChangeRequest"
  }

  def endpoints = values.toList.sortBy(_.z)
  def values    = findValues
}

class ChangeRequestApiImpl(
    diffService:          DiffService,
    readTechnique:        TechniqueRepository,
    readChangeRequest:    RoChangeRequestRepository,
    writeChangeRequest:   WoChangeRequestRepository,
    readWorkflow:         RoWorkflowRepository,
    workflowLevelService: WorkflowLevelService,
    commitRepository:     CommitAndDeployChangeRequestService,
    userPropertyService:  UserPropertyService,
    userService:          UserService
) extends LiftApiModuleProvider[ChangeRequestApi] {
  import com.normation.plugins.changevalidation.api.ChangeRequestApi as API
  implicit private val diffServiceImpl: DiffService = diffService

  override def schemas: ApiModuleProvider[ChangeRequestApi] = API

  // Checks if we need external validation
  def checkWorkflow: Boolean = {
    workflowLevelService.getWorkflowService().needExternalValidation()
  }

  def serialize(cr: ChangeRequest, status: WorkflowNodeId)(implicit
      techniqueByDirective: Map[DirectiveId, Technique]
  ): PureResult[ChangeRequestJson] = {
    val isAcceptable = commitRepository.isMergeable(cr)
    ChangeRequestJson.from(cr, status, isAcceptable)
  }

  private[this] def disabledWorkflowAnswer[T]: IOResult[T] = {
    Inconsistency("Workflow are disabled in Rudder, change request API is not available").fail
  }

  // While there is no authorisation on API, they got all rights.
  private[this] def apiUserRights = Seq("deployer", "validator")

  def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints
      .map(e => {
        e match {
          case API.ListChangeRequests     => ListChangeRequests
          case API.ChangeRequestsDetails  => ChangeRequestsDetails
          case API.DeclineRequestsDetails => DeclineRequestsDetails
          case API.AcceptRequestsDetails  => AcceptRequestsDetails
          case API.UpdateRequestsDetails  => UpdateRequestsDetails
        }
      })
      .toList
  }

  def checkUserAction(workflowNodeId: WorkflowNodeId, target: WorkflowNodeId): PureResult[String] = {
    if (workflowNodeId == Validation.id) {
      if (!userService.getCurrentUser.checkRights(AuthorizationType.Validator.Write)) {
        Left(Inconsistency(s"User is not authorized to update a 'pending validation' change"))
      } else if (target == Deployed.id && !userService.getCurrentUser.checkRights(AuthorizationType.Deployer.Write)) {
        Left(Inconsistency(s"User is not authorized to update a 'pending validation' change to 'deployed' state"))
      } else {
        Right("user is authorized to do step")
      }
    } else if (
      workflowNodeId == Deployment.id && !userService.getCurrentUser.checkRights(
        AuthorizationType.Deployer.Write
      )
    ) {
      Left(Inconsistency("User is not authorized to update a 'pending deployment' change"))
    } else {
      Right("user is authorized to do step")
    }
  }

  object ListChangeRequests extends LiftApiModule0 {
    val schema: ChangeRequestApi.ListChangeRequests.type = API.ListChangeRequests

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      def listChangeRequestsByFilter(filter: ChangeRequestFilter): IOResult[Seq[ChangeRequestJson]] = {
        for {
          crsWithStatus <- readChangeRequest.getByFilter(filter)
          serialized    <- crsWithStatus.sortBy(_._1.id.value).accumulate {
                             case (cr, status) => getDirectiveTechniques(cr).flatMap(serialize(cr, status)(_).toIO)
                           }
        } yield {
          serialized
        }
      }

      (for {
        filter <- extractFilters(req.params).toIO
        res    <- checkWorkflow match {
                    case true  => listChangeRequestsByFilter(filter).chainError("Could not fetch ChangeRequests")
                    case false => disabledWorkflowAnswer
                  }
      } yield {
        res
      }).toLiftResponseList(params, schema)

    }
  }

  object ChangeRequestsDetails extends LiftApiModule {
    val schema: ChangeRequestApi.ChangeRequestsDetails.type = API.ChangeRequestsDetails

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sid:        String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      withChangeRequestContext(sid, params, schema, "find")((changeRequest, status, techniqueByDirective) =>
        serialize(changeRequest, status)(techniqueByDirective).toIO
      ).toLiftResponseOne(params, schema, Some(sid))
    }
  }

  object DeclineRequestsDetails extends LiftApiModule {
    val schema: ChangeRequestApi.DeclineRequestsDetails.type = API.DeclineRequestsDetails

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      // we need to check rights for validator/deployer here, API level is not sufficient.
      def actualRefuse(changeRequest: ChangeRequest, step: WorkflowNodeId)(implicit
          techniqueByDirective: Map[DirectiveId, Technique]
      ): IOResult[ChangeRequestJson] = {
        for {
          backSteps  <- workflowLevelService.getWorkflowService().findBackSteps(apiUserRights, step, false).succeed
          optStep     = backSteps.find(_._1 == WorkflowNodeId("Cancelled"))
          stepFunc   <-
            optStep.notOptional(
              s"Could not decline ChangeRequest ${id} details cause is: could not decline ChangeRequest ${id}, because status '${step.value}' cannot be cancelled."
            )
          (_, func)   = stepFunc
          reason     <- extractReason(req)
          result     <- func(changeRequest.id, authzToken.qc.actor, reason).toIO
          serialized <- serialize(changeRequest, result)(techniqueByDirective).toIO
        } yield {
          serialized
        }
      }

      withChangeRequestContext(id, params, schema, "decline")((changeRequest, status, techniqueByDirective) =>
        actualRefuse(changeRequest, status)(techniqueByDirective.toMap)
      ).toLiftResponseOne(params, schema, Some(id))
    }
  }

  object AcceptRequestsDetails extends LiftApiModule {
    val schema: ChangeRequestApi.AcceptRequestsDetails.type = API.AcceptRequestsDetails

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      def actualAccept(changeRequest: ChangeRequest, step: WorkflowNodeId, targetStep: WorkflowNodeId)(implicit
          techniqueByDirective: Map[DirectiveId, Technique]
      ): IOResult[ChangeRequestJson] = {
        for {
          nextSteps  <- workflowLevelService.getWorkflowService().findNextSteps(apiUserRights, step, false).succeed
          optStep     = nextSteps.actions.find(_._1 == targetStep)
          stepFunc   <-
            optStep.notOptional(
              s"Could not accept ChangeRequest ${id} details cause is: you could not send Change Request from '${step.value}' to '${targetStep.value}'."
            )
          (_, func)   = stepFunc
          reason     <- extractReason(req)
          result     <- func(changeRequest.id, authzToken.qc.actor, reason).toIO
          serialized <- serialize(changeRequest, result)(techniqueByDirective).toIO
        } yield {
          serialized
        }
      }

      (for {
        targetStep <- extractWorkflowTargetStatus(req.params).toIO
        res        <- {
          withChangeRequestContext(id, params, schema, "accept") { (changeRequest, currentState, techniqueByDirective) =>
            implicit val directiveCtx = techniqueByDirective
            checkUserAction(currentState, targetStep).toIO *>
            (currentState match {
              case Deployment.id | Validation.id =>
                actualAccept(changeRequest, currentState, targetStep)
              case Cancelled.id                  =>
                Inconsistency(
                  s"Could not accept ChangeRequest ${id} details cause is: ChangeRequest ${id} has already been cancelled."
                ).fail
              case Deployed.id                   =>
                Inconsistency(
                  s"Could not accept ChangeRequest ${id} details cause is: ChangeRequest ${id} has already been deployed."
                ).fail
              case WorkflowNodeId(unknownState)  =>
                Unexpected(
                  s"Could not accept ChangeRequest ${id} details cause is: ChangeRequest ${id} is in an unknown state : '${unknownState}'."
                ).fail
            })
          }
        }

      } yield {
        res
      }).toLiftResponseOne(params, schema, Some(id))
    }
  }

  object UpdateRequestsDetails extends LiftApiModule {
    val schema: ChangeRequestApi.UpdateRequestsDetails.type = API.UpdateRequestsDetails

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      def updateInfo(changeRequest: ChangeRequest, status: WorkflowNodeId, apiInfo: APIChangeRequestInfo)(implicit
          techniqueByDirective: Map[DirectiveId, Technique]
      ): IOResult[ChangeRequestJson] = {
        val newInfo = apiInfo.updateCrInfo(changeRequest.info)
        if (changeRequest.info == newInfo) {
          val message = s"Could not update ChangeRequest ${id} details cause is: No changes to save."
          Inconsistency(message).fail
        } else {
          val newCR = ChangeRequest.updateInfo(changeRequest, newInfo)
          for {
            updated    <- writeChangeRequest.updateChangeRequest(newCR, authzToken.qc.actor, None).toIO
            serialized <- serialize(updated, status)(techniqueByDirective).toIO
          } yield {
            serialized
          }
        }
      }

      withChangeRequestContext(id, params, schema, "update")((changeRequest, status, techniqueByDirective) =>
        updateInfo(changeRequest, status, extractChangeRequestInfo(req.params))(techniqueByDirective.toMap)
      ).toLiftResponseOne(params, schema, Some(id))
    }
  }

  private def withChangeRequestContext[T: JsonEncoder](
      sid:          String,
      params:       DefaultParams,
      schema:       EndpointSchema,
      actionDetail: String
  )(
      block:        (ChangeRequest, WorkflowNodeId, Map[DirectiveId, Technique]) => IOResult[T]
  ): IOResult[T] = {
    val id = {
      // PureResult.attempt(s"'${sid}' is not a valid change request id (need to be an integer)")(ChangeRequestId(sid.toInt))
      Box(sid.toIntOption.map(ChangeRequestId(_))) ?~ (s"'${sid}' is not a valid change request id (need to be an integer)")
    }

    checkWorkflow match {
      case true =>
        (for {
          crId          <- id
          optCr         <- readChangeRequest.get(crId) ?~! (s"Could not find ChangeRequest ${sid}")
          changeRequest <-
            Box(optCr) ?~ (s"Could not get ChangeRequest ${sid} details cause is: change request with id ${sid} does not exist.")
          status        <- readWorkflow.getStateOfChangeRequest(crId) ?~! (s"Could not find ChangeRequest ${sid} status")
          result        <- getDirectiveTechniques(changeRequest).flatMap(block(changeRequest, status, _)).toBox
        } yield {
          result
        }).toIO
          .chainError(s"Could not ${actionDetail} ChangeRequest ${sid}")

      case false =>
        disabledWorkflowAnswer
    }
  }

  private def getDirectiveTechniques(changeRequest: ChangeRequest): IOResult[Map[DirectiveId, Technique]] = {
    (ZIO
      .foreach(changeRequest match {
        case cr: ConfigurationChangeRequest => cr.directives.toList
        case _ => List.empty
      }) {
        case (directiveId, changes) =>
          changes.changes.change.toIO.flatMap(item => {
            val diff        = item.diff
            val techniqueId = TechniqueId(diff.techniqueName, diff.directive.techniqueVersion)
            val technique   = readTechnique.get(techniqueId)
            technique
              .map((directiveId, _))
              .notOptional(s"Could not find technique ${techniqueId.serialize} for directive ${directiveId.serialize}")
          })
      })
      .map(_.toMap)
  }

  private def extractWorkflowStatus(params: Map[String, List[String]]): PureResult[Seq[WorkflowNodeId]] = {
    params
      .get("status")
      .flatMap(_.headOption)
      .map(_.toLowerCase())
      .map {
        case "open"   => Right(workflowLevelService.getWorkflowService().openSteps)
        case "closed" => Right(workflowLevelService.getWorkflowService().closedSteps)
        case "all"    => Right(workflowLevelService.getWorkflowService().stepsValue)
        case value    =>
          workflowLevelService.getWorkflowService().stepsValue.find(_.value.equalsIgnoreCase(value)) match {
            case Some(state) => Right(Seq(state))
            case None        => Left(Unexpected(s"'${value}' is not a possible state for change requests"))
          }

      }
      .getOrElse(
        Right(workflowLevelService.getWorkflowService().openSteps)
      )
  }

  private def extractWorkflowTargetStatus(params: Map[String, List[String]]): PureResult[WorkflowNodeId] = {
    params
      .get("status")
      .flatMap(_.headOption)
      .notOptionalPure("workflow status should not be empty")
      .map(_.toLowerCase())
      .flatMap(value => {
        val possiblestates = workflowLevelService.getWorkflowService().stepsValue
        possiblestates.find(_.value.equalsIgnoreCase(value)) match {
          case Some(state) => Right(state)
          case None        =>
            Left(
              Unexpected(
                s"'${value}' is not a possible state for change requests, available values are: ${possiblestates.map(_.value).mkString("[ ", ", ", " ]")}"
              )
            )
        }
      })
  }

  private def extractChangeRequestInfo(params: Map[String, List[String]]): APIChangeRequestInfo = {
    APIChangeRequestInfo(
      params.get("name").flatMap(_.headOption),
      params.get("description").flatMap(_.headOption)
    )
  }

  private def extractReason(req: Req): IOResult[Option[String]] = {
    import ReasonBehavior.*
    (userPropertyService.reasonsFieldBehavior match {
      case Disabled => ZIO.none
      case mode     =>
        val reason = req.params.get("reason").flatMap(_.headOption)
        (mode: @unchecked) match {
          case Mandatory =>
            reason
              .notOptional("Reason field is mandatory and should be at least 5 characters long")
              .reject {
                case s if s.lengthIs < 5 => Inconsistency("Reason field should be at least 5 characters long")
              }
              .map(Some(_))
          case Optionnal => reason.succeed
        }
    }).chainError("There was an error while extracting reason message")
  }

  private[this] def extractFilters(params: Map[String, List[String]]): PureResult[ChangeRequestFilter] = {
    import ChangeRequestFilter.*
    for {
      status     <- extractWorkflowStatus(params)
      byRule      = params.get("ruleId").flatMap(_.headOption).map(id => ByRule(RuleUid(id)))
      byDirective = params.get("directiveId").flatMap(_.headOption).map(id => ByDirective(DirectiveUid(id)))
      byNodeGroup = params.get("nodeGroupId").flatMap(_.headOption).map(id => ByNodeGroup(NodeGroupUid(id)))
    } yield {
      ChangeRequestFilter(NonEmptyChunk.fromIterableOption(status), byRule orElse byDirective orElse byNodeGroup)
    }
  }

}
