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

import com.normation.box._
import com.normation.cfclerk.services.TechniqueRepository
import com.normation.plugins.changevalidation.ChangeRequestFilter
import com.normation.plugins.changevalidation.RoChangeRequestRepository
import com.normation.plugins.changevalidation.RoWorkflowRepository
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl
import com.normation.plugins.changevalidation.WoChangeRequestRepository
import com.normation.plugins.changevalidation.WoWorkflowRepository
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.DELETE
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.apidata.RestDataSerializer
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.GeneralApi
import com.normation.rudder.rest.OneParam
import com.normation.rudder.rest.RestExtractorService
import com.normation.rudder.rest.RestUtils._
import com.normation.rudder.rest.RestUtils.toJsonError
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.StartsAtVersion3
import com.normation.rudder.rest.ZeroParam
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.services.workflows.WorkflowLevelService
import com.normation.rudder.users.CurrentUser
import net.liftweb.common.Box
import net.liftweb.common.EmptyBox
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JsonAST.JArray
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JString
import sourcecode.Line
import zio.NonEmptyChunk

sealed trait ChangeRequestApi extends EndpointSchema with GeneralApi with SortIndex
object ChangeRequestApi       extends ApiModuleProvider[ChangeRequestApi] {

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
    override def dataContainer: Option[String]          = None
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
    override def dataContainer: Option[String]          = None
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
    override def dataContainer: Option[String]          = None
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
    override def dataContainer: Option[String]          = None
  }

  def endpoints = ca.mrvisser.sealerate.values[ChangeRequestApi].toList.sortBy(_.z)
}

