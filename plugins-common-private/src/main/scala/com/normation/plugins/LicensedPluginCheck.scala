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

import com.normation.license.*
import com.normation.license.MaybeLicenseError.Maybe
import com.normation.rudder.domain.logger.PluginLogger
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.syntax.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import org.joda.time.DateTime
import scala.util.control.NonFatal

/*
 * An utility method that check if a license file exists and is valid.
 * It then analysis it and return these information in a normalized format.
 */

trait LicensedPluginCheck extends PluginStatus {
  import com.normation.plugins.LicensedPluginCheck.*

  /*
   * implementation must define variable with the following maven properties
   * that will be replaced at build time:
   */
//  val pluginResourcePublickey = "${plugin-resource-publickey}"
//  val pluginResourceLicense   = "${plugin-resource-license}"
//  val pluginDeclaredVersion   = "${plugin-declared-version}"
//  val pluginId                = "${plugin-fullname}"

  def pluginResourcePublickey: String
  def pluginResourceLicense:   String
  def pluginDeclaredVersion:   String
  def pluginId:                String

// this one is generally provided by NodeInfoService
  def getNumberOfNodes: Int

// this one is only for evolution / future check. No need to override it for now.
  def checkAny: Option[Map[String, String] => Either[String, Unit]] = None

  /*
   * we don't want to check each time if the license is ok or not. So we only change if license or key file is updated
   */
  def getModDate(path: String): Option[FileTime]                         = {
    try {
      Some(Files.getLastModifiedTime(Paths.get(path)))
    } catch {
      case NonFatal(ex) => None
    }
  }
  def readLicense:              Maybe[(License.CheckedLicense, Version)] = {
    val lic = LicenseReader.readAndCheckLicenseFS(
      pluginResourceLicense,
      pluginResourcePublickey,
      new DateTime(),
      pluginDeclaredVersion,
      pluginId
    )
    // log
    lic.fold(
      error => PluginLogger.error(s"Plugin '${pluginId}' license error: ${error.msg}"),
      ok => PluginLogger.info(s"Plugin '${pluginId}' has a license and the license signature is valid.")
    )
    lic
  }

  // some cached information
  private[this] var licenseModDate = Option.empty[FileTime]
  private[this] var pubkeyModDate  = Option.empty[FileTime]
  private[this] var infoCache: MaybeLicenseError.Maybe[(License.CheckedLicense, Version)] = Left(
    LicenseError.IO("License not initialized yet or missing licenses related files.")
  )

  def maybeLicense: Maybe[(License.CheckedLicense, Version)] = {
    val licenseMod = getModDate(pluginResourceLicense)
    val pubkeyMod  = getModDate(pluginResourcePublickey)
    if (licenseMod != licenseModDate || pubkeyMod != pubkeyModDate) {
      licenseModDate = licenseMod
      pubkeyModDate = pubkeyMod
      infoCache = readLicense
    }
    infoCache
  }

  def current: RudderPluginLicenseStatus = {
    (for {
      info              <- maybeLicense
      (license, version) = info
      check             <- LicenseChecker.checkLicenseRuntime(license, DateTime.now, version, pluginId, getNumberOfNodes, checkAny)
    } yield {
      check
    }) match {
      case Right(x) => RudderPluginLicenseStatus.EnabledWithLicense(x.content.transformInto[PluginLicense])
      case Left(y)  =>
        RudderPluginLicenseStatus.Disabled(
          y.msg,
          maybeLicense.toOption.map { case (l, _) => l.content.transformInto[PluginLicense] }
        )
    }
  }
}

// LicenseInformation has exactly the fields in license with different wrapper types : define transformers
private object LicensedPluginCheck {
  import com.normation.utils.DateFormaterService.JodaTimeToJava

  // Required for min-max version which is a parsed version
  // The license defines a version with a .toString method
  implicit val transformerVersion: Transformer[Version, String] = _.toString

  implicit val transformerLicensee:   Transformer[LicenseField.Licensee, Licensee]     = Transformer.derive
  implicit val transformerSoftwareId: Transformer[LicenseField.SoftwareId, SoftwareId] = Transformer.derive
  implicit val transformerMinVersion: Transformer[LicenseField.MinVersion, MinVersion] = Transformer.derive
  implicit val transformerMaxVersion: Transformer[LicenseField.MaxVersion, MaxVersion] = Transformer.derive
  implicit val transformerMaxNodes:   Transformer[LicenseField.MaxNodes, MaxNodes]     = Transformer.derive

  implicit val transformerPluginLicense: Transformer[LicenseInformation, PluginLicense] = {
    Transformer
      .define[LicenseInformation, PluginLicense]
      .withFieldComputed(_.startDate, _.startDate.value.toJava)
      .withFieldComputed(_.endDate, _.endDate.value.toJava)
      .withFieldComputed(_.others, _.others.map(_.raw).toMap)
      .buildTransformer
  }
}
