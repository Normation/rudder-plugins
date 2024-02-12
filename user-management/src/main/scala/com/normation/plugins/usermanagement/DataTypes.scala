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

package com.normation.plugins.usermanagement

import bootstrap.liftweb.AuthBackendProvidersManager
import bootstrap.liftweb.PasswordEncoder
import bootstrap.liftweb.ValidatedUserList
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import com.normation.rudder.RudderRoles
import com.normation.zio._
import io.scalaland.chimney._
import io.scalaland.chimney.dsl._
import net.liftweb.common.Logger
import org.slf4j.LoggerFactory
import zio.json._

/**
 * Applicative log of interest for Rudder ops.
 */
object UserManagementLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("usermanagement")
}

object Serialisation {

  implicit val jsonUserFormDataDecoder:       JsonDecoder[JsonUserFormData]       = DeriveJsonDecoder.gen[JsonUserFormData]
  implicit val jsonRoleAuthorizationsDecoder: JsonDecoder[JsonRoleAuthorizations] = DeriveJsonDecoder.gen[JsonRoleAuthorizations]

  implicit val jsonUserEncoder:             JsonEncoder[JsonUser]             = DeriveJsonEncoder.gen[JsonUser]
  implicit val jsonAuthConfigEncoder:       JsonEncoder[JsonAuthConfig]       = DeriveJsonEncoder.gen[JsonAuthConfig]
  implicit val jsonRoleEncoder:             JsonEncoder[JsonRole]             = DeriveJsonEncoder.gen[JsonRole]
  implicit val jsonInternalUserDataEncoder: JsonEncoder[JsonInternalUserData] = DeriveJsonEncoder.gen[JsonInternalUserData]
  implicit val jsonAddedUserEncoder:        JsonEncoder[JsonAddedUser]        = DeriveJsonEncoder.gen[JsonAddedUser]
  implicit val jsonUpdatedUserEncoder:      JsonEncoder[JsonUpdatedUser]      = DeriveJsonEncoder.gen[JsonUpdatedUser]
  implicit val jsonUsernameEncoder:         JsonEncoder[JsonUsername]         = DeriveJsonEncoder.gen[JsonUsername]
  implicit val jsonDeletedUserEncoder:      JsonEncoder[JsonDeletedUser]      = DeriveJsonEncoder.gen[JsonDeletedUser]
  implicit val jsonReloadStatusEncoder:     JsonEncoder[JsonReloadStatus]     = DeriveJsonEncoder.gen[JsonReloadStatus]
  implicit val jsonReloadResultEncoder:     JsonEncoder[JsonReloadResult]     = DeriveJsonEncoder.gen[JsonReloadResult]
  implicit val jsonRoleCoverageEncoder:     JsonEncoder[JsonRoleCoverage]     = DeriveJsonEncoder.gen[JsonRoleCoverage]
  implicit val jsonCoverageEncoder:         JsonEncoder[JsonCoverage]         = DeriveJsonEncoder.gen[JsonCoverage]

  implicit class AuthConfigSer(auth: ValidatedUserList) {
    def serialize(implicit authProviderManager: AuthBackendProvidersManager): JsonAuthConfig = {
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

      val jUser = auth.users.map {
        case (_, u) =>
          val (rs, custom) = {
            UserManagementService
              .computeRoleCoverage(RudderRoles.getAllRoles.runNow.values.toSet, u.authz.authorizationTypes)
              .getOrElse(Set.empty)
              .partition {
                case Custom(_) => false
                case _         => true
              }
          }
          JsonUser(
            u.getUsername,
            if (custom.isEmpty) Set.empty else custom.head.rights.displayAuthorizations.split(",").toSet,
            rs.map(_.name)
          )
      }.toList.sortBy(_.login)
      val json  = JsonAuthConfig(encoder, roleListOverride, authBackendsProvider, jUser)
      json
    }
  }

  def PassEncoderToString(auth: ValidatedUserList): String = {
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

final case class JsonAuthConfig(
    digest:                 String,
    roleListOverride:       String,
    authenticationBackends: Set[String],
    users:                  List[JsonUser]
)

final case class JsonUser(
    login:       String,
    authz:       Set[String],
    permissions: Set[String]
)

final case class JsonRole(
    @jsonField("id") name: String,
    rights:                List[String]
)

final case class JsonReloadResult(reload: JsonReloadStatus)

object JsonReloadResult {
  val Done = JsonReloadResult(JsonReloadStatus("Done"))
}
final case class JsonReloadStatus(status: String)

final case class JsonInternalUserData(
    username:    String,
    password:    String,
    permissions: List[String]
)

object JsonInternalUserData {
  implicit val transformer: Transformer[User, JsonInternalUserData] = Transformer.derive[User, JsonInternalUserData]
}

final case class JsonAddedUser(
    addedUser: JsonInternalUserData
) extends AnyVal
object JsonAddedUser        {
  implicit val transformer: Transformer[User, JsonAddedUser] = (u: User) => JsonAddedUser(u.transformInto[JsonInternalUserData])
}

final case class JsonUpdatedUser(
    updatedUser: JsonInternalUserData
) extends AnyVal
object JsonUpdatedUser      {
  implicit val transformer: Transformer[User, JsonUpdatedUser] = (u: User) =>
    JsonUpdatedUser(u.transformInto[JsonInternalUserData])
}

final case class JsonUsername(
    username: String
)

final case class JsonDeletedUser(
    deletedUser: JsonUsername
) extends AnyVal
object JsonDeletedUser      {
  implicit val usernameTransformer: Transformer[String, JsonUsername]    = JsonUsername(_)
  implicit val transformer:         Transformer[String, JsonDeletedUser] = (s: String) => JsonDeletedUser(s.transformInto[JsonUsername])
}

final case class JsonUserFormData(
    username:    String,
    password:    String,
    permissions: List[String],
    isPreHashed: Boolean
)

object JsonUserFormData {
  implicit val transformer: Transformer[JsonUserFormData, User] = Transformer.derive[JsonUserFormData, User]
}

final case class JsonCoverage(
    coverage: JsonRoleCoverage
) extends AnyVal
object JsonCoverage     {
  implicit val transformer: Transformer[(Set[Role], Set[Custom]), JsonCoverage] = (x: (Set[Role], Set[Custom])) =>
    x.transformInto[JsonRoleCoverage].transformInto[JsonCoverage]
}

final case class JsonRoleCoverage(
    permissions: Set[String],
    custom:      List[String]
)

object JsonRoleCoverage {
  implicit private[JsonRoleCoverage] val roleTransformer:        Transformer[Role, String]              = _.name
  implicit private[JsonRoleCoverage] val customRolesTransformer: Transformer[Set[Custom], List[String]] =
    _.flatMap(_.rights.authorizationTypes.map(_.id)).toList.sorted

  implicit val transformer: Transformer[(Set[Role], Set[Custom]), JsonRoleCoverage] =
    Transformer.derive[(Set[Role], Set[Custom]), JsonRoleCoverage]
}

final case class JsonRoleAuthorizations(
    permissions: List[String],
    authz:       List[String]
)
