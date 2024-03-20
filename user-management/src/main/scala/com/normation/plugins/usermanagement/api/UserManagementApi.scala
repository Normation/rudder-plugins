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
import bootstrap.liftweb.DefaultAuthBackendProvider
import bootstrap.liftweb.FileUserDetailListProvider
import bootstrap.liftweb.PasswordEncoder
import bootstrap.liftweb.ProviderRoleExtension
import com.normation.errors.*
import com.normation.plugins.usermanagement.JsonAddedUser
import com.normation.plugins.usermanagement.JsonAuthConfig
import com.normation.plugins.usermanagement.JsonCoverage
import com.normation.plugins.usermanagement.JsonDeletedUser
import com.normation.plugins.usermanagement.JsonProviderInfo
import com.normation.plugins.usermanagement.JsonProviderProperty
import com.normation.plugins.usermanagement.JsonReloadResult
import com.normation.plugins.usermanagement.JsonRights
import com.normation.plugins.usermanagement.JsonRole
import com.normation.plugins.usermanagement.JsonRoleAuthorizations
import com.normation.plugins.usermanagement.JsonRoles
import com.normation.plugins.usermanagement.JsonStatus
import com.normation.plugins.usermanagement.JsonUpdatedUser
import com.normation.plugins.usermanagement.JsonUpdatedUserInfo
import com.normation.plugins.usermanagement.JsonUser
import com.normation.plugins.usermanagement.JsonUserFormData
import com.normation.plugins.usermanagement.Serialisation.*
import com.normation.plugins.usermanagement.UpdateUserFile
import com.normation.plugins.usermanagement.UpdateUserInfo
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
import com.normation.rudder.api.HttpAction.PUT
import com.normation.rudder.apidata.ZioJsonExtractor
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.rest.*
import com.normation.rudder.rest.EndpointSchema.syntax.*
import com.normation.rudder.rest.implicits.*
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.users.EventTrace
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.RudderUserDetail
import com.normation.rudder.users.UserInfo
import com.normation.rudder.users.UserRepository
import com.normation.rudder.users.UserSession
import com.normation.rudder.users.UserStatus
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import org.joda.time.DateTime
import sourcecode.Line
import zio.ZIO
import zio.syntax.*

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

  final case object UpdateUser extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Update user's administration fields"
    val (action, path) = POST / "usermanagement" / "update" / "{username}"

    override def dataContainer: Option[String] = None
  }

  final case object UpdateUserInfo extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Update user's information"
    val (action, path) = POST / "usermanagement" / "update" / "info" / "{username}"

    override def dataContainer: Option[String] = None
  }

  final case object ActivateUser extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Activate a user"
    val (action, path) = PUT / "usermanagement" / "status" / "activate" / "{username}"

    override def dataContainer: Option[String] = None
  }

  final case object DisableUser extends UserManagementApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Disable a user"
    val (action, path) = PUT / "usermanagement" / "status" / "disable" / "{username}"

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
    userManagementService: UserManagementService,
    roleApiMapping:        RoleApiMapping
) extends LiftApiModuleProvider[UserManagementApi] {
  api =>

  override def schemas: ApiModuleProvider[UserManagementApi] = UserManagementApi

  override def getLiftEndpoints(): List[LiftApiModule] = {
    UserManagementApi.endpoints.map {
      case UserManagementApi.GetUserInfo     => GetUserInfo
      case UserManagementApi.ReloadUsersConf => ReloadUsersConf
      case UserManagementApi.AddUser         => AddUser
      case UserManagementApi.DeleteUser      => DeleteUser
      case UserManagementApi.UpdateUser      => UpdateUser
      case UserManagementApi.UpdateUserInfo  => UpdateUserInfo
      case UserManagementApi.ActivateUser    => ActivateUser
      case UserManagementApi.DisableUser     => DisableUser
      case UserManagementApi.RoleCoverage    => RoleCoverage
      case UserManagementApi.GetRoles        => GetRoles
    }.toList
  }

  /*
   * Return a Json Object that list users with their authorizations
   */
  object GetUserInfo extends LiftApiModule0 {
    override val schema: UserManagementApi.GetUserInfo.type = UserManagementApi.GetUserInfo

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      (for {
        users     <- userRepo.getAll()
        allRoles  <- RudderRoles.getAllRoles
        file       = userService.authConfig
        roles      = allRoles.values.toSet
        jsonUsers <-
          ZIO
            .foreach(users)(u => {
              implicit val currentRoles: Set[Role] = roles
              // we take last session to get last known roles and authz of the user
              // we need to merge at the level of JsonUser because last session roles are just String
              userRepo.getLastPreviousLogin(u.id, false).map { lastSession =>
                // depending on provider property configuration, we should merge or override roles
                val providerProperties        = authProvider.getProviderProperties()
                val mainProviderRoleExtension = providerProperties.get(u.managedBy).map(_.providerRoleExtension)

                val defaultUser            =
                  RudderUserDetail(RudderAccount.User(u.id, ""), u.status, Set(), ApiAuthorization.None, NodeSecurityContext.None)
                val userWithoutPermissions = transformUser(
                  defaultUser,
                  u,
                  Map(u.managedBy -> JsonProviderInfo.fromUser(defaultUser, u.managedBy)),
                  lastSession.map(_.creationDate)
                )

                file.users.get(u.id) match {
                  case None    => {
                    // we still need to consider one role extension case : if provider cannot define roles, user cannot have any role
                    mainProviderRoleExtension match {
                      case Some(ProviderRoleExtension.None) => userWithoutPermissions
                      case _                                => transformProvidedUser(u, lastSession)
                    }
                  }
                  case Some(x) => {
                    // we need to update the status to the latest one from database
                    val currentUserDetails = x.copy(status = u.status)

                    // since file definition does not depend on session use the file user as base for file-managed users
                    val fileProviderInfo = JsonProviderInfo.fromUser(x, DefaultAuthBackendProvider.FILE)

                    if (u.managedBy == DefaultAuthBackendProvider.FILE) {
                      transformUser(
                        currentUserDetails,
                        u,
                        Map(fileProviderInfo.provider -> fileProviderInfo),
                        lastSession.map(_.creationDate)
                      ).withRoleCoverage(currentUserDetails)
                    } else {
                      // we need to merge the two users, the one from the file and the one from the session
                      mainProviderRoleExtension match {
                        case Some(ProviderRoleExtension.WithOverride) =>
                          // Do not recompute roles nor roles coverage, because file roles are overridden by provider roles
                          transformProvidedUser(u, lastSession).addProviderInfo(fileProviderInfo)
                        case Some(ProviderRoleExtension.NoOverride)   =>
                          // Merge the previous session roles with the file roles and recompute role coverage over the merge result
                          transformProvidedUser(u, lastSession).merge(fileProviderInfo).withRoleCoverage(currentUserDetails)
                        case Some(ProviderRoleExtension.None)         =>
                          // Ignore the session roles which may have previously been saved with another role extension mode
                          userWithoutPermissions.merge(fileProviderInfo).withRoleCoverage(currentUserDetails)
                        case None                                     =>
                          // Provider no longer known, fallback to file provider
                          transformUser(
                            currentUserDetails,
                            u,
                            Map(fileProviderInfo.provider -> fileProviderInfo),
                            lastSession.map(_.creationDate)
                          ).withRoleCoverage(currentUserDetails)
                      }
                    }
                  }
                }
              }
            })
      } yield {
        serialize(jsonUsers.sortBy(_.id))(file.encoder, authProvider)
      }).chainError("Error when retrieving user list").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object GetRoles extends LiftApiModule0 {
    val schema: UserManagementApi.GetRoles.type = UserManagementApi.GetRoles

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
    override val schema: UserManagementApi.ReloadUsersConf.type = UserManagementApi.ReloadUsersConf

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
    val schema: UserManagementApi.AddUser.type = UserManagementApi.AddUser

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {

      (for {
        user <- ZioJsonExtractor.parseJson[JsonUserFormData](req).toIO
        _    <- ZIO.when(userService.authConfig.users.keySet contains user.username) {
                  Inconsistency(s"User '${user.username}' already exists").fail
                }
        _    <-
          userManagementService
            .add(user.transformInto[User], user.isPreHashed)
        _    <- userManagementService.updateInfo(user.username, user.transformInto[UpdateUserInfo])
      } yield {
        user.transformInto[JsonAddedUser]
      }).chainError("Could not add user").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object DeleteUser extends LiftApiModule {
    val schema: UserManagementApi.DeleteUser.type = UserManagementApi.DeleteUser

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {

      (for {
        _ <- userManagementService.remove(id, authzToken.qc.actor, "User deleted by user management API")
      } yield {
        id.transformInto[JsonDeletedUser]
      }).chainError(s"Could not delete user ${id}").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object UpdateUser extends LiftApiModule {
    val schema: UserManagementApi.UpdateUser.type = UserManagementApi.UpdateUser

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        u        <- ZioJsonExtractor.parseJson[JsonUserFormData](req).toIO // We ignore the "user info" part of the request
        user      = u.copy(permissions = u.permissions.filter(_ != AuthorizationType.NoRights.id))
        allRoles <- RudderRoles.getAllRoles
        _        <-
          userManagementService.update(id, user.transformInto[UpdateUserFile], user.isPreHashed)(
            allRoles
          )
      } yield {
        user.transformInto[User].transformInto[JsonUpdatedUser]
      }).chainError(s"Could not update user '${id}'").toLiftResponseOne(params, schema, _ => None)
    }
  }

  object UpdateUserInfo extends LiftApiModule {
    val schema: UserManagementApi.UpdateUserInfo.type = UserManagementApi.UpdateUserInfo

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        u <- ZioJsonExtractor.parseJson[UpdateUserInfo](req).toIO
        _ <- userManagementService.updateInfo(id, u)
      } yield {
        u.transformInto[JsonUpdatedUserInfo]
      }).chainError(s"Could not update user '${id}' information").toLiftResponseOne(params, schema, _ => Some(id))
    }
  }

  object ActivateUser extends LiftApiModule {
    val schema: UserManagementApi.ActivateUser.type = UserManagementApi.ActivateUser

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        user       <- userRepo.get(id).notOptional(s"User '$id' does not exist therefore cannot be activated")
        jsonStatus <- user.status match {
                        case UserStatus.Active   => JsonStatus(UserStatus.Active).succeed
                        case UserStatus.Disabled => {
                          val eventTrace = EventTrace(
                            authzToken.qc.actor,
                            DateTime.now,
                            "User current disabled status set to 'active' by user management API"
                          )
                          userRepo.setActive(List(user.id), eventTrace).as(JsonStatus(UserStatus.Active))
                        }
                        case UserStatus.Deleted  =>
                          Inconsistency(s"User '$id' cannot be activated because the user is currently deleted").fail
                      }
      } yield {
        jsonStatus
      }).chainError(s"Could not activate user '$id'").toLiftResponseOne(params, schema, _ => Some(id))
    }
  }

  object DisableUser extends LiftApiModule {
    val schema: UserManagementApi.DisableUser.type = UserManagementApi.DisableUser

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        id:         String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      (for {
        user       <- userRepo.get(id).notOptional(s"User '$id' does not exist therefore cannot be disabled")
        jsonStatus <- user.status match {
                        case UserStatus.Disabled => JsonStatus(UserStatus.Disabled).succeed
                        case UserStatus.Active   => {
                          val eventTrace = EventTrace(
                            authzToken.qc.actor,
                            DateTime.now,
                            "User current active status set to 'disabled' by user management API"
                          )
                          userRepo.disable(List(user.id), None, List.empty, eventTrace).as(JsonStatus(UserStatus.Disabled))
                        }
                        case UserStatus.Deleted  =>
                          Inconsistency(s"User '$id' cannot be disabled because the user is currently deleted").fail
                      }
      } yield {
        jsonStatus
      }).chainError(s"Could not disable user '$id'").toLiftResponseOne(params, schema, _ => Some(id))
    }
  }

  object RoleCoverage extends LiftApiModule {
    val schema: UserManagementApi.RoleCoverage.type = UserManagementApi.RoleCoverage

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

  def serialize(
      users: List[JsonUser]
  )(passwordEncoder: PasswordEncoder.Rudder, authProviderManager: AuthBackendProvidersManager): JsonAuthConfig = {
    val encoder: String = PassEncoderToString(passwordEncoder)
    val providerProperties   = authProviderManager.getProviderProperties()
    val authBackendsProvider = authProviderManager.getConfiguredProviders().map(_.name).toSet

    // Aggregate all provider properties, if any provider can override user roles then it means there is a global override
    val roleListOverride    = providerProperties.values.max.providerRoleExtension
    val providersProperties = providerProperties.view.mapValues(_.transformInto[JsonProviderProperty]).toMap

    JsonAuthConfig(encoder, roleListOverride, authBackendsProvider, providersProperties, users)
  }

  private def transformUser(
      u:             RudderUserDetail,
      info:          UserInfo,
      providersInfo: Map[String, JsonProviderInfo],
      lastLogin:     Option[DateTime]
  ): JsonUser = {
    // NoRights and AnyRights directly map to known user permissions. AnyRights takes precedence over NoRights.
    if (u.authz.authorizationTypes.contains(AuthorizationType.AnyRights)) {
      JsonUser.anyRights(u.getUsername, info.name, info.email, info.otherInfo, u.status, providersInfo, lastLogin)
    } else if (u.authz.authorizationTypes.isEmpty || u.authz.authorizationTypes.contains(AuthorizationType.NoRights)) {
      JsonUser.noRights(u.getUsername, info.name, info.email, info.otherInfo, u.status, providersInfo, lastLogin)
    } else {
      JsonUser(u.getUsername, info.name, info.email, info.otherInfo, u.status, providersInfo, lastLogin)
    }
  }

  implicit def transformDbUserToJsonUser(implicit userInfo: UserInfo): Transformer[UserSession, JsonUser] = {
    // Filter out custom permissions of form "anon[..]" and take the aliased roles of form "alias(role)" (see toDisplayNames)
    def getDisplayPermissions(userSession: UserSession): JsonRoles = {
      val customRegex  = """^anon\[(.*)\]$""".r
      val aliasedRegex = """^.*\((.*)\)$""".r
      JsonRoles(userSession.permissions.flatMap {
        case customRegex(perm) => None
        case aliasedRegex(r)   => Some(r)
        case perm              => Some(perm)
      }.toSet)
    }
    Transformer
      .define[UserSession, JsonUser]
      .withFieldConst(_.id, userInfo.id)
      .withFieldComputed(_.authz, s => JsonRights(s.authz.toSet))
      .withFieldComputed(_.roles, getDisplayPermissions(_))
      .withFieldComputed(_.rolesCoverage, getDisplayPermissions(_))
      .withFieldConst(_.name, userInfo.name)
      .withFieldConst(_.email, userInfo.email)
      .withFieldConst(_.otherInfo, userInfo.otherInfo)
      .withFieldConst(_.status, userInfo.status)
      .withFieldConst(_.providers, List(userInfo.managedBy))
      .withFieldComputed(
        _.providersInfo,
        s => {
          Map(
            userInfo.managedBy -> JsonProviderInfo(
              userInfo.managedBy,
              JsonRights(s.authz.toSet),
              getDisplayPermissions(s),
              JsonRights.empty
            )
          )
        }
      )
      .withFieldComputed(_.lastLogin, s => Some(s.creationDate))
      .withFieldConst(_.customRights, JsonRights.empty)
      .buildTransformer
  }

  /**
   * Use the last session information as user permissions and authz.
   * The resulting user has the exact same permissions and authz as provided in the last user session.
   *
   * Current user permissions may be different if they have changed since we saved the user info, roles may also no longer exist,
   * so we do not attempt to parse as roles, but we still need to transform roles that are aliases or that are unnamed.
   */
  private def transformProvidedUser(userInfo: UserInfo, lastSession: Option[UserSession])(implicit
      allRoles: Set[Role]
  ): JsonUser = {
    lastSession match {
      case None              => {
        val defaultUser = {
          RudderUserDetail(
            RudderAccount.User(userInfo.id, ""),
            userInfo.status,
            Set(),
            ApiAuthorization.None,
            NodeSecurityContext.None
          )
        }
        transformUser(
          defaultUser,
          userInfo,
          Map(userInfo.managedBy -> JsonProviderInfo.fromUser(defaultUser, userInfo.managedBy)),
          lastSession.map(_.creationDate)
        )
      }
      case Some(userSession) => {
        implicit val user: UserInfo = userInfo
        userSession.transformInto[JsonUser]
      }
    }
  }

  private def PassEncoderToString(encoder: PasswordEncoder.Rudder): String = {
    encoder match {
      case PasswordEncoder.MD5    => "MD5"
      case PasswordEncoder.SHA1   => "SHA-1"
      case PasswordEncoder.SHA256 => "SHA-256"
      case PasswordEncoder.SHA512 => "SHA-512"
      case PasswordEncoder.BCRYPT => "BCRYPT"
      case _                      => "plain text"
    }
  }
}
