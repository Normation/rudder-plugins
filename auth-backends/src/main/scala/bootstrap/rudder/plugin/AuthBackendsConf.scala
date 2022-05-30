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

import com.normation.plugins.RudderPluginModule
import com.normation.plugins.authbackends.AuthBackendsLogger
import com.normation.plugins.authbackends.AuthBackendsPluginDef
import com.normation.plugins.authbackends.AuthBackendsRepository
import com.normation.plugins.authbackends.CheckRudderPluginEnableImpl
import com.normation.plugins.authbackends.LoginFormRendering
import com.normation.plugins.authbackends.RudderPropertyBasedOAuth2RegistrationDefinition
import com.normation.plugins.authbackends.api.AuthBackendsApiImpl
import com.normation.plugins.authbackends.snippet.Oauth2LoginBanner
import com.normation.rudder.domain.logger.PluginLogger
import com.normation.rudder.web.services.RudderUserDetail

import bootstrap.liftweb.AuthBackendsProvider
import bootstrap.liftweb.AuthenticationMethods
import bootstrap.liftweb.RudderConfig
import bootstrap.liftweb.RudderInMemoryUserDetailsService
import bootstrap.liftweb.RudderProperties
import com.typesafe.config.ConfigException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationProvider
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler

import java.util

import com.normation.zio._


/*
 * Actual configuration of the plugin logic
 */
object AuthBackendsConf extends RudderPluginModule {
  /*
   * property name to do what to know with the login form:
   */
  val DISPLAY_LOGIN_FORM_PROP = "rudder.auth.displayLoginForm"

  // Radius client WARN about a lot of things, which produce very long stack trace with rudder.
  // If the user didn't explicitly set level, change it to error. User is still able to change
  // it back in configuration file.
  val log = org.slf4j.LoggerFactory.getLogger("net.jradius.log.Log4JRadiusLogger").asInstanceOf[ch.qos.logback.classic.Logger]
  // if user didn' change level, set it to error only
  if(log.getLevel == null) {
    log.setLevel(ch.qos.logback.classic.Level.ERROR)
  }

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val authBackendsProvider = new AuthBackendsProvider {
    def authenticationBackends = Set("ldap", "radius")
    def name = s"Enterprise Authentication Backends: '${authenticationBackends.mkString("','")}'"

    override def allowedToUseBackend(name: String): Boolean = {
      // same behavior for all authentication backends: only depends on the plugin status
      pluginStatusService.isEnabled
    }
  }

