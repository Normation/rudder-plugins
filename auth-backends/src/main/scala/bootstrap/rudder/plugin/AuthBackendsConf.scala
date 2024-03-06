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

package bootstrap.rudder.plugin

import bootstrap.liftweb.AuthBackendsProvider
import bootstrap.liftweb.AuthenticationMethods
import bootstrap.liftweb.RudderConfig
import bootstrap.liftweb.RudderInMemoryUserDetailsService
import bootstrap.liftweb.RudderProperties
import com.normation.errors.IOResult
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.authbackends.AuthBackendsLogger
import com.normation.plugins.authbackends.AuthBackendsLoggerPure
import com.normation.plugins.authbackends.AuthBackendsPluginDef
import com.normation.plugins.authbackends.AuthBackendsRepository
import com.normation.plugins.authbackends.CheckRudderPluginEnableImpl
import com.normation.plugins.authbackends.LoginFormRendering
import com.normation.plugins.authbackends.RudderClientRegistration
import com.normation.plugins.authbackends.RudderPropertyBasedOAuth2RegistrationDefinition
import com.normation.plugins.authbackends.api.AuthBackendsApiImpl
import com.normation.plugins.authbackends.snippet.Oauth2LoginBanner
import com.normation.rudder.Role
import com.normation.rudder.RudderRoles
import com.normation.rudder.api.AclPath
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.domain.eventlog.RudderEventActor
import com.normation.rudder.domain.logger.ApplicationLoggerPure
import com.normation.rudder.domain.logger.PluginLogger
import com.normation.rudder.rest.RoleApiMapping
import com.normation.rudder.users._
import com.normation.zio._
import com.typesafe.config.ConfigException
import java.net.URI
import java.util
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.joda.time.DateTime
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.convert.converter.Converter
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationProvider
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequestEntityConverter
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.client.web._
import org.springframework.security.oauth2.core._
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.util.StringUtils
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownContentTypeException
import scala.jdk.CollectionConverters._
import zio.syntax._

/*
 * Actual configuration of the plugin logic
 */
object AuthBackendsConf extends RudderPluginModule {
  /*
   * property name to do what to know with the login form:
   */
  val DISPLAY_LOGIN_FORM_PROP = "rudder.auth.displayLoginForm"

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService = new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val authBackendsProvider = new AuthBackendsProvider {
    def authenticationBackends = Set("ldap")
    def name                   = s"Enterprise Authentication Backends: '${authenticationBackends.mkString("','")}'"

    override def allowedToUseBackend(name: String): Boolean = {
      // same behavior for all authentication backends: only depends on the plugin status
      pluginStatusService.isEnabled()
    }
  }

  val oauthBackendNames = Set("oauth2", "oidc")
  RudderConfig.authenticationProviders.addProvider(authBackendsProvider)
  RudderConfig.authenticationProviders.addProvider(new AuthBackendsProvider() {
    override def authenticationBackends:            Set[String] = oauthBackendNames
    override def name:                              String      =
      s"Oauth2 and OpenID Connect authentication backends provider: '${authenticationBackends.mkString("','")}"
    override def allowedToUseBackend(name: String): Boolean     = pluginStatusService.isEnabled()
  })

  lazy val isOauthConfiguredByUser = {
    // We need to know if we have to initialize oauth/oicd specific code and snippet.
    // For that, we need to look in config file directly, because initialisation is complicated and we have no way to
    // know what part of auth is initialized before what other. It duplicates parsing, but it seems to be the price
    // of having plugins & spring. We let the full init be done in rudder itself.
    val configuredAuthProviders = AuthenticationMethods.getForConfig(RudderProperties.config).map(_.name)
    configuredAuthProviders.find(a => oauthBackendNames.contains(a)).isDefined
  }

  lazy val oauth2registrations = RudderPropertyBasedOAuth2RegistrationDefinition.make().runNow

  lazy val pluginDef = new AuthBackendsPluginDef(AuthBackendsConf.pluginStatusService)

  lazy val api = new AuthBackendsApiImpl(
    RudderConfig.restExtractorService,
    new AuthBackendsRepository(RudderConfig.authenticationProviders, RudderProperties.config)
  )

