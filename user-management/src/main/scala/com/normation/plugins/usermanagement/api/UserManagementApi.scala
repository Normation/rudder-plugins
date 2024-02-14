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

import bootstrap.liftweb.AuthBackendProvidersManager
import bootstrap.liftweb.FileUserDetailListProvider
import bootstrap.liftweb.PasswordEncoder
import bootstrap.liftweb.ValidatedUserList
import com.normation.errors._
import com.normation.plugins.usermanagement.JsonAddedUser
import com.normation.plugins.usermanagement.JsonAuthConfig
import com.normation.plugins.usermanagement.JsonCoverage
import com.normation.plugins.usermanagement.JsonDeletedUser
import com.normation.plugins.usermanagement.JsonReloadResult
import com.normation.plugins.usermanagement.JsonRole
import com.normation.plugins.usermanagement.JsonRoleAuthorizations
import com.normation.plugins.usermanagement.JsonUpdatedUser
import com.normation.plugins.usermanagement.JsonUser
import com.normation.plugins.usermanagement.JsonUserFormData
import com.normation.plugins.usermanagement.Serialisation._
import com.normation.plugins.usermanagement.User
import com.normation.plugins.usermanagement.UserManagementService
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import com.normation.rudder.RudderRoles
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.DELETE
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.apidata.ZioJsonExtractor
import com.normation.rudder.rest._
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.implicits._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.RudderUserDetail
import com.normation.rudder.users.UserRepository
import com.softwaremill.quicklens._
import io.scalaland.chimney.dsl._
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line
import zio.ZIO
import zio.syntax._

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
    userService:           FileUserDetailListProvider,
    authProvider:          AuthBackendProvidersManager,
    userManagementService: UserManagementService
) extends LiftApiModuleProvider[UserManagementApi] {
  api =>

  override def schemas = UserManagementApi

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

  /*
   * Return a Json Object that list users with their authorizations
   */
  object GetUserInfo extends LiftApiModule0 {
    val schema = UserManagementApi.GetUserInfo
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      // This is just a compat hub done so that we can see all users. There will be problems
      (for {
        users    <- userRepo.getAll()
        allRoles <- RudderRoles.getAllRoles
      } yield {
        val file         = userService.authConfig
        val updatedUsers = users.map(u => {
          file.users.get(u.id) match {
            case None    =>
              (
                u.id,
                RudderUserDetail(RudderAccount.User(u.id, ""), u.status, Set(), ApiAuthorization.None)
              )
            case Some(x) => (x.getUsername, x)
          }
        })
        val authFile     = file.modify(_.users).setTo(updatedUsers.toMap)
        serialize(authFile, allRoles.values.toSet)(authProvider)
      }).chainError("Error when retrieving user list").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object GetRoles extends LiftApiModule0 {
    val schema = UserManagementApi.GetRoles
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        allRoles <- RudderRoles.getAllRoles
        roles     = allRoles.values.toList

        json = roles.map(role => {
                 val displayAuthz = role.rights.authorizationTypes.map(_.id).toList.sorted
                 val authz_all    = displayAuthz
                   .map(_.split("_").head)
                   .map(authz => if (displayAuthz.count(_.split("_").head == authz) == 3) s"${authz}_all" else authz)
                   .filter(_.contains("_"))
                   .distinct
                 val authz_type   = displayAuthz.filter(x => !authz_all.map(_.split("_").head).contains(x.split("_").head))
                 JsonRole(role.name, authz_type ++ authz_all)
               })

      } yield {
        json
      }).toLiftResponseOne(params, schema, _ => None)
    }
  }

  object ReloadUsersConf extends LiftApiModule0 {
    val schema = UserManagementApi.ReloadUsersConf

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        _ <- reload()
      } yield {
        JsonReloadResult.Done
      }).chainError("Could not reload user's configuration")
        .toLiftResponseOne(params, schema, _ => None)
    }
  }

  private def reload(): IOResult[Unit] = {
    userService.reloadPure().chainError("Error when trying to reload the list of users from 'rudder-users.xml' file")
  }

  object AddUser extends LiftApiModule0 {
    val schema = UserManagementApi.AddUser

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      (for {
        user  <- ZioJsonExtractor.parseJson[JsonUserFormData](req).toIO
        _     <- ZIO.when(userService.authConfig.users.keySet contains user.username) {
                   Inconsistency(s"User '${user.username}' already exists").fail
                 }
        added <- userManagementService.add(user.transformInto[User], user.isPreHashed)
        _     <- reload()

      } yield {
        added.transformInto[JsonAddedUser]
      }).chainError("Could not add user").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object DeleteUser extends LiftApiModule {
    val schema = UserManagementApi.DeleteUser

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      (for {
        _ <- userManagementService.remove(id, authzToken.actor)
        _ <- reload()
      } yield {
        id.transformInto[JsonDeletedUser]
      }).chainError(s"Could not delete user ${id}").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object UpdateUserInfos extends LiftApiModule {
    val schema = UserManagementApi.UpdateUserInfos

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        user           <- ZioJsonExtractor.parseJson[JsonUserFormData](req).toIO
        checkExistence <- if (!(userService.authConfig.users.keySet contains id)) {
                            // we may have users that where added by OIDC, and still want to add them in file
                            userRepo.get(id).flatMap {
                              case Some(u) => userManagementService.add(User(u.id, "", Set()), user.isPreHashed)
                              case None    => Inconsistency(s"'$id' does not exists").fail
                            }
                          } else {
                            userManagementService.update(id, user.transformInto[User], user.isPreHashed)
                          }
        _              <- reload()
      } yield {
        user.transformInto[User].transformInto[JsonUpdatedUser]
      }).chainError(s"Could not update user '$id'").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object RoleCoverage extends LiftApiModule {
    val schema = UserManagementApi.RoleCoverage

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        data          <- ZioJsonExtractor.parseJson[JsonRoleAuthorizations](req).toIO
        parsed        <- RudderRoles.parseRoles(data.permissions)
        coverage      <- UserManagementService
                           .computeRoleCoverage(
                             parsed.toSet,
                             data.authz.flatMap(a => AuthorizationType.parseRight(a).getOrElse(Set())).toSet ++ Role.ua
                           )
                           .notOptional("Could not compute role's coverage")
        roleAndCustoms = coverage.partitionMap {
                           case c: Custom => Right(c)
                           case r => Left(r)
                         }
      } yield {
        roleAndCustoms.transformInto[JsonCoverage]
      }).chainError(s"Could not get role's coverage user from request").toLiftResponseOne(params, schema, _ => None)
    }
  }

  def serialize(auth: ValidatedUserList, allRoles: Set[Role])(authProviderManager: AuthBackendProvidersManager) = {
    val encoder: String = PassEncoderToString(auth)
    val authBackendsProvider = authProviderManager.getConfiguredProviders().map(_.name).toSet

    // for now, we can only guess if the role list can be extended/overridden (and only guess for the worse).
    // The correct solution is to get that from rudder, but it will be done along with other enhancement about
    // user / roles management.
    // Also, until then, we need to update that test is other backend get that possibility
    val roleListOverride = if (authBackendsProvider.contains("oidc") || authBackendsProvider.contains("oauth2")) {
      "override" // should be a type provided by rudder core
    } else {
      "none"
    }

    // NoRights and AnyRights directly map to known user permissions. AnyRights takes precedence over NoRights.
    val transformUser: RudderUserDetail => JsonUser = {
      case u if u.authz.authorizationTypes.contains(AuthorizationType.AnyRights)                                      =>
        JsonUser.anyRights(u.getUsername)
      case u if u.authz.authorizationTypes.isEmpty || u.authz.authorizationTypes.contains(AuthorizationType.NoRights) =>
        JsonUser.noRights(u.getUsername)
      case u                                                                                                          =>
        JsonUser.fromUser(u)(allRoles)
    }

    val jUser = auth.users.values.toList.sortBy(_.getUsername).map(transformUser(_))
    val json  = JsonAuthConfig(encoder, roleListOverride, authBackendsProvider, jUser)
    json
  }

  private def PassEncoderToString(auth: ValidatedUserList): String = {
    auth.encoder match {
      case PasswordEncoder.MD5    => "MD5"
      case PasswordEncoder.SHA1   => "SHA-1"
      case PasswordEncoder.SHA256 => "SHA-256"
      case PasswordEncoder.SHA512 => "SHA-512"
      case PasswordEncoder.BCRYPT => "BCRYPT"
      case _                      => "plain text"
    }
  }
}
