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

package com.normation.plugins.nodeexternalreports.service

import com.normation.box.*
import com.normation.errors.*
import com.normation.inventory.domain.NodeId
import com.normation.rudder.facts.nodes.NodeFactRepository
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.utils.FileUtils
import com.typesafe.config.*
import java.io.File
import net.liftweb.common.*
import net.liftweb.util.Helpers.tryo
import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters.*
import zio.syntax.*

final case class ExternalReport(
    title:         String,
    description:   String,
    rootDirectory: File,
    reportName:    String => String,
    contentType:   String
)

final case class ExternalReports(
    tabTitle: String,
    reports:  SortedMap[String, ExternalReport]
)

final case class NodeExternalReport(
    title:       String,
    description: String,
    fileName:    Option[String]
)

final case class NodeExternalReports(
    tabTitle: String,
    reports:  SortedMap[String, NodeExternalReport]
)

/**
 * Read the reports configuration file and build the
 * awaited reports
 */
class ReadExternalReports(nodeFactRepo: NodeFactRepository, val reportConfigFile: String) extends Loggable {

  private[this] var config: Box[ExternalReports] = null

  private[this] def loadConfig(): Box[ExternalReports] = tryo {

    val configFile = new File(reportConfigFile)

    if (!configFile.exists) {
      throw new IllegalArgumentException(s"The configuration file '${reportConfigFile}' does not exist")
    }

    val config = ConfigFactory.parseFile(
      configFile,
      ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)
    )

    val pluginKey = "plugin.node-external-reports.reports"

    val reports = SortedMap[String, ExternalReport]() ++ (for {
      report <- config.getObject(pluginKey).keySet.asScala
    } yield {
      (
        report,
        ExternalReport(
          title = config.getString(s"${pluginKey}.${report}.title"),
          description = config.getString(s"${pluginKey}.${report}.description"),
          rootDirectory = new File(config.getString(s"${pluginKey}.${report}.dirname")),
          reportName = x => config.getString(s"${pluginKey}.${report}.filename").replace("@@node@@", x),
          contentType = config.getString(s"${pluginKey}.${report}.content-type")
        )
      )
    })

    ExternalReports(
      tabTitle = config.getString("plugin.node-external-reports.tab-name"),
      reports = reports
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
  def getExternalReports(nodeId: NodeId)(implicit qc: QueryContext): Box[NodeExternalReports] = {
    if (config == null) loadAndUpdateConfig()

    for {
      conf    <- config
      optNode <- nodeFactRepo.get(nodeId).toBox
      node    <- optNode match {
                   case None    => Failure(s"The node with ID '${nodeId}' was not found, we can't add external information")
                   case Some(n) => Full(n)
                 }
    } yield {
      NodeExternalReports(
        tabTitle = conf.tabTitle,
        reports = conf.reports.map {
          case (key, report) =>
            (key, NodeExternalReport(report.title, report.description, resolveExistingFileName(report, node.id.value, node.fqdn)))
        }
      )
    }
  }

  /**
   * Resolve, server-side, the report file name for a node: try the hostname-based name first,
   * then the uuid-based one, and only return a name whose file actually exists. The node uuid
   * and fqdn are the only inputs used to build the name - never a client-supplied file name.
   */
  private def resolveExistingFileName(report: ExternalReport, nodeUuid: String, fqdn: String): Option[String] = {
    val uuidName     = report.reportName(nodeUuid).toLowerCase
    val hostnameName = report.reportName(fqdn).toLowerCase

    if ((new File(report.rootDirectory, hostnameName)).exists) {
      Some(hostnameName)
    } else if ((new File(report.rootDirectory, uuidName)).exists) {
      Some(uuidName)
    } else None
  }

  /**
   * Return the report file and its content type for the given node and report type.
   *
   * The file name is resolved server-side from the node (looked up through the fact repository,
   * so tenant/node ACLs carried by `qc` are enforced) - the caller never supplies a file name.
   * As defense in depth, the resolved name is still run through `sanitizePath` and fails closed
   * on any attempt to escape the report root directory.
   *
   * `None` means either: the report type is unknown, the node is not visible to the caller, or no
   * report file exists for that node.
   */
  def getReportFile(nodeId: NodeId, reportType: String)(implicit qc: QueryContext): IOResult[Option[(File, String)]] = {
    if (config == null) loadAndUpdateConfig()
    for {
      conf <- config.toIO
      opt  <- nodeFactRepo.get(nodeId)
      res  <- (opt, conf.reports.get(reportType)) match {
                case (Some(node), Some(report)) =>
                  resolveExistingFileName(report, node.id.value, node.fqdn) match {
                    case Some(fileName) =>
                      FileUtils
                        .sanitizePath(better.files.File(report.rootDirectory.getPath), fileName)
                        .map(f => Some((f.toJava, report.contentType)))
                    case None           => None.succeed
                  }
                case _                          => None.succeed
              }
    } yield res
  }

}
