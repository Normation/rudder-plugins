/*
 *************************************************************************************
 * Copyright 2021 Normation SAS
 *************************************************************************************
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * In accordance with the terms of section 7 (7. Additional Terms.) of
 * the GNU Affero GPL v3, the copyright holders add the following
 * Additional permissions:
 * Notwithstanding to the terms of section 5 (5. Conveying Modified Source
 * Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
 * licence, when you create a Related Module, this Related Module is
 * not considered as a part of the work and may be distributed under the
 * license agreement of your choice.
 * A "Related Module" means a set of sources files including their
 * documentation that, without modification of the Source Code, enables
 * supplementary functions or services in addition to those offered by
 * the Software.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
 *
 *************************************************************************************
 */

package com.normation.plugins.authbackends

import cats.data.NonEmptyList
import com.normation.errors._
import com.typesafe.config.Config
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import zio._
import zio.syntax._

/**
 * This file contain logic related to Oauth2/OpenID Connect authentication and
 * user data sharing.
 *
 * In spring security, Oauth2 is implemented in a dedicated filter that
 * intercept request before other authentication methods (the exact order is
 * defined in: https://docs.spring.io/spring-security/site/docs/5.3.9.RELEASE/reference/html5/#servlet-security-filters)
 *
 * It requires registration to oauth2 services as a client, and several oauth2
 * services can be configured: this is contractualized by implementing the `InMemoryOAuth2AuthorizedClientService` trait
 * In our case, the configuration is done in the rudder property configuration file, and
 * we just parse the different client and register them in an in memory immutable map.
 *
 * The other important aspect is the identifier used to match the user on the oauth service.
 * For now, that identifier must be use as login in rudder.
 *
 */

/*
 * Data container class to add our properties to spring ClientRegistration ones
 * We never want to print the secret in logs, so override it.
 */
final case class RudderClientRegistration(registration: ClientRegistration, infoMsg: String) {
  override def toString: String = {
    toDebugStringWithSecret.replaceFirst("""clientSecret='([^']+?)'""", "clientSecret='*****'")
  }

  // avoid that in logs etc, use only for interactive debugging sessions
  def toDebugStringWithSecret = s"""{${registration.toString}}, '${infoMsg}'"""
}

/*
 * Client registration definition based on rudder property file:
 * - read property `rudder.auth.oauth2.client.registrations` which give a comma-separated
 *   list of client registration to oauth2 providers
 * - for each registration, read the needed properties based on template
 *   `rudder.auth.oauth2.client.${registration}.${propertyName}`
 */
object RudderPropertyBasedOAuth2RegistrationDefinition {

  val A_NAME            = "name"
  val A_CLIENT_ID       = "client.id"
  val A_CLIENT_SECRET   = "client.secret"
  val A_CLIENT_REDIRECT = "client.redirect"
  val A_AUTH_METHOD     = "authMethod"
  val A_GRANT_TYPE      = "grantType"
  val A_INFO_MESSAGE    = "ui.infoMessage"
  val A_SCOPE           = "scope"
  val A_URI_AUTH        = "uri.auth"
  val A_URI_TOKEN       = "uri.token"
  val A_URI_USER_INFO   = "uri.userInfo"
  val A_URI_JWK_SET     = "uri.jwkSet"
  val A_PIVOT_ATTR      = "userNameAttributeName"

  val authMethods = {
    import ClientAuthenticationMethod._
    List(CLIENT_SECRET_BASIC, CLIENT_SECRET_POST, CLIENT_SECRET_JWT, NONE)
  }

  val grantTypes = {
    import AuthorizationGrantType._
    List(AUTHORIZATION_CODE, REFRESH_TOKEN, CLIENT_CREDENTIALS, PASSWORD) // IMPLICIT is deprecated for security reason
  }

  val baseProperty = "rudder.auth.oauth2.provider"

