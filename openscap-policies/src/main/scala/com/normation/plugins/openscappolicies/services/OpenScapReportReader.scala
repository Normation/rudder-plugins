package com.normation.plugins.openscappolicies.services

import com.normation.inventory.domain.NodeId
import com.normation.plugins.openscappolicies.{OpenScapReport, OpenscapPoliciesLogger}
import com.normation.rudder.services.nodes.NodeInfoService
import net.liftweb.common._
import net.liftweb.util.Helpers.tryo
import better.files._


/**
 * Read the openscap report
 * It is n /var/rudder/shared-files/root/NodeId/openscap.html
 */
class OpenScapReportReader(nodeInfoService: NodeInfoService) {

  val OPENSCAP_REPORT_FILENAME = "openscap.html"
  val OPENSCAP_REPORT_PATH = "/var/rudder/shared-files/root/"

  val logger = OpenscapPoliciesLogger

  private[this] def computePathFromNodeId(nodeId: NodeId): String = {
    OPENSCAP_REPORT_PATH + nodeId.value + "/" + OPENSCAP_REPORT_FILENAME
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
  def getOpenScapReport(nodeId: NodeId): Box[OpenScapReport] = {
    val path = computePathFromNodeId(nodeId)
    for {
      reportExists <- checkOpenScapReportExistence(nodeId)
      result       <- reportExists match {
            case false =>
              val errMessage = s"OpenSCAP reports missing at location $path - can't show anything"
              logger.error(errMessage)
              Failure(errMessage)
            case true =>
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
    } yield {
    result
    }
  }
}
