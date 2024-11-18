/*
 *************************************************************************************
 * Copyright 2011 Normation SAS
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

import com.normation.zio.*
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TestReadOidcConfig extends Specification {

  // WARNING: HOCON doesn't behave the same if you read from a file or from a string, so for the test to be relevant,
  // we need to load from files.

  "reading the configuration should works" >> {

    val config = ConfigFactory.parseResources("oidc/oidc_simple.properties")
    val regs   = RudderPropertyBasedOAuth2RegistrationDefinition.readAllRegistrations(config).runNow.toMap

    regs.keySet === Set("someidp")
  }

  "we should have two entitlement mapping" >> {

    val config = ConfigFactory.parseResources("oidc/oidc_simple.properties")
    val regs   = RudderPropertyBasedOAuth2RegistrationDefinition.readAllRegistrations(config).runNow.toMap

    regs("someidp").roleMapping === Map(
      "rudder_admin"    -> "administrator",
      "rudder_readonly" -> "readonly"
    )
  }

  "we can use complex roles names with the reverse mapping" >> {
    val config = ConfigFactory.parseResources("oidc/oidc_reverse_role_mapping.properties")
    val regs   = RudderPropertyBasedOAuth2RegistrationDefinition.readAllRegistrations(config).runNow.toMap

    regs("someidp").roleMapping === Map(
      "rudder_admin"                                                                       -> "administrator",
      "rudder_readonly"                                                                    -> "readonlyOVERRIDDEN",
      "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "administrator"
    )
  }

}
