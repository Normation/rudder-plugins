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

import bootstrap.liftweb.LogoutPostAction
import bootstrap.liftweb.UserLogout
import bootstrap.rudder.plugin.BuildLogout
import cats.data.NonEmptyList
import com.normation.errors.*
import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.readProviders
import com.typesafe.config.Config
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import zio.*
import zio.syntax.*

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
 * We then have two broad cases:
 * - delegating authentication for *users* to an IdP through an interactive process which is called `authorization_code`
 *   workflow.
 *   One of the important aspect of that workflow is the identifier used to match the user between the IdP and Rudder.
 *   For now, that identifier must be used as Rudder login.
 * - delegating authentication for *API tokens* to an IdP through a JWT bearer token created by an IdP and for which we
 *   check the authenticity through a process which is called `client_credentials` workflow.
 *
 */

sealed trait ProvidedList {
  def debugName:         String
  def enabled:           Boolean
  def attributeName:     String
  def overrides:         Boolean
  def restrictToMapping: Boolean
  def mapping:           Map[String, String]

  override def toString: String = if (enabled) s"enabled, list obtained from attribute: ${attributeName} (override: ${overrides})"
  else "disabled"
}

final case class ProvidedRoles(
    enabled:           Boolean,
    attributeName:     String,
    overrides:         Boolean,
    restrictToMapping: Boolean,
    mapping:           Map[String, String]
) extends ProvidedList {
  val debugName = "roles"
}

final case class ProvidedTenants(
    enabled:           Boolean,
    attributeName:     String,
    overrides:         Boolean,
    restrictToMapping: Boolean,
    mapping:           Map[String, String]
) extends ProvidedList {
  val debugName = "tenants"
}

final case class JwtAudience(
    check: Boolean,
    value: String
)

/*
 * We have to data structures to model the configuration parameters needed for a workflow:
 * - the first, `RudderJWTRegistration` is simple and is only used for API protection with Bearer token. It only needs an
 *   URL to get a public key for signature checking, and some role mapping
 * - the second, `RudderClientRegistration`, is complicated because in it, Rudder takes active part in the authentication
 *   and act for the users to make them log on IdP and then into Rudder. We need it to have a client ID and secret in
 *   the IdP, and lots of other parameters.
 */

// properties common to both JWT and OAuth2/OIDC registration
sealed trait RudderOAuth2Registration {
  def registrationId: String
  def roles:          ProvidedRoles
  def tenants:        ProvidedTenants
}

/*
 * API access with JWT (bearer token) - client_credentials workflow
 */
final case class RudderJwtRegistration(
    registrationId: String,
    jwkSetUri:      String,
    audience:       JwtAudience,
    roles:          ProvidedRoles,
    tenants:        ProvidedTenants
) extends RudderOAuth2Registration // there is nothing secret here, no need to override `toString()`.

/*
 * User login - authorization_code workflow.
 * Data container class to add our properties to spring ClientRegistration ones
 * We never want to print the secret in logs, so override it.
 */
final case class RudderClientRegistration(
    registration:      ClientRegistration,
    logoutUrl:         Option[String],
    logoutRedirectUrl: Option[String],
    infoMsg:           String,
    roles:             ProvidedRoles,
    provisioning:      Boolean,
    tenants:           ProvidedTenants
) extends RudderOAuth2Registration {
  override def registrationId: String = registration.getRegistrationId

  override def toString: String = {
    toDebugStringWithSecret.replaceFirst("""clientSecret='([^']+?)'""", "clientSecret='*****'")
  }

  // avoid that in logs etc, use only for interactive debugging sessions
  def toDebugStringWithSecret =
    s"""{${registration.toString}}, '${infoMsg}', roles: ${roles.toString}, user provisioning enabled: ${provisioning}"""
}

/*
 * We want to have same attribute name for same things in both JWT and OAuth2/OIDC configuration files.
 */
