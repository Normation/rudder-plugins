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

import com.normation.rudder.AuthorizationType
import com.normation.rudder.Rights
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import com.normation.rudder.users.RudderUserDetail
import io.scalaland.chimney.Transformer
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

  implicit val jsonRightsEncoder:           JsonEncoder[JsonRights]           =
    JsonEncoder[List[String]].contramap(_.authorizationTypes.map(_.id).toList.sorted)
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
}

final case class JsonAuthConfig(
    digest:                 String,
    roleListOverride:       String,
    authenticationBackends: Set[String],
    users:                  List[JsonUser]
)

final case class JsonRights(
    authorizationTypes: Set[AuthorizationType]
) extends AnyVal
object JsonRights {
  implicit val transformer: Transformer[Rights, JsonRights] = Transformer.derive[Rights, JsonRights]

  // We don't want to send "no_rights" for now, as it is not yet handled back as an empty set of rights when updating a user
  val empty:     JsonRights = JsonRights(Set.empty)
  val AnyRights: JsonRights = Rights.AnyRights.transformInto[JsonRights]
}

/**
  * @param getUsername The identifier/username
  * @param authz All authorizations for the user
  * @param permissions All role names for the user
  * @param rolesCoverage All roles names that are infered from all authz of the user
  * @param customRights All custom rights for the user
  */
final case class JsonUser(
    @jsonField("login") getUsername: String,
    authz:                           JsonRights,
    @jsonField("permissions") roles: Set[String], // role name
    rolesCoverage:                   Set[String], // role name
    customRights:                    JsonRights
)

object JsonUser {
  implicit private[JsonUser] val roleTransformer: Transformer[Role, String] = _.name

  def noRights(username: String):  JsonUser = JsonUser(username, JsonRights.empty, Set.empty, Set.empty, JsonRights.empty)
  def anyRights(username: String): JsonUser =
    JsonUser(username, JsonRights.AnyRights, Set(Role.Administrator.name), Set(Role.Administrator.name), JsonRights.empty)

  def fromUser(u: RudderUserDetail)(allRoles: Set[Role]): JsonUser = {
    val (allUserRoles, customUserRights) = {
      UserManagementService
        .computeRoleCoverage(allRoles, u.authz.authorizationTypes)
        .getOrElse(Set.empty)
        .partitionMap {
          case Custom(customRights) => Right(customRights.authorizationTypes)
          case r                    => Left(r)
        }
    }

    // custom anonymous roles and permissions are already inside roleCoverage and customRights fields
    val roles = u.roles.filter {
      case _: Custom => false
      case _ => true
    }.map(_.name)

    JsonUser(
      u.getUsername,
      JsonRights(u.authz.authorizationTypes),
      roles,
      allUserRoles.map(_.name),
      JsonRights(customUserRights.flatten)
    )
  }
}

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
