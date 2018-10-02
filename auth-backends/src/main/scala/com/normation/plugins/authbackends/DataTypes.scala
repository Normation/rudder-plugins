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

package com.normation.plugins.authbackends

import net.liftweb.common.Logger
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.Extraction
import net.liftweb.json.NoTypeHints
import org.slf4j.LoggerFactory

/**
 * Applicative log of interest for Rudder ops.
 */
object AuthBackendsLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("auth-backends")
}


/*
 * The Json ADT to present information to client side
 * All the *Json case clases define an user API, any change
 * must be handle with care (and forward compatibility in mind).
 */


/*
 * Authentication configuration.
 * Backends are not generic, even if it's what we would like to have in
 * the long run. But that suppose to have for each back-ends a schema of
 * its properties and some meta-data like human name, description, etc.
 * This is well beyond the scope of that version.
 *
 *
 */
final case class JsonAuthConfiguration(
    declaredProviders: String // order in config file as it is, without any parsing
  , computedProviders: Seq[String] // order after resolution (root admin, plugin status, etc)
  , adminConfig      : JsonAdminConfig
  , fileConfig       : JsonFileConfig
  , ldapConfig       : JsonLdapConfig
  , radiusConfig     : JsonRadiusConfig
)

/*
 * A configuration parameter in a config file.
 */
final case class ConfigOption(
    description: String
  , key        : String
  , value      : String
)

final case class JsonAdminConfig(
    description: String
  , login      : ConfigOption
  , password   : ConfigOption
  , enabled    : Boolean
)

final case class JsonFileConfig(
    providerId : String
  , description: String
  , filePath   : String
)

final case class JsonLdapConfig(
    providerId  : String
  , description : String
  , ldapUrl     : ConfigOption
  , bindDn      : ConfigOption
  , bindPassword: ConfigOption
  , searchBase  : ConfigOption
  , ldapFilter  : ConfigOption
)

final case class JsonRadiusConfig(
    providerId : String
  , description: String
  , hostName   : ConfigOption
  , hostPort   : ConfigOption
  , secret     : ConfigOption
  , timeout    : ConfigOption
  , retries    : ConfigOption
  , protocol   : ConfigOption
)


final object JsonSerialization {

  implicit val formats = net.liftweb.json.Serialization.formats(NoTypeHints)

  implicit class ConfigOptionToJson(config: JsonAuthConfiguration) {
    def toJson(): JValue = {
      Extraction.decompose(config)
    }
  }
}