  val registrationAttributes = Map(
    A_NAME            -> "human readable name to use in the button 'login with XXXX'",
    A_CLIENT_ID       -> "id generated in the Oauth2 service provider to identify rudder as a client app",
    A_CLIENT_SECRET   -> "the corresponding secret key",
    A_CLIENT_REDIRECT -> "rudder URL to redirect to once authentication is done on the provider (must be resolvable from user browser)",
    A_AUTH_METHOD     -> s"authentication method to use (${authMethods.map(_.getValue).mkString(",")})",
    A_GRANT_TYPE      -> s"authorization grant type to use (${grantTypes.map(_.getValue).mkString(",")}",
    A_INFO_MESSAGE    -> "message displayed in the login form, for example to tell the user what login he must use",
    A_SCOPE           -> "data scope to request access to",
    A_URI_AUTH        -> "provider URL to contact for main authentication (see provider documentation)",
    A_URI_TOKEN       -> "provider URL to contact for token verification (see provider documentation)",
    A_URI_USER_INFO   -> "provider URL to contact to get user information (see provider documentation)",
    A_URI_JWK_SET     -> "provider URL to check signature of JWT token (see provider documentation)",
    A_PIVOT_ATTR      -> "the attribute used to find local app user"
  )

  def parseAuthenticationMethod(method: String): PureResult[ClientAuthenticationMethod] = {
    authMethods.find(_.getValue.equalsIgnoreCase(method)) match {
      case None    =>
        // spring change the name between the version we use in 6.2 and 7.0. We want to provide compatibility
        // and only document the most recent ones.
        method.toLowerCase match {
          case "post" | "client_secret_post"   => Right(ClientAuthenticationMethod.CLIENT_SECRET_POST)
          case "basic" | "client_secret_basic" => Right(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          case _                               =>
            Left(
              Inconsistency(
                s"Requested OAUTh2 authentication methods '${method}' is not recognized, please use one of: ${authMethods.map(_.getValue).mkString("'", "','", "'")}"
              )
            )
        }
      case Some(m) => Right(m)
    }
  }

  def parseAuthorizationGrantType(grant: String): PureResult[AuthorizationGrantType] = {
    grantTypes.find(_.getValue.equalsIgnoreCase(grant)) match {
      case None    =>
        Left(
          Inconsistency(
            s"Requested OAUTh2 authorization grant type '${grant}' is not recognized, please use one of: ${grantTypes.map(_.getValue).mkString("'", "','", "'")}"
          )
        )
      case Some(m) => Right(m)
    }
  }

  /*
   * In a first release of the plugin, scope were comma separated, which is inconsistent with OIDC spec
   * https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
   * `Multiple scope values MAY be used by creating a space delimited, case sensitive list of ASCII scope values.`
   * It is not documented anymore and will only support an handful of early adopters.
   * Compatibility can be removed at some point in the future (timeline: rudder 7.2)
   */
  def parseScope(scopes: String): IOResult[List[String]] = {
    // it seems that only the given list of email, phone etc is supported, even if the speak of "case sensitive", let be
    // a bit more broad, the protocol lib will check more thoroughly.
    val ascii                 = """\w""".r
    def checkScope(s: String) = {
      if (ascii.matches(s))
        Inconsistency(s"Only ascii [a-zA-Z0-9_] is authorized in scope definition but '${s}' doesn't matches it").fail
      else s.succeed
    }
    val s                     = (
      if (scopes.contains(",")) scopes.split(",")
      else scopes.split("""\s+""")
    ).toList.map(_.trim)

    ZIO.partition(s)(checkScope).flatMap {
      case (errs, oks) =>
        errs.toList match {
          case Nil       => oks.toList.succeed
          case h :: tail => Accumulated(NonEmptyList.of(h, tail: _*)).fail
        }
    }
  }

  def readOneRegistration(id: String, config: Config): IOResult[RudderClientRegistration] = {

    // utility method that read key in config and fails with an error message if
    // not found.
    def read(key: String): IOResult[String] = {
      val path = baseProperty + "." + id + "." + key
      IOResult.effect(s"Missing key '${path}' for OAUTH2 registration '${id}' (${registrationAttributes(key)})")(
        config.getString(path)
      )
    }
    for {
      name           <- read(A_NAME)
      clientId       <- read(A_CLIENT_ID)
      clientSecret   <- read(A_CLIENT_SECRET)
      clientRedirect <- read(A_CLIENT_REDIRECT)
      authMethod     <- read(A_AUTH_METHOD).flatMap(parseAuthenticationMethod(_).toIO)
      grantTypes     <- read(A_GRANT_TYPE).flatMap(parseAuthorizationGrantType(_).toIO)
      infoMessage    <- read(A_INFO_MESSAGE)
      scopes         <- read(A_SCOPE).flatMap(parseScope(_))
      uriAuth        <- read(A_URI_AUTH)
      uriToken       <- read(A_URI_TOKEN)
      uriUserInfo    <- read(A_URI_USER_INFO)
      pivotAttr      <- read(A_PIVOT_ATTR)
      jwkSetUri      <- read(A_URI_JWK_SET)
    } yield {
      RudderClientRegistration(
        ClientRegistration
          .withRegistrationId(id)
          .clientId(clientId)
          .clientSecret(clientSecret)
          .clientAuthenticationMethod(authMethod)
          .authorizationGrantType(grantTypes)
          .redirectUri(clientRedirect)
          .scope(scopes: _*)
          .authorizationUri(uriAuth)
          .tokenUri(uriToken)
          .userInfoUri(uriUserInfo)
          .userNameAttributeName(pivotAttr)
          .clientName(name)
          .jwkSetUri(jwkSetUri)
          .build(),
        infoMessage
      )
    }
  }

  def readProviders(config: Config): IOResult[List[String]] = {
    val path = baseProperty + ".registrations"
    IOResult.effect(
      s"Missing property '${path}' which define the comma separated list of provider registration to use for OAUTH2."
    )(
      config.getString(path).split(",").map(_.trim).toList
    )
  }

  /*
   * Read the whole set of providers with their registrations.
   * We return a list to keep the order provided in the config file.
   */
  def readAllRegistrations(config: Config): IOResult[List[(String, RudderClientRegistration)]] = {
    for {
      // we don't want to fail if the list of provider is missing, just log it as a warning
      providers     <- readProviders(config)
      // we don't want to fail if one of the registration is not ok, just log it
      _             <- AuthBackendsLoggerPure.info(s"List of configured providers for oauth2/OpenIDConnect: ${providers.mkString(", ")}")
      registrations <- ZIO.foreach(providers) { p =>
                         readOneRegistration(p, config).foldM(
                           err =>
                             AuthBackendsLoggerPure.error(
                               s"Error when reading OAUTH2 configuration for registration to '${p}' provider: ${err.fullMsg}'"
                             ) *>
                             None.succeed,
                           res => {
                             AuthBackendsLoggerPure.debug(s"New registration for provider '${p}': ${res} ") *>
                             Some((p, res)).succeed
                           }
                         )
                       }
    } yield registrations.flatten
  }

  def make(): IOResult[RudderPropertyBasedOAuth2RegistrationDefinition] = {
    for {
      ref <- Ref.make(List.empty[(String, RudderClientRegistration)])
    } yield {
      new RudderPropertyBasedOAuth2RegistrationDefinition(ref)
    }
  }

}

class RudderPropertyBasedOAuth2RegistrationDefinition(val registrations: Ref[List[(String, RudderClientRegistration)]]) {
  import RudderPropertyBasedOAuth2RegistrationDefinition._

  /*
   * read information from config and update internal cache
   */
  def updateRegistration(config: Config): IOResult[Unit] = {
    for {
      newOnes <- readAllRegistrations(config)
      _       <- registrations.set(newOnes)
    } yield ()
  }

}
