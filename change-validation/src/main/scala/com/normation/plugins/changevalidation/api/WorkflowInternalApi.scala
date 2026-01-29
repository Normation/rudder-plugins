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

package com.normation.plugins.changevalidation.api

import cats.data.NonEmptyList
import com.normation.cfclerk.domain.Technique
import com.normation.cfclerk.domain.TechniqueId
import com.normation.cfclerk.services.TechniqueRepository
import com.normation.errors.IOResult
import com.normation.errors.OptionToIoResult
import com.normation.inventory.domain.NodeId
import com.normation.plugins.changevalidation.*
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.Directive
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.facts.nodes.NodeFactRepository
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.repository.RoDirectiveRepository
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.rest.*
import com.normation.rudder.rest.EndpointSchema.syntax.AddPath
import com.normation.rudder.rest.EndpointSchema.syntax.BuildPath
import com.normation.rudder.rest.implicits.ToLiftResponseOne
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.rule.category.RoRuleCategoryRepository
import com.normation.rudder.rule.category.RuleCategoryService
import com.normation.rudder.services.eventlog.ChangeRequestEventLogService
import com.normation.rudder.services.eventlog.EventLogDetailsService
import com.normation.rudder.services.eventlog.WorkflowEventLogService
import com.normation.rudder.services.modification.DiffService
import com.normation.rudder.services.workflows.CommitAndDeployChangeRequestService
import com.normation.rudder.services.workflows.WorkflowLevelService
import enumeratum.Enum
import enumeratum.EnumEntry
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import scala.annotation.tailrec
import scala.collection.MapView
import sourcecode.Line
import zio.ZIO
import zio.syntax.ToZio

sealed trait WorkflowInternalApi extends EnumEntry with EndpointSchema with InternalApi with SortIndex
object WorkflowInternalApi       extends Enum[WorkflowInternalApi] with ApiModuleProvider[WorkflowInternalApi] {

  case object PendingChangeRequestCount extends WorkflowInternalApi with ZeroParam with StartsAtVersion21 with SortIndex {
    val z: Int = implicitly[Line].value
    val (action, path) = GET / "changevalidation" / "workflow" / "pendingCountByStatus"
    val description    =
      "Get total count of change requests in each state, i.e. PendingValidation and PendingDeployment"

    override def dataContainer: Option[String]          = Some("workflow")
    override def authz:         List[AuthorizationType] = {
      List(AuthorizationType.Deployer.Read, AuthorizationType.Validator.Read)
    }
  }

  case object ChangeRequestMainDetails extends WorkflowInternalApi with OneParam with StartsAtVersion21 with SortIndex {
    val z              = implicitly[Line].value
    val (action, path) = GET / "changevalidation" / "workflow" / "changeRequestMainDetails" / "{id}"
    val description    =
      "Get the main details and list of logs of a change request by its ID."

    override def dataContainer: Option[String]          = Some("workflow")
    override def authz:         List[AuthorizationType] = List(AuthorizationType.Deployer.Read, AuthorizationType.Validator.Read)
  }

  case object ChangeRequestChanges extends WorkflowInternalApi with OneParam with StartsAtVersion21 with SortIndex {
    val z              = implicitly[Line].value
    val (action, path) = GET / "changevalidation" / "workflow" / "changeRequestChanges" / "{id}"
    val description    =
      "Get all the changes of a configuration change request."

    override def dataContainer: Option[String] = Some("workflow")

    override def authz: List[AuthorizationType] = List(AuthorizationType.Deployer.Read, AuthorizationType.Validator.Read)

  }

  override def endpoints: List[WorkflowInternalApi]       = values.toList.sortBy(_.z)
  override def values:    IndexedSeq[WorkflowInternalApi] = findValues
}

