package com.normation.plugins.scaleoutrelay.api

import com.normation.eventlog.EventActor
import com.normation.inventory.domain.NodeId
import com.normation.plugins.scaleoutrelay.ScaleOutRelayService
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.rest._
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest.implicits._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line

sealed trait ScaleOutRelayApi extends EndpointSchema with GeneralApi with SortIndex {
  override def dataContainer: Option[String] = None
}
object ScaleOutRelayApi       extends ApiModuleProvider[ScaleOutRelayApi]           {

  final case object PromoteToRelay extends ScaleOutRelayApi with OneParam with StartsAtVersion10 {
    val z              = implicitly[Line].value
    val description    = "Promote a node to relay"
    val (action, path) = POST / "scaleoutrelay" / "promote" / "{nodeId}"
  }

  final case object DemoteToNode extends ScaleOutRelayApi with OneParam with StartsAtVersion14 {
    val z              = implicitly[Line].value
    val description    = "Demote a relay to a simple node"
    val (action, path) = POST / "scaleoutrelay" / "demote" / "{nodeId}"
  }

  override def endpoints: List[ScaleOutRelayApi] = ca.mrvisser.sealerate.values[ScaleOutRelayApi].toList.sortBy(_.z)
}

class ScaleOutRelayApiImpl(
    scaleOutRelayService: ScaleOutRelayService
) extends LiftApiModuleProvider[ScaleOutRelayApi] {

  override def schemas = ScaleOutRelayApi

  override def getLiftEndpoints(): List[LiftApiModule] = {
    ScaleOutRelayApi.endpoints.map {
      case ScaleOutRelayApi.PromoteToRelay => PromoteToRelay
      case ScaleOutRelayApi.DemoteToNode   => DemoteToNode
    }.toList
  }

  object PromoteToRelay extends LiftApiModule {
    val schema = ScaleOutRelayApi.PromoteToRelay

    def process(
        version: ApiVersion,
        path:    ApiPath,
        nodeId:  String,
        req:     Req,
        params:  DefaultParams,
        authz:   AuthzToken
    ): LiftResponse = {
      scaleOutRelayService
        .promoteNodeToRelay(NodeId(nodeId), EventActor("rudder"), Some(s"Promote node ${nodeId} to relay"))
        .chainError(s"Error when trying to promote mode $nodeId")
        .as(nodeId)
        .toLiftResponseOne(params, schema, None)
    }
  }
  object DemoteToNode   extends LiftApiModule {
    val schema = ScaleOutRelayApi.DemoteToNode

    def process(
        version: ApiVersion,
        path:    ApiPath,
        nodeId:  String,
        req:     Req,
        params:  DefaultParams,
        authz:   AuthzToken
    ): LiftResponse = {
      scaleOutRelayService
        .demoteRelayToNode(NodeId(nodeId), EventActor("rudder"), Some(s"Demote relay ${nodeId} to node"))
        .chainError(s"Error when trying to demote mode $nodeId")
        .as(nodeId)
        .toLiftResponseOne(params, schema, None)
    }
  }
}
