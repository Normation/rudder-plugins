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

import bootstrap.liftweb.RudderConfig
import com.normation.plugins.SnippetExtensionRegister
import com.normation.plugins.apiauthorizations._
import com.normation.rudder.rest.ApiAuthorizationLevelService
import net.liftweb.common.Loggable
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.{ApplicationContext, ApplicationContextAware}
import org.springframework.context.annotation.{Bean, Configuration}

// service that provide ACL level
object AclLevel extends ApiAuthorizationLevelService {
  override def aclEnabled: Boolean = true

  override def name: String = "Fine grained API authorizations with ACLs"
}


/*
 * Actual configuration of the API Authorizations plugin
 */
object ApiAuthorizationsConf {
  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl()

  // override default service level
  RudderConfig.apiAuthorizationLevelService.overrideLevel(AclLevel)

  lazy val userApi = new UserApi(
      RudderConfig.restExtractorService
    , RudderConfig.roApiAccountRepository
    , RudderConfig.woApiAccountRepository
    , RudderConfig.tokenGenerator
    , RudderConfig.stringUuidGenerator
  )
}

/**
 * Init module
 */
@Configuration
class ApiAuthorizationPluginConf extends Loggable with ApplicationContextAware with InitializingBean {
  @Bean def apiAuthorizationsModuleDef = new ApiAuthorizationsPluginDef(ApiAuthorizationsConf.pluginStatusService)

  @Bean def apiAccountsExtention = new ApiAccountsExtension()
  @Bean def userInformationExtention = new UserInformationExtension()

  // spring thingies
  var appContext : ApplicationContext = null

  override def afterPropertiesSet() : Unit = {
    val ext = appContext.getBean(classOf[SnippetExtensionRegister])
    ext.register(apiAccountsExtention)
    ext.register(userInformationExtention)
  }

  override def setApplicationContext(applicationContext:ApplicationContext) = {
    appContext = applicationContext
  }
}

