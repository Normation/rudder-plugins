/*
 *************************************************************************************
 * Copyright 2016 Normation SAS
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
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.branding.BrandingConfService
import com.normation.plugins.branding.BrandingPluginDef
import com.normation.plugins.branding.CheckRudderPluginEnableImpl
import com.normation.plugins.branding.api.BrandingApi
import com.normation.plugins.branding.api.BrandingApiService
import com.normation.plugins.branding.snippet.BrandingResources
import com.normation.plugins.branding.snippet.CommonBranding
import com.normation.plugins.branding.snippet.LoginBranding

/*
 * Actual configuration of the data sources logic
 */
object BrandingPluginConf extends RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService = new CheckRudderPluginEnableImpl(RudderConfig.nodeFactRepository)
  lazy val pluginDef: BrandingPluginDef = new BrandingPluginDef(BrandingPluginConf.pluginStatusService)

  val brandingConfService: BrandingConfService = new BrandingConfService(BrandingConfService.defaultConfigFilePath)
  val brandingApiService:  BrandingApiService  = new BrandingApiService(brandingConfService)
  val brandingApi:         BrandingApi         =
    new BrandingApi(brandingApiService, RudderConfig.stringUuidGenerator)

  RudderConfig.rudderApi.addModules(brandingApi.getLiftEndpoints())
  RudderConfig.snippetExtensionRegister.register(new BrandingResources(pluginDef.status))
  RudderConfig.snippetExtensionRegister.register(new CommonBranding(pluginStatusService))
  RudderConfig.snippetExtensionRegister.register(new LoginBranding(pluginStatusService, pluginDef.version))
}
