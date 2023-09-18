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
import com.normation.box._
import com.normation.errors._
import com.normation.plugins.usermanagement.Serialization
import com.normation.plugins.usermanagement.User
import com.normation.plugins.usermanagement.UserManagementService
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Role
import com.normation.rudder.RudderRoles
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.DELETE
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.repository.json.DataExtractor.CompleteJson
import com.normation.rudder.rest._
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.RudderUserDetail
import com.normation.rudder.users.UserRepository
import com.normation.zio._
import com.softwaremill.quicklens._
import net.liftweb.common.Box
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.Formats
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JString
import net.liftweb.json.JValue
import net.liftweb.json.NoTypeHints
import sourcecode.Line

/*
 * This file contains the internal API used to discuss with the JS application.
 *
 * It gives the list of currently configured authentication backends.
 */
sealed trait UserManagementApi extends EndpointSchema with GeneralApi with SortIndex
object UserManagementApi       extends ApiModuleProvider[UserManagementApi] {

  final case object GetUserInfo extends UserManagementApi with ZeroParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Get information about registered users in Rudder"
    val (action, path) = GET / "usermanagement" / "users"

    override def authz:         List[AuthorizationType] = List(AuthorizationType.Administration.Read)
    override def dataContainer: Option[String]          = None
  }

  final case object GetRoles extends UserManagementApi with ZeroParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Get roles and their authorizations"
    val (action, path) = GET / "usermanagement" / "roles"

    override def authz:         List[AuthorizationType] = List(AuthorizationType.Administration.Read)
    override def dataContainer: Option[String]          = None
  }

  /*
   * This one does not return the list of users so that it can allow script integration
   * but without revealing the actual list of users.
   */
  final case object ReloadUsersConf extends UserManagementApi with ZeroParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Reload (read again rudder-users.xml and process result) information about registered users in Rudder"
    val (action, path) = POST / "usermanagement" / "users" / "reload"

    override def dataContainer: Option[String] = None
  }

  final case object DeleteUser extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Delete a user from the system"
    val (action, path) = DELETE / "usermanagement" / "{username}"

    override def dataContainer: Option[String] = None
  }

  final case object AddUser extends UserManagementApi with ZeroParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Add a user with his information and privileges"
    val (action, path) = POST / "usermanagement"

    override def dataContainer: Option[String] = None
  }

  final case object UpdateUserInfos extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Update user's infos"
    val (action, path) = POST / "usermanagement" / "update" / "{username}"

    override def dataContainer: Option[String] = None
  }

  final case object RoleCoverage extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Get the coverage of roles over rights"
    val (action, path) = POST / "usermanagement" / "coverage" / "{username}"

    override def dataContainer: Option[String] = None
  }

  def endpoints = ca.mrvisser.sealerate.values[UserManagementApi].toList.sortBy(_.z)
}

