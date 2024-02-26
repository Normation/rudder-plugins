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
import bootstrap.liftweb.UserFileProcessing
import com.normation.plugins.PluginStatus
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.usermanagement.CheckRudderPluginEnableImpl
import com.normation.plugins.usermanagement.UserManagementPluginDef
import com.normation.plugins.usermanagement.UserManagementService
import com.normation.plugins.usermanagement.api.UserManagementApiImpl
import com.normation.rudder.rest.RoleApiMapping
import com.normation.rudder.users.UserAuthorisationLevel

/*
 * The user authorization level
 */
class UserManagementAuthorizationLevel(status: PluginStatus) extends UserAuthorisationLevel {
  override def userAuthEnabled: Boolean = status.isEnabled()
  override val name:            String  = "User Management plugin: extended authorizations"
}

/*
 * Actual configuration of the plugin logic
 */
object UserManagementConf extends RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService = new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val pluginDef = new UserManagementPluginDef(UserManagementConf.pluginStatusService)

  lazy val api = new UserManagementApiImpl(
    RudderConfig.userRepository,
    RudderConfig.rudderUserListProvider,
    RudderConfig.authenticationProviders,
    new UserManagementService(
      RudderConfig.userRepository,
      RudderConfig.rudderUserListProvider,
      UserFileProcessing.getUserResourceFile()
    ),
    new RoleApiMapping(RudderConfig.authorizationApiMapping)
  )

  RudderConfig.userAuthorisationLevel.overrideLevel(new UserManagementAuthorizationLevel(pluginStatusService))
}