  val oauthBackendNames = Set("oauth2", "oidc")
  RudderConfig.authenticationProviders.addProvider(authBackendsProvider)
  RudderConfig.authenticationProviders.addProvider(new AuthBackendsProvider() {
    override def authenticationBackends: Set[String] = oauthBackendNames
    override def name: String = s"Oauth2 and OpenID Connect authentication backends provider: '${authenticationBackends.mkString("','")}"
    override def allowedToUseBackend(name: String): Boolean = pluginStatusService.isEnabled
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
      RudderConfig.restExtractorService
    , new AuthBackendsRepository(RudderConfig.authenticationProviders, RudderProperties.config)
  )

  lazy val loginFormRendering = try {
    LoginFormRendering.parse(RudderProperties.config.getString(DISPLAY_LOGIN_FORM_PROP)) match {
      case Right(v)  => v
      case Left(err) =>
        PluginLogger.warn(s"Error for property '${DISPLAY_LOGIN_FORM_PROP}': ${err}")
    }
  } catch {
    case ex: ConfigException => // if not defined, default to "show"
      LoginFormRendering.Show
  }

  // oauth2 button on login page
  if(isOauthConfiguredByUser) {
    PluginLogger.info(s"Oauthv2 or OIDC authentication backend is enabled, updating login form")
    RudderConfig.snippetExtensionRegister.register(new Oauth2LoginBanner(pluginStatusService, pluginDef.version, oauth2registrations))
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
   * - all the (oauth2, oidc)AuthenticationProvider by hand in rudder (since we don't have an xml file for them)
   * - build filter by hand. To know what to do and avoid NPEs, we look how it's done in the code configurer class
   * - hi-jack the main security filter, declared in applicationContext-security.xml, update the list of filters,
   *   and put it back into spring.
   */
  override def setApplicationContext(applicationContext: ApplicationContext): Unit = {
    if(AuthBackendsConf.isOauthConfiguredByUser) {
      // create the two OAUTH2 filters that are going to be added in the filter chain.
      // logic copy/pasted from OAuth2LoginConfigurer init and configure methods
      def createFilters(rudderUserDetailsService: RudderInMemoryUserDetailsService) = {
        val authenticationFilter: OAuth2LoginAuthenticationFilter = new OAuth2LoginAuthenticationFilter(
          clientRegistrationRepository, authorizedClientRepository, loginProcessingUrl
        )

        val authorizationRequestFilter = new OAuth2AuthorizationRequestRedirectFilter(authorizationRequestResolver)
        authorizationRequestFilter.setAuthorizationRequestRepository(authorizationRequestRepository)
        // request cache ?
        // authorizationRequestFilter.setRequestCache( ??? )

        authenticationFilter.setFilterProcessesUrl(loginProcessingUrl)
        authenticationFilter.setAuthorizationRequestRepository(authorizationRequestRepository)
        authenticationFilter.setAuthenticationSuccessHandler(rudderOauth2AuthSuccessHandler)
        authenticationFilter.setAuthenticationFailureHandler(applicationContext.getBean("rudderWebAuthenticationFailureHandler").asInstanceOf[AuthenticationFailureHandler])
        (authorizationRequestFilter, authenticationFilter)
      }

      val http = applicationContext.getBean("mainHttpSecurityFilters", classOf[DefaultSecurityFilterChain])

      val rudderUserService = applicationContext.getBean("rudderUserDetailsService", classOf[RudderInMemoryUserDetailsService])
      val (oAuth2AuthorizationRequestRedirectFilter, oAuth2LoginAuthenticationFilter) = createFilters(rudderUserService)

      // add authentication providers to rudder list

      RudderConfig.authenticationProviders.addSpringAuthenticationProvider("oauth2", oauth2AuthenticationProvider(rudderUserService))
      RudderConfig.authenticationProviders.addSpringAuthenticationProvider("oidc", oidcAuthenticationProvider(rudderUserService))
      val manager = applicationContext.getBean("org.springframework.security.authenticationManager", classOf[AuthenticationManager])
      oAuth2LoginAuthenticationFilter.setAuthenticationManager(manager)

      val filters = http.getFilters
      filters.add(3, oAuth2AuthorizationRequestRedirectFilter)
      filters.add(4, oAuth2LoginAuthenticationFilter)
      val newSecurityChain = new DefaultSecurityFilterChain(http.getRequestMatcher, filters)

      applicationContext.getAutowireCapableBeanFactory.configureBean(newSecurityChain, "mainHttpSecurityFilters")
    }
  }



  @Bean def rudderOauth2AuthSuccessHandler: AuthenticationSuccessHandler = new SimpleUrlAuthenticationSuccessHandler("/secure/index.html")

  /**
   * We read configuration for OIDC providers in rudder config files.
   * The format is defined in
   */
  @Bean def clientRegistrationRepository: ClientRegistrationRepository = {
    val registrations = (for {
        _ <- AuthBackendsConf.oauth2registrations.updateRegistration(RudderProperties.config)
        r <- AuthBackendsConf.oauth2registrations.registrations.get
      } yield {
        r.toMap
      }).runNow
    if(registrations.isEmpty) {
      AuthBackendsLogger.error(s"No registration configured, please disable OAUTH2 provider or correct registration")
    }

    new ClientRegistrationRepository {
      val map = registrations
      override def findByRegistrationId(registrationId: String): ClientRegistration = {
        map.get(registrationId) match {
          case None    => null
          case Some(x) => x.registration
        }
      }
    }
  }

  /**
   * We don't use rights provided by spring, nor the one provided by OAUTH2, so
   * alway map user to role `ROLE_USER`
   */
  @Bean def userAuthoritiesMapper = {
    import scala.jdk.CollectionConverters._

    new GrantedAuthoritiesMapper {
      override def mapAuthorities(authorities: util.Collection[_ <: GrantedAuthority]): util.Collection[_ <: GrantedAuthority] = {
        authorities.asScala.flatMap {
          case _: OidcUserAuthority | _: OAuth2UserAuthority =>
            Some(new SimpleGrantedAuthority("ROLE_USER"))
          case _ => None //ignore, no granted authority for it
        }
      }.asJavaCollection
    }
  }


  /**
   *  Retrieve rudder user based on information provided in the oidc token.
   *  Create an hybride Oidc/Rudder UserDetails.
   */
  @Bean def oidcUserService(rudderUserDetailsService: RudderInMemoryUserDetailsService): OidcUserService = {
    new OidcUserService() {
      override def loadUser(userRequest: OidcUserRequest): OidcUser = {
        val user = super.loadUser(userRequest)

        // check that we know that user in our DB
        val rudderUser = rudderUserDetailsService.loadUserByUsername(user.getUserInfo.getPreferredUsername)

        new RudderOidcDetails(user, rudderUser)
      }
    }
  }

  @Bean def oauth2UserService(rudderUserDetailsService: RudderInMemoryUserDetailsService): OAuth2UserService[OAuth2UserRequest, OAuth2User] = {
    val defaultUserService = new DefaultOAuth2UserService()

    new OAuth2UserService[OAuth2UserRequest, OAuth2User]() {
      override def loadUser(userRequest: OAuth2UserRequest): OAuth2User = {
        val user = defaultUserService.loadUser(userRequest)

        // check that we know that user in our DB
        val rudderUser = rudderUserDetailsService.loadUserByUsername(user.getName)

        new RudderOauth2Details(user, rudderUser)
      }
    }
  }

  // following beans are the detault one provided by spring security for oauth2 logic

  val authorizationRequestBaseUri = "/oauth2/authorization"
  val loginProcessingUrl = "/login/oauth2/code/*"

  @Bean def authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
    clientRegistrationRepository, authorizationRequestBaseUri
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

  @Bean def oauth2AuthenticationProvider(rudderUserDetailsService: RudderInMemoryUserDetailsService) = {
    val x = new OAuth2LoginAuthenticationProvider(rudderAuthorizationCodeTokenResponseClient(), oauth2UserService(rudderUserDetailsService))
    x.setAuthoritiesMapper(userAuthoritiesMapper)
    x
  }

  @Bean def oidcAuthenticationProvider(rudderUserDetailsService: RudderInMemoryUserDetailsService): OidcAuthorizationCodeAuthenticationProvider = {
    val x = new OidcAuthorizationCodeAuthenticationProvider(
      rudderAuthorizationCodeTokenResponseClient(),
      oidcUserService(rudderUserDetailsService)
    )
    x.setJwtDecoderFactory(jwtDecoderFactory)
    x.setAuthoritiesMapper(userAuthoritiesMapper)
    x
  }
}

// a couple of dedicated user details that have the needed information for the SSO part

final class RudderOidcDetails(oidc: OidcUser, rudder: RudderUserDetail) extends RudderUserDetail(rudder.account, rudder.roles, rudder.apiAuthz) with OidcUser {
  override def getClaims: util.Map[String, AnyRef] = oidc.getClaims
  override def getUserInfo: OidcUserInfo = oidc.getUserInfo
  override def getIdToken: OidcIdToken = oidc.getIdToken
  override def getAttributes: util.Map[String, AnyRef] = oidc.getAttributes
  override def getName: String = oidc.getName
}

final class RudderOauth2Details(oauth2: OAuth2User, rudder: RudderUserDetail) extends RudderUserDetail(rudder.account, rudder.roles, rudder.apiAuthz) with OAuth2User {
  override def getAttributes: util.Map[String, AnyRef] = oauth2.getAttributes
  override def getName: String = oauth2.getName
}
