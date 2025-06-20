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

package com.normation.plugins.authbackends.api

import com.normation.errors.IOResult
import com.normation.plugins.authbackends.AuthBackendsRepository
import com.normation.plugins.authbackends.JsonSerialization
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax.*
import com.normation.rudder.rest.InternalApi
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.StartsAtVersion10
import com.normation.rudder.rest.ZeroParam
import com.normation.rudder.rest.implicits.*
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import enumeratum.*
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line

/*
 * This file contains the internal API used to discuss with the JS application.
 *
 * It gives the list of currently configured authentication backends.
 */
sealed trait AuthBackendsApi extends EnumEntry with EndpointSchema with InternalApi with SortIndex

object AuthBackendsApi extends Enum[AuthBackendsApi] with ApiModuleProvider[AuthBackendsApi] {

  case object GetAuthenticationInformation extends AuthBackendsApi with ZeroParam with StartsAtVersion10 {
    val z: Int = implicitly[Line].value
    val description    = "Get information about current authentication configuration"
    val (action, path) = GET / "authbackends" / "current-configuration"

    override def authz:         List[AuthorizationType] = List(AuthorizationType.Administration.Read)
    override def dataContainer: Option[String]          = None
  }

  def endpoints: List[AuthBackendsApi] = values.toList.sortBy(_.z)
  def values = findValues
}

class AuthBackendsApiImpl(
    authRepo: AuthBackendsRepository
) extends LiftApiModuleProvider[AuthBackendsApi] {
  api =>

  def schemas: ApiModuleProvider[AuthBackendsApi] = AuthBackendsApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    AuthBackendsApi.endpoints.map { case AuthBackendsApi.GetAuthenticationInformation => GetAuthenticationInformation }
  }

  /*
   * Return a Json Object that list available backend,
   * their state of configuration, and what are the current
   * enabled ones.
   */
  object GetAuthenticationInformation extends LiftApiModule0 {
    val schema: AuthBackendsApi.GetAuthenticationInformation.type = AuthBackendsApi.GetAuthenticationInformation

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      import com.normation.plugins.authbackends.JsonSerialization.*

      IOResult
        .attempt("Error when trying to get group information")(
          authRepo.getConfigOption()
        )
        .toLiftResponseOne(params, schema, None)
    }
  }

}
