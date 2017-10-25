/*
*************************************************************************************
* Copyright 2017 Normation SAS
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

package com.normation.plugins.datasources

import java.util.Formatter.DateTime
import org.joda.time.DateTime
import com.normation.license._
import com.normation.plugins.PluginStatus
import com.normation.plugins.PluginStatusInfo
import com.normation.plugins.PluginLicenseInfo
import com.normation.rudder.domain.logger.PluginLogger


/*
 * This template file will processed at build time to choose
 * the correct immplementation to use for the interface.
 * The default implementation is to always enable status.
 *
 * The class will be loaded by ServiceLoader, it needs an empty constructor.
 * 
 * All implem is totally plugin independant, so it should be factored out
 * in a trait, but there is no good place for that one. 
 * It can't be in Rudder because it would make a dep towards license-lib. 
 * Can't be in license-lib else dep toward Rudder. 
 */

final class CheckRudderPluginDatasourcesEnableImpl() extends PluginStatus {

  val pluginId = "${plugin-id}"
  
  //the following string should be replaced at compile time 
  //(in maven language, they are "filtered")
  val maybeLicense = LicenseReader.readAndCheckLicense("${plugin-resource-license}", "${plugin-resource-publickey}", "${plugin-declared-version}", pluginId)
   
  //log at that point of loading if we successfully read the license information for the plugin
  maybeLicense.fold( 
      error => PluginLogger.error(s"Plugin '${pluginId}' license error: ${error.msg}") 
    , ok    => PluginLogger.info("Plugin '${plugin-id}' has a license and the license signature is valid.") 
  )
    
  def current: PluginStatusInfo = {
    (for {
      info               <- maybeLicense
      (license, version) = info
      check              <- LicenseChecker.checkLicense(license, DateTime.now, version, "${plugin-id}")
    } yield {
      check
    }) match {
      case Right(x) => PluginStatusInfo.EnabledWithLicense(licenseInformation(x))
      case Left (y) => PluginStatusInfo.Disabled(y.msg, maybeLicense.toOption.map { case (l, v) => licenseInformation(l) })
    }
  }
  
  private[this] def licenseInformation(l: License): PluginLicenseInfo = {
    PluginLicenseInfo(
        licensee   = l.content.licensee.value
      , softwareId = l.content.softwareId.value
      , minVersion = l.content.minVersion.value.toString
      , maxVersion = l.content.maxVersion.value.toString
      , startDate  = l.content.startDate.value
      , endDate    = l.content.endDate.value
    )
  }
}

