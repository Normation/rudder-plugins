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

package com.normation.plugins.changevalidation.api

import com.normation.plugins.changevalidation.*
import com.normation.plugins.changevalidation.RudderJsonMapping.*
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.repository.RoNodeGroupRepository
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax.*
import com.normation.rudder.rest.InternalApi
import com.normation.rudder.rest.RudderJsonRequest.*
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.StartsAtVersion10
import com.normation.rudder.rest.ZeroParam
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.rest.syntax.*
import enumeratum.*
import java.nio.charset.StandardCharsets
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line
import zio.ZIO

/*
 * This file contains the internal API used to discuss with the JS application.
 * It gives the list of available groups with the one currently selected for
 * supervision, and is able to save an updated list.
 */

sealed trait SupervisedTargetsApi extends EnumEntry with EndpointSchema with InternalApi with SortIndex
object SupervisedTargetsApi       extends Enum[SupervisedTargetsApi] with ApiModuleProvider[SupervisedTargetsApi] {
  val zz = 11
  case object GetAllTargets           extends SupervisedTargetsApi with ZeroParam with StartsAtVersion10 {
    val z: Int = implicitly[Line].value
    val description    = "Get all available node groups with their role in change request validation"
    val (action, path) = GET / "changevalidation" / "supervised" / "targets"

    override def dataContainer: Option[String]          = None
    override def authz:         List[AuthorizationType] = List(AuthorizationType.Administration.Read)
  }
  case object UpdateSupervisedTargets extends SupervisedTargetsApi with ZeroParam with StartsAtVersion10 {
    val z: Int = implicitly[Line].value
    val description    = "Save the updated list of groups"
    val (action, path) = POST / "changevalidation" / "supervised" / "targets"

    override def dataContainer: Option[String]          = None
    val authz:                  List[AuthorizationType] = AuthorizationType.Administration.Write :: Nil
  }

  def endpoints: List[SupervisedTargetsApi] = values.toList.sortBy(_.z)
  def values = findValues
}

class SupervisedTargetsApiImpl(
    unsupervisedTargetsRepos: UnsupervisedTargetsRepository,
    nodeGroupRepository:      RoNodeGroupRepository
) extends LiftApiModuleProvider[SupervisedTargetsApi] {

  override def schemas: ApiModuleProvider[SupervisedTargetsApi] = SupervisedTargetsApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    SupervisedTargetsApi.endpoints.map {
      case SupervisedTargetsApi.GetAllTargets           => GetAllTargets
      case SupervisedTargetsApi.UpdateSupervisedTargets => UpdateSupervisedTargets
    }
  }

  /*
   * Return a Json Object that looks like:
   *
   * { "name": "root category"
   * , "targets": [
   *     {"id": "group:xxxx", "name": "Some name chosen by user", "description": "", "supervised":true}
   *   , {"id": "group:xxxx", "name": "Some other group", "description": "", "supervised":false}
   *   , ...
   *   ]
   * , "categories" : [
   *      { "name": "sub category"
   *      , "target": [ {...}, {...}, ...]
   *      , "categories": ...
   *      }
   *   ]
   * }
   */
  object GetAllTargets extends LiftApiModule0 {
    val schema: SupervisedTargetsApi.GetAllTargets.type = SupervisedTargetsApi.GetAllTargets

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      import com.normation.plugins.changevalidation.RudderJsonMapping.*

      (for {
        groups       <- nodeGroupRepository.getFullGroupLibrary()
        unsupervised <- unsupervisedTargetsRepos.load()
        supervised    = UnsupervisedTargetsRepository.invertTargets(unsupervised, groups)
        jsonRootCat   = groups.toJson(supervised)
      } yield {
        jsonRootCat
      })
        .chainError("Error when trying to get group information")
        .onError(err => ZIO.foreach(err.failureOption.map(_.fullMsg))(ChangeValidationLoggerPure.error(_)))
        .toLiftResponseOne(params, schema, None)
    }
  }

  /*
   * We get from the UI a list of target we want to supervise.
   * This is the simple list of target name, nothing else, in a
   * JSON: {"supervised": [ "target1", "groupid:XXXX", etc ] }
   * It returns an empty data set.
   */
  object UpdateSupervisedTargets extends LiftApiModule0 {

    // from the JSON, etract the list of target name to supervise

    val schema: SupervisedTargetsApi.UpdateSupervisedTargets.type = SupervisedTargetsApi.UpdateSupervisedTargets

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        targets <-
          req
            .fromJson[SupervisedSimpleTargets]
            .toIO
            .chainError(
              s"Error when trying to parse JSON content ${new String(req.body.getOrElse(Array[Byte]()), StandardCharsets.UTF_8)} as a set of rule target."
            )
        groups  <- nodeGroupRepository.getFullGroupLibrary()
        saved   <- unsupervisedTargetsRepos.save(UnsupervisedTargetsRepository.invertTargets(targets.supervised, groups))
        res      = "Set of target needing validation has been updated"
      } yield res)
        .chainError("An error occurred when trying to save the set of rule target which needs validation")
        .onError(err => ZIO.foreach(err.failureOption.map(_.fullMsg))(ChangeValidationLoggerPure.error(_)))
        .toLiftResponseOne(params, schema, None)
    }
  }
}