  lazy val loginFormRendering: LoginFormRendering = {
    try {
      LoginFormRendering.parse(RudderProperties.config.getString(DISPLAY_LOGIN_FORM_PROP)) match {
        case Right(v)  => v
        case Left(err) =>
          PluginLogger.warn(s"Error for property '${DISPLAY_LOGIN_FORM_PROP}': ${err}")
          LoginFormRendering.Show
      }
    } catch {
      case ex: ConfigException => // if not defined, default to "show"
        LoginFormRendering.Show
    }
  }

  // oauth2 button on login page
  if (isOauthConfiguredByUser) {
    PluginLogger.info(s"Oauthv2 or OIDC authentication backend is enabled, updating login form")
    RudderConfig.snippetExtensionRegister.register(
      new Oauth2LoginBanner(pluginStatusService, pluginDef.version, oauth2registrations)
    )
  }
}

/*
 * Entry point for spring. Because we love factories!
 */
@Configuration
class AuthBackendsSpringConfiguration extends ApplicationContextAware {

  /*
   * Manually create the update security filter chain to include OAUTH2/OIDC related filters.
   *
   * Here, the correct solution would likely be to use a code-base
   * configuration in place of applicationContext-security.xml,
   * and provide a hook in the `HttpSecurity` configurer to let plugins
   * add their configuration. Everything would be so much easier.
   * Since we don't have that code base config, we need to:
   * - init all the (oauth2, oidc)AuthenticationProvider by hand in rudder (since we don't have an xml file for them)
   * - build filter by hand. To know what to do and avoid NPEs, we look how it's done in the code configurer class
   * - hi-jack the main security filter, declared in applicationContext-security.xml, update the list of filters,
   *   and put it back into spring.
   */
  override def setApplicationContext(applicationContext: ApplicationContext): Unit = {
    if (AuthBackendsConf.isOauthConfiguredByUser) {
      // create the two OAUTH2 filters that are going to be added in the filter chain.
      // logic copy/pasted from OAuth2LoginConfigurer init and configure methods
      def createFilters(rudderUserDetailsService: RudderInMemoryUserDetailsService) = {
        val authenticationFilter: OAuth2LoginAuthenticationFilter = new OAuth2LoginAuthenticationFilter(
          clientRegistrationRepository,
          authorizedClientRepository,
          loginProcessingUrl
        ) {
          override def attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication = {
            AuthBackendsLogger.debug(s"Processing OAuth2/OIDC authorisation validation and starting authentication request")
            val auth = super.attemptAuthentication(request, response)
            auth
          }
        }

        val authorizationRequestFilter = new OAuth2AuthorizationRequestRedirectFilter(authorizationRequestResolver)
        authorizationRequestFilter.setAuthorizationRequestRepository(authorizationRequestRepository)
        // request cache ?
        // authorizationRequestFilter.setRequestCache( ??? )

        authenticationFilter.setFilterProcessesUrl(loginProcessingUrl)
        authenticationFilter.setAuthorizationRequestRepository(authorizationRequestRepository)
        authenticationFilter.setAuthenticationSuccessHandler(rudderOauth2AuthSuccessHandler)
        authenticationFilter.setAuthenticationFailureHandler(
          applicationContext.getBean("rudderWebAuthenticationFailureHandler").asInstanceOf[AuthenticationFailureHandler]
        )

        (authorizationRequestFilter, authenticationFilter)
      }

      val http = applicationContext.getBean("mainHttpSecurityFilters", classOf[DefaultSecurityFilterChain])

      val rudderUserService      = applicationContext.getBean("rudderUserDetailsService", classOf[RudderInMemoryUserDetailsService])
      val registrationRepository =
        applicationContext.getBean("clientRegistrationRepository", classOf[RudderClientRegistrationRepository])

      val (oAuth2AuthorizationRequestRedirectFilter, oAuth2LoginAuthenticationFilter) = createFilters(rudderUserService)

      // add authentication providers to rudder list

      RudderConfig.authenticationProviders.addSpringAuthenticationProvider(
        RudderOAuth2UserService.PROTOCOL_ID,
        oauth2AuthenticationProvider(rudderUserService, registrationRepository, userRepository, roleApiMapping)
      )
      RudderConfig.authenticationProviders.addSpringAuthenticationProvider(
        RudderOidcUserService.PROTOCOL_ID,
        oidcAuthenticationProvider(rudderUserService, registrationRepository, userRepository, roleApiMapping)
      )
      val manager =
        applicationContext.getBean("org.springframework.security.authenticationManager", classOf[AuthenticationManager])
      oAuth2LoginAuthenticationFilter.setAuthenticationManager(manager)

      val filters          = http.getFilters
      filters.add(3, oAuth2AuthorizationRequestRedirectFilter)
      filters.add(4, oAuth2LoginAuthenticationFilter)
      val newSecurityChain = new DefaultSecurityFilterChain(http.getRequestMatcher, filters)

      applicationContext.getAutowireCapableBeanFactory.configureBean(newSecurityChain, "mainHttpSecurityFilters")
    }
  }

