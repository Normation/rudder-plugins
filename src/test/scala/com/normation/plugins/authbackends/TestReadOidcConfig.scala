/*
 *************************************************************************************
 * Copyright 2024 Normation SAS
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

import bootstrap.rudder.plugin.RudderTokenMapping
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.tenants.TenantId
import com.normation.zio.*
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import scala.concurrent.duration.*
import zio.Chunk

@RunWith(classOf[JUnitRunner])
class TestReadOidcConfig extends Specification {

  // WARNING: HOCON doesn't behave the same if you read from a file or from a string, so for the test to be relevant,
  // we need to load from files.

  "reading an OIDC configuration" should {
    val registration = RudderPropertyBasedOAuth2RegistrationDefinition.make().runNow

    "read the correct name" in {

      val config = ConfigFactory.parseResources("oidc/oidc_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp")
    }

    "have two entitlements mapping" in {

      val config = ConfigFactory.parseResources("oidc/oidc_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"    -> "administrator",
        "rudder_readonly" -> "readonly"
      )) and (regs("someidp").tenants.mapping === Map(
        "rudder_TA" -> "TA",
        "rudder_TB" -> "TB"
      ))
    }

    "be able to use complex roles names with the reverse mapping" in {
      val config = ConfigFactory.parseResources("oidc/oidc_reverse_role_mapping.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"                                                                       -> "administrator",
        "rudder_readonly"                                                                    -> "readonlyOVERRIDDEN",
        "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "administrator"
      )) and (
        regs("someidp").tenants.mapping === Map(
          "rudder_TA"                                                                          -> "TA",
          "rudder_TB"                                                                          -> "TB_OVERRIDDEN",
          "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "TA"
        )
      )
    }

  }

  "tenants mapping" should {
    val config       = ConfigFactory.parseResources("oidc/oidc_tenants.properties")
    val registration = RudderPropertyBasedOAuth2RegistrationDefinition.make().runNow
    val regs         = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

    "work for simple tenants" in {
      val tokenValues = Set("rudder_TA", "rudder_TB")
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", NodeSecurityContext.None)(_ => Some(tokenValues))

      tenants === NodeSecurityContext.ByTenants(Chunk(TenantId("TA"), TenantId("TB")))
    }

    "work for no tenants" in {
      val tokenValues = Set.empty[String]
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", NodeSecurityContext.None)(_ => Some(tokenValues))

      tenants === NodeSecurityContext.None
    }

    "work for none" in {
      val tokenValues = Set("rudder_none", "rudder_TA")
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", NodeSecurityContext.None)(_ => Some(tokenValues))

      tenants === NodeSecurityContext.None
    }

    "work for all" in {
      val tokenValues = Set("rudder_all", "rudder_TA")
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", NodeSecurityContext.None)(_ => Some(tokenValues))

      tenants === NodeSecurityContext.All
    }
  }

  "reading a JWT configuration" should {
    val registration = RudderPropertyBasedJwtRegistrationDefinition.make().runNow

    "read the correct name" in {

      val config = ConfigFactory.parseResources("jwt/jwt_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp")
    }

    "have two entitlements mapping" in {

      val config = ConfigFactory.parseResources("jwt/jwt_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"    -> "administrator",
        "rudder_readonly" -> "readonly"
      )) and (regs("someidp").tenants.mapping === Map(
        "rudder_TA" -> "TA",
        "rudder_TB" -> "TB"
      ))
    }

    "be able to use complex roles names with the reverse mapping" in {
      val config = ConfigFactory.parseResources("jwt/jwt_reverse_role_mapping.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"                                                                       -> "administrator",
        "rudder_readonly"                                                                    -> "readonlyOVERRIDDEN",
        "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "administrator"
      )) and (
        regs("someidp").tenants.mapping === Map(
          "rudder_TA"                                                                          -> "TA",
          "rudder_TB"                                                                          -> "TB_OVERRIDDEN",
          "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "TA"
        )
      )
    }
  }

  "reading an opaque configuration" should {
    val reg = RudderOpaqueTokenRegistration(
      "someidp",
      "xxxClientIdxxx",
      "xxxClientPassxxx",
      "https://someidp/oauth2/v1/introspect",
      "email",
      Some(6.minutes)
    )

    val registration = RudderPropertyBasedOpaqueTokenRegistrationDefinition.make().runNow

    "read the correct configuration" in {

      val config = ConfigFactory.parseResources("opaque/opaque.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp") and regs("someidp") === reg
    }

    "read the correct configuration with default" in {

      val config = ConfigFactory.parseResources("opaque/opaque_default.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp") and regs("someidp") === reg.copy(pivotAttributeName = "sub", cacheRequestDuration = None)
    }

  }

}
