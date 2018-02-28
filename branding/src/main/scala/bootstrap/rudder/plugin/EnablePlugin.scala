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

package bootstrap.rudder.plugin

import org.joda.time.DateTime

/**
 * This file defined an entry point for license information and other
 * enabling of the plugin
 */

sealed trait BrandingStatus
final object BrandingStatus {
  final case object Enabled extends BrandingStatus
  final case class Disabled(reason: String) extends BrandingStatus
}

/*
 * This object gives main information about license information.
 * It is destinated to be read to the user. No string information
 * should be used for comparison.
 */
final case class BrandingLicenseInfo(
    licensee  : String
  , softwareId: String
  , minVersion: String
  , maxVersion: String
  , startDate : DateTime
  , endDate   : DateTime
)

trait CheckRudderPluginBrandingEnable {

  /*
   * Does the plugin has a license to display?
   */
  def hasLicense(): Boolean

  /*
   * Is the plugin currently enabled (at the moment of the request)
   */
  def isEnabled(): Boolean

  /*
   * What is the CURRENT status of the plugin at the moment of the request
   */
  def enabledStatus(): BrandingStatus

  /*
   * Information about the license. Maybe none is the plugin
   * is not a limited version.
   */
  def licenseInformation(): Option[BrandingLicenseInfo]
}
