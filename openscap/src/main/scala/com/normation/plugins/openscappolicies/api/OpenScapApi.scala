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

package com.normation.plugins.openscappolicies.api

import com.normation.inventory.domain.NodeId
import com.normation.plugins.openscappolicies.OpenscapPoliciesLogger
import com.normation.plugins.openscappolicies.services.OpenScapReportReader
import com.normation.plugins.openscappolicies.services.ReportSanitizer
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.GET
import com.normation.rudder.rest.*
import com.normation.rudder.rest.ApiModuleProvider
import com.normation.rudder.rest.EndpointSchema
import com.normation.rudder.rest.EndpointSchema.syntax.*
import com.normation.rudder.rest.GeneralApi
import com.normation.rudder.rest.SortIndex
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.zio.*
import enumeratum.*
import net.liftweb.http.InMemoryResponse
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line
import zio.syntax.*

sealed trait OpenScapApi extends EnumEntry with EndpointSchema with GeneralApi with SortIndex

object OpenScapApi extends Enum[OpenScapApi] with ApiModuleProvider[OpenScapApi] {

  // use that endpoint to get the original report. Be careful, can XSS
  final case object GetOpenScapReport extends OpenScapApi with OneParam with StartsAtVersion12 {
    val z              = implicitly[Line].value
    val description    = "Get OpenSCAP report for a node"
    val (action, path) = GET / "openscap" / "report" / "{id}"

    override def dataContainer: Option[String]          = None
    override val authz:         List[AuthorizationType] = AuthorizationType.Node.Read :: Nil
  }

  final case object GetSanitizedOpenScapReport extends OpenScapApi with OneParam with StartsAtVersion12 {
    val z              = implicitly[Line].value
    val description    = "Get sanitized OpenSCAP report for a node"
    val (action, path) = GET / "openscap" / "sanitized" / "{id}"

    override def dataContainer: Option[String]          = None
    override val authz:         List[AuthorizationType] = AuthorizationType.Node.Read :: Nil
  }

  def endpoints = values.toList.sortBy(_.z)
  def values    = findValues
}

class OpenScapApiImpl(
    openScapReportReader: OpenScapReportReader
) extends LiftApiModuleProvider[OpenScapApi] {
  api =>

  val logger = OpenscapPoliciesLogger

  def schemas: ApiModuleProvider[OpenScapApi] = OpenScapApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    OpenScapApi.endpoints.map {
      case OpenScapApi.GetOpenScapReport          => GetOpenScapReport
      case OpenScapApi.GetSanitizedOpenScapReport => GetSanitizedOpenScapReport
    }
  }

  def getReport(nodeId: NodeId, postProcessContent: String => String): LiftResponse = {
    (for {
      opt    <- openScapReportReader.getOpenScapReportFile(nodeId)
      report <- opt match {
                  case None                   => None.succeed
                  case Some((hostname, file)) =>
                    openScapReportReader.getOpenScapReportContent(nodeId, hostname, file).map(Some.apply)
                }
    } yield {
      report
    }).either.runNow match {
      case Right(Some(report)) =>
        logger.trace("doing in memory response")
        InMemoryResponse(
          postProcessContent(report.content).getBytes(),
          ("Content-Type", "text/html") :: ("Content-Disposition", "inline") :: Nil,
          Nil,
          200
        )
      case Right(None)         =>
        logger.trace("No report found")
        InMemoryResponse(
          s"No OpenSCAP report found for node '${nodeId}''".getBytes(),
          ("Content-Type" -> "text/txt") :: Nil,
          Nil,
          404
        )
      case Left(err)           =>
        val errorMessage = s"Could not get the OpenSCAP report for node ${nodeId.value}: ${err.fullMsg}"
        logger.info(errorMessage) // this is info level, because it can be expected errors like "no report"
        InMemoryResponse(errorMessage.getBytes(), Nil, Nil, 500)
    }
  }

  object GetOpenScapReport extends LiftApiModule {
    val schema: OpenScapApi.GetOpenScapReport.type = OpenScapApi.GetOpenScapReport

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        nodeId:     String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      getReport(NodeId(nodeId), identity)
    }
  }

  object GetSanitizedOpenScapReport extends LiftApiModule {
    val schema: OpenScapApi.GetSanitizedOpenScapReport.type = OpenScapApi.GetSanitizedOpenScapReport

    def process(
        version:    ApiVersion,
        path:       ApiPath,
        nodeId:     String,
        req:        Req,
        params:     DefaultParams,
        authzToken: AuthzToken
    ): LiftResponse = {
      getReport(NodeId(nodeId), ReportSanitizer.sanitizeHTMLReport)
    }
  }
}
