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

package com.normation.plugins

import com.normation.rudder.domain.logger.ApplicationLogger
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Logger
import net.liftweb.util.Helpers
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.xml.NodeSeq


object DataSourceLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("plugins")
}

/**
 * This file defined an entry point for license plugin information and other
 * enabling of the plugin
 */
sealed trait PluginStatus
final object PluginStatus {
  final case object Enabled                  extends PluginStatus
  final case class  Disabled(reason: String) extends PluginStatus
}

/*
 * This object gives main plugin information about license plugin information.
 * It is destinated to be read to the user. No string plugin information
 * should be used for comparison.
 */
final case class PluginLicenseInfo(
    licensee  : String
  , softwareId: String
  , minVersion: String
  , maxVersion: String
  , startDate : DateTime
  , endDate   : DateTime
)

trait CheckRudderPluginEnable {

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
  def enabledStatus(): PluginStatus

  /*
   * plugin information about the license. Maybe none is the plugin
   * is not a limited version.
   */
  def licenseInformation(): Option[PluginLicenseInfo]
}


/*
 * Default implementation to use when the plugin is unlicensed / always on.
 */
trait PluginEnableImpl extends CheckRudderPluginEnable {
    def hasLicense = false
    def isEnabled = true
    def enabledStatus = PluginStatus.Enabled
    def licenseInformation = None
}

/*
 * A standard implementation of the RudderPluginDef which expects to get most of its properties
 * from "build.conf" file.
 * It also manage plugin information from a License plugin information trait.
 *
 * Children must give base package (implement `basePackage`) so that build.conf file can be found.
 */
trait DefaultPluginDef extends RudderPluginDef {


  def pluginStatus: CheckRudderPluginEnable

  //get properties name for the plugin from "build.conf" file
  //have default string for errors (and avoid "missing prop exception"):
  lazy val defaults = List(
      "plugin-name"
    , "plugin-fullname"
    , "plugin-title-description"
    , "plugin-version"
  ).map(p => s"$p=missing property with name '$p' in file 'build.conf' for '${basePackage}'").mkString("\n")

  // ConfigFactory does not want the "/" at begining nor the ".conf" on the end
  lazy val buildConfPath = basePackage.replaceAll("""\.""", "/") + "/build.conf"
  lazy val buildConf = try {
    ConfigFactory.load(this.getClass.getClassLoader, buildConfPath).withFallback(ConfigFactory.parseString(defaults))
  } catch {
    case ex: ConfigException => //something want very wrong with "build.conf" parsing

      ApplicationLogger.error(s"Error when parsing coniguration file for plugin '${basePackage}': ${ex.getMessage}", ex)
      ConfigFactory.parseString(defaults)
  }

  override lazy val name = PluginName(buildConf.getString("plugin-fullname"))
  override lazy val displayName = buildConf.getString("plugin-title-description")
  override lazy val version = {
    val versionString = buildConf.getString("plugin-version")
    PluginVersion.from(versionString).getOrElse(
      //a version name that indicate an erro
      PluginVersion(0,0,1, s"ERROR-PARSING-VERSION: ${versionString}")
    )
  }

  override def description : NodeSeq  = (
     <div>
     {
      if(buildConf.hasPathOrNull("plugin-web-description")) {
        Helpers.secureXML.loadString(buildConf.getString("plugin-web-description"))
      } else {
        displayName
      }
      }
     { // license plugin information
       if(!pluginStatus.hasLicense) {
         NodeSeq.Empty
       } else {
           val (bg, msg) = pluginStatus.enabledStatus() match {
             case PluginStatus.Enabled => ("info", None)
             case PluginStatus.Disabled(msg) => ("danger", Some(msg))
           }

           <div class="tw-bs"><div id="license-information" style="padding:5px; margin: 5px;" class={s"bs-callout bs-callout-${bg}"}>
             <h4>License plugin information</h4>
             <p>This binary version of the plugin is submited to a license with the
                following information:</p>
             <div class={s"bg-${bg}"}>{
               pluginStatus.licenseInformation() match {
                 case None    => <p>It was not possible to read information about the license.</p>
                 case Some(i) =>
                 <dl class="dl-horizontal" style="padding:5px;">
                   <dt>Plugin with ID</dt> <dd>{i.softwareId}</dd>
                   <dt>Licensee</dt> <dd>{i.licensee}</dd>
                   <dt>Supported version</dt> <dd>from {i.minVersion} to {i.maxVersion}</dd>
                   <dt>Validity period</dt> <dd>from {i.startDate.toString("YYYY-MM-dd")} to {i.endDate.toString("YYYY-MM-dd")}</dd>
                 </dl>
                 }}
                 {msg.map(s => <p class="text-danger">{s}</p>).getOrElse(NodeSeq.Empty)}
             </div>
           </div></div>
       }
     }
     </div>
  )
}