object RudderRegistrationPropertyCommon {
  val A_NAME                    = "name"
  val A_CLIENT_ID               = "client.id"
  val A_CLIENT_SECRET           = "client.secret"
  val A_CLIENT_REDIRECT         = "client.redirect"
  val A_AUTH_METHOD             = "authMethod"
  val A_GRANT_TYPE              = "grantType"
  val A_INFO_MESSAGE            = "ui.infoMessage"
  val A_SCOPE                   = "scope"
  val A_AUDIENCE_CHECK          = "audience.check"
  val A_AUDIENCE_VALUE          = "audience.value"
  val A_TENANTS_ENABLED         = "tenants.enabled"
  val A_TENANTS_ATTRIBUTE       = "tenants.attribute"
  val A_TENANTS_OVERRIDE        = "tenants.override"
  val A_ENFORCE_TENANTS_MAPPING = "tenants.mapping.restricted"
  val A_TENANTS_MAPPING         = "tenants.mapping.entitlements"        // ie: OIDC tenant = Rudder tenant
  val A_TENANTS_REVERSE_MAPPING = "tenants.mapping.reverseEntitlements" // ie: Rudder tenants = OIDC tenants (overrides mapping)
  val A_URI_AUTH                = "uri.auth"
  val A_URI_TOKEN               = "uri.token"
  val A_URI_USER_INFO           = "uri.userInfo"
  val A_URI_JWK_SET             = "uri.jwkSet"
  val A_URI_LOGOUT              = "uri.logout"
  val A_URI_LOGOUT_REDIRECT     = "uri.logoutRedirect"
  val A_PIVOT_ATTRIBUTE         = "userNameAttributeName"
  val A_ROLES_ENABLED           = "roles.enabled"
  val A_ROLES_ATTRIBUTE         = "roles.attribute"
  val A_ROLES_OVERRIDE          = "roles.override"
  val A_ENFORCE_ROLES_MAPPING   = "roles.mapping.restricted"
  val A_ROLES_MAPPING           = "roles.mapping.entitlements"          // ie: OIDC role = Rudder role
  val A_ROLES_REVERSE_MAPPING   = "roles.mapping.reverseEntitlements"   // ie: Rudder role = OIDC role (overrides mapping)
  val A_PROVISIONING            = "enableProvisioning"

  val authMethods = {
    import ClientAuthenticationMethod.*
    List(CLIENT_SECRET_BASIC, CLIENT_SECRET_POST, CLIENT_SECRET_JWT, NONE)
  }

  val grantTypes = {
    import AuthorizationGrantType.*
    List(AUTHORIZATION_CODE, REFRESH_TOKEN, CLIENT_CREDENTIALS) // PASSWORD and IMPLICIT are deprecated for security reasons
  }