  @Bean def userRepository: UserRepository = RudderConfig.userRepository
  @Bean def roleApiMapping: RoleApiMapping = new RoleApiMapping(RudderConfig.authorizationApiMapping)

  @Bean def rudderOauth2AuthSuccessHandler: AuthenticationSuccessHandler = new SimpleUrlAuthenticationSuccessHandler(
    "/secure/index.html"
  )

  /**
   * We read configuration for OIDC providers in rudder config files.
   * The format is defined in
   */
  @Bean def clientRegistrationRepository: RudderClientRegistrationRepository = {
    val registrations = (
      for {
        _ <- AuthBackendsConf.oauth2registrations.updateRegistration(RudderProperties.config)
        r <- AuthBackendsConf.oauth2registrations.registrations.get
      } yield {
        r.toMap
      }
    ).foldZIO(
      err =>
        (if (AuthBackendsConf.isOauthConfiguredByUser) {
           AuthBackendsLoggerPure.error(err.fullMsg)
         } else {
           AuthBackendsLoggerPure.debug(err.fullMsg)
         }) *> Map.empty[String, RudderClientRegistration].succeed,
      ok => ok.succeed
    ).runNow

    new RudderClientRegistrationRepository(registrations)
  }

  /**
   * We don't use rights provided by spring, nor the one provided by OAUTH2, so
   * always map user to role `ROLE_USER`
   */
  @Bean def userAuthoritiesMapper = {

    new GrantedAuthoritiesMapper {
      override def mapAuthorities(authorities: util.Collection[_ <: GrantedAuthority]): util.Collection[_ <: GrantedAuthority] = {
        authorities.asScala.flatMap {
          case _: OidcUserAuthority | _: OAuth2UserAuthority =>
            Some(new SimpleGrantedAuthority("ROLE_USER"))
          case _                                             => None // ignore, no granted authority for it
        }
      }.asJavaCollection
    }
  }

  /**
   *  Retrieve rudder user based on information provided in the oidc token.
   *  Create an hybride Oidc/Rudder UserDetails.
   */
  @Bean def oidcUserService(
      rudderUserDetailsService: RudderInMemoryUserDetailsService,
      registrationRepository:   RudderClientRegistrationRepository,
      userRepository:           UserRepository,
      roleApiMapping:           RoleApiMapping
  ): OidcUserService = {
    new RudderOidcUserService(rudderUserDetailsService, registrationRepository, userRepository, roleApiMapping)
  }

  @Bean def oauth2UserService(
      rudderUserDetailsService: RudderInMemoryUserDetailsService,
      registrationRepository:   RudderClientRegistrationRepository,
      userRepository:           UserRepository,
      roleApiMapping:           RoleApiMapping
  ): OAuth2UserService[OAuth2UserRequest, OAuth2User] = {
    new RudderOAuth2UserService(rudderUserDetailsService, registrationRepository, userRepository, roleApiMapping)
  }

  // following beans are the default one provided by spring security for oauth2 logic

  val authorizationRequestBaseUri = "/oauth2/authorization"
  val loginProcessingUrl          = "/login/oauth2/code/*"

  @Bean def authorizationRequestResolver = new RudderDefaultOAuth2AuthorizationRequestResolver(
    clientRegistrationRepository,
    authorizationRequestBaseUri
  )

