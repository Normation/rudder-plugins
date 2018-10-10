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

package com.normation.plugins.usermanagement.api

import bootstrap.liftweb.FileUserDetailListProvider
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.ApiPath
import com.normation.rudder.rest.ApiVersion
import com.normation.rudder.rest.AuthzToken
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.GeneralApi
import com.normation.rudder.rest.RestExtractorService
import com.normation.rudder.rest.RestUtils
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.StartsAtVersion10
import com.normation.rudder.rest.ZeroParam
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.NoTypeHints



/*
 * This file contains the internal API used to discuss with the JS application.
 *
 * It gives the list of currently configured authentication backends.
 */
sealed trait UserManagementApi extends EndpointSchema with GeneralApi with SortIndex
object UserManagementApi extends ApiModuleProvider[UserManagementApi] {

  final case object GetUserInfo extends UserManagementApi with ZeroParam with StartsAtVersion10 {
    val z = zz
    val description    = "Get information about registered users in Rudder"
    val (action, path) = GET / "usermanagement" / "users"
  }

  /*
   * This one does not return the list of users so that it can allow script integration
   * but without revealing the actual list of users.
   */
  final case object ReloadUsersConf extends UserManagementApi with ZeroParam with StartsAtVersion10 {
    val z = zz
    val description    = "Reload (read again rudder-users.xml and process result) information about registered users in Rudder"
    val (action, path) = POST / "usermanagement" / "users" / "reload"
  }

  def endpoints = ca.mrvisser.sealerate.values[UserManagementApi].toList.sortBy( _.z )
}


class UserManagementApiImpl(
    restExtractorService: RestExtractorService
  , userService         : FileUserDetailListProvider
) extends LiftApiModuleProvider[UserManagementApi] {
  api =>

  implicit val formats = net.liftweb.json.Serialization.formats(NoTypeHints)

  def schemas = UserManagementApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    UserManagementApi.endpoints.map(e => e match {
        case UserManagementApi.GetUserInfo     => GetUserInfo
        case UserManagementApi.ReloadUsersConf => ReloadUsersConf
    }).toList
  }

  /*
   * Return a Json Object that list users with their authorizations
   */
  object GetUserInfo extends LiftApiModule0 {
    val schema = UserManagementApi.GetUserInfo
    val restExtractor = api.restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      import com.normation.plugins.usermanagement.Serialisation._

      RestUtils.toJsonResponse(None, userService.authConfig.toJson)(schema.name, params.prettify)
    }
  }

  object ReloadUsersConf extends LiftApiModule0 {
    val schema = UserManagementApi.ReloadUsersConf
    val restExtractor = api.restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      userService.reload() match {
        case Right(()) =>
          RestUtils.toJsonResponse(None, JString("done"))(schema.name, params.prettify)
        case Left(error) =>
          val err = "Error when trying to reload the list of users from 'rudder-users.xml' file: " + error.msg
          RestUtils.toJsonError(None, JString(err))(schema.name, params.prettify)
      }
    }
  }

}



