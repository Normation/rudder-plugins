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
import com.github.benmanes.caffeine.cache.Caffeine
import com.normation.errors.IOResult
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.authbackends.*
import com.normation.plugins.authbackends.api.AuthBackendsApiImpl
import com.normation.plugins.authbackends.snippet.Oauth2LoginBanner
import com.normation.rudder.Role
import com.normation.rudder.RudderRoles
import com.normation.rudder.api.*
import com.normation.rudder.api.ApiAccountExpirationPolicy.ExpireAtDate
import com.normation.rudder.domain.eventlog.RudderEventActor
import com.normation.rudder.domain.logger.ApplicationLoggerPure
import com.normation.rudder.domain.logger.PluginLogger
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.rest.RoleApiMapping
import com.normation.rudder.users.*
import com.normation.zio.*
import com.typesafe.config.ConfigException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URI
import java.time.Instant
import java.util
import org.joda.time.DateTime
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.convert.converter.Converter
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.core.userdetails.UserDetails
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
import org.springframework.security.oauth2.client.web.*
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.DefaultOpaqueTokenAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.util.StringUtils
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.UnknownContentTypeException
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import zio.ZIO
import zio.syntax.*

/*
 * Actual configuration of the plugin logic
 */
object AuthBackendsConf extends RudderPluginModule {
  /*
   * property name to do what to know with the login form:
   */
  val DISPLAY_LOGIN_FORM_PROP = "rudder.auth.displayLoginForm"

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService = new CheckRudderPluginEnableImpl(RudderConfig.nodeFactRepository)

  lazy val authBackendsProvider = new AuthBackendsProvider {
    def authenticationBackends = Set("ldap")
    def name                   = s"Enterprise Authentication Backends: '${authenticationBackends.mkString("','")}'"

    override def allowedToUseBackend(name: String): Boolean = {
      // same behavior for all authentication backends: only depends on the plugin status
      pluginStatusService.isEnabled()
    }
  }

  private val oauthBackendNames       = Set(RudderOAuth2UserService.PROTOCOL_ID, RudderOidcUserService.PROTOCOL_ID)
  // Javascript web token
  private val jwtBackendNames         = Set(RudderJwtAuthenticationProvider.PROTOCOL_ID)
  // opaque bearer token
  private val opaqueTokenBackendNames = Set(RudderOpaqueTokenAuthenticationProvider.PROTOCOL_ID)

  RudderConfig.authenticationProviders.addProvider(authBackendsProvider)
  RudderConfig.authenticationProviders.addProvider(new AuthBackendsProvider() {
    override def authenticationBackends: Set[String] = oauthBackendNames
    override def name:                   String      =
      s"Oauth2 and OpenID Connect authentication backends provider: '${authenticationBackends.mkString("','")}"
    override def allowedToUseBackend(name: String): Boolean = pluginStatusService.isEnabled()
  })
  RudderConfig.authenticationProviders.addProvider(new AuthBackendsProvider() {
    override def authenticationBackends: Set[String] = jwtBackendNames
    override def name:                   String      =
      s"Oauth2 and OpenID Connect authentication backends provider for JWT Bearer token in REST API: '${authenticationBackends.mkString("','")}"
    override def allowedToUseBackend(name: String): Boolean = pluginStatusService.isEnabled()
  })
  RudderConfig.authenticationProviders.addProvider(new AuthBackendsProvider() {
    override def authenticationBackends: Set[String] = opaqueTokenBackendNames
    override def name:                   String      =
      s"Oauth2 and OpenID Connect authentication backends provider for Opaque Bearer token in REST API: '${authenticationBackends.mkString("','")}"
    override def allowedToUseBackend(name: String): Boolean = pluginStatusService.isEnabled()
  })

  lazy val (isOauthConfiguredByUser: Boolean, isJwtConfiguredByUser: Boolean, isOpaqueTokenConfiguredByUser: Boolean) = {
    // We need to know if we have to initialize oauth/oidc specific code and snippet.
    // For that, we need to look in config file directly, because initialisation is complicated and we have no way to
    // know what part of auth is initialized before what other. It duplicates parsing, but it seems to be the price
    // of having plugins & spring. We let the full init be done in rudder itself.
    val configuredAuthProviders = AuthenticationMethods.getForConfig(RudderProperties.config).map(_.name)
    (
      configuredAuthProviders.exists(a => oauthBackendNames.contains(a)),
      configuredAuthProviders.exists(a => jwtBackendNames.contains(a)),
      configuredAuthProviders.exists(a => opaqueTokenBackendNames.contains(a))
    )
  }

  lazy val oauth2registrations      = RudderPropertyBasedOAuth2RegistrationDefinition.make().runNow
  lazy val jwtRegistrations         = RudderPropertyBasedJwtRegistrationDefinition.make().runNow
  lazy val opaqueTokenRegistrations = RudderPropertyBasedOpaqueTokenRegistrationDefinition.make().runNow

  override lazy val pluginDef: AuthBackendsPluginDef = new AuthBackendsPluginDef(AuthBackendsConf.pluginStatusService)