  @Bean def authorizedClientRepository: OAuth2AuthorizedClientRepository =
    new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService)

  @Bean def authorizedClientService: OAuth2AuthorizedClientService =
    new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)

  @Bean def authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository()

  @Bean def rudderAuthorizationCodeTokenResponseClient() = {
    new DefaultAuthorizationCodeTokenResponseClient()
  }

  @Bean def jwtDecoderFactory = new OidcIdTokenDecoderFactory()

  @Bean def oauth2AuthenticationProvider(
      rudderUserDetailsService: RudderInMemoryUserDetailsService,
      registrationRepository:   RudderClientRegistrationRepository,
      userRepository:           UserRepository,
      roleApiMapping:           RoleApiMapping
  ) = {
    val x = new OAuth2LoginAuthenticationProvider(
      rudderAuthorizationCodeTokenResponseClient(),
      oauth2UserService(rudderUserDetailsService, registrationRepository, userRepository, roleApiMapping)
    )
    x.setAuthoritiesMapper(userAuthoritiesMapper)
    x
  }

  @Bean def oidcAuthenticationProvider(
      rudderUserDetailsService: RudderInMemoryUserDetailsService,
      registrationRepository:   RudderClientRegistrationRepository,
      userRepository:           UserRepository,
      roleApiMapping:           RoleApiMapping
  ): OidcAuthorizationCodeAuthenticationProvider = {
    val x = new OidcAuthorizationCodeAuthenticationProvider(
      rudderAuthorizationCodeTokenResponseClient(),
      oidcUserService(rudderUserDetailsService, registrationRepository, userRepository, roleApiMapping)
    )
    x.setJwtDecoderFactory(jwtDecoderFactory)
    x.setAuthoritiesMapper(userAuthoritiesMapper)
    x
  }
}

// a couple of dedicated user details that have the needed information for the SSO part

class RudderClientRegistrationRepository(val registrations: Map[String, RudderClientRegistration])
    extends ClientRegistrationRepository {
  override def findByRegistrationId(registrationId: String): ClientRegistration = {
    registrations.get(registrationId) match {
      case None    => null
      case Some(x) => x.registration
    }
  }
}

final class RudderOidcDetails(oidc: OidcUser, rudder: RudderUserDetail)
    extends RudderUserDetail(rudder.account, rudder.status, rudder.roles, rudder.apiAuthz) with OidcUser {
  override def getClaims:     util.Map[String, AnyRef] = oidc.getClaims
  override def getUserInfo:   OidcUserInfo             = oidc.getUserInfo
  override def getIdToken:    OidcIdToken              = oidc.getIdToken
  override def getAttributes: util.Map[String, AnyRef] = oidc.getAttributes
  override def getName:       String                   = oidc.getName
}

final class RudderOauth2Details(oauth2: OAuth2User, rudder: RudderUserDetail)
    extends RudderUserDetail(rudder.account, rudder.status, rudder.roles, rudder.apiAuthz) with OAuth2User {
  override def getAttributes: util.Map[String, AnyRef] = oauth2.getAttributes
  override def getName:       String                   = oauth2.getName
}

/*
 * We need to reimplement these methods because we need to correctly manage errors without having horrible
 * stack traces in logs. Classes below are reimplementation or extension of the corresponding Spring classes
 * with these features.
 */

class RudderDefaultOAuth2AuthorizationRequestResolver(
    clientRegistrationRepository: ClientRegistrationRepository,
    authorizationRequestBaseUri:  String
) extends OAuth2AuthorizationRequestResolver {
  val resolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri)

  def cleanResolve(request: HttpServletRequest, registrationId: Option[String]) = {
    val authzRequest = {
      try {
        registrationId match {
          case None     => resolver.resolve(request)
          case Some(id) => resolver.resolve(request, id)
        }
      } catch {
        case ex: Exception =>
          val err = new OAuth2Error("authorization_request_build_error", ex.getMessage, null)
          throw new OAuth2AuthenticationException(err, err.toString, ex)
      }
    }
    if (null != authzRequest && AuthBackendsLogger.isDebugEnabled) {
      AuthBackendsLogger.debug(s"Processing OAuth2/OIDC authorization to: ${authzRequest.getAuthorizationUri}")
      if (AuthBackendsLogger.isTraceEnabled) {
        val traceReq = s"\n  - authorization URI: ${authzRequest.getAuthorizationUri}" +
          s"\n  - grant type: ${authzRequest.getGrantType.getValue}" +
          s"\n  - response type: ${authzRequest.getResponseType.getValue}" +
          s"\n  - client ID: ${authzRequest.getClientId}" +
          s"\n  - redirect URI: ${authzRequest.getRedirectUri}" +
          s"\n  - scopes: ${authzRequest.getScopes.asScala.mkString(" ")}" +
          s"\n  - state: ${authzRequest.getState}" +
          s"\n  - additional parameters: ${authzRequest.getAdditionalParameters.asScala.toList.mkString("; ")}" +
          s"\n  - authorization request URI: ${authzRequest.getAuthorizationRequestUri}" +
          s"\n  - attributes: ${authzRequest.getAttributes.asScala.toList.mkString("; ")}"
        AuthBackendsLogger.trace(s"  authorization request details: ${traceReq}")
      }
    }
    authzRequest
  }

  override def resolve(request: HttpServletRequest): OAuth2AuthorizationRequest = {
    cleanResolve(request, None)
  }

  override def resolve(request: HttpServletRequest, registrationId: String): OAuth2AuthorizationRequest = {
    cleanResolve(request, Some(registrationId))
  }

}

