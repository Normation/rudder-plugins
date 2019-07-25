/*
*************************************************************************************
* Copyright 2019 Normation SAS
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
import com.normation.plugins.createnodeapi.CreateNodeApiPluginDef
import com.normation.plugins.createnodeapi.CheckRudderPluginEnableImpl
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.createnodeapi.CreateNodeApiImpl
import com.normation.rudder.repository.ldap.LDAPEntityMapper

/*
 * Actual configuration of the plugin logic
 */
object CreatenodeapiConf extends RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val pluginDef = new CreateNodeApiPluginDef(CreatenodeapiConf.pluginStatusService)

  lazy val api = {
    // this one is not exposed :/
    val entityMapper = new LDAPEntityMapper(RudderConfig.rudderDit, RudderConfig.nodeDit, RudderConfig.acceptedNodesDit, RudderConfig.cmdbQueryParser, RudderConfig.ldapInventoryMapper)
    new CreateNodeApiImpl(
        RudderConfig.restExtractorService
      , RudderConfig.fullInventoryRepository
      , RudderConfig.rwLdap
      , entityMapper
      , RudderConfig.newNodeManager
      , RudderConfig.stringUuidGenerator
      , RudderConfig.nodeDit
      , CreatenodeapiConf.pluginStatusService
      , RudderConfig.pendingNodesDit
      , RudderConfig.acceptedNodesDit
    )
  }
}




