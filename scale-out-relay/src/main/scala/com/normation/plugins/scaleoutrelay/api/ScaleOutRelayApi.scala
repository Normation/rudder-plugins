package com.normation.plugins.scaleoutrelay.api


import com.normation.box._
import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.plugins.scaleoutrelay.ScaleOutRelayService
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.RestUtils.toJsonError
import com.normation.rudder.rest.RestUtils.toJsonResponse
import com.normation.rudder.rest._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import net.liftweb.common.Box
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json.NoTypeHints
import sourcecode.Line



sealed trait ScaleOutRelayApi extends EndpointSchema with GeneralApi with SortIndex
object ScaleOutRelayApi extends ApiModuleProvider[ScaleOutRelayApi] {

  final case object PromoteNodeToRelay extends ScaleOutRelayApi with OneParam with StartsAtVersion10 {
    val z = implicitly[Line].value
    val description = "Promote a node to relay"
    val (action, path) = POST / "scaleoutrelay" / "promote" / "{nodeId}"
  }

  override def endpoints: List[ScaleOutRelayApi] = ca.mrvisser.sealerate.values[ScaleOutRelayApi].toList.sortBy( _.z )
}

class ScaleOutRelayApiImpl(
    restExtractorService: RestExtractorService
    , scaleOutRelayService : ScaleOutRelayService
) extends LiftApiModuleProvider[ScaleOutRelayApi] {

  api =>

  implicit val formats = net.liftweb.json.Serialization.formats((NoTypeHints))
  override def schemas = ScaleOutRelayApi

  override def getLiftEndpoints(): List[LiftApiModule] = {
    ScaleOutRelayApi.endpoints.map {
      case ScaleOutRelayApi.PromoteNodeToRelay => PromoteNodeToRelay
    }.toList
  }

  def response(function: Box[JValue], req: Req, errorMessage: String, id: Option[String], dataName: String)(implicit action: String): LiftResponse = {
    RestUtils.response(restExtractorService, dataName, id)(function, req, errorMessage)
  }

  object PromoteNodeToRelay extends LiftApiModule {
    val schema = ScaleOutRelayApi.PromoteNodeToRelay
    val restExtractor = api.restExtractorService

    def process(version: ApiVersion, path: ApiPath, nodeId: String, req: Req, params: DefaultParams, authz: AuthzToken): LiftResponse = {
      val response = for {
        node <- scaleOutRelayService.promoteNodeToRelay(NodeId(nodeId), EventActor("rudder"), Some(s"Promote node ${nodeId} to relay")).toBox
      } yield {
        node
      }

      response match {
        case Full(node) =>
          toJsonResponse(None,node.id.value)("promoteToRelay",true)
        case eb: EmptyBox =>
          val message = (eb ?~ (s"Error when trying to promote mode $nodeId")).msg
          toJsonError(None, message)("promoteToRelay",true)
      }
    }
  }
}
