/*
*************************************************************************************
* Copyright 2014 Normation SAS
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

package com.normation.plugins.nodeexternalreport.service

import java.io.File

import scala.collection.immutable.SortedMap
import scala.collection.JavaConverters.asScalaSetConverter

import com.normation.inventory.domain.NodeId
import com.normation.rudder.services.nodes.NodeInfoService
import com.typesafe.config._

import net.liftweb.common._
import net.liftweb.util.Helpers.tryo

final case class ExternalReport(
    title        : String
  , description  : String
  , rootDirectory: File
  , reportName   : String => String
  , contentType  : String
)

final case class ExternalReports(
    tabTitle : String
  , reports  : SortedMap[String, ExternalReport]
)

final case class NodeExternalReport(
    title        : String
  , description  : String
  , fileName     : Option[String]
)

final case class NodeExternalReports(
    tabTitle : String
  , reports  : SortedMap[String, NodeExternalReport]
)

/**
 * Read the reports configuration file and build the
 * awaited reports
 */
class ReadExternalReports(nodeInfoService: NodeInfoService, val reportConfigFile: String) extends Loggable {

  private[this] var config: Box[ExternalReports] = null

  private[this] def loadConfig(): Box[ExternalReports] = tryo {

    val configFile = new File(reportConfigFile)

    if(!configFile.exists) {
      throw new IllegalArgumentException(s"The configuration file '${reportConfigFile}' does not exist")
    }

    val config = ConfigFactory.parseFile(
        configFile
     ,  ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)
    )

    val pluginKey = "plugin.node-external-reports.reports"

    val reports = SortedMap[String, ExternalReport]() ++ (for {
                     report <- config.getObject(pluginKey).keySet.asScala
                    } yield {
                      (
                          report
                        , ExternalReport(
                              title         = config.getString(s"${pluginKey}.${report}.title")
                            , description   = config.getString(s"${pluginKey}.${report}.description")
                            , rootDirectory = new File(config.getString(s"${pluginKey}.${report}.dirname"))
                            , reportName    = x => config.getString(s"${pluginKey}.${report}.filename").replace("@@node@@", x)
                            , contentType   = config.getString(s"${pluginKey}.${report}.content-type")
                          )
                      )
                    })

    ExternalReports(
        tabTitle = config.getString("plugin.node-external-reports.tab-name")
      , reports  = reports
    )
  }

  def loadAndUpdateConfig(): Box[ExternalReports] = {
    config = loadConfig()
    config
  }

  /**
   * For a given node, return the structure
   * with the correct report file for that node.
   * A Node says that no file was found
   */
  def getExternalReports(nodeId: NodeId): Box[NodeExternalReports] = {
    if(config == null) loadAndUpdateConfig()

    for {
      conf    <- config
      optNode <- nodeInfoService.getNodeInfo(nodeId)
      node    <- optNode match {
        case None    => Failure(s"The node with ID '${nodeId}' was not found, we can add external information")
        case Some(n) => Full(n)
      }
    } yield {
      NodeExternalReports(
          tabTitle = conf.tabTitle
        , reports  = conf.reports.map { case (key, report) =>
            val fileName = {
              val uuidName     = report.reportName(node.id.value).toLowerCase
              val hostnameName = report.reportName(node.hostname).toLowerCase

              if((new File(report.rootDirectory, hostnameName)).exists) {
                Some(hostnameName)
              } else if((new File(report.rootDirectory, uuidName)).exists) {
                Some(uuidName)
              } else None
            }
            (key, NodeExternalReport(report.title, report.description, fileName))
          }
      )
    }
  }

  def getFileContent(reportType: String, fileName: String): Box[(File, String)] = {
    for {
      conf   <- config
      report <- Box(conf.reports.get(reportType))
    } yield {
      (new File(report.rootDirectory, fileName), report.contentType)
    }
  }

}
