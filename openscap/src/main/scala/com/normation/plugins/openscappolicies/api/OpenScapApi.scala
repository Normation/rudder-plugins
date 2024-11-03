package com.normation.plugins.openscappolicies.api

import com.normation.box.*
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
import enumeratum.*
import net.liftweb.common.Box
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.http.InMemoryResponse
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line

sealed trait OpenScapApi extends EnumEntry with EndpointSchema with GeneralApi with SortIndex

object OpenScapApi extends Enum[OpenScapApi] with ApiModuleProvider[OpenScapApi] {

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
    openScapReportReader: OpenScapReportReader,
    reportSanitizer:      ReportSanitizer
) extends LiftApiModuleProvider[OpenScapApi] {
  api =>

  val logger = OpenscapPoliciesLogger

  def schemas: ApiModuleProvider[OpenScapApi] = OpenScapApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    OpenScapApi.endpoints.map {
      case e =>
        e match {
          case OpenScapApi.GetOpenScapReport          => GetOpenScapReport
          case OpenScapApi.GetSanitizedOpenScapReport => GetSanitizedOpenScapReport
        }
    }.toList
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
      (for {
        report <- openScapReportReader.getOpenScapReport(NodeId(nodeId))(authzToken.qc)
      } yield {
        logger.info(s"Report for node ${nodeId} has been found ")
        report
      }) match {
        case Full(Some(report)) =>
          logger.trace("doing in memory response")
          InMemoryResponse(
            report.content.getBytes(),
            ("Content-Type", "text/html") :: ("Content-Disposition", "inline") :: Nil,
            Nil,
            200
          )
        case Full(None)         =>
          logger.trace("No report found")
          InMemoryResponse(
            s"No OpenSCAP report found for nodeId ${nodeId}".getBytes(),
            ("Content-Type" -> "text/txt") ::
            Nil,
            Nil,
            404
          )
        case eb: EmptyBox =>
          val errorMessage = eb ?~! "Could not get the OpenSCAP report for node ${nodeId}"
          logger.error(errorMessage.messageChain)
          InMemoryResponse(errorMessage.messageChain.getBytes(), Nil, Nil, 404)
      }

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
      (for {
        report       <- openScapReportReader.getOpenScapReport(NodeId(nodeId))(
                          authzToken.qc
                        ) ?~! s"Cannot get OpenSCAP report for node ${nodeId}"
        existence    <- Box(report) ?~! s"Report not found for node ${nodeId}"
        sanitizedXml <- reportSanitizer.sanitizeReport(existence).toBox ?~! "Error while sanitizing report"
      } yield {
        logger.trace(s"Report for node ${nodeId} has been found and sanitized")
        sanitizedXml
      }) match {
        case Full(sanitizedReport) =>
          logger.trace("Doing in memory response")
          InMemoryResponse(
            sanitizedReport.toString().getBytes(),
            ("Content-Type", "text/html") :: ("Content-Disposition", "inline") :: Nil,
            Nil,
            200
          )
        case eb: EmptyBox =>
          val errorMessage = eb ?~! "Could not get the sanitized OpenSCAP report for node ${nodeId}"
          logger.error(errorMessage.messageChain)
          val html         = <div class="error">{errorMessage.messageChain}</div>
          InMemoryResponse(
            html.toString().getBytes(),
            ("Content-Type", "text/html") :: ("Content-Disposition", "inline") :: Nil,
            Nil,
            404
          )
      }
    }
  }
}