class WorkflowInternalApiImpl(
    readWorkflow:                 RoWorkflowRepository,
    diffService:                  DiffService,
    readTechnique:                TechniqueRepository,
    workflowLevelService:         WorkflowLevelService,
    roChangeRequestRepository:    RoChangeRequestRepository,
    eventLogDetailsService:       EventLogDetailsService,
    changeRequestEventLogService: ChangeRequestEventLogService,
    commitRepository:             CommitAndDeployChangeRequestService,
    workflowEventLogService:      WorkflowEventLogService,
    nodeFactRepository:           NodeFactRepository,
    directiveRepository:          RoDirectiveRepository,
    nodeGroupRepository:          RoNodeGroupRepository,
    ruleCategoryService:          RuleCategoryService,
    ruleCategoryRepository:       RoRuleCategoryRepository
) extends LiftApiModuleProvider[WorkflowInternalApi] {

  import com.normation.plugins.changevalidation.api.WorkflowInternalApi as API

  override def schemas: ApiModuleProvider[WorkflowInternalApi] = API

  override def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints.map {
      case API.PendingChangeRequestCount => PendingChangeRequestCount
      case API.ChangeRequestMainDetails  => ChangeRequestMainDetails
      case API.ChangeRequestChanges      => ChangeRequestChanges
    }
  }

  object PendingChangeRequestCount extends LiftApiModule0 {

    override val schema: EndpointSchema0 = API.PendingChangeRequestCount

    override def process0(
        version:    ApiVersion,
        path:       ApiPath,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      val isValidator = authzToken.user.checkRights(AuthorizationType.Validator.Read)
      val isDeployer  = authzToken.user.checkRights(AuthorizationType.Deployer.Read)

      val filter = {
        if (isValidator && isDeployer) {
          List(TwoValidationStepsWorkflowServiceImpl.Validation.id, TwoValidationStepsWorkflowServiceImpl.Deployment.id)
        } else if (isValidator) List(TwoValidationStepsWorkflowServiceImpl.Validation.id)
        else if (isDeployer) List(TwoValidationStepsWorkflowServiceImpl.Deployment.id)
        else List()
      }

      NonEmptyList.fromList(filter) match {
        case None                 =>
          // Should never happen : a request from a user that doesn't have either rights will not be processed here
          PendingCountJson(None, None).succeed.toLiftResponseOne(params, schema, None)
        case Some(nonEmptyFilter) =>
          readWorkflow
            .getCountByState(nonEmptyFilter)
            .map(PendingCountJson.from)
            .chainError("Could not get pending change request count")
            .toLiftResponseOne(params, schema, None)
      }

    }

  }

  object ChangeRequestMainDetails extends LiftApiModule {

    override val schema: WorkflowInternalApi.ChangeRequestMainDetails.type = API.ChangeRequestMainDetails

    private def findAllNextSteps(nextStatus: Option[WorkflowNodeId]): Seq[WorkflowNodeId] = {

      /**
       * This function returns the full list of "next steps" that can be reached from a given workflow status.
       *
       * The supported states in change-validation and the "steps" between these states (either "next steps" or
       * "back steps") represent a finite state machine.
       *
       * A given status cannot appear in its own list of "next steps", i.e. it is not possible
       * to backtrack to a status that a given change request has already been in before.
       */
      @tailrec
      def findAllNextStepsAux(curStatus: Option[WorkflowNodeId], acc: Seq[WorkflowNodeId]): Seq[WorkflowNodeId] = {
        curStatus match {
          case Some(cur) =>
            val next = workflowLevelService.getWorkflowService().findNextStatus(cur)
            findAllNextStepsAux(next, acc :+ cur)
          case None      => acc
        }
      }

      findAllNextStepsAux(nextStatus, Seq.empty)
    }

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sid:        String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      implicit val qc: QueryContext = authzToken.qc

      (for {
        crId           <- sid.toIntOption
                            .notOptional(s"'${sid}' is not a valid change request id (need to be an integer)")
                            .map(ChangeRequestId(_))
        changeRequest  <- roChangeRequestRepository
                            .get(crId)
                            .chainError(s"Could not find ChangeRequest ${sid}")
                            .notOptional(s"Change request with id ${sid} does not exist.")
        status         <- readWorkflow
                            .getStateOfChangeRequest(crId)
                            .chainError(s"Could not find ChangeRequest ${sid} status")
        isMergeable     = commitRepository.isMergeable(changeRequest)
        simpleCrJson    = SimpleChangeRequestJson.from(changeRequest, status, isMergeable)
        workflowService = workflowLevelService.getWorkflowService()
        isPending       = workflowService.isPending(status)
        backStatus      = workflowService.findBackStatus(status)
        nextStatus      = workflowService.findNextStatus(status)
        allNextSteps    = findAllNextSteps(nextStatus)
        crEventLogs    <- changeRequestEventLogService.getChangeRequestHistory(changeRequest.id)
        wfEventLogs    <- workflowEventLogService.getChangeRequestHistory(changeRequest.id)
      } yield {
        ChangeRequestMainDetailsJson.from(
          changeRequest,
          simpleCrJson,
          isPending,
          wfEventLogs,
          crEventLogs,
          backStatus,
          allNextSteps
        )(using
          eventLogDetailsService
        )
      })
        .toLiftResponseOne(params, schema, Some(sid))

    }

  }

  object ChangeRequestChanges extends LiftApiModule {
    override val schema: WorkflowInternalApi.ChangeRequestChanges.type = API.ChangeRequestChanges

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

    private def getNodeGroupNames: IOResult[MapView[NodeGroupId, String]] = {
      for {
        library <- nodeGroupRepository.getFullGroupLibrary()
      } yield {
        library.allGroups.view.mapValues(_.nodeGroup.name)
      }
    }

    private def getDirectives: IOResult[MapView[DirectiveId, Directive]] = {
      for {
        library <- directiveRepository.getFullDirectiveLibrary()
      } yield {
        library.allDirectives.view.mapValues(_._2)
      }
    }

    private def getNodeNames(implicit qc: QueryContext): IOResult[MapView[NodeId, String]] = {
      for {
        library <- nodeFactRepository.getAll()
      } yield {
        library.mapValues(_.fqdn)
      }
    }

    private def withChangeRequestContext[T](
        sid:          String,
        params:       DefaultParams,
        schema:       EndpointSchema,
        actionDetail: String
    )(
        block:        (ChangeRequest, WorkflowNodeId, Map[DirectiveId, Technique]) => IOResult[T]
    ): IOResult[T] = {

      (for {
        crId          <- sid.toIntOption
                           .notOptional(s"'${sid}' is not a valid change request id (need to be an integer)")
                           .map(ChangeRequestId(_))
        changeRequest <- roChangeRequestRepository
                           .get(crId)
                           .chainError(s"Could not find ChangeRequest ${sid}")
                           .notOptional(s"Change request with id ${sid} does not exist.")
        status        <- readWorkflow
                           .getStateOfChangeRequest(crId)
                           .chainError(s"Could not find ChangeRequest ${sid} status")
        result        <- getDirectiveTechniques(changeRequest)
                           .flatMap(block(changeRequest, status, _))
      } yield {
        result
      }).chainError(s"Could not ${actionDetail} ChangeRequest ${sid}")

    }

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        sid:        String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      implicit val qc: QueryContext = authzToken.qc

      withChangeRequestContext(sid, params, schema, "find")((changeRequest, status, techniqueByDirective) => {
        for {
          nodeGroups   <- getNodeGroupNames
          directives   <- getDirectives
          nodes        <- getNodeNames
          groupLib     <- nodeGroupRepository.getFullGroupLibrary()
          allTargets    = groupLib.allTargets.view
          rootCategory <- ruleCategoryRepository.getRootCategory()
          changesJson  <- ChangeRequestChangesJson
                            .from(changeRequest)(using
                              techniqueByDirective,
                              diffService,
                              nodeGroups,
                              directives,
                              nodes,
                              allTargets,
                              ruleCategoryService,
                              rootCategory
                            )
                            .toIO
        } yield {
          changesJson
        }
      }).toLiftResponseOne(params, schema, Some(sid))

    }
  }

}
