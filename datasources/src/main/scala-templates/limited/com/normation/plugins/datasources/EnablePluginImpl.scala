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

import com.normation.license._
import java.util.Formatter.DateTime
import java.io.InputStreamReader
import org.joda.time.DateTime
import bootstrap.rudder.plugin.CheckRudderPluginDatasourcesEnable
import bootstrap.rudder.plugin.DatasourcesStatus
import bootstrap.rudder.plugin.DatasourcesLicenseInfo


/*
 * This template file will processed at build time to choose
 * the correct immplementation to use for the interface.
 * The default implementation is to always enable status.
 *
 * The class will be loaded by ServiceLoader, it needs an empty constructor.
 */

final class CheckRudderPluginDatasourcesEnableImpl() extends CheckRudderPluginDatasourcesEnable {
  // here are processed variables
  val CLASSPATH_KEYFILE = "${plugin-resource-publickey}"
  val FS_SIGNED_LICENSE = "${plugin-resource-license}"
  val VERSION           = "${plugin-declared-version}"

  val maybeLicense = LicenseReader.readLicense(FS_SIGNED_LICENSE)
  val hasLicense = true  
  
  // for now, we only read license info at load, because it's time consuming
  val maybeInfo = 
    for {
      unchecked <- maybeLicense
      publicKey <- {
                     val key = this.getClass.getClassLoader.getResourceAsStream(CLASSPATH_KEYFILE)
                     if(key == null) {
                       Left(LicenseError.IO(s"The resources '${CLASSPATH_KEYFILE}' was not found"))
                     } else {
                       RSAKeyManagement.readPKCS8PublicKey(new InputStreamReader(key), None) //don't give to much info about path
                     }
                   }
      checked   <- LicenseChecker.checkSignature(unchecked, publicKey)
      version   <- Version.from(VERSION) match {
                     case None    => Left(LicenseError.Parsing(s"Version is not valid: '${VERSION}'."))
                     case Some(v) => Right(v)
                   }
    } yield {
      (checked, version)
    }

  maybeInfo.fold( error => DataSourceLogger.error(error) , ok =>  DataSourceLogger.warn("License signature is valid.") )
    
  def isEnabled = enabledStatus == DatasourcesStatus.Enabled

  def enabledStatus: DatasourcesStatus = {
    (for {
      info               <- maybeInfo
      (license, version) = info
      check              <- LicenseChecker.checkLicense(license, DateTime.now, version)
    } yield {
      check
    }) match {
      case Right(x) => DatasourcesStatus.Enabled
      case Left (y) => DatasourcesStatus.Disabled(y.msg)
    }
  }
  
  def licenseInformation(): Option[DatasourcesLicenseInfo] = maybeLicense match {
    case Left(_)  => None
    case Right(l) => Some(DatasourcesLicenseInfo(
          licensee   = l.content.licensee.value
        , softwareId = l.content.softwareId.value
        , minVersion = l.content.minVersion.value.toString
        , maxVersion = l.content.maxVersion.value.toString
        , startDate  = l.content.startDate.value
        , endDate    = l.content.endDate.value
      ))
  }
  
}

