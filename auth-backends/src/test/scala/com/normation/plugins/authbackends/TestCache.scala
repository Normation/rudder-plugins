/*
 *************************************************************************************
 * Copyright 2025 Normation SAS
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

import bootstrap.rudder.plugin.RudderOAuth2OpaqueToken
import bootstrap.rudder.plugin.RudderOpaqueTokenAuthenticationProvider
import com.normation.rudder.api.AccountToken
import com.normation.rudder.api.ApiAccount
import com.normation.rudder.api.ApiAccountExpirationPolicy
import com.normation.rudder.api.ApiAccountId
import com.normation.rudder.api.ApiAccountKind
import com.normation.rudder.api.ApiAccountName
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.RudderUserDetail
import com.normation.rudder.users.UserStatus
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.authentication.*
import org.springframework.security.oauth2.server.resource.introspection.*
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import scala.util.Try

@RunWith(classOf[JUnitRunner])
class TestCache extends Specification {

  sequential

  private val BAD_TOKEN_UNKNOWN       = "unknown"
  private val BAD_TOKEN_INTROSPECTION = "introspectionEndpoint"
  private val GOOD_TOKEN_EXP1         = "goodToken_exp1"
  private val GOOD_TOKEN_EXP2         = "goodToken_exp2"

  private val TOKEN_CREATION = Instant.ofEpochMilli(0)
  private val TOKEN_EXP1     = Instant.ofEpochSecond(3 * 60)
  private val TOKEN_EXP2     = Instant.ofEpochSecond(10 * 60)
  private val TOKEN_EXP3     = Instant.ofEpochSecond(20 * 60)

  private def rudderUserDetails(s: String) = RudderUserDetail(
    RudderAccount.Api(
      ApiAccount(
        ApiAccountId(s),
        ApiAccountKind.PublicApi(ApiAuthorization.RW, ApiAccountExpirationPolicy.NeverExpire),
        ApiAccountName(s),
        AccountToken(None, TOKEN_CREATION),
        "",
        isEnabled = true,
        TOKEN_CREATION,
        None,
        NodeSecurityContext.All
      )
    ),
    UserStatus.Active,
    Set(),
    ApiAuthorization.RW,
    NodeSecurityContext.All
  )

  private class Count {
    private var i = 0
    def inc: Unit = { i = i + 1 }
    def get: Int  = i
  }

  implicit class StringToAuthentication(s: String) {
    def toAuth: BearerTokenAuthenticationToken = new BearerTokenAuthenticationToken(s)
  }

  private class TestOpaqueTokenIntrospector extends OpaqueTokenIntrospector {
    val count: Count = new Count

    def introspect(token: String): OAuth2AuthenticatedPrincipal = {
      count.inc
      token match {
        case GOOD_TOKEN_EXP1 | GOOD_TOKEN_EXP2 =>
          new OAuth2IntrospectionAuthenticatedPrincipal(Map[String, AnyRef](("xxx", "xxx")).asJava, null)

        case BAD_TOKEN_UNKNOWN       => throw new BadOpaqueTokenException("Provided token isn't active")
        case BAD_TOKEN_INTROSPECTION => throw new OAuth2IntrospectionException("introspection endpoint returned error")
        case x                       => throw new OAuth2IntrospectionException("other error")
      }
    }
  }

  private val converter: OpaqueTokenAuthenticationConverter = new OpaqueTokenAuthenticationConverter {
    override def convert(introspectedToken: String, authenticatedPrincipal: OAuth2AuthenticatedPrincipal): Authentication = {
      val exp = introspectedToken match {
        case GOOD_TOKEN_EXP1 => TOKEN_EXP1
        case GOOD_TOKEN_EXP2 => TOKEN_EXP2
        case _               => TOKEN_EXP3
      }

      RudderOAuth2OpaqueToken(
        new BearerTokenAuthentication(
          authenticatedPrincipal,
          new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, introspectedToken, TOKEN_CREATION, exp),
          new java.util.ArrayList()
        ),
        rudderUserDetails(introspectedToken)
      )
    }
  }

//  private val providerCache = new RudderOpaqueTokenAuthenticationProvider(introspector, converter, Some(Duration(4, TimeUnit.MINUTES)))

  "when we don't have a cache" >> {
    val introspector    = new TestOpaqueTokenIntrospector
    val providerNoCache =
      new RudderOpaqueTokenAuthenticationProvider(introspector, converter, None, () => Instant.ofEpochSecond(4 * 60))

    "two times a correct value leads to 2 requests" in {

      providerNoCache.authenticate(GOOD_TOKEN_EXP1.toAuth)
      providerNoCache.authenticate(GOOD_TOKEN_EXP1.toAuth)
      introspector.count.get === 2
    }

    "two times a bad value leads to 2 requests" in {

      Try(providerNoCache.authenticate(BAD_TOKEN_UNKNOWN.toAuth))
      Try(providerNoCache.authenticate(BAD_TOKEN_UNKNOWN.toAuth))
      introspector.count.get === 4
    }
  }

  "when we have a cache" >> {
    val introspector  = new TestOpaqueTokenIntrospector
    val providerCache = {
      new RudderOpaqueTokenAuthenticationProvider(
        introspector,
        converter,
        Some(Duration(5, TimeUnit.MINUTES)),
        () => Instant.ofEpochSecond(4 * 60)
      )
    }

    "two times a correct value leads to 1 requests" in {

      providerCache.authenticate(GOOD_TOKEN_EXP2.toAuth)
      providerCache.authenticate(GOOD_TOKEN_EXP2.toAuth)
      introspector.count.get === 1
    }

    "two times a bad value leads to 1 requests" in {

      Try(providerCache.authenticate(BAD_TOKEN_UNKNOWN.toAuth))
      Try(providerCache.authenticate(BAD_TOKEN_UNKNOWN.toAuth))
      introspector.count.get === 2
    }
  }

  "when we have a cache and expiration happens during cache" >> {
    val introspector  = new TestOpaqueTokenIntrospector
    var c             = 0
    val providerCache = {
      new RudderOpaqueTokenAuthenticationProvider(
        introspector,
        converter,
        Some(Duration(5, TimeUnit.MINUTES)),
        () => Instant.ofEpochSecond(c.toLong * 60)
      )
    }

    "first request ok" in {
      providerCache.authenticate(GOOD_TOKEN_EXP1.toAuth)
      introspector.count.get === 1
    }

    "second request ko even if in cache" in {
      c = 5
      val res = Try(providerCache.authenticate(GOOD_TOKEN_EXP1.toAuth))

      res must beAnInstanceOf[scala.util.Failure[?]]
      introspector.count.get === 1
    }
  }
}
