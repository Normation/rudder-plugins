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

package com.normation.plugins

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

import com.normation.license._
import com.normation.rudder.domain.logger.PluginLogger
import org.joda.time.DateTime

import scala.util.control.NonFatal

/*
 * An utility method that check if a license file exists and is valid.
 * It then analysis it and return these information in a normalized format.
 */

trait LicensedPluginCheck extends PluginStatus {

  /*
   * implementation must define variable with the following maven properties
   * that will be replaced at build time:
   */
//  val pluginResourcePublickey = "${plugin-resource-publickey}"
//  val pluginResourceLicense   = "${plugin-resource-license}"
//  val pluginDeclaredVersion   = "${plugin-declared-version}"
//  val pluginId                = "${plugin-fullname}"

  def pluginResourcePublickey: String
  def pluginResourceLicense  : String
  def pluginDeclaredVersion  : String
  def pluginId               : String

// this one is generally provided by NodeInfoService
  def getNumberOfNodes: Int

// this one is only for evolution / futur check. No need to override it for now.
  def checkAny: Option[Map[String, String] => Either[String, Unit]] = None

  /*
   * we don't want to check each time if the license is ok or not. So we only change if license or key file is updated
   */
  def getModDate(path: String): Option[FileTime] = {
    try {
      Some(Files.getLastModifiedTime(Paths.get(path)))
    } catch {
      case NonFatal(ex) => None
    }
  }
  def readLicense = {
    val lic = LicenseReader.readAndCheckLicenseFS(pluginResourceLicense, pluginResourcePublickey, new DateTime(), pluginDeclaredVersion, pluginId)
    // log
    lic.fold(
      error => PluginLogger.error(s"Plugin '${pluginId}' license error: ${error.msg}")
    , ok    => PluginLogger.info(s"Plugin '${pluginId}' has a license and the license signature is valid.")
    )
    lic
  }

  // some cached information
  private[this] var licenseModDate = Option.empty[FileTime]
  private[this] var pubkeyModDate  = Option.empty[FileTime]
  private[this] var infoCache: MaybeLicenseError.Maybe[(License.CheckedLicense, Version)] = Left(LicenseError.IO("License not initialized yet or missing licenses related files."))

  def maybeLicense = {
    val licenseMod = getModDate(pluginResourceLicense)
    val pubkeyMod  = getModDate(pluginResourcePublickey)
    if(licenseMod != licenseModDate || pubkeyMod != pubkeyModDate) {
      licenseModDate = licenseMod
      pubkeyModDate  = pubkeyMod
      infoCache      = readLicense
    }
    infoCache
  }

  def current: PluginStatusInfo = {
    (for {
      info               <- maybeLicense
      (license, version) = info
      check              <- LicenseChecker.checkLicenseRuntime(license, DateTime.now, version, pluginId, getNumberOfNodes, checkAny)
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
      , maxNodes   = l.content.maxNodes.value
      , others     = l.content.others.map(_.raw).toMap
    )
  }
}
