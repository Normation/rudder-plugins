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
import com.normation.plugins.changevalidation.PendingCountJson
import com.normation.plugins.changevalidation.RoWorkflowRepository
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax.AddPath
import com.normation.rudder.rest.EndpointSchema.syntax.BuildPath
import com.normation.rudder.rest.EndpointSchema0
import com.normation.rudder.rest.InternalApi
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.StartsAtVersion21
import com.normation.rudder.rest.ZeroParam
import com.normation.rudder.rest.implicits.ToLiftResponseOne
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.users.UserService
import enumeratum.Enum
import enumeratum.EnumEntry
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line
import zio.syntax.ToZio

sealed trait WorkflowInternalApi extends EnumEntry with EndpointSchema with InternalApi with SortIndex
object WorkflowInternalApi       extends Enum[WorkflowInternalApi] with ApiModuleProvider[WorkflowInternalApi] {

  final case object PendingChangeRequestCount extends WorkflowInternalApi with ZeroParam with StartsAtVersion21 with SortIndex {
    val z              = implicitly[Line].value
    val (action, path) = GET / "changevalidation" / "workflow" / "pendingCountByStatus"
    val description    =
      "Get total count of change requests in each state, i.e. PendingValidation and PendingDeployment"

    override def dataContainer: Option[String]          = Some("workflow")
    override def authz:         List[AuthorizationType] = {
      List(AuthorizationType.Deployer.Read, AuthorizationType.Validator.Read)
    }
  }

  override def endpoints: List[WorkflowInternalApi]       = values.toList.sortBy(_.z)
  override def values:    IndexedSeq[WorkflowInternalApi] = findValues
}

class WorkflowInternalApiImpl(
    readWorkflow: RoWorkflowRepository,
    userService:  UserService
) extends LiftApiModuleProvider[WorkflowInternalApi] {
  import com.normation.plugins.changevalidation.api.WorkflowInternalApi as API

  override def schemas: ApiModuleProvider[WorkflowInternalApi] = API

  override def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints.map { case API.PendingChangeRequestCount => PendingChangeRequestCount }
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

      val user = userService.getCurrentUser

      val isValidator = user.checkRights(AuthorizationType.Validator.Read)
      val isDeployer  = user.checkRights(AuthorizationType.Deployer.Read)

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
}
