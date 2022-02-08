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

import bootstrap.liftweb.PasswordEncoder
import bootstrap.liftweb.RudderConfig
import bootstrap.liftweb.UserDetailList
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import net.liftweb.common.Logger
import net.liftweb.json.JsonAST.JValue
import org.slf4j.LoggerFactory
import net.liftweb.json.{Serialization => S}

/**
 * Applicative log of interest for Rudder ops.
 */
object UserManagementLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("usermanagement")
}

object Serialisation {

  implicit class AuthConfigSer(auth: UserDetailList) {
    def toJson: JValue = {
      val encoder: String = PassEncoderToString(auth)
      val authBackendsProvider = RudderConfig.authenticationProviders.getConfiguredProviders().map(_.name).toSet


      val jUser = auth.users.map {
        case(_,u) =>
          val (rs, custom) = UserManagementService.computeRoleCoverage(Role.values, u.authz.authorizationTypes).getOrElse(Set.empty).partition {
            case Custom(_) => false
            case _ => true
          }
          JsonUser(
            u.getUsername
            , if (custom.isEmpty) Set.empty else custom.head.rights.displayAuthorizations.split(",").toSet
            , rs.map(_.name)
          )
      }.toList.sortBy( _.login )
      val json = JsonAuthConfig(encoder, authBackendsProvider, jUser)
      import net.liftweb.json._
      implicit val formats = S.formats(NoTypeHints)
      Extraction.decompose(json)
    }
  }

  def PassEncoderToString(auth: UserDetailList): String = {
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
    digest: String
  , authenticationBackends: Set[String]
  , users : List[JsonUser]
)

final case class JsonUser(
    login: String
  , authz: Set[String]
  , role: Set[String]
)
