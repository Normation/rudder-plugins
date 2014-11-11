/*
*************************************************************************************
* Copyright 2014 Normation SAS
*************************************************************************************
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU Affero GPL v3, the copyright holders add the following
* Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
* licence, when you create a Related Module, this Related Module is
* not considered as a part of the work and may be distributed under the
* license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
*
*************************************************************************************
*/

package com.normation.plugins.nodeexternalreport.service

import java.sql.Timestamp
import org.joda.time.DateTime
import org.squeryl.{ Schema, Session, SessionFactory }
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode.{ _ }
import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.annotations.Column
import net.liftweb.common.Loggable
import java.io.File
import com.normation.rudder.services.nodes.NodeInfoService
import com.typesafe.config._
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo
import scala.collection.JavaConverters.asScalaSetConverter
import com.normation.inventory.domain.NodeId
import scala.collection.immutable.SortedMap

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
 *
 */
class ReadExternalReports(nodeInfoService: NodeInfoService, val reportConfigFile: String) extends Loggable {

  private[this] var config = loadConfig

  private[this] def loadConfig(): Box[ExternalReports] = {
    val config = {
      val c = ConfigFactory.parseFile(new File(reportConfigFile))
      ConfigFactory.load(c)
    }

    val pluginKey = "plugin.externalReport.reports"

    val reports = SortedMap[String, ExternalReport]() ++ (for {
                     report <- config.getObject(pluginKey).keySet.asScala
                    } yield {
                      (
                          report
                        , ExternalReport(
                              title         = config.getString(s"${pluginKey}.${report}.title")
                            , description   = config.getString(s"${pluginKey}.${report}.description")
                            , rootDirectory = new File(config.getString(s"${pluginKey}.${report}.rootPath"))
                            , reportName    = x => config.getString(s"${pluginKey}.${report}.reportName").replace("(node)", x)
                            , contentType   = config.getString(s"${pluginKey}.${report}.contentType")
                          )
                      )
                    })

    tryo(ExternalReports(
        tabTitle = config.getString("plugin.externalReport.tabName")
      , reports  = reports
    ))
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
    for {
      conf <- config
      node <- nodeInfoService.getNodeInfo(nodeId)
    } yield {
      NodeExternalReports(
          tabTitle = conf.tabTitle
        , reports  = conf.reports.map { case (key, report) =>
            val fileName = {
              val uuidName = report.reportName(node.id.value).toLowerCase
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
