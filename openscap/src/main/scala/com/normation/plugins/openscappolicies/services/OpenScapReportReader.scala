/*
 *************************************************************************************
 * Copyright 2024 Normation SAS
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

package com.normation.plugins.openscappolicies.services

import better.files.*
import com.normation.box.*
import com.normation.cfclerk.domain.TechniqueName
import com.normation.errors.*
import com.normation.inventory.domain.NodeId
import com.normation.plugins.openscappolicies.OpenscapPoliciesLogger
import com.normation.plugins.openscappolicies.OpenscapPoliciesLoggerPure
import com.normation.plugins.openscappolicies.OpenScapReport
import com.normation.rudder.repository.FindExpectedReportRepository
import com.normation.rudder.repository.RoDirectiveRepository
import com.normation.rudder.services.nodes.NodeInfoService
import java.nio.charset.StandardCharsets
import net.liftweb.common.*
import zio.*
import zio.syntax.*

/**
 * Read the openscap report
 * It is n /var/rudder/shared-files/root/NodeId/openscap.html
 */
class OpenScapReportReader(
    nodeInfoService:              NodeInfoService,
    directiveRepository:          RoDirectiveRepository,
    pluginDirectiveRepository:    GetActiveTechniqueIds,
    findExpectedReportRepository: FindExpectedReportRepository,
    openScapReportDirPath:        String
) {
  import OpenScapReportReader.*

  val openScapReportPath = File(openScapReportDirPath)

  val logger = OpenscapPoliciesLogger

  private[this] def computePathFromNodeId(nodeId: NodeId): String = {
    (openScapReportPath / nodeId.value / OPENSCAP_REPORT_FILENAME).pathAsString
  }

  def checkifOpenScapApplied(nodeId: NodeId): Box[Boolean] = {
    val openScapDirectives = (for {
      // get active technique
      activeTechniqueIds <- pluginDirectiveRepository.getActiveTechniqueIdByTechniqueName(TechniqueName(OPENSCAP_TECHNIQUE_ID))
      // get directive from these active techniques
      directives         <- ZIO.foreach(activeTechniqueIds)(atId => directiveRepository.getDirectives(atId))
    } yield {
      directives.flatten
    }).toBox

    openScapDirectives match {
      case f: Failure =>
        val errMessage = s"Could not identify the list of OpenSCAP related directives, cause is: ${f.messageChain}"
        logger.error(errMessage)
        Failure(errMessage)
      case Empty =>
        val errMessage = s"Could not identify the list of OpenSCAP related directives, no cause reported"
        logger.error(errMessage)
        Failure(errMessage)
      case Full(directives) if directives.size == 0 =>
        logger.info("There are no OpensCAP-based directive yet")
        Full(false)
      case Full(directives) =>
        val expectedOption = (for {
          expectedReports <- findExpectedReportRepository.getCurrentExpectedsReports(Set(nodeId))
          expected        <- Box(expectedReports.get(nodeId)) ?~! s"Cannot find expected reports for node id ${nodeId}"
        } yield {
          expected
        })
        expectedOption match {
          case e: EmptyBox => e
          case Full(option) =>
            option match {
              case None           => Full(false) // node doesn't have expected reports, so openScap is not applied yet
              case Some(expected) =>
                val directivesId       = directives.map(_.id)
                val expectedDirectives = expected.ruleExpectedReports.flatMap(_.directives).map(_.directiveId)

                Full(expectedDirectives.intersect(directivesId).nonEmpty)
            }
        }
    }
  }

  /*
   * Retrieve the hostname and file of OpenSCAP report for given node if it exists, None if not.
   */
  def getOpenScapReportFile(nodeId: NodeId): IOResult[Option[(String, File)]] = {
    for {
      nodeInfo  <- nodeInfoService.getNodeInfo(nodeId).notOptional(s"Node with id ${nodeId.value} does not exist")
      path       = computePathFromNodeId(nodeInfo.id)
      reportFile = File(path)
      exists    <- IOResult.attempt(reportFile.exists())
      res       <- if (!exists) {
                     None.succeed
                   } else {
                     OpenscapPoliciesLoggerPure.debug(s"OpenSCAP report for node '${nodeId.value}' exists at ${path}") *>
                     Some((nodeInfo.hostname, reportFile)).succeed
                   }
    } yield res
  }

  /**
   * Retrieve the report for the corresponding node file.
   * Return an error in case of I/O problem.
   */
  def getOpenScapReportContent(nodeId: NodeId, hostname: String, file: File): IOResult[OpenScapReport] = {
    for {
      content <- IOResult.attempt(s"Error when retrieving report content")(file.contentAsString(StandardCharsets.UTF_8))
    } yield OpenScapReport(nodeId, hostname, content)
  }
}

object OpenScapReportReader {

  val OPENSCAP_REPORT_FILENAME = "openscap_report.html"
  val OPENSCAP_TECHNIQUE_ID    = "plugin_openscap_policies"

}