  lazy val api = new AuthBackendsApiImpl(
    new AuthBackendsRepositoryImpl(RudderConfig.authenticationProviders, RudderProperties.config)
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
      new Oauth2LoginBanner(pluginStatusService, oauth2registrations)
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
        // here, we can't use applicationContext.getBean without circular reference. It will be put in Spring cache.
        oauth2AuthenticationProvider(rudderUserService, registrationRepository, userRepository, roleApiMapping)
      )
      RudderConfig.authenticationProviders.addSpringAuthenticationProvider(
        RudderOidcUserService.PROTOCOL_ID,
        // here, we can't use applicationContext.getBean without circular reference. It will be put in Spring cache.
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

    // Adding API authentication protected with OAuth2 thanks to a JWT "bearer token"
    if (AuthBackendsConf.isJwtConfiguredByUser) {
      // only add filter if we have one registration
      val optConfig = applicationContext.getBean("jwtRegistrationRepository", classOf[Option[RudderJwtRegistration]])
      optConfig match {

        case Some(config) =>
          RudderConfig.authenticationProviders.addSpringAuthenticationProvider(
            RudderJwtAuthenticationProvider.PROTOCOL_ID,
            // here, we can't use applicationContext.getBean without circular reference. It will be put in Spring cache.
            oauth2ApiJwtAuthenticationProvider(optConfig)
          )

          val http    = applicationContext.getBean("publicApiSecurityFilter", classOf[DefaultSecurityFilterChain])
          val filters = http.getFilters
          val manager =
            applicationContext.getBean("org.springframework.security.authenticationManager", classOf[AuthenticationManager])
          filters.add(3, new BearerTokenAuthenticationFilter(manager))

          val newSecurityChain = new DefaultSecurityFilterChain(http.getRequestMatcher, filters)

          applicationContext.getAutowireCapableBeanFactory.configureBean(newSecurityChain, "publicApiSecurityFilter")

        case None =>
          AuthBackendsLogger.info(
            s"${RudderJwtAuthenticationProvider.PROTOCOL_ID} is configured as an authentication provider but there is no valid registration for it: it will be ignored"
          )
      }
    }

    // Adding API authentication protected with OAuth2 thanks to an "opaque access bearer token"
    if (AuthBackendsConf.isOpaqueTokenConfiguredByUser) {
      // only add filter if we have one registration
      val optConfig =
        applicationContext.getBean("opaqueTokenRegistrationRepository", classOf[Option[RudderOpaqueTokenRegistration]])
      optConfig match {
        case Some(config) =>
          RudderConfig.authenticationProviders.addSpringAuthenticationProvider(
            RudderJwtAuthenticationProvider.PROTOCOL_ID,
            // here, we can't use applicationContext.getBean without circular reference. It will be put in Spring cache.
            oauth2ApiOpaqueTokenAuthenticationProvider(optConfig)
          )

          val http    = applicationContext.getBean("publicApiSecurityFilter", classOf[DefaultSecurityFilterChain])
          val filters = http.getFilters
          val manager =
            applicationContext.getBean("org.springframework.security.authenticationManager", classOf[AuthenticationManager])
          filters.add(3, new BearerTokenAuthenticationFilter(manager))

          val newSecurityChain = new DefaultSecurityFilterChain(http.getRequestMatcher, filters)

          applicationContext.getAutowireCapableBeanFactory.configureBean(newSecurityChain, "publicApiSecurityFilter")

        case None =>
          AuthBackendsLogger.warn(
            s"Warning! ${RudderOpaqueTokenAuthenticationProvider.PROTOCOL_ID} is configured as an authentication provider but there is no valid registration for it: it will be ignored"
          )
      }
    }
  }

  @Bean def userRepository: UserRepository = RudderConfig.userRepository
  @Bean def roleApiMapping: RoleApiMapping = new RoleApiMapping(RudderConfig.authorizationApiMapping)

  @Bean def rudderOauth2AuthSuccessHandler: AuthenticationSuccessHandler = new SimpleUrlAuthenticationSuccessHandler(
    "/secure/index.html"
  )

  /**
   * We read configuration for OIDC providers in rudder config files.
   * The format is defined in documentation and parsed in RudderPropertyBasedOAuth2RegistrationDefinition
   */
  @Bean def clientRegistrationRepository: RudderClientRegistrationRepository = {
    val registrations = (
      for {
        _ <- AuthBackendsConf.oauth2registrations.updateRegistration(RudderProperties.config)
        r <- AuthBackendsConf.oauth2registrations.registrations.get
      } yield {
        r.toMap
      }
    ).catchAll { err =>
      (if (AuthBackendsConf.isOauthConfiguredByUser) {
         AuthBackendsLoggerPure.error(err.fullMsg)
       } else {
         AuthBackendsLoggerPure.debug(err.fullMsg)
       }) *> Map.empty[String, RudderClientRegistration].succeed
    }.runNow

    new RudderClientRegistrationRepository(registrations)
  }

  /**
   * We also need to read configuration for JWT providers in rudder config files.
   * The format is defined in documentation and parsed in RudderPropertyBasedJwtRegistrationDefinition
   *
   * For now, we are able to manage ONLY ONE registration, because SpringSecurity token decoder
   * doesn't have any logic to handle more than one JWK url.
   */
  @Bean def jwtRegistrationRepository: Option[RudderJwtRegistration] = {
    (
      for {
        _ <- AuthBackendsConf.jwtRegistrations.updateRegistration(RudderProperties.config)
        r <- AuthBackendsConf.jwtRegistrations.registrations.get
        _ <- r match {
               case Nil | _ :: Nil => ZIO.unit
               case h :: tail      =>
                 AuthBackendsLoggerPure.warn(
                   s"Warning! Rudder JWT only support one provider at a time. Only '${h._1}' will be use, '${tail.map(_._1).mkString("','")}' will be ignored."
                 )
             }
      } yield {
        r.headOption.map(_._2)
      }
    ).catchAll(err => {
      (if (AuthBackendsConf.isJwtConfiguredByUser) {
         AuthBackendsLoggerPure.error(err.fullMsg)
       } else {
         AuthBackendsLoggerPure.debug(err.fullMsg)
       }) *> None.succeed
    }).runNow
  }