  val registrationAttributes = Map(
    A_NAME                    -> "human readable name to use in the button 'login with XXXX'",
    A_CLIENT_ID               -> "id generated in the Oauth2 service provider to identify rudder as a client app",
    A_CLIENT_SECRET           -> "the corresponding secret key",
    A_CLIENT_REDIRECT         -> "rudder URL to redirect to once authentication is done on the provider (must be resolvable from user browser)",
    A_AUTH_METHOD             -> s"authentication method to use (${authMethods.map(_.getValue).mkString(",")})",
    A_GRANT_TYPE              -> s"authorization grant type to use (${grantTypes.map(_.getValue).mkString(",")}",
    A_INFO_MESSAGE            -> "message displayed in the login form, for example to tell the user what login he must use",
    A_SCOPE                   -> "data scope to request access to",
    A_AUDIENCE_CHECK          -> "(default 'true') in the case of JWT, does Rudder need to check the 'aud' claim of the token?",
    A_AUDIENCE_VALUE          -> "(default 'io.rudder.api') in the case of JWT, the audience value that the token must have to be given access to an API",
    A_TENANTS_ENABLED         -> "(default false) if enable, restrict the authentication to the provided list of tenants",
    A_TENANTS_ATTRIBUTE       -> "(default '') the name of the attribute containing the list of authorized tenants",
    A_TENANTS_OVERRIDE        -> "(default false) keep user configured tenants in rudder-user.xml or override them with the one provided in the token",
    A_TENANTS_MAPPING         -> s"(optional) provides a map of alias `IdP tenant name` -> `Rudder tenant name`, where each IdP tenant name is a sub-key of '${A_TENANTS_MAPPING}'",
    A_TENANTS_REVERSE_MAPPING -> s"(optional) provides a map of alias `Rudder tenant name` -> `IdP tenant name`, where each IdP tenant name is a sub-key of '${A_TENANTS_MAPPING}', useful when the IdP tenant name contains '='",
    A_ENFORCE_TENANTS_MAPPING -> "(default true) if true, restricts roles available by the IdP to the role defined in mapping entitlement. Else the map provides alias for Rudder internal role names.",
    A_URI_AUTH                -> "provider URL to contact for main authentication (see provider documentation)",
    A_URI_TOKEN               -> "provider URL to contact for token verification (see provider documentation)",
    A_URI_USER_INFO           -> "provider URL to contact to get user information (see provider documentation)",
    A_URI_JWK_SET             -> "provider URL to check signature of JWT token (see provider documentation)",
    A_URI_LOGOUT              -> "(optional) provider URL to logout and end session (see provider documentation).",
    A_URI_LOGOUT_REDIRECT     -> "(optional) the redirect URL to provide to the IdP after logout",
    A_PIVOT_ATTRIBUTE         -> "the attribute used to find local app user",
    A_ROLES_ENABLED           -> "(default false) enable custom role extension by OIDC",
    A_ROLES_ATTRIBUTE         -> "the attribute to use for list of custom role name. It's content in token must be a array of strings.",
    A_ROLES_OVERRIDE          -> "(default false) keep user configured roles in rudder-user.xml or override them with the one provided in the token",
    A_PROVISIONING            -> "(default false) allows the automatic creation of users in Rudder in they successfully authenticate with OIDC",
    A_ROLES_MAPPING           -> s"(optional) provides a map of alias `IdP role name` -> `Rudder role name`, where each IdP role name is a sub-key of '${A_ROLES_MAPPING}'",
    A_ROLES_REVERSE_MAPPING   -> s"(optional) provides a map of alias `Rudder role name` -> `IdP role name`, where each IdP role name is a sub-key of '${A_ROLES_MAPPING}', useful when the IdP role name contains '='",
    A_ENFORCE_ROLES_MAPPING   -> "(default true) if true, restricts roles available by the IdP to the role defined in mapping entitlement. Else the map provides alias for Rudder internal role names."
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
                s"Requested OAUTH2 authentication methods '${method}' is not recognized, please use one of: ${authMethods.map(_.getValue).mkString("'", "','", "'")}"
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
            s"Requested OAUTH2 authorization grant type '${grant}' is not recognized, please use one of: ${grantTypes.map(_.getValue).mkString("'", "','", "'")}"
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
          case h :: tail => Accumulated(NonEmptyList.of(h, tail*)).fail
        }
    }
  }

  // utility method that read key in config and fails with an error message if
  // not found.
  protected[authbackends] def read(key: String)(implicit base: BasePath, config: Config): IOResult[String] = {
    val path = base.path(key)
    IOResult.attempt(s"Missing key '${path}' for registration '${base.id}' (${registrationAttributes(key)})")(
      config.getString(path)
    )
  }

  protected[authbackends] def toBool(s: String) = s.toLowerCase match {
    case "true" => true
    case _      => false
  }

  protected[authbackends] def readMap(
      key: String
  )(implicit base: BasePath, config: Config): IOResult[Map[String, String]] = {
    val path = base.path(key)
    import scala.jdk.CollectionConverters.*
    for {
      keySet <- IOResult
                  .attempt(s"Missing key '${path}' for OAUTH2 registration '${base.id}' (${registrationAttributes(key)})")(
                    config.getConfig(path).entrySet().asScala.map(_.getKey()).toList
                  )
                  .catchAll(_ =>
                    List().succeed
                  ) // in that case, we suppose that the key is just missing so we return a default empty value for mapping
      values <- ZIO.foreach(keySet) { key =>
                  val wholeKey = path + "." + key
                  IOResult.attempt(s"Error when reading role entitlement mapping '${wholeKey}'") {
                    (key, config.getString(wholeKey))
                  }
                }
    } yield values.toMap
  }

  protected[authbackends] def readRoles()(implicit base: BasePath, config: Config): IOResult[ProvidedRoles] = {
    for {
      rolesEnabled       <- read(A_ROLES_ENABLED).catchAll(_ => "false".succeed)
      rolesAttr          <- read(A_ROLES_ATTRIBUTE).catchAll(_ => "".succeed)
      rolesOverride      <- read(A_ROLES_OVERRIDE).catchAll(_ => "false".succeed)
      enforceRoleMapping <- read(A_ENFORCE_ROLES_MAPPING).catchAll(_ => "false".succeed)
      mapping            <- readMap(A_ROLES_MAPPING)
      reverseMapping     <- readMap(A_ROLES_REVERSE_MAPPING)
    } yield {
      ProvidedRoles(
        toBool(rolesEnabled),
        rolesAttr,
        toBool(rolesOverride),
        toBool(enforceRoleMapping),
        mapping ++ reverseMapping.map { case (a, b) => (b, a) }
      )
    }
  }

  protected[authbackends] def readTenants()(implicit base: BasePath, config: Config): IOResult[ProvidedTenants] = {
    for {
      tenantsEnabled     <- read(A_TENANTS_ENABLED).catchAll(_ => "false".succeed)
      tenantsAttr        <- read(A_TENANTS_ATTRIBUTE).catchAll(_ => "".succeed)
      tenantsOverride    <- read(A_TENANTS_OVERRIDE).catchAll(_ => "false".succeed)
      enforceRoleMapping <- read(A_ENFORCE_TENANTS_MAPPING).catchAll(_ => "false".succeed)
      mapping            <- readMap(A_TENANTS_MAPPING)
      reverseMapping     <- readMap(A_TENANTS_REVERSE_MAPPING)
    } yield {
      ProvidedTenants(
        toBool(tenantsEnabled),
        tenantsAttr,
        toBool(tenantsOverride),
        toBool(enforceRoleMapping),
        mapping ++ reverseMapping.map { case (a, b) => (b, a) }
      )
    }
  }

  def readProviders(config: Config, baseProperty: String): IOResult[List[String]] = {
    val path = baseProperty + ".registrations"
    IOResult.attempt(
      s"Missing property '${path}' which define the comma separated list of provider registration to use for OAUTH2."
    )(
      config.getString(path).split(",").map(_.trim).toList
    )
  }
}

