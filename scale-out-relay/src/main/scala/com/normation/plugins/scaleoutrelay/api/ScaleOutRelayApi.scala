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
import com.normation.rudder.api.ApiVersion
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



sealed trait ScaleOutRelayApi extends EndpointSchema with GeneralApi with SortIndex {
  override def dataContainer: Option[String] = None
}
object ScaleOutRelayApi extends ApiModuleProvider[ScaleOutRelayApi] {

  final case object PromoteToRelay extends ScaleOutRelayApi with OneParam with StartsAtVersion10 {
    val z = implicitly[Line].value
    val description = "Promote a node to relay"
    val (action, path) = POST / "scaleoutrelay" / "promote" / "{nodeId}"
  }

  final case object DemoteToNode extends ScaleOutRelayApi with OneParam with StartsAtVersion14 {
    val z = implicitly[Line].value
    val description = "Demote a relay to a simple node"
    val (action, path) = POST / "scaleoutrelay" / "demote" / "{nodeId}"
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
      case ScaleOutRelayApi.PromoteToRelay => PromoteToRelay
      case ScaleOutRelayApi.DemoteToNode   => DemoteToNode
    }.toList
  }

  def response(function: Box[JValue], req: Req, errorMessage: String, id: Option[String], dataName: String)(implicit action: String): LiftResponse = {
    RestUtils.response(restExtractorService, dataName, id)(function, req, errorMessage)
  }

  object PromoteToRelay extends LiftApiModule {
    val schema = ScaleOutRelayApi.PromoteToRelay
    val restExtractor = api.restExtractorService

    def process(version: ApiVersion, path: ApiPath, nodeId: String, req: Req, params: DefaultParams, authz: AuthzToken): LiftResponse = {
      scaleOutRelayService.promoteNodeToRelay(NodeId(nodeId), EventActor("rudder"), Some(s"Promote node ${nodeId} to relay")).toBox match {
        case Full(node) =>
          toJsonResponse(None, nodeId)("promoteToRelay",true)
        case eb: EmptyBox =>
          val message = (eb ?~ (s"Error when trying to promote mode $nodeId")).msg
          toJsonError(None, message)("promoteToRelay",true)
      }
    }
  }
  object DemoteToNode extends LiftApiModule {
    val schema = ScaleOutRelayApi.DemoteToNode
    val restExtractor = api.restExtractorService

    def process(version: ApiVersion, path: ApiPath, nodeId: String, req: Req, params: DefaultParams, authz: AuthzToken): LiftResponse = {
      scaleOutRelayService.demoteRelayToNode(NodeId(nodeId), EventActor("rudder"), Some(s"Demote relay ${nodeId} to node")).toBox match {
        case Full(node) =>
          toJsonResponse(None, nodeId)("demoteToNode",true)
        case eb: EmptyBox =>
          val message = (eb ?~ (s"Error when trying to promote mode $nodeId")).msg
          toJsonError(None, message)("demoteToNode",true)
      }
    }
  }
}