  /*
   * The same then JWT, for opaque token registration
   */
  @Bean def opaqueTokenRegistrationRepository: Option[RudderOpaqueTokenRegistration] = {
    (
      for {
        _ <- AuthBackendsConf.opaqueTokenRegistrations.updateRegistration(RudderProperties.config)
        r <- AuthBackendsConf.opaqueTokenRegistrations.registrations.get
        _ <- r match {
               case Nil | _ :: Nil => ZIO.unit
               case h :: tail      =>
                 AuthBackendsLoggerPure.warn(
                   s"Warning! Rudder opaque access bearer tokens only support one provider at a time. Only '${h._1}' will be use, '${tail.map(_._1).mkString("','")}' will be ignored."
                 )
             }
      } yield {
        r.headOption.map(_._2)
      }
    ).catchAll { err =>
      (if (AuthBackendsConf.isOpaqueTokenConfiguredByUser) {
         AuthBackendsLoggerPure.error(err.fullMsg)
       } else {
         AuthBackendsLoggerPure.debug(err.fullMsg)
       }) *> None.succeed
    }.runNow
  }

  /**
   * We don't use rights provided by spring, nor the one provided by OAUTH2, so
   * always map user to role `ROLE_USER`
   */
  @Bean def userAuthoritiesMapper = {

    new GrantedAuthoritiesMapper {
      override def mapAuthorities(authorities: util.Collection[? <: GrantedAuthority]): util.Collection[? <: GrantedAuthority] = {
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
  ): OAuth2LoginAuthenticationProvider = {
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

  @Bean def oauth2ApiJwtAuthenticationProvider(
      jwtRegistrationRepository: Option[RudderJwtRegistration]
  ): AuthenticationProvider = {
    jwtRegistrationRepository match {
      case Some(config) =>
        val decoder   = NimbusJwtDecoder.withJwkSetUri(config.jwkSetUri).build
        val converter = new RudderJwtAuthenticationConverter(
          config,
          RudderConfig.roleApiMapping
        )
        new RudderJwtAuthenticationProvider(decoder, converter)

      case None => MissingConfigurationAuthenticationProvider
    }
  }

  @Bean def oauth2ApiOpaqueTokenAuthenticationProvider(
      opaqueTokenRegistrationRepository: Option[RudderOpaqueTokenRegistration]
  ): AuthenticationProvider = {
    opaqueTokenRegistrationRepository match {
      case Some(config) =>
        val introspector = new NimbusOpaqueTokenIntrospector(config.introspectUri, config.clientId, config.clientSecret)
        new RudderOpaqueTokenAuthenticationProvider(
          introspector,
          new RudderOpaqueTokenAuthenticationConverter(RudderConfig.roApiAccountRepository, config),
          config.cacheRequestDuration,
          () => Instant.now()
        )

      case None => MissingConfigurationAuthenticationProvider
    }
  }

}

// a couple of dedicated user details that have the needed information for the SSO part

//////////////// RudderUserDetails from OAuth2/OIDC login ////////////////

/*
 * The `RudderDetails` class that is instantiated from an OAuth2 `client_credentials` login (ie when a user, generally
 * a service, asked the IdP for a `Bearer` token and then used that token to access Rudder APIs)
 * We can't directly inherit `JwtAuthenticationToken` and `RudderUserDetail` because none is a trait, only classes.
 */
case class RudderOAuth2Jwt(jwt: JwtAuthenticationToken, rudderUserDetail: RudderUserDetail)
    extends JwtAuthenticationToken(jwt.getToken, rudderUserDetail.getAuthorities) with UserDetails {

  this.setDetails(jwt.getDetails)
  this.setAuthenticated(jwt.isAuthenticated)

  // this is important, it's the way to identify a RudderUser
  override def getPrincipal: AnyRef = rudderUserDetail

  override def getPassword: String = rudderUserDetail.getPassword
  override def getUsername: String = rudderUserDetail.getUsername

  override def isAccountNonExpired:     Boolean = rudderUserDetail.isAccountNonExpired
  override def isAccountNonLocked:      Boolean = rudderUserDetail.isAccountNonLocked
  override def isCredentialsNonExpired: Boolean = rudderUserDetail.isCredentialsNonExpired
  override def isEnabled:               Boolean = rudderUserDetail.isEnabled
}

case class RudderOAuth2OpaqueToken(obt: BearerTokenAuthentication, rudderUserDetail: RudderUserDetail)
    extends BearerTokenAuthentication(
      obt.getPrincipal.asInstanceOf[OAuth2AuthenticatedPrincipal],
      obt.getCredentials.asInstanceOf[OAuth2AccessToken],
      rudderUserDetail.getAuthorities
    ) with UserDetails {

  this.setDetails(obt.getDetails)
  this.setAuthenticated(obt.isAuthenticated)

  // this is important, it's the way to identify a RudderUser
  override def getPrincipal: AnyRef = rudderUserDetail

  override def getPassword: String = rudderUserDetail.getPassword
  override def getUsername: String = rudderUserDetail.getUsername

  override def isAccountNonExpired:     Boolean = rudderUserDetail.isAccountNonExpired
  override def isAccountNonLocked:      Boolean = rudderUserDetail.isAccountNonLocked
  override def isCredentialsNonExpired: Boolean = rudderUserDetail.isCredentialsNonExpired
  override def isEnabled:               Boolean = rudderUserDetail.isEnabled

  override def getTokenAttributes: util.Map[String, AnyRef] = obt.getTokenAttributes
}

/*
 * The `RudderDetails` class that is instantiated from an OIDC `authorization_code` login (ie when a user actually
 * logged on the IdP)
 */
final class RudderOidcDetails(oidc: OidcUser, rudder: RudderUserDetail)
    extends RudderUserDetail(rudder.account, rudder.status, rudder.roles, rudder.apiAuthz, rudder.nodePerms) with OidcUser {
  override def getClaims:     util.Map[String, AnyRef] = oidc.getClaims
  override def getUserInfo:   OidcUserInfo             = oidc.getUserInfo
  override def getIdToken:    OidcIdToken              = oidc.getIdToken
  override def getAttributes: util.Map[String, AnyRef] = oidc.getAttributes
  override def getName:       String                   = oidc.getName
}

/*
 * The `RudderDetails` class that is instantiated from an OIDC `authorization_code` login (ie when a user actually
 * logged on the IdP)
 */
final class RudderOauth2Details(oauth2: OAuth2User, rudder: RudderUserDetail)
    extends RudderUserDetail(rudder.account, rudder.status, rudder.roles, rudder.apiAuthz, rudder.nodePerms) with OAuth2User {
  override def getAttributes: util.Map[String, AnyRef] = oauth2.getAttributes
  override def getName:       String                   = oauth2.getName
}

//////////////// User OAuth2/OIDC authentication - `authorization_code` workflows ////////////////

class RudderClientRegistrationRepository(val registrations: Map[String, RudderClientRegistration])
    extends ClientRegistrationRepository {
  override def findByRegistrationId(registrationId: String): ClientRegistration = {
    registrations.get(registrationId) match {
      case None    => null
      case Some(x) => x.registration
    }
  }
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

object RudderTokenMapping {

  /*
   * This is the way to get the list of defined role given what an OAuth2 token gives.
   * - we have a notion of "default roles" which are the one the user or the token have by default, without
   *   token info
   * - we can map roles to rudder ones so that IdP role names and Rudder ones are independent
   * - we can restrict the available roles to the ones mapped so that a Rogue IdP can't use Rudder internal
   *   roles names (and chaos ensues if those internal name change)
   */
  def getRoles(
      reg:           RudderOAuth2Registration & RegistrationWithRoles,
      principal:     String, // user name or token id
      protocolName:  String, // oauth2Api, oauth2, oidc
      default:       Set[Role]
  )(
      getTokenRoles: String => Option[Set[String]]
  ): Set[Role] = {
    val roles = if (reg.roles.enabled) {
      val filteredRoles = getProvidedList(reg.roles, principal)(getTokenRoles)

      AuthBackendsLogger.trace(
        s"IdP configuration has registered role mapping: [${reg.roles.mapping.toList.sorted.map(x => s"${x._1 -> x._2}").mkString("; ")}]"
      )
      val mappedRoles: Set[Role] = filteredRoles.flatMap { r =>
        val role = reg.roles.mapping.get(r) match {
          // if the role is not in the mapping, use the provided name as is.
          case None    => RudderRoles.findRoleByName(r)
          case Some(m) =>
            AuthBackendsLogger.debug(
              s"Principal '${principal}': mapping IdP provided role '${r}' to Rudder role '${m}' "
            )
            RudderRoles
              .findRoleByName(m)
              .map(_.map(x => Role.Alias(x, r, s"Alias from ${reg.registrationId} IdP")))
        }
        role.runNow.orElse {
          AuthBackendsLogger.debug(
            s"Role '${r}' does not match any Rudder role, ignoring it for user ${principal}"
          )
          None
        }
      }

      if (mappedRoles.nonEmpty) {
        ApplicationLoggerPure.Auth.logEffect.info(
          s"Principal '${principal}' role list extended with ${protocolName} provided roles: [${Role
              .toDisplayNames(mappedRoles)
              .mkString(", ")}] (override: ${reg.roles.overrides})"
        )
      } else {
        AuthBackendsLogger.debug(
          s"No roles provided by ${protocolName} in attribute: ${reg.roles.attributeName} (or attribute is missing, or user-management plugin is missing)"
        )
      }

      val roles = if (reg.roles.overrides) {
        // override means: don't use user role configured in rudder-users.xml
        mappedRoles
      } else {
        default ++ mappedRoles
      }
      AuthBackendsLogger.debug(
        s"Principal '${principal}' final list of roles: [${roles.map(_.name).mkString(", ")}]"
      )
      roles

    } else {
      AuthBackendsLogger.debug(s"${protocolName} configuration is not configured to use token provided roles")
      default
    }

    roles
  }

  /*
   * This is the way to get the list of defined tenants given what an OAuth2 token gives.
   * - we have a notion of "default tenants" which are the one the user or the token have by default, without
   *   token info
   * - we can map tenants to rudder ones so that IdP tenant names and Rudder ones are independent
   * - we can restrict the available tenants to the ones mapped so that a Rogue IdP can't use Rudder internal
   *   tenants names (and chaos ensues if those internal name change - even if tenants should be public names)
   */
  def getTenants(
      reg:             RudderOAuth2Registration & RegistrationWithRoles,
      principal:       String, // user name or token id
      protocolName:    String, // oauth2Api, oauth2, oidc
      default:         NodeSecurityContext
  )(
      getTokenTenants: String => Option[Set[String]]
  ): NodeSecurityContext = {
    val tenants = if (reg.tenants.enabled) {
      val filteredTenants = getProvidedList(reg.tenants, principal)(getTokenTenants)

      AuthBackendsLogger.trace(
        s"IdP configuration has registered tenant mapping: [${reg.tenants.mapping.toList.sorted.map(x => s"${x._1 -> x._2}").mkString("; ")}]"
      )

      // for the tenant mapping, we don't check with existing tenants because the real list
      // will be checked at each request (ie, the intersection is done for each node access)
      val mappedTenants = filteredTenants.toList.map { t =>
        reg.tenants.mapping.get(t) match {
          // if the tenant is not in the mapping, use the provided name as is.
          case None    => t
          case Some(m) =>
            AuthBackendsLogger.debug(
              s"Principal '${principal}': mapping IdP provided role '${t}' to Rudder role '${m}' "
            )
            m
        }
      }

      val parsedTenants = NodeSecurityContext.parseList(Some(mappedTenants)) match {
        case Left(err)  =>
          AuthBackendsLogger.debug(
            s"Parsing provided tenants for ${protocolName} in attribute: ${reg.tenants.attributeName} for principal ${principal} lead to an error, disabling all tenants: ${err.fullMsg}"
          )
          NodeSecurityContext.None
        case Right(nsc) =>
          ApplicationLoggerPure.Auth.logEffect.info(
            s"Principal '${principal}' tenant list extended with ${protocolName} provided tenants: '${nsc.value}' (override: ${reg.tenants.overrides})"
          )
          nsc
      }

      val tenants = if (reg.tenants.overrides) {
        // override means: don't use user tenants configured in rudder-users.xml
        parsedTenants
      } else {
        default.plus(parsedTenants)
      }
      AuthBackendsLogger.debug(
        s"Principal '${principal}' final list of tenants: '${tenants.value}'"
      )
      tenants

    } else {
      AuthBackendsLogger.debug(s"${protocolName} configuration is not configured to use token provided tenants")
      default
    }

    tenants
  }

  /*
   * A generic method to get the list of string corresponding to the given `ProvidedList`.
   */
  def getProvidedList(
      provided:     ProvidedList,
      principal:    String // user name or token id
  )(
      getTokenList: String => Option[Set[String]]
  ): Set[String] = {
    val custom = {
      try {
        getTokenList(provided.attributeName) match {
          case Some(l) =>
            l
          case None    =>
            AuthBackendsLogger.warn(
              s"Principal '${principal}' returned information does not contain an attribute '${provided.attributeName}' " +
              s"which is the one configured for custom ${provided.debugName} provisioning (see " +
              s"'rudder.auth.oauth2.provider.$${idpID}.${provided.debugName}.attribute' value). " +
              s"Please check that the attribute name is correct and that requested scope provides that attribute."
            )
            Set.empty[String]
        }
      } catch {
        case ex: Exception =>
          AuthBackendsLogger.warn(
            s"Unable to get custom ${provided.debugName} for user '${principal}' when looking for attribute '${provided.attributeName}' :${ex.getMessage}'"
          )
          Set.empty[String]
      }
    }

    // check if we have role mapping or restriction
    val filteredSet = if (provided.restrictToMapping) {
      val f = custom.intersect(provided.mapping.keySet)
      AuthBackendsLogger.debug(
        s"IdP configuration enforce restriction to mapped ${provided.debugName}, resulting filtered list: [${f.mkString(", ")}]"
      )
      f
    } else custom

    filteredSet
  }

  /*
   * Map roles to the corresponding API ACL.
   */
  def getApiAuthorization(roleApiMapping: RoleApiMapping, roles: Set[Role]): ApiAuthorization.ACL = {
    // we derive api authz from users rights
    val acl = roleApiMapping
      .getApiAclFromRoles(roles.toSeq)
      .groupBy(_.path.parts.head)
      .flatMap {
        case (_, seq) =>
          seq.sortBy(_.path)(using AclPath.orderingaAclPath).sortBy(_.path.parts.head.value)
      }
      .toList

    ApiAuthorization.ACL(acl)
  }
}

trait RudderUserServerMapping[R <: OAuth2UserRequest, U <: OAuth2User, T <: RudderUserDetail & U] {

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
            .addUser(
              protocolId,
              user.getName,
              EventTrace(
                RudderEventActor,
                DateTime.now(),
                s"Provisioning is enabled for ${protocolName} '${idp}'"
              )
            ) *> ApplicationLoggerPure.Auth.info(
            s"User '${user.getName}' automatically created because provisioning is enabled for ${protocolName} '${idp}'"
          )).runNow

          // retry
          rudderUserDetailsService.loadUserByUsername(user.getName)
      }
    }
    // for now, tenants are not configurable by OIDC
    val tenants    = rudderUserDetailsService.authConfigProvider.getUserByName(user.getName) match {
      // when the user is not defined in rudder-users.xml, we give it the whole perm on nodes for compatibility
      case Left(_)  => NodeSecurityContext.All
      // if the user is defined in rudder-users.xml, we get whatever is defined there.
      case Right(u) => u.nodePerms
    }

    buildUser(optReg, userRequest, user, roleApiMapping, rudderUser, newUserDetails, tenants)
  }

  def buildUser(
      optReg:         Option[RudderOAuth2Registration & RegistrationWithRoles],
      userRequest:    R,
      user:           U,
      roleApiMapping: RoleApiMapping,
      rudder:         RudderUserDetail,
      userBuilder:    (U, RudderUserDetail) => T,
      tenants:        NodeSecurityContext
  ): T = {

    val (roles, nsc) = optReg match {
      case None =>
        AuthBackendsLogger.trace(
          s"No configuration found for ${protocolName} registration id: ${userRequest.getClientRegistration.getRegistrationId}"
        )
        (rudder.roles, tenants) // if no registration, use defaults

      case Some(reg) =>
        val getAttr = (attributeName: String) => {
          if (user.getAttributes.containsKey(attributeName)) {
            import scala.jdk.CollectionConverters.*
            Some(user.getAttribute[java.util.ArrayList[String]](attributeName).asScala.toSet)
          } else None
        }

        val roles = RudderTokenMapping.getRoles(reg, rudder.getUsername, protocolId, rudder.roles)(getAttr)
        val nsc   = RudderTokenMapping.getTenants(reg, rudder.getUsername, protocolId, tenants)(getAttr)

        (roles, nsc)
    }

    // we derive api authz from users rights
    val apiAuthz    = RudderTokenMapping.getApiAuthorization(roleApiMapping, roles)
    val userDetails = rudder.copy(roles = roles, apiAuthz = apiAuthz, nodePerms = nsc)
    AuthBackendsLogger.debug(
      s"Principal '${rudder.getUsername}' final roles: [${roles.map(_.name).mkString(", ")}], and API authz: ${apiAuthz.debugString}, and tenants: ${nsc.value}"
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
) extends OidcUserService with RudderUserServerMapping[OidcUserRequest, OidcUser, RudderUserDetail & OidcUser] {
  // we need to use our copy of DefaultOAuth2UserService to log/manage errors
  super.setOauth2UserService(new RudderDefaultOAuth2UserService()): @unchecked

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
    with RudderUserServerMapping[OAuth2UserRequest, OAuth2User, RudderUserDetail & OAuth2User] {
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

class RudderDefaultOAuth2UserService extends DefaultOAuth2UserService with DebugOAuth2Attributes {

  /*
   * this is a copy of parent method with more logs/error management
   */
  private val MISSING_USER_INFO_URI_ERROR_CODE: String = "oauth2:missing_user_info_uri"

  private val MISSING_USER_NAME_ATTRIBUTE_ERROR_CODE: String = "oauth2:missing_user_name_attribute"

  private val INVALID_USER_INFO_RESPONSE_ERROR_CODE: String = "oauth2:invalid_user_info_response"

  private val PARAMETERIZED_RESPONSE_TYPE: ParameterizedTypeReference[java.util.Map[String, AnyRef]] =
    new ParameterizedTypeReference[java.util.Map[String, AnyRef]]() {}

  private val requestEntityConverter: Converter[OAuth2UserRequest, RequestEntity[?]] = new OAuth2UserRequestEntityConverter

  private val restOperations: RestOperations = {
    val restTemplate = new RestTemplate
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler)
    restTemplate
  }

  override val debugRequestKind: String = "  user info attribute values"

  import scala.jdk.CollectionConverters.*

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
    val request:               RequestEntity[?]                              = this.requestEntityConverter.convert(userRequest)
    val response:              ResponseEntity[java.util.Map[String, AnyRef]] = getResponse(userRequest, request)

    val body = response.getBody.asScala

    AuthBackendsLogger.debug(
      s"OAuth2/OIDC user info request with scopes [${userRequest.getClientRegistration.getScopes.asScala.toList.sorted.mkString(" ")}] " +
      s"returned attributes: ${body.keySet.toList.sorted.mkString(", ")}"
    )
    AuthBackendsLogger.trace(debugAttributes(body))

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
      request:     RequestEntity[?]
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

//////////////// REST API OAuth2 authentication - `client_credentials` workflows ////////////////

/////////////// OAuth2/OIDC - JWT bearer token ///////////////

/*
 * A placeholder AuthenticationProvider to use when we don't have any registration.
 * Spring undecidable initialisation patterns force us to do strange things.
 */
object MissingConfigurationAuthenticationProvider extends AuthenticationProvider {
  override def authenticate(authentication: Authentication): Authentication = {
    throw new OAuth2AuthenticationException(
      s"This authentication provider is missing configuration and can't be called for authentication"
    )
  }
  override def supports(authentication: Class[?]): Boolean = false
}

/*
 * Authentication provider for OAuth2/OIDC for protecting APIs with JWT tokens
 */
object RudderJwtAuthenticationProvider {

  val PROTOCOL_ID: String = "oauth2ApiJwt"
}

/*
 * JWT bearer token for oauth2 protected API - authentication provider.
 * This class is here only to allow to hook our mapping class in place of Spring default one.
 */
class RudderJwtAuthenticationProvider(jwtDecoder: JwtDecoder, converter: RudderJwtAuthenticationConverter)
    extends AuthenticationProvider {
  private val jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtDecoder)
  jwtAuthenticationProvider.setJwtAuthenticationConverter(converter)

  override def authenticate(authentication: Authentication): Authentication = {
    val a = jwtAuthenticationProvider.authenticate(authentication)
    a
  }

  override def supports(authentication: Class[?]): Boolean = jwtAuthenticationProvider.supports(authentication)
}

class RudderJwtAuthenticationConverter(
    registration:   RudderJwtRegistration,
    roleApiMapping: RoleApiMapping
) extends Converter[Jwt, AbstractAuthenticationToken] with DebugOAuth2TokenAttributes {

  override val debugTokenKind:             String                         = "JWT OAuth2/OIDC token request"
  override val pivotAttributeRegistration: RegistrationWithPivotAttribute = registration

  import bootstrap.rudder.plugin.RudderJwtAuthenticationProvider.PROTOCOL_ID
  private val jwtConverter = new JwtAuthenticationConverter()

  override def convert(jwt: Jwt): AbstractAuthenticationToken = {
    val t = jwtConverter.convert(jwt).asInstanceOf[JwtAuthenticationToken]

    // Find the registration for that token. It's done by looking at the client ID it must contain.
    // We only have the clientId, so we need to check them all
    val clientId = t.getToken.getClaimAsString(pivotAttribute)

    AuthBackendsLogger.trace(debugAttributes(t.getTokenAttributes.asScala))

    if (clientId == null) { // we're in Java-land, these things can happen
      throw new InvalidBearerTokenException(
        s"A JWT Bearer token was received but it doesn't have a 'cid' claim, so we don't have a client ID and the token is invalid"
      )
    } else {

      // check that audience matches the expected one if the registration enforce it
      if (registration.audience.check && !t.getToken.getAudience.asScala.contains(registration.audience.value)) {
        throw new InvalidBearerTokenException(
          s"Audience is not the expected one for token, client with ID ${clientId} must target audience: ${registration.audience.value}, but got ${t.getToken.getAudience.asScala
              .mkString(", ")}"
        )
      }

      def getAttr(attributeName: String) = t.getToken.getClaimAsStringList(attributeName) match {
        case null => None
        case x    =>
          import scala.jdk.CollectionConverters.*
          Some(x.asScala.toSet)
      }

      val roles    = RudderTokenMapping.getRoles(registration, t.getName, PROTOCOL_ID, default = Set())(getAttr)
      val nsc      =
        RudderTokenMapping.getTenants(registration, t.getName, PROTOCOL_ID, default = NodeSecurityContext.None)(getAttr)
      val apiAuthz = RudderTokenMapping.getApiAuthorization(roleApiMapping, roles)

      // create RudderUserDetails from token
      val details: RudderUserDetail = {
        val created    = jwt.getIssuedAt
        val expiration = ExpireAtDate(jwt.getExpiresAt)

        RudderUserDetail(
          RudderAccount.Api(
            ApiAccount(
              ApiAccountId(jwt.getId),
              ApiAccountKind.PublicApi(apiAuthz, expiration),
              ApiAccountName(jwt.getId),
              AccountToken(Some(ApiTokenHash.fromHashValue(jwt.getTokenValue)), created),
              "",
              isEnabled = true,              // always enabled at that point, since the token is valid
              created,
              lastAuthenticationDate = None, // access for this token are already traced in logs, JWT token are also ephemeral
              nsc
            )
          ),
          UserStatus.Active, // always active at the point, since the token is valid
          roles,
          apiAuthz,
          nsc
        )
      }

      AuthBackendsLogger.debug(
        s"Principal from JWT '${details.getUsername}' final roles: [${roles.map(_.name).mkString(", ")}], and API authz: ${apiAuthz.debugString}, and tenants: ${nsc.value}"
      )

      RudderOAuth2Jwt(t, details)
    }
  }
}

/////////////// OAuth2/OIDC - Opaque bearer access token ///////////////

/*
 * Authentication provider for OAuth2/OIDC for protecting APIs with JWT tokens
 */
object RudderOpaqueTokenAuthenticationProvider {
  val PROTOCOL_ID: String = "oauth2ApiOpaqueToken"
}

// this class is only here to allow to use our convert in place of default spring configuration
class RudderOpaqueTokenAuthenticationProvider(
    introspector:    OpaqueTokenIntrospector,
    converter:       OpaqueTokenAuthenticationConverter,
    validationCache: Option[Duration],
    now:             () => Instant
) extends AuthenticationProvider {
  private val opaqueTokenAuthenticationProvider = new OpaqueTokenAuthenticationProvider(introspector)
  opaqueTokenAuthenticationProvider.setAuthenticationConverter(converter)

  /*
   * The cache semantic is:
   * - if the key is not defined, we don't have that token in cache: IdP validation is needed
   * - if the key is defined:
   *   - when `Left(ex)`, it means that the token is not valid and we can fail the authentication
   *   - when `Right(auth)`, it means we can check if the auth is already ok.
   */
  private val cache = validationCache match {
    // negative or too small duration are the same as no cache defined.
    case Some(duration) if (duration.toMillis > 0) =>
      Some(
        Caffeine
          .newBuilder()
          .maximumSize(1_000)
          .expireAfterWrite(java.time.Duration.ofMillis(duration.toMillis))
          .build[String, Either[AuthenticationException, RudderOAuth2OpaqueToken]]()
      )

    case _ => None
  }

  /*
   * The (pure) method to use in case of cache miss.
   */
  private def doRemoteAuthentication(
      authentication: BearerTokenAuthenticationToken
  ): Either[AuthenticationException, RudderOAuth2OpaqueToken] = {
    try {
      opaqueTokenAuthenticationProvider.authenticate(authentication) match {
        case token: RudderOAuth2OpaqueToken => Right(token)

        case _ => Left(new InvalidBearerTokenException(s"Token is not of the expected RudderOAuth2OpaqueToken type"))
      }
    } catch {
      case ex: AuthenticationException =>
        Left(ex)
    }
  }

  override def authenticate(authentication: Authentication): Authentication = {
    authentication match {
      case token: BearerTokenAuthenticationToken =>
        // check is that token already have an authentication
        cache match {
          // cache defined:
          case Some(c) =>
            c.get(token.getToken, (_: String) => doRemoteAuthentication(token)) match {
              case Left(ex)                                     => throw ex
              // we do have an existing authentication.
              // Expiration will be checked in convert.
              case Right(auth @ RudderOAuth2OpaqueToken(ta, _)) =>
                ta.getCredentials match {
                  case x: OAuth2AccessToken =>
                    if (now().isAfter(x.getExpiresAt)) {
                      // token is expired, remove it from cache
                      c.invalidate(token.getToken)
                      // and it's an authentication error
                      throw new InvalidBearerTokenException(s"Token is expired")
                    } else auth

                  case _ =>
                    throw new InvalidBearerTokenException(s"Token credential is not of the expected OAuth2AccessToken type")
                }
            }

          // no cache in use
          case None    => opaqueTokenAuthenticationProvider.authenticate(authentication)
        }

      case _ => null
    }
  }

  override def supports(authentication: Class[?]): Boolean = opaqueTokenAuthenticationProvider.supports(authentication)
}

/*
 * Logic for mapping a validated opaque bearer access token to Rudder logic.
 * All the authentication security is done by spring-security, here we manage mapping
 * logic to rudder "user details".
 */
class RudderOpaqueTokenAuthenticationConverter(
    roApiAccountRepository:                  RoApiAccountRepository,
    override val pivotAttributeRegistration: RegistrationWithPivotAttribute
) extends OpaqueTokenAuthenticationConverter with DebugOAuth2TokenAttributes {

  override val debugTokenKind: String = "Opaque OAuth2/OIDC token request"

  override def convert(introspectedToken: String, authenticatedPrincipal: OAuth2AuthenticatedPrincipal): Authentication = {
    val t = DefaultOpaqueTokenAuthenticationConverter
      .convert(introspectedToken, authenticatedPrincipal)

    val tokenAttributes = t.getTokenAttributes.asScala

    AuthBackendsLogger.trace(debugAttributes(tokenAttributes))

    // retrieve token id
    val tokenId = tokenAttributes.get(pivotAttribute) match {
      case Some(v) =>
        // we only understand string for that value
        v match {
          case null | "" =>
            throw new InvalidBearerTokenException(
              s"An opaque Bearer token was received but value for '${pivotAttribute}' claim isn't a non-empty string so the token is invalid"
            )
          case id: String => ApiAccountId(id)
          case _ =>
            throw new InvalidBearerTokenException(
              s"An opaque Bearer token was received but value for '${pivotAttribute}' claim isn't a string so the token is invalid"
            )
        }

      case None =>
        throw new InvalidBearerTokenException(
          s"An opaque Bearer token was received but it doesn't have a '${pivotAttribute}' claim, so we don't have a token ID and the token is invalid"
        )
    }

    // try to lookup token id
    roApiAccountRepository.getById(tokenId).runNow match {
      case None             =>
        throw new InvalidBearerTokenException(
          s"An opaque Bearer token was received but No token with ID ${tokenId.value} is configured in Rudder"
        )
      case Some(apiAccount) =>
        if (!apiAccount.isEnabled) {
          throw new InvalidBearerTokenException(
            s"An opaque Bearer token was received but token with ID ${tokenId.value} is disabled in Rudder"
          )
        } else {

          // we only accept public API token for that kind of authentication
          apiAccount.kind match {
            case ApiAccountKind.PublicApi(authz, expirationPolicy) =>
              expirationPolicy match {
                case ExpireAtDate(date) if (Instant.now().isAfter(date)) =>
                  throw new InvalidBearerTokenException(
                    s"An opaque Bearer token was received but API token with ID ${tokenId.value} is expired in Rudder"
                  )

                case _ => // no expiration date or expiration date not reached
                  val user = RudderUserDetail(
                    RudderAccount.Api(apiAccount),
                    UserStatus.Active,
                    RudderAuthType.Api.apiRudderRole,
                    authz,
                    apiAccount.tenants
                  )
                  RudderOAuth2OpaqueToken(t, user)
              }

            // all other API account type leads to an error
            case _                                                 =>
              throw new InvalidBearerTokenException(
                s"An opaque Bearer token was received but No valid public API token with ID ${tokenId.value} is configured in Rudder"
              )
          }
        }
    }
  }
}

trait DebugOAuth2Attributes {
  def debugRequestKind: String

  def debugAttributes(attrs: Iterable[(String, AnyRef)]): String =
    s"${debugRequestKind}: ${debugTokenAttributes(attrs)}"

  // debug each attribute in a new line
  private def debugTokenAttributes(attrs: Iterable[(String, AnyRef)]): String =
    attrs.toList.sortBy(_._1).map { case (k, v) => s"$k: $v" }.mkString("\n  - ", "\n  - ", "")
}

trait DebugOAuth2TokenAttributes extends DebugOAuth2Attributes {
  def debugTokenKind:         String
  final def debugRequestKind: String = s"${debugTokenKind} with token mapping attribute: '${pivotAttribute}' returned attributes"

  def pivotAttributeRegistration: RegistrationWithPivotAttribute
  def pivotAttribute:             String = pivotAttributeRegistration.pivotAttributeName
}
