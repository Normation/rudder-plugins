package com.normation.plugins.openscappolicies.api

import java.io.InputStream

import com.normation.inventory.domain.NodeId
import com.normation.plugins.openscappolicies.OpenscapPoliciesLogger
import com.normation.plugins.openscappolicies.services.{OpenScapReportReader, ReportSanitizer}
import com.normation.rudder.api.HttpAction.{GET, PUT}
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.{ApiModuleProvider, EndpointSchema, GeneralApi, SortIndex, StartsAtVersion10, ZeroParam}
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest._
import com.normation.rudder.rest.lift.{DefaultParams, LiftApiModule, LiftApiModule0, LiftApiModuleProvider}
import net.liftweb.http.{InMemoryResponse, LiftResponse, Req}
import net.liftweb.json.{JValue, NoTypeHints}
import net.liftweb.common.{Box, EmptyBox, Failure, Full}
import sourcecode.Line
import com.normation.box._

sealed trait OpenScapApi extends EndpointSchema with GeneralApi with SortIndex
object OpenScapApi extends ApiModuleProvider[OpenScapApi] {

  final case object GetOpenScapReport extends OpenScapApi with OneParam with StartsAtVersion10 {
    val z = implicitly[Line].value
    val description    = "Get OpenScap report for a node"
    val (action, path) = GET / "openscap" / "report" / "{id}"
  }

  final case object GetSanitizedOpenScapReport extends OpenScapApi with OneParam with StartsAtVersion15 {
    val z = implicitly[Line].value
    val description    = "Get sanitized OpenScap report for a node"
    val (action, path) = GET / "openscap" / "sanitized" / "{id}"
  }

  def endpoints = ca.mrvisser.sealerate.values[OpenScapApi].toList.sortBy( _.z )
}


class OpenScapApiImpl(
    restExtractorService: RestExtractorService
  , openScapReportReader: OpenScapReportReader
  , reportSanitizer      : ReportSanitizer
) extends LiftApiModuleProvider[OpenScapApi] {
  api =>

  val logger = OpenscapPoliciesLogger

  implicit val formats = net.liftweb.json.Serialization.formats(NoTypeHints)

  def schemas = OpenScapApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    OpenScapApi.endpoints.map { case e => e match {
      case OpenScapApi.GetOpenScapReport => GetOpenScapReport
      case OpenScapApi.GetSanitizedOpenScapReport => GetSanitizedOpenScapReport
    }}.toList
  }

  def response(function: Box[JValue], req: Req, errorMessage: String, id: Option[String], dataName : String)(implicit action: String): LiftResponse = {
    RestUtils.response(restExtractorService, dataName, id)(function, req, errorMessage)
  }

  object GetOpenScapReport extends LiftApiModule {
    val schema = OpenScapApi.GetOpenScapReport
    val restExtractor = api.restExtractorService

    def process(version: ApiVersion, path: ApiPath, nodeId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "getOpenScapReport"
      (for {
        report <- openScapReportReader.getOpenScapReport(NodeId(nodeId))
      } yield {
        logger.info(s"Report for node ${nodeId} has been found ")
        report
      }) match {
        case Full(report) =>
          logger.trace("doing in memory response")
          InMemoryResponse(
              report.content.getBytes()
            , ("Content-Type" -> "text/html") ::
              ("Content-Disposition","""attachment;filename="rudder-openscap-%s.html"""".format(nodeId)) ::
              Nil
            , Nil
            , 200)
        case eb: EmptyBox =>
          val errorMessage = eb ?~! "Could not get the OpenScap report for node ${nodeId}"
          logger.error(errorMessage.messageChain)
          val html = <div class="error">{errorMessage.messageChain}</div>
          InMemoryResponse(
              errorMessage.messageChain.getBytes()
            , Nil
            , Nil
            , 404)
      }

    }
  }

  object GetSanitizedOpenScapReport extends LiftApiModule {
    val schema = OpenScapApi.GetSanitizedOpenScapReport
    val restExtractor = api.restExtractorService

    def process(version: ApiVersion, path: ApiPath, nodeId: String, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "getSanitizedOpenScapReport"
      (for {
        report        <- openScapReportReader.getOpenScapReport(NodeId(nodeId)) ?~! s"Cannot get OpenScap Report for node ${nodeId}"
        sanitizedXml  <- reportSanitizer.sanitizeReport(report).toBox ?~! "Error while sanitizing report"
      } yield {
        logger.trace(s"Report for node ${nodeId} has been found and sanitized")
        sanitizedXml
      }) match {
        case Full(sanitizedReport) =>
          logger.trace("Doing in memory response")
          InMemoryResponse(
            sanitizedReport.toString().getBytes()
            // somehow, the X-FRAME-OPTIONS get rewrote to DENY here
            ,  ("Content-Type", "text/html") :: ("Content-Disposition", "inline") :: Nil
            , Nil
            , 200)
        case eb: EmptyBox =>
          val errorMessage = eb ?~! "Could not get the sanitized OpenScap report for node ${nodeId}"
          logger.error(errorMessage.messageChain)
          val html = <div class="error">{errorMessage.messageChain}</div>
          InMemoryResponse(
            html.toString().getBytes()
            ,  ("Content-Type", "text/html") :: ("Content-Disposition", "inline")  :: Nil
            , Nil
            , 404)
      }

    }
  }

}