class UserManagementApiImpl(
    userRepo:              UserRepository,
    restExtractorService:  RestExtractorService,
    userService:           FileUserDetailListProvider,
    userManagementService: UserManagementService
) extends LiftApiModuleProvider[UserManagementApi] {
  api =>

  implicit val formats: Formats = net.liftweb.json.Serialization.formats(NoTypeHints)

  override def schemas = UserManagementApi

  def extractUser(json: JValue): Box[User] = {
    for {
      username    <- CompleteJson.extractJsonString(json, "username")
      password    <- CompleteJson.extractJsonString(json, "password")
      permissions <- CompleteJson.extractJsonListString(json, "permissions")
    } yield {
      User(username, password, permissions.toSet)
    }
  }

  def extractIsHashed(json: JValue): Box[Boolean] = {
    CompleteJson.extractJsonBoolean(json, "isPreHashed")
  }

  override def getLiftEndpoints(): List[LiftApiModule] = {
    UserManagementApi.endpoints.map {
      case UserManagementApi.GetUserInfo     => GetUserInfo
      case UserManagementApi.ReloadUsersConf => ReloadUsersConf
      case UserManagementApi.AddUser         => AddUser
      case UserManagementApi.DeleteUser      => DeleteUser
      case UserManagementApi.UpdateUserInfos => UpdateUserInfos
      case UserManagementApi.RoleCoverage    => RoleCoverage
      case UserManagementApi.GetRoles        => GetRoles
    }.toList
  }

  def response(function: Box[JValue], req: Req, errorMessage: String, id: Option[String], dataName: String)(implicit
      action:            String
  ): LiftResponse = {
    RestUtils.response(restExtractorService, dataName, id)(function, req, errorMessage)
  }

  /*
   * Return a Json Object that list users with their authorizations
   */
  object GetUserInfo extends LiftApiModule0 {
    val schema        = UserManagementApi.GetUserInfo
    val restExtractor = api.restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      import com.normation.plugins.usermanagement.Serialisation._

      // This is just a compat hub done so that we can see all users. There will be problems
      (for {
        users <- userRepo.getAll()
      } yield {
        val file         = userService.authConfig
        val updatedUsers = users.map(u => {
          file.users.get(u.id) match {
            case None    => (u.id, RudderUserDetail(RudderAccount.User(u.id, ""), u.status, Set(), ApiAuthorization.None))
            case Some(x) => (x.getUsername, x)
          }
        })
        file.modify(_.users).setTo(updatedUsers.toMap)

      }).either.runNow match {
        case Left(err)       =>
          RestUtils.toJsonError(None, JString(s"Error when retrieving user list: ${err.fullMsg}"))(schema.name, params.prettify)
        case Right(authFile) =>
          RestUtils.toJsonResponse(None, authFile.toJson)(schema.name, params.prettify)
      }
    }
  }

  object GetRoles extends LiftApiModule0 {
    val schema        = UserManagementApi.GetRoles
    val restExtractor = api.restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val allRoleAndAuthz: Map[String, List[String]] = RudderRoles.getAllRoles.runNow.values
        .map(role => role.name -> role.rights.authorizationTypes.map(_.id).toList.sorted)
        .map {
          case (k, v) => {
            val authz_all  = v
              .map(_.split("_").head)
              .map(authz => if (v.count(_.split("_").head == authz) == 3) s"${authz}_all" else authz)
              .filter(_.contains("_"))
              .distinct
            val authz_type = v.filter(x => !authz_all.map(_.split("_").head).contains(x.split("_").head))
            k -> (authz_type ++ authz_all)
          }
        }
        .toMap
      RestUtils.toJsonResponse(None, Serialization.serializeRoleInfo(allRoleAndAuthz))(schema.name, params.prettify)
    }
  }

  object ReloadUsersConf extends LiftApiModule0 {
    val schema        = UserManagementApi.ReloadUsersConf
    val restExtractor = api.restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "reloadUserConf"

      val value: Box[JValue] = for {
        response <- reload().toBox
      } yield {
        "status" -> "Done"
      }
      response(value, req, "Could not reload user's configuration", None, "reload")
    }
  }

  private def reload(): IOResult[Unit] = {
    userService.reloadPure().chainError("Error when trying to reload the list of users from 'rudder-users.xml' file")
  }

  object AddUser extends LiftApiModule0 {
    val schema        = UserManagementApi.AddUser
    val restExtractor = api.restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "addUser"

      val value: Box[JValue] = for {
        json           <- req.json ?~! "No JSON data sent"
        user           <- extractUser(json)
        isPreHashed    <- extractIsHashed(json)
        checkExistence <- if (userService.authConfig.users.keySet contains user.username)
                            Failure(s"User '${user.username}' already exists")
                          else Full("ok")
        added          <- userManagementService.add(user, isPreHashed).toBox
        _              <- reload().toBox

      } yield {
        Serialization.serializeUser(added)
      }
      response(value, req, "Could not add user", None, "addedUser")
    }
  }

  object DeleteUser extends LiftApiModule {
    val schema        = UserManagementApi.DeleteUser
    val restExtractor = api.restExtractorService

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      implicit val action = "deleteUser"

      val value: Box[JValue] = for {
        _ <- userManagementService.remove(id, authzToken.actor).toBox
        _ <- reload().toBox
      } yield {
        "username" -> id
      }
      response(value, req, s"Could not delete user ${id}", None, "deletedUser")
    }
  }

  object UpdateUserInfos extends LiftApiModule {
    val schema        = UserManagementApi.UpdateUserInfos
    val restExtractor = api.restExtractorService

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      implicit val action = "updateInfosUser"

      val value: Box[JValue] = for {
        json           <- req.json ?~! "No JSON data sent"
        user           <- extractUser(json)
        isPreHashed    <- extractIsHashed(json)
        checkExistence <- if (!(userService.authConfig.users.keySet contains id)) {
                            // we may have users that where added by OIDC, and still want to add them in file
                            userRepo.get(id).toBox.flatMap {
                              case Some(u) => userManagementService.add(User(u.id, "", Set()), isPreHashed).toBox
                              case None    => Failure(s"'$id' does not exists")
                            }
                          } else {
                            userManagementService.update(id, user, isPreHashed).toBox
                          }
        _              <- reload().toBox
      } yield {
        Serialization.serializeUser(user)
      }
      response(value, req, s"Could not update user '$id''", None, "updatedUser")
    }
  }

  object RoleCoverage extends LiftApiModule {
    val schema        = UserManagementApi.RoleCoverage
    val restExtractor = api.restExtractorService

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      implicit val action = "rolesCoverageOnRights"

      val value: Box[JValue] = for {
        permissions <- restExtractorService.extractList("permissions")(req)(json => Full(json))
        authzs      <- restExtractorService.extractList("authz")(req)(json => Full(json))
        parsed      <- RudderRoles.parseRoles(permissions).toBox
        coverage    <- UserManagementService.computeRoleCoverage(
                         parsed.toSet,
                         authzs.flatMap(a => AuthorizationType.parseRight(a).getOrElse(Set())).toSet ++ Role.ua
                       )
      } yield {
        Serialization.serializeRole(coverage)
      }
      response(value, req, s"Could not get role's coverage user from request", None, "coverage")
    }
  }
}
