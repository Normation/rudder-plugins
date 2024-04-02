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
import java.io.StringReader
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TestReadOidcConfig extends Specification {

  val oidcConfig = {
    """rudder.auth.oauth2.provider.registrations=someidp
      |rudder.auth.oauth2.provider.someidp.name=Some ID
      |rudder.auth.oauth2.provider.someidp.ui.infoMessage="Hey, log in to Some Idp!"
      |rudder.auth.oauth2.provider.someidp.client.id=xxxClientIdxxx
      |rudder.auth.oauth2.provider.someidp.client.secret=xxxClientPassxxx
      |rudder.auth.oauth2.provider.someidp.scope=openid email profile groups
      |rudder.auth.oauth2.provider.someidp.userNameAttributeName=email
      |rudder.auth.oauth2.provider.someidp.uri.auth="https://someidp/oauth2/v1/authorize"
      |rudder.auth.oauth2.provider.someidp.uri.token="https://someidp/oauth2/v1/token"
      |rudder.auth.oauth2.provider.someidp.uri.userInfo="https://someidp/oauth2/v1/userinfo"
      |rudder.auth.oauth2.provider.someidp.uri.jwkSet="https://someidp/oauth2/v1/keys"
      |rudder.auth.oauth2.provider.someidp.client.redirect="{baseUrl}/login/oauth2/code/{registrationId}"
      |rudder.auth.oauth2.provider.someidp.grantType=authorization_code
      |rudder.auth.oauth2.provider.someidp.authMethod=basic
      |rudder.auth.oauth2.provider.someidp.roles.enabled=true
      |rudder.auth.oauth2.provider.someidp.roles.attribute=customroles
      |rudder.auth.oauth2.provider.someidp.roles.override=true
      |rudder.auth.oauth2.provider.someidp.roles.mapping.enforced=true
      |rudder.auth.oauth2.provider.someidp.roles.mapping.entitlements.rudder_admin=administrator
      |rudder.auth.oauth2.provider.someidp.roles.mapping.entitlements.rudder_readonly=readonly
      |rudder.auth.oauth2.provider.someidp.enableProvisioning=true
      |""".stripMargin
  }

  val config = ConfigFactory.parseReader(new StringReader(oidcConfig))
  val regs   = RudderPropertyBasedOAuth2RegistrationDefinition.readAllRegistrations(config).runNow.toMap

  "reading the configuration should works" >> {
    regs.keySet === Set("someidp")
  }

  "we should have two entitlement mapping" >> {
    regs("someidp").roleMapping === Map(
      "rudder_admin"    -> "administrator",
      "rudder_readonly" -> "readonly"
    )
  }
}