trait RudderUserServerMapping[R <: OAuth2UserRequest, U <: OAuth2User, T <: RudderUserDetail with U] {

  def registrationRepository: RudderClientRegistrationRepository
  def protocolId:             String
  def protocolName:           String

  def mapRudderUser(
      delegateLoadUser:         R => U,
      rudderUserDetailsService: RudderInMemoryUserDetailsService,
      userRepository:           UserRepository,
      roleApiMapping:           RoleApiMapping,
      userRequest:              R,
      newUserDetails:           (U, RudderUserDetail) => T
  ): T = {
    val user = delegateLoadUser(userRequest)
    val sub  = user.getAttributes.get("sub").toString
    AuthBackendsLogger.debug(
      s"Identifying ${protocolName} user info with sub: '${sub}' on rudder user base using login: '${user.getName}'"
    )

    val optReg = registrationRepository.registrations.get(userRequest.getClientRegistration.getRegistrationId)

    // check that we know that user in our DB, else if "provisioning" is enabled, create it
    val rudderUser = {
      try {
        rudderUserDetailsService.loadUserByUsername(user.getName)
      } catch {
        case ex: UsernameNotFoundException if (optReg.map(_.provisioning).getOrElse(false)) =>
          val idp = optReg.map(_.registration.getRegistrationId).getOrElse("")
          // provisioning is enabled, create the user and try again
          (userRepository
            .setExistingUsers(
              protocolId,
              List(user.getName),
              EventTrace(
                RudderEventActor,
                DateTime.now(),
                s"Provisioning is enabled for ${protocolName} '${idp}'"
              )
            ) *> ApplicationLoggerPure.User.info(
            s"User '${user.getName}' automatically created because provisioning is enabled for ${protocolName} '${idp}'"
          )).runNow

          // retry
          rudderUserDetailsService.loadUserByUsername(user.getName)
      }
    }

    buildUser(optReg, userRequest, user, roleApiMapping, rudderUser, newUserDetails)
  }