class ChangeRequestApiImpl(
    restExtractorService: RestExtractorService,
    readChangeRequest:    RoChangeRequestRepository,
    writeChangeRequest:   WoChangeRequestRepository,
    readWorkflow:         RoWorkflowRepository,
    writeWorkflow:        WoWorkflowRepository,
    readTechnique:        TechniqueRepository,
    workflowLevelService: WorkflowLevelService,
    commitRepository:     CommitAndDeployChangeRequestService,
    restDataSerializer:   RestDataSerializer
) extends LiftApiModuleProvider[ChangeRequestApi] {

  import com.normation.plugins.changevalidation.api.{ChangeRequestApi => API}

  override def schemas: ApiModuleProvider[ChangeRequestApi] = API

  def checkWorkflow = {
    if (workflowLevelService.getWorkflowService().needExternalValidation())
      Full("Ok")
    else
      Failure("workflow disabled")
  }

  def serialize(cr: ChangeRequest, status: WorkflowNodeId, version: ApiVersion) = {
    val isAcceptable = commitRepository.isMergeable(cr)
    restDataSerializer.serializeCR(cr, status, isAcceptable, version)
  }
  private[this] def unboxAnswer(actionName: String, id: ChangeRequestId, boxedAnswer: Box[LiftResponse])(implicit
      action:                               String,
      prettify:                             Boolean
  ) = {
    boxedAnswer match {
      case Full(response) => response
      case eb: EmptyBox =>
        val fail    = eb ?~! (s"Could not $actionName ChangeRequest ${id}")
        val message = s"Could not $actionName ChangeRequest ${id} details cause is: ${fail.messageChain}."
        toJsonError(Some(id.value.toString), message)
    }
  }

  private[this] def disabledWorkflowAnswer(crId: Option[String])(implicit action: String, prettify: Boolean) = {
    toJsonError(crId, "Workflow are disabled in Rudder, change request API is not available")
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

  def checkUserAction(workflowNodeId: WorkflowNodeId, target: WorkflowNodeId): Box[String] = {
    if (workflowNodeId == TwoValidationStepsWorkflowServiceImpl.Validation.id) {
      if (!CurrentUser.checkRights(AuthorizationType.Validator.Write)) {
        Failure(s"User is not authorized to update a 'pending validation' change")
      } else if (
        target == TwoValidationStepsWorkflowServiceImpl.Deployed.id && !CurrentUser.checkRights(AuthorizationType.Deployer.Write)
      ) {
        Failure(s"User is not authorized to update a 'pending validation' change to 'deployed' state")
      } else {
        Full("user is authorized to do step")
      }
    } else if (
      workflowNodeId == TwoValidationStepsWorkflowServiceImpl.Deployment.id && !CurrentUser.checkRights(
        AuthorizationType.Deployer.Write
      )
    ) {
      Failure(s"User is not authorized to update a 'pending deployment' change")
    } else {
      Full("user is authorized to do step")
    }
  }

  object ListChangeRequests extends LiftApiModule0 {
    val schema        = API.ListChangeRequests
    val restExtractor = restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      extractFilters(req.params) match {
        case Full(filter) =>
          implicit val action   = "listChangeRequests"
          implicit val prettify = restExtractor.extractPrettify(req.params)

          def listChangeRequestsByFilter(filter: ChangeRequestFilter) = {
            for {
              crs <- readChangeRequest.getByFilter(filter).toBox ?~ ("Could not fetch ChangeRequests")
            } yield {
              val result = JArray(crs.map { case (cr, status) => serialize(cr, status, version) }.toList)
              Full(result)
            }
          }
          def concatenateJArray(a: JArray, b: JArray): JArray = {
            JArray(a.arr ++ b.arr)
          }

          checkWorkflow match {
            case Full(_) =>
              (for {
                results <- listChangeRequestsByFilter(filter) ?~ ("Could not fetch ChangeRequests")
              } yield {
                val res: JValue = (results foldRight JArray(List()))(concatenateJArray)
                toJsonResponse(None, res)
              }) match {
                case Full(response) =>
                  response
                case eb: EmptyBox =>
                  val fail = eb ?~ ("Could not fetch ChangeRequests")
                  toJsonError(None, fail.messageChain)
              }
            case eb: EmptyBox =>
              disabledWorkflowAnswer(None)
          }

        case eb: EmptyBox =>
          toJsonError(None, JString("No parameter 'status' sent"))(
            "listChangeRequests",
            restExtractor.extractPrettify(req.params)
          )
      }
    }
  }

  object ChangeRequestsDetails extends LiftApiModule {
    val schema        = API.ChangeRequestsDetails
    val restExtractor = restExtractorService
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sid:        String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      try {
        implicit val action   = "changeRequestDetails"
        implicit val prettify = restExtractor.extractPrettify(req.params)

        val id = ChangeRequestId(sid.toInt)

        checkWorkflow match {

          case Full(_) =>
            val answer = for {
              optCr         <- readChangeRequest.get(id) ?~! (s"Could not find ChangeRequest ${id}")
              changeRequest <-
                optCr
                  .map(Full(_))
                  .getOrElse(
                    Failure(s"Could not get ChangeRequest ${id} details cause is: change request with id ${id} does not exist.")
                  )
              status        <- readWorkflow.getStateOfChangeRequest(id) ?~! (s"Could not find ChangeRequest ${id} status")
            } yield {
              val jsonChangeRequest = List(serialize(changeRequest, status, version))
              toJsonResponse(Some(id.value.toString), ("changeRequests" -> JArray(jsonChangeRequest)))
            }
            unboxAnswer("find", id, answer)
          case eb: EmptyBox =>
            disabledWorkflowAnswer(None)
        }
      } catch {
        case e: Exception =>
          toJsonError(None, JString(s"'${sid}' is not a valid change request id (need to be an integer)"))(
            "changeRequestDetails",
            restExtractor.extractPrettify(req.params)
          )
      }
    }
  }

  object DeclineRequestsDetails extends LiftApiModule {
    val schema        = API.DeclineRequestsDetails
    val restExtractor = restExtractorService
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      implicit val action   = "declineChangeRequest"
      implicit val prettify = restExtractor.extractPrettify(req.params)

      // we need to check rights for validator/deployer here, API level is not sufficient.

      try {
        val crId                                                             = ChangeRequestId(id.toInt)
        def actualRefuse(changeRequest: ChangeRequest, step: WorkflowNodeId) = {
          val backSteps = workflowLevelService.getWorkflowService().findBackSteps(apiUserRights, step, false)
          val optStep   = backSteps.find(_._1 == WorkflowNodeId("Cancelled"))
          val answer    = for {
            (_, func) <-
              optStep
                .map(Full(_))
                .getOrElse(
                  Failure(
                    s"Could not decline ChangeRequest ${id} details cause is: could not decline ChangeRequest ${id}, because status '${step.value}' cannot be cancelled."
                  )
                )
            reason    <- restExtractor.extractReason(req) ?~ "There was an error while extracting reason message"
            result    <- func(crId, authzToken.qc.actor, reason) ?~! (s"Could not decline ChangeRequest ${id}")
          } yield {
            val jsonChangeRequest = List(serialize(changeRequest, result, version))
            toJsonResponse(Some(id.toString), ("changeRequests" -> JArray(jsonChangeRequest)))
          }
          unboxAnswer("decline", crId, answer)
        }

        checkWorkflow match {
          case Full(_) =>
            val answer = {
              for {
                optCR         <- readChangeRequest.get(crId) ?~! (s"Could not find ChangeRequest ${id}")
                changeRequest <-
                  optCR
                    .map(Full(_))
                    .getOrElse(
                      Failure(
                        s"Could not decline ChangeRequest ${id} details cause is: change request with id ${id} does not exist."
                      )
                    )
                currentState  <-
                  readWorkflow.getStateOfChangeRequest(crId) ?~! (s"Could not find actual state of ChangeRequest ${id}")
                authzOk       <- checkUserAction(currentState, TwoValidationStepsWorkflowServiceImpl.Cancelled.id)
              } yield {
                actualRefuse(changeRequest, currentState)
              }
            }
            unboxAnswer("decline", crId, answer)

          case eb: EmptyBox =>
            disabledWorkflowAnswer(None)
        }
      } catch {
        case e: Exception =>
          toJsonError(None, JString(s"${id} is not a valid change request id (need to be an integer)"))(
            "declineChangeRequest",
            restExtractor.extractPrettify(req.params)
          )
      }
    }
  }

  object AcceptRequestsDetails extends LiftApiModule {
    val schema        = API.AcceptRequestsDetails
    val restExtractor = restExtractorService
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      implicit val action   = "acceptChangeRequest"
      implicit val prettify = restExtractor.extractPrettify(req.params)
      restExtractor.extractWorkflowTargetStatus(req.params) match {
        case Full(targetStep) =>
          try {
            val crId                                                             = ChangeRequestId(id.toInt)
            def actualAccept(changeRequest: ChangeRequest, step: WorkflowNodeId) = {
              val nextSteps = workflowLevelService.getWorkflowService().findNextSteps(apiUserRights, step, false)
              val optStep   = nextSteps.actions.find(_._1 == targetStep)
              val answer    = for {
                (_, func) <-
                  optStep
                    .map(Full(_))
                    .getOrElse(
                      Failure(
                        s"Could not accept ChangeRequest ${id} details cause is: you could not send Change Request from '${step.value}' to '${targetStep.value}'."
                      )
                    )
                reason    <- restExtractor.extractReason(req) ?~ "There was an error while extracting reason message"
                result    <- func(crId, authzToken.qc.actor, reason) ?~! (s"Could not accept ChangeRequest ${id}")
              } yield {
                val jsonChangeRequest = List(serialize(changeRequest, result, version))
                toJsonResponse(Some(id), ("changeRequests" -> JArray(jsonChangeRequest)))
              }
              unboxAnswer("accept", crId, answer)
            }

            checkWorkflow match {
              case Full(_) =>
                val answer = {
                  for {
                    optCR         <- readChangeRequest.get(crId) ?~! (s"Could not find ChangeRequest ${id}")
                    changeRequest <-
                      optCR
                        .map(Full(_))
                        .getOrElse(
                          Failure(
                            s"Could not accedt ChangeRequest ${id} details cause is: change request with id ${id} does not exist."
                          )
                        )
                    currentState  <-
                      readWorkflow.getStateOfChangeRequest(crId) ?~! (s"Could not find actual state of ChangeRequest ${id}")
                    authzOk       <- checkUserAction(currentState, targetStep)
                  } yield {
                    currentState.value match {
                      case "Pending validation" =>
                        actualAccept(changeRequest, currentState)
                      case "Pending deployment" =>
                        actualAccept(changeRequest, currentState)
                      case "Cancelled"          =>
                        val message =
                          s"Could not accept ChangeRequest ${id} details cause is: ChangeRequest ${id} has already been cancelled."
                        toJsonError(Some(id), message)
                      case "Deployed"           =>
                        val message =
                          s"Could not accept ChangeRequest ${id} details cause is: ChangeRequest ${id} has already been deployed."
                        toJsonError(Some(id), message)
                    }
                  }
                }
                unboxAnswer("decline", crId, answer)
              case eb: EmptyBox =>
                disabledWorkflowAnswer(None)
            }

          } catch {
            case e: Exception =>
              toJsonError(None, JString(s"${id} is not a valid change request id (need to be an integer)"))(
                "acceptChangeRequest",
                restExtractor.extractPrettify(req.params)
              )
          }

        case eb: EmptyBox =>
          val fail    = eb ?~ "Not valid 'status' parameter sent"
          val message = s"Could not accept ChangeRequest ${id} details cause is: ${fail.messageChain}."
          toJsonError(None, JString(message))("acceptChangeRequest", restExtractor.extractPrettify(req.params))
      }
    }
  }

  object UpdateRequestsDetails extends LiftApiModule {
    val schema        = API.UpdateRequestsDetails
    val restExtractor = restExtractorService
    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      restExtractor.extractChangeRequestInfo(req.params) match {
        case Full(apiInfo) =>
          implicit val action   = "updateChangeRequest"
          implicit val prettify = restExtractor.extractPrettify(req.params)

          def updateInfo(changeRequest: ChangeRequest, status: WorkflowNodeId) = {
            val newInfo = apiInfo.updateCrInfo(changeRequest.info)
            if (changeRequest.info == newInfo) {
              val message = s"Could not update ChangeRequest ${id} details cause is: No changes to save."
              toJsonError(Some(id), message)
            } else {
              val newCR = ChangeRequest.updateInfo(changeRequest, newInfo)
              writeChangeRequest.updateChangeRequest(newCR, authzToken.qc.actor, None) match {
                case Full(cr) =>
                  val jsonChangeRequest = List(serialize(cr, status, version))
                  toJsonResponse(Some(id), ("changeRequests" -> JArray(jsonChangeRequest)))
                case eb: EmptyBox =>
                  val fail    = eb ?~! (s"Could not update ChangeRequest ${id}")
                  val message = s"Could not update ChangeRequest ${id} details cause is: ${fail.messageChain}."
                  toJsonError(Some(id), message)
              }
            }
          }

          val crId = ChangeRequestId(id.toInt)
          checkWorkflow match {
            case Full(_) =>
              val answer = for {
                optCr         <- readChangeRequest.get(crId) ?~! (s"Could not find ChangeRequest ${id}")
                changeRequest <-
                  optCr
                    .map(Full(_))
                    .getOrElse(
                      Failure(
                        s"Could not update ChangeRequest ${id} details cause is: change request with id ${id} does not exist."
                      )
                    )
                status        <- readWorkflow.getStateOfChangeRequest(crId) ?~! (s"Could not find ChangeRequest ${id} status")
              } yield {
                updateInfo(changeRequest, status)
              }
              unboxAnswer("update", crId, answer)
            case eb: EmptyBox =>
              disabledWorkflowAnswer(None)
          }
        case eb: EmptyBox =>
          val fail    = eb ?~! (s"No parameters sent to update change request")
          val message = s"Could not update ChangeRequest ${id} details cause is: ${fail.messageChain}."
          toJsonError(None, JString(message))("updateChangeRequest", restExtractor.extractPrettify(req.params))
      }
    }
  }

  private[this] def extractFilters(params: Map[String, List[String]]): Box[ChangeRequestFilter] = {
    import ChangeRequestFilter._
    for {
      status     <- restExtractorService.extractWorkflowStatus(params)
      byRule      = params.get("ruleId").flatMap(_.headOption).map(id => ByRule(RuleUid(id)))
      byDirective = params.get("directiveId").flatMap(_.headOption).map(id => ByDirective(DirectiveUid(id)))
      byNodeGroup = params.get("nodeGroupId").flatMap(_.headOption).map(id => ByNodeGroup(NodeGroupUid(id)))
    } yield {
      ChangeRequestFilter(NonEmptyChunk.fromIterableOption(status), byRule orElse byDirective orElse byNodeGroup)
    }
  }

}