/*
 * Client registration definition based on rudder property file:
 * - read property `rudder.auth.{oauth2,jwt}.client.registrations` which give a comma-separated
 *   list of client registration to oauth2 providers
 * - for each registration, read the needed properties based on template
 *   `rudder.auth.{oauth2,jwt}.client.${registration}.${propertyName}`
 */

// a base trait for common methods
trait RudderPropertyBasedRegistration[A <: RudderOAuth2Registration] {

  /*
   * Name of the authentication kind to use in logs
   */
  def registrationLogName: String
  /*
   * Name of the root path used for the base property.
   */
  def baseProperty:        String

  /*
   * The list of registration for that kind of JWT/OAuth2 authentication
   */
  def registrations: Ref[List[(String, A)]]

  /*
   * How to decode one authentication
   */
  def readOneRegistration(id: String, config: Config): IOResult[A]

  /*
   * Read the whole set of providers with their registrations.
   * We return a list to keep the order provided in the config file.
   */
  def readAllRegistrations(
      config:              Config,
      readOneRegistration: (String, Config) => IOResult[A]
  ): IOResult[List[(String, A)]] = {
    for {
      // we don't want to fail if the list of provider is missing, just log it as a warning
      providers     <- readProviders(config, baseProperty)
      // we don't want to fail if one of the registration is not ok, just log it
      _             <- AuthBackendsLoggerPure.info(s"List of configured providers for ${registrationLogName}: ${providers.mkString(", ")}")
      registrations <- ZIO.foreach(providers) { p =>
                         readOneRegistration(p, config).foldZIO(
                           err =>
                             AuthBackendsLoggerPure.error(
                               s"Error when reading ${registrationLogName} configuration for registration to '${p}' provider: ${err.fullMsg}'"
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

  /*
   * read information from config and update internal cache
   */
  def updateRegistration(config: Config): IOResult[Unit]

}

object RudderPropertyBasedJwtRegistrationDefinition {

  private val baseProperty        = "rudder.auth.jwt.provider"
  private val registrationLogName = "OAuth2 JWT"

  def make(): IOResult[RudderPropertyBasedJwtRegistrationDefinition] = {
    for {
      ref <- Ref.make(List.empty[(String, RudderJwtRegistration)])
    } yield {
      new RudderPropertyBasedJwtRegistrationDefinition(ref)
    }
  }

}

class RudderPropertyBasedJwtRegistrationDefinition(val registrations: Ref[List[(String, RudderJwtRegistration)]])
    extends RudderPropertyBasedRegistration[RudderJwtRegistration] {

  import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.*

  override def baseProperty:        String = RudderPropertyBasedJwtRegistrationDefinition.baseProperty
  override def registrationLogName: String = RudderPropertyBasedJwtRegistrationDefinition.registrationLogName

  def updateRegistration(config: Config): IOResult[Unit] = {
    for {
      newOnes <- readAllRegistrations(config, readOneRegistration)
      _       <- registrations.set(newOnes)
    } yield ()
  }

  def readOneRegistration(id: String, config: Config): IOResult[RudderJwtRegistration] = {
    implicit val base = BasePath(baseProperty, id)
    implicit val c    = config

    for {
      jwkSetUri     <- read(A_URI_JWK_SET)
      checkAudience <- read(A_AUDIENCE_CHECK).catchAll(_ => "true".succeed)
      audienceValue <- read(A_AUDIENCE_VALUE).catchAll(_ => "io.rudder.api".succeed)
      roles         <- readRoles()
      tenants       <- readTenants()
    } yield {
      RudderJwtRegistration(
        id,
        jwkSetUri,
        JwtAudience(toBool(checkAudience), audienceValue),
        roles,
        tenants
      )
    }
  }
}

/*
 * Client registration definition based on rudder property file:
 * - read property `rudder.auth.oauth2.client.registrations` which give a comma-separated
 *   list of client registration to oauth2 providers
 * - for each registration, read the needed properties based on template
 *   `rudder.auth.oauth2.client.${registration}.${propertyName}`
 */
object RudderPropertyBasedOAuth2RegistrationDefinition {
  private val baseProperty        = "rudder.auth.oauth2.provider"
  private val registrationLogName = "OAuth2/OIDC"

  def make(): IOResult[RudderPropertyBasedOAuth2RegistrationDefinition] = {
    for {
      ref <- Ref.make(List.empty[(String, RudderClientRegistration)])
    } yield {
      new RudderPropertyBasedOAuth2RegistrationDefinition(ref)
    }
  }
}

class RudderPropertyBasedOAuth2RegistrationDefinition(val registrations: Ref[List[(String, RudderClientRegistration)]])
    extends RudderPropertyBasedRegistration[RudderClientRegistration] {

  import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.*

  override def baseProperty:        String = RudderPropertyBasedOAuth2RegistrationDefinition.baseProperty
  override def registrationLogName: String = RudderPropertyBasedOAuth2RegistrationDefinition.registrationLogName

  /*
   * read information from config and update internal cache
   */
  override def updateRegistration(config: Config): IOResult[Unit] = {
    for {
      newOnes <- readAllRegistrations(config, readOneRegistration)
      _       <- registrations.set(newOnes)
      // for each oauthRegistration, register a logout action
      _       <- ZIO.foreach(newOnes) {
                   case (id, r) =>
                     r.logoutUrl match {
                       case Some(url) =>
                         val logoutAction =
                           LogoutPostAction(s"oauth2_end_session:${id}", BuildLogout.build(id, url, r.logoutRedirectUrl))
                         AuthBackendsLoggerPure.debug(s"Adding remote logout URL GET for '${id}'") *>
                         UserLogout.logoutActions.update(_.appended(logoutAction))
                       case None      =>
                         AuthBackendsLoggerPure.debug(s"No remote logout URL configured for '${id}'")
                     }
                 }
    } yield ()
  }

  override def readOneRegistration(id: String, config: Config): IOResult[RudderClientRegistration] = {
    implicit val base = BasePath(baseProperty, id)
    implicit val c    = config

    for {
      name                <- read(A_NAME)
      clientId            <- read(A_CLIENT_ID)
      clientSecret        <- read(A_CLIENT_SECRET)
      clientRedirect      <- read(A_CLIENT_REDIRECT)
      authMethod          <- read(A_AUTH_METHOD).flatMap(parseAuthenticationMethod(_).toIO)
      grantTypes          <- read(A_GRANT_TYPE).flatMap(parseAuthorizationGrantType(_).toIO)
      infoMessage         <- read(A_INFO_MESSAGE)
      scopes              <- read(A_SCOPE).flatMap(parseScope)
      uriAuth             <- read(A_URI_AUTH)
      uriToken            <- read(A_URI_TOKEN)
      uriUserInfo         <- read(A_URI_USER_INFO)
      pivotAttr           <- read(A_PIVOT_ATTRIBUTE)
      jwkSetUri           <- read(A_URI_JWK_SET)
      logoutUri           <- read(A_URI_LOGOUT).fold(_ => Option.empty[String], Some(_))
      logoutUriRedirect   <- read(A_URI_LOGOUT_REDIRECT).fold(_ => Option.empty[String], Some(_))
      roles               <- readRoles()
      tenants             <- readTenants()
      provisioningAllowed <- read(A_PROVISIONING).catchAll(_ => "false".succeed)
    } yield {
      RudderClientRegistration(
        ClientRegistration
          .withRegistrationId(id)
          .clientId(clientId)
          .clientSecret(clientSecret)
          .clientAuthenticationMethod(authMethod)
          .authorizationGrantType(grantTypes)
          .redirectUri(clientRedirect)
          .scope(scopes*)
          .authorizationUri(uriAuth)
          .tokenUri(uriToken)
          .userInfoUri(uriUserInfo)
          .userNameAttributeName(pivotAttr)
          .clientName(name)
          .jwkSetUri(jwkSetUri)
          .build(),
        logoutUri,
        logoutUriRedirect,
        infoMessage,
        roles,
        toBool(provisioningAllowed),
        tenants
      )
    }
  }

}
