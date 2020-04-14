package com.normation.plugins.openscappolicies.services

import better.files._
import com.normation.box._
import com.normation.cfclerk.domain.TechniqueName
import com.normation.inventory.domain.NodeId
import com.normation.plugins.openscappolicies.repository.DirectiveRepository
import com.normation.plugins.openscappolicies.OpenScapReport
import com.normation.plugins.openscappolicies.OpenscapPoliciesLogger
import com.normation.rudder.repository.FindExpectedReportRepository
import com.normation.rudder.repository.RoDirectiveRepository
import com.normation.rudder.services.nodes.NodeInfoService
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo


/**
 * Read the openscap report
 * It is n /var/rudder/shared-files/root/NodeId/openscap.html
 */
class OpenScapReportReader(
    nodeInfoService             : NodeInfoService
  , directiveRepository         : RoDirectiveRepository
  , pluginDirectiveRepository   : DirectiveRepository
  , findExpectedReportRepository: FindExpectedReportRepository) {

  val OPENSCAP_REPORT_FILENAME = "openscap.html"
  val OPENSCAP_REPORT_PATH = "/var/rudder/shared-files/root/"


  val OPENSCAP_TECHNIQUE_ID = "plugin_openscap_policies"
  val logger = OpenscapPoliciesLogger

  private[this] def computePathFromNodeId(nodeId: NodeId): String = {
    OPENSCAP_REPORT_PATH + nodeId.value + "/" + OPENSCAP_REPORT_FILENAME
  }

  def checkifOpenScapApplied(nodeId: NodeId): Box[Boolean] = {
    import zio._
    val openScapDirectives = (for {
      // get active technique
      activeTechniques  <- pluginDirectiveRepository.getActiveTechniqueByTechniqueName(TechniqueName(OPENSCAP_TECHNIQUE_ID))
      activeTechniqueId = activeTechniques.map(_.id)
      // get directive from these active techniques
      directives        <- ZIO.foreach(activeTechniqueId) { atId =>
                              directiveRepository.getDirectives(atId)
                           }

    } yield {
      directives.flatten
    }).toBox

    openScapDirectives match {
      case f:Failure =>
        val errMessage = s"Could not identify the list of OpenSCAP related directives, cause is: ${f.messageChain}"
        logger.error(errMessage)
        Failure(errMessage)
      case Empty =>
        val errMessage = s"Could not identify the list of OpenSCAP related directives, no cause reported"
        logger.error(errMessage)
        Failure(errMessage)
      case Full(directives) if directives.size == 0 =>
        logger.info("There are not OpensCAP based directive yet")
        Full(false)
      case Full(directives) =>

        val expectedOption = (for {
            expectedReports <- findExpectedReportRepository.getCurrentExpectedsReports(Set(nodeId))
            expected        <- Box(expectedReports.get(nodeId)) ?~! s"Cannot find expected reports for node id ${nodeId}"
          } yield {
            expected
          })
          expectedOption match {
            case e:EmptyBox => e
            case Full(option) => option match {
              case None => Full(false) // node doesn't have expected reports, so openScap is not applied yet
              case Some(expected) =>
                val directivesId = directives.map (_.id)
                val expectedDirectives = expected.ruleExpectedReports.flatMap(_.directives).map(_.directiveId)

                Full(expectedDirectives.intersect(directivesId).nonEmpty)
            }
          }
    }
  }
  /**
   * Checks if the report exists, return True if exists, of False otherwise.
   * Everything else is a failure
   */
  def checkOpenScapReportExistence(nodeId: NodeId) : Box[Boolean] = {
    nodeInfoService.getNodeInfo(nodeId) match {
      case t: EmptyBox =>
        val errMessage = s"Node with id ${nodeId.value} does not exists"
        logger.error(errMessage)
        Failure(errMessage)
      case Full(id) =>
        id match {
          case None =>
            val errMessage = s"Node with id ${nodeId.value} not found"
            logger.error(errMessage)
            Failure(errMessage)
          case Some(nodeInfo) =>
            val path = computePathFromNodeId(nodeInfo.id)
            val reportFile = File(path)
            if (!reportFile.exists()) {
              val errMessage = s"OpenSCAP reports missing at location $path - can't show anything"
              logger.error(errMessage)
              Full(false)
            } else {
              // file exist, reading it
              logger.debug(s"Report file exists at ${path}")
              Full(true)
            }
        }
    }
  }

  /** Simply get the report - should be use for the UI, after validatio */
  def getOpenScapReportWithoutCheck(nodeId: NodeId): Box[OpenScapReport] = {
    val path = computePathFromNodeId(nodeId)
    val reportFile = File(path)
    if (!reportFile.exists()) {
      val errMessage = s"OpenSCAP reports missing at location $path - can't show anything"
      logger.error(errMessage)
      Failure(errMessage)
    } else {
      // file exist, reading it
      logger.debug(s"Loading report file at ${path}")
      tryo(OpenScapReport(nodeId, reportFile.contentAsString))
    }
  }

  /**
   * For a given node, return the structure
   * with the openscap report.
   * If the file is not there, fails
   * Used for API
   */
  def getOpenScapReport(nodeId: NodeId): Box[Option[OpenScapReport]] = {
    val path = computePathFromNodeId(nodeId)
    for {
      reportExists <- checkOpenScapReportExistence(nodeId)
      result       <- reportExists match {
            case false =>
              val errMessage = s"OpenSCAP reports missing at location $path - can't show anything"
              logger.debug(errMessage)
              Full(None)
            case true =>
              val reportFile = File(path)
              if (!reportFile.exists()) {
                val errMessage = s"OpenSCAP reports missing at location $path - can't show anything"
                logger.error(errMessage)
                Failure(errMessage)
              } else {
                // file exist, reading it
                logger.debug(s"Loading report file at ${path}")
                tryo(Some(OpenScapReport(nodeId, reportFile.contentAsString)))
          }
        }
    } yield {
      result
    }
  }
}
