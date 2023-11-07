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

import com.normation.eventlog.EventActor
import com.normation.plugins.changevalidation.RoValidatedUserRepository
import com.normation.plugins.changevalidation.WorkflowUsers
import com.normation.plugins.changevalidation.WoValidatedUserRepository
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.DELETE
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.apidata.RestDataSerializer
import com.normation.rudder.rest._
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.RestUtils.toJsonError
import com.normation.rudder.rest.RestUtils.toJsonResponse
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.AuthorizationType

import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.DefaultFormats
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonAST.JArray
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JValue
import sourcecode.Line

sealed trait ValidatedUserApi extends EndpointSchema with GeneralApi with SortIndex
object ValidatedUserApi       extends ApiModuleProvider[ValidatedUserApi] {

  final case object ListUsers                   extends ValidatedUserApi with ZeroParam with StartsAtVersion3 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "List all users"
    val (action, path) = GET / "users"

    override def dataContainer: Option[String] = None
    override def authz: List[AuthorizationType] = List(AuthorizationType.Administration.Read)
  }
  final case object DeleteValidatedUsersDetails extends ValidatedUserApi with OneParam with StartsAtVersion3 with SortIndex  {
    val z              = implicitly[Line].value
    val description    = "Remove validated user"
    val (action, path) = DELETE / "validatedUsers" / "{username}"

    override def dataContainer: Option[String] = None
  }
  final case object SaveWorkflowUsers           extends ValidatedUserApi with ZeroParam with StartsAtVersion3 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "save list of workflow's users"
    val (action, path) = POST / "validatedUsers"

    override def dataContainer: Option[String] = None
  }

  def endpoints = ca.mrvisser.sealerate.values[ValidatedUserApi].toList.sortBy(_.z)
}

class ValidatedUserApiImpl(
    restExtractorService: RestExtractorService,
    readValidatedUser:    RoValidatedUserRepository,
    writeValidatedUser:   WoValidatedUserRepository,
    restDataSerializer:   RestDataSerializer
) extends LiftApiModuleProvider[ValidatedUserApi] {

  import com.normation.plugins.changevalidation.api.{ValidatedUserApi => API}

  override def schemas: ApiModuleProvider[ValidatedUserApi] = API

  def serializeEA(vu: EventActor): JValue = {
    "username" -> vu.name
  }

  def serializeWU(wu: WorkflowUsers): JValue = {
    (
      ("username"      -> wu.actor.name)
      ~ ("isValidated" -> wu.isValidated)
      ~ ("userExists"  -> wu.userExists)
    )
  }

  def extractEA(req: Req): Option[List[EventActor]] = {
    implicit val formats = DefaultFormats
    req.json match {
      case Full(content) =>
        content match {
          case JObject(json) =>
            json \\ "validatedUsers" match {
              case JObject(obj) =>
                val users = (obj \ "validatedUsers").extract[List[String]]
                Some(users.map(EventActor))
              case _            => None
            }
          case _             => None
        }
      case _             => None
    }
  }

  override def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints.map {
      case API.ListUsers                   => ListUsers
      case API.DeleteValidatedUsersDetails => DeleteValidatedUsersDetails
      case API.SaveWorkflowUsers           => SaveWorkflowUsers
    }
  }

  object ListUsers extends LiftApiModule0 {
    val schema        = API.ListUsers
    val restExtractor = restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action   = "listUsers"
      implicit val prettify = restExtractor.extractPrettify(req.params)
      readValidatedUser.getUsers() match {
        case Full(users) =>
          val result = JArray(users.map(serializeWU).toList)
          toJsonResponse(None, result)
        case eb: EmptyBox =>
          val fail = eb ?~ "Could not fetch ValidatedUser"
          toJsonError(None, fail.messageChain)
      }
    }
  }

  object DeleteValidatedUsersDetails extends LiftApiModule {
    val schema        = API.DeleteValidatedUsersDetails
    val restExtractor = restExtractorService

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      implicit val action   = "removeValidatedUser"
      implicit val prettify = restExtractor.extractPrettify(req.params)

      writeValidatedUser.deleteUser(EventActor(id)) match {
        case Full(exUser) => toJsonResponse(None, exUser.name)
        case eb: EmptyBox =>
          val fail = eb ?~ s"Could not fetch ValidatedUser"
          toJsonError(None, fail.messageChain)
      }
    }
  }

  object SaveWorkflowUsers extends LiftApiModule0 {
    val schema        = API.SaveWorkflowUsers
    val restExtractor = restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action   = "saveWorkflowUser"
      implicit val prettify = restExtractor.extractPrettify(req.params)

      extractEA(req) match {
        case Some(ea) =>
          writeValidatedUser.saveWorkflowUsers(ea) match {
            case Full(newWorkflow) =>
              val result = JArray(newWorkflow.map(serializeWU).toList)
              toJsonResponse(None, result)
            case eb: EmptyBox =>
              val fail = eb ?~ s"Could not fetch ValidatedUser"
              toJsonError(None, fail.messageChain)
          }
        case None     => toJsonError(None, s"Could not extract workflow user list from request")
      }
    }
  }
}
