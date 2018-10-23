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
import bootstrap.liftweb.RudderConfig
import bootstrap.liftweb.RudderProperties
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.authbackends.AuthBackendsPluginDef
import com.normation.plugins.authbackends.AuthBackendsRepository
import com.normation.plugins.authbackends.CheckRudderPluginEnableImpl
import com.normation.plugins.authbackends.api.AuthBackendsApiImpl


/*
 * Actual configuration of the plugin logic
 */
object AuthBackendsConf extends RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl()

  lazy val authBackendsProvider = new AuthBackendsProvider {
    def authenticationBackends = Set("ldap", "radius")
    def name = s"Enterprise Authentication Backends: '${authenticationBackends.mkString("','")}'"

    override def allowedToUseBackend(name: String): Boolean = {
      // same behavior for all authentication backends: only depends on the plugin status
      pluginStatusService.isEnabled
    }
  }

  RudderConfig.authenticationProviders.addProvider(authBackendsProvider)

  lazy val pluginDef = new AuthBackendsPluginDef(AuthBackendsConf.pluginStatusService)

  lazy val api = new AuthBackendsApiImpl(
      RudderConfig.restExtractorService
    , new AuthBackendsRepository(RudderConfig.authenticationProviders, RudderProperties.config)
  )
}
