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
import bootstrap.liftweb.UserDetailList
import net.liftweb.common.Logger
import org.slf4j.LoggerFactory

/**
 * Applicative log of interest for Rudder ops.
 */
object UserManagementLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("user-management")
}


object Serialisation {

  implicit class AuthConfigSer(auth: UserDetailList) {
    def toJson: String = {
      val encoder = auth.encoder match {
        case PasswordEncoder.MD5       => "MD5"
        case PasswordEncoder.SHA1      => "SHA-1"
        case PasswordEncoder.SHA256    => "SHA-256"
        case PasswordEncoder.SHA512    => "SHA-512"
        case _                         => "plain text"
      }
      val json = JsonAuthConfig(encoder, auth.users.map { case(_,u)=> JsonUser(u.getUsername, u.authz.displayAuthorizations) }.toList )
      import net.liftweb.json._
      import net.liftweb.json.Serialization.write
      implicit val formats = Serialization.formats(NoTypeHints)
      write(json)
    }
  }
}

final case class JsonAuthConfig(
    digest: String
  , users : List[JsonUser]
)

final case class JsonUser(
    login: String
  , authz: String
)