  def buildUser(
      optReg:         Option[RudderClientRegistration],
      userRequest:    R,
      user:           U,
      roleApiMapping: RoleApiMapping,
      rudder:         RudderUserDetail,
      userBuilder:    (U, RudderUserDetail) => T
  ): T = {
    val roles = {
      optReg match {
        case None      =>
          AuthBackendsLogger.trace(
            s"No configuration found for ${protocolName} registration id: ${userRequest.getClientRegistration.getRegistrationId}"
          )
          rudder.roles // if no registration, use user roles
        case Some(reg) =>
          if (reg.roles.enabled) {
            val custom = {
              try {
                import scala.jdk.CollectionConverters._
                if (user.getAttributes.containsKey(reg.roles.attributeName)) {
                  user
                    .getAttribute[java.util.ArrayList[String]](reg.roles.attributeName)
                    .asScala
                    .toSet
                } else {
                  AuthBackendsLogger.warn(
                    s"User '${rudder.getUsername}' returned information does not contain an attribute '${reg.roles.attributeName}' " +
                    s"which is the one configured for custom role provisioning (see 'rudder.auth.oauth2.provider.$${idpID}.roles.attribute'" +
                    s" value). Please check that the attribute name is correct and that requested scope provides that attribute."
                  )
                  Set.empty[String]
                }
              } catch {
                case ex: Exception =>
                  AuthBackendsLogger.warn(
                    s"Unable to get custom roles for user '${rudder.getUsername}' when looking for attribute '${reg.roles.attributeName}' :${ex.getMessage}'"
                  )
                  Set.empty[String]
              }
            }

            // check if we have role mapping or restriction
            val filteredRoles = if (reg.restrictRoleMapping) {
              val f = custom.filter(r => reg.roleMapping.keySet.contains(r))
              AuthBackendsLogger.debug(
                s"IdP configuration enforce restriction to mapped role, resulting filtered list: [${f.mkString(", ")}]"
              )
              f
            } else custom
            AuthBackendsLogger.trace(
              s"IdP configuration has registered role mapping: [${reg.roleMapping.toList.sorted.map(x => s"${x._1 -> x._2}").mkString("; ")}]"
            )
            val mappedRoles: Set[Role] = filteredRoles.flatMap { r =>
              val role = reg.roleMapping.get(r) match {
                // if the role is not in the mapping, use the provided name as is.
                case None    => RudderRoles.findRoleByName(r)
                case Some(m) =>
                  AuthBackendsLogger.debug(
                    s"Principal '${rudder.getUsername}': mapping IdP provided role '${r}' to Rudder role '${m}' "
                  )
                  RudderRoles
                    .findRoleByName(m)
                    .map(_.map(x => Role.Alias(x, r, s"Alias from ${reg.registration.getRegistrationId} IdP")))
              }
              role.runNow.orElse {
                AuthBackendsLogger.debug(
                  s"Role '${r}' does not match any Rudder role, ignoring it for user ${rudder.getUsername}"
                )
                None
              }
            }

            if (mappedRoles.nonEmpty) {
              ApplicationLoggerPure.Authz.logEffect.info(
                s"Principal '${rudder.getUsername}' role list extended with ${protocolName} provided roles: [${Role
                    .toDisplayNames(mappedRoles)
                    .mkString(", ")}] (override: ${reg.roles.over})"
              )
            } else {
              AuthBackendsLogger.debug(
                s"No roles provided by ${protocolName} in attribute: ${reg.roles.attributeName} (or attribute is missing, or user-management plugin is missing)"
              )
            }

            val roles = if (reg.roles.over) {
              // override means: don't use user role configured in rudder-users.xml
              mappedRoles
            } else {
              rudder.roles ++ mappedRoles
            }
            AuthBackendsLogger.debug(
              s"Principal '${rudder.getUsername}' final list of roles: [${roles.map(_.name).mkString(", ")}]"
            )
            roles

          } else {
            AuthBackendsLogger.debug(s"${protocolName} configuration is not configured to use token provided roles")
            rudder.roles
          }
      }
    }
    // we derive api authz from users rights
    val acls  = roleApiMapping
      .getApiAclFromRoles(roles.toSeq)
      .groupBy(_.path.parts.head)
      .flatMap {
        case (_, seq) =>
          seq.sortBy(_.path)(AclPath.orderingaAclPath).sortBy(_.path.parts.head.value)
      }
      .toList

    val apiAuthz    = ApiAuthorization.ACL(acls)
    val userDetails = rudder
      .copy(roles = roles, apiAuthz = apiAuthz)
    AuthBackendsLogger.debug(
      s"Principal '${rudder.getUsername}' final roles: [${roles.map(_.name).mkString(", ")}], and API authz: ${apiAuthz.debugString}"
    )
    // we need to update roles in all cases
    userBuilder(user, userDetails)
  }

}

object RudderOidcUserService {
  val PROTOCOL_ID = "oidc"
}
class RudderOidcUserService(
    rudderUserDetailsService:            RudderInMemoryUserDetailsService,
    override val registrationRepository: RudderClientRegistrationRepository,
    userRepository:                      UserRepository,
    roleApiMapping:                      RoleApiMapping
) extends OidcUserService with RudderUserServerMapping[OidcUserRequest, OidcUser, RudderUserDetail with OidcUser] {

  // we need to use our copy of DefaultOAuth2UserService to log/manage errors
  super.setOauth2UserService(new RudderDefaultOAuth2UserService())

  override val protocolId   = RudderOidcUserService.PROTOCOL_ID
  override val protocolName = "OIDC"

  override def loadUser(userRequest: OidcUserRequest): OidcUser = {
    mapRudderUser(
      super.loadUser(_),
      rudderUserDetailsService,
      userRepository,
      roleApiMapping,
      userRequest,
      new RudderOidcDetails(_, _)
    )
  }
}

object RudderOAuth2UserService {
  val PROTOCOL_ID = "oauth2"
}
class RudderOAuth2UserService(
    rudderUserDetailsService:            RudderInMemoryUserDetailsService,
    override val registrationRepository: RudderClientRegistrationRepository,
    userRepository:                      UserRepository,
    roleApiMapping:                      RoleApiMapping
) extends OAuth2UserService[OAuth2UserRequest, OAuth2User]
    with RudderUserServerMapping[OAuth2UserRequest, OAuth2User, RudderUserDetail with OAuth2User] {
  val defaultUserService = new RudderDefaultOAuth2UserService()

  override val protocolId   = RudderOAuth2UserService.PROTOCOL_ID
  override val protocolName = "OAuth2"

  override def loadUser(userRequest: OAuth2UserRequest): OAuth2User = {
    mapRudderUser(
      defaultUserService.loadUser(_),
      rudderUserDetailsService,
      userRepository,
      roleApiMapping,
      userRequest,
      new RudderOauth2Details(_, _)
    )
  }
}

/*
 * An utility to help build logout request to IdP
 */
object BuildLogout {
  // will call by GET the given string and happening if possible "id_token_hint=${id_token}"
  def build(registrationId: String, url: String, logoutRedirect: Option[String]): Authentication => IOResult[Option[URI]] = {
    (authentication: Authentication) =>
      {
        authentication match {
          case oauth2Token: OAuth2AuthenticationToken =>
            for {
              queryParam <- oauth2Token.getPrincipal match {
                              case oidc: OidcUser =>
                                val redirect = logoutRedirect match {
                                  case None    => ""
                                  case Some(u) => s"&post_logout_redirect_uri=${u}"
                                }
                                s"?id_token_hint=${oidc.getIdToken.getTokenValue}${redirect}".succeed
                              case x =>
                                AuthBackendsLoggerPure.debug(
                                  s"That user kind of oauth2 token (${x}) does not provide a token_id"
                                ) *>
                                "".succeed
                            }
              // query IdP
              fullUrl     = url + queryParam
              _          <- AuthBackendsLoggerPure.debug(s"OAuth2/OIDC logout: calling remote URL for '${registrationId}': ${fullUrl}")
              uri        <- IOResult.attempt(s"Error: bad redirect URL")(URI.create(fullUrl))
            } yield Some(uri)
          case x =>
            AuthBackendsLoggerPure.debug(s"That kind of authentication (${x}) does not support remote logout") *> None.succeed
        }
      }
  }
}

class RudderDefaultOAuth2UserService extends DefaultOAuth2UserService {

  /*
   * this is a copy of parent method with more logs/error management
   */
  private val MISSING_USER_INFO_URI_ERROR_CODE: String = "oauth2:missing_user_info_uri"

  private val MISSING_USER_NAME_ATTRIBUTE_ERROR_CODE: String = "oauth2:missing_user_name_attribute"

  private val INVALID_USER_INFO_RESPONSE_ERROR_CODE: String = "oauth2:invalid_user_info_response"

  private val PARAMETERIZED_RESPONSE_TYPE: ParameterizedTypeReference[java.util.Map[String, AnyRef]] =
    new ParameterizedTypeReference[java.util.Map[String, AnyRef]]() {}

  private val requestEntityConverter: Converter[OAuth2UserRequest, RequestEntity[_]] = new OAuth2UserRequestEntityConverter

  private val restOperations: RestOperations = {
    val restTemplate = new RestTemplate
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler)
    restTemplate
  }

  import scala.jdk.CollectionConverters._

  @throws[OAuth2AuthenticationException]
  override def loadUser(userRequest: OAuth2UserRequest): OAuth2User = {
    if (!(StringUtils.hasText(userRequest.getClientRegistration.getProviderDetails.getUserInfoEndpoint.getUri))) {
      val oauth2Error: OAuth2Error = new OAuth2Error(
        MISSING_USER_INFO_URI_ERROR_CODE,
        "Missing required UserInfo Uri in UserInfoEndpoint for Client Registration: " + userRequest.getClientRegistration.getRegistrationId,
        null
      )
      throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString)
    }
    val userNameAttributeName: String                                        =
      userRequest.getClientRegistration.getProviderDetails.getUserInfoEndpoint.getUserNameAttributeName
    if (!(StringUtils.hasText(userNameAttributeName))) {
      val oauth2Error: OAuth2Error = new OAuth2Error(
        MISSING_USER_NAME_ATTRIBUTE_ERROR_CODE,
        "Missing required \"user name\" attribute name in UserInfoEndpoint for Client Registration: " + userRequest.getClientRegistration.getRegistrationId,
        null
      )
      throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString)
    }
    val request:               RequestEntity[_]                              = this.requestEntityConverter.convert(userRequest)
    val response:              ResponseEntity[java.util.Map[String, AnyRef]] = getResponse(userRequest, request)

    AuthBackendsLogger.debug(
      s"OAuth2/OIDC user info request with scopes [${userRequest.getClientRegistration.getScopes.asScala.toList.sorted.mkString(" ")}] " +
      s"returned attributes: ${response.getBody.asScala.keySet.toList.sorted.mkString(", ")}"
    )
    AuthBackendsLogger.trace(
      s"  user info attribute values: ${response.getBody.asScala.toList.sortBy(_._1).map { case (k, v) => s"$k: $v" }.mkString("\n  - ", "\n  - ", "")} "
    )

    val userAttributes: java.util.Map[String, AnyRef]   = response.getBody
    val authorities:    java.util.Set[GrantedAuthority] = new java.util.LinkedHashSet[GrantedAuthority]()
    authorities.add(new OAuth2UserAuthority(userAttributes))
    val token:          OAuth2AccessToken               = userRequest.getAccessToken
    token.getScopes.asScala.foreach(authority => authorities.add(new SimpleGrantedAuthority("SCOPE_" + authority)))
    try {
      new DefaultOAuth2User(authorities, userAttributes, userNameAttributeName)
    } catch {
      case ex: Exception =>
        val oauth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE, ex.getMessage, null)
        throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString)
    }
  }

  private def getResponse(
      userRequest: OAuth2UserRequest,
      request:     RequestEntity[_]
  ): ResponseEntity[java.util.Map[String, AnyRef]] = {
    try {
      return this.restOperations.exchange(request, PARAMETERIZED_RESPONSE_TYPE)
    } catch {
      case ex: OAuth2AuthorizationException =>
        var oauth2Error:  OAuth2Error   = ex.getError
        val errorDetails: StringBuilder = new StringBuilder
        errorDetails.append("Error details: [")
        errorDetails
          .append("UserInfo Uri: ")
          .append(userRequest.getClientRegistration.getProviderDetails.getUserInfoEndpoint.getUri)
        errorDetails.append(", Error Code: ").append(oauth2Error.getErrorCode)
        if (oauth2Error.getDescription != null) {
          errorDetails.append(", Error Description: ").append(oauth2Error.getDescription)
        }
        errorDetails.append("]")
        oauth2Error = new OAuth2Error(
          INVALID_USER_INFO_RESPONSE_ERROR_CODE,
          "An error occurred while attempting to retrieve the UserInfo Resource: " + errorDetails.toString,
          null
        )
        throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString, ex)
      case ex: UnknownContentTypeException  =>
        val errorMessage: String      =
          "An error occurred while attempting to retrieve the UserInfo Resource from '" + userRequest.getClientRegistration.getProviderDetails.getUserInfoEndpoint.getUri + "': response contains invalid content type '" + ex.getContentType.toString + "'. " + "The UserInfo Response should return a JSON object (content type 'application/json') " + "that contains a collection of name and value pairs of the claims about the authenticated End-User. " + "Please ensure the UserInfo Uri in UserInfoEndpoint for Client Registration '" + userRequest.getClientRegistration.getRegistrationId + "' conforms to the UserInfo Endpoint, " + "as defined in OpenID Connect 1.0: 'https://openid.net/specs/openid-connect-core-1_0.html#UserInfo'"
        val oauth2Error:  OAuth2Error = new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE, errorMessage, null)
        throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString, ex)
      case ex: RestClientException          =>
        val oauth2Error: OAuth2Error = new OAuth2Error(
          INVALID_USER_INFO_RESPONSE_ERROR_CODE,
          "An error occurred while attempting to retrieve the UserInfo Resource: " + ex.getMessage,
          null
        )
        throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString, ex)
    }
  }
}
