package com.normation.plugins.scaleoutrelay.api

import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.plugins.scaleoutrelay.ScaleOutRelayService
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.api.HttpAction.POST
import com.normation.rudder.facts.nodes.ChangeContext
import com.normation.rudder.rest.*
import com.normation.rudder.rest.EndpointSchema.syntax.*
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.rest.syntax.*
import com.normation.utils.StringUuidGenerator
import enumeratum.*
import java.time.Instant
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import sourcecode.Line

sealed trait ScaleOutRelayApi extends EnumEntry with EndpointSchema with GeneralApi with SortIndex {
  override def dataContainer: Option[String] = None
}

object ScaleOutRelayApi extends Enum[ScaleOutRelayApi] with ApiModuleProvider[ScaleOutRelayApi] {

  case object PromoteToRelay extends ScaleOutRelayApi with OneParam with StartsAtVersion10 {
    val z: Int = implicitly[Line].value
    val description    = "Promote a node to relay"
    val (action, path) = POST / "scaleoutrelay" / "promote" / "{nodeId}"
    val authz: List[AuthorizationType] = AuthorizationType.Administration.Write :: Nil
  }

  case object DemoteToNode extends ScaleOutRelayApi with OneParam with StartsAtVersion14 {
    val z: Int = implicitly[Line].value
    val description    = "Demote a relay to a simple node"
    val (action, path) = POST / "scaleoutrelay" / "demote" / "{nodeId}"
    val authz: List[AuthorizationType] = AuthorizationType.Administration.Write :: Nil
  }

  override def endpoints: List[ScaleOutRelayApi] = values.toList.sortBy(_.z)
  def values = findValues
}

class ScaleOutRelayApiImpl(
    scaleOutRelayService: ScaleOutRelayService,
    uuidGen:              StringUuidGenerator
) extends LiftApiModuleProvider[ScaleOutRelayApi] {

  override def schemas: ApiModuleProvider[ScaleOutRelayApi] = ScaleOutRelayApi

  override def getLiftEndpoints(): List[LiftApiModule] = {
    ScaleOutRelayApi.endpoints.map {
      case ScaleOutRelayApi.PromoteToRelay => PromoteToRelay
      case ScaleOutRelayApi.DemoteToNode   => DemoteToNode
    }.toList
  }

  object PromoteToRelay extends LiftApiModule {
    val schema: ScaleOutRelayApi.PromoteToRelay.type = ScaleOutRelayApi.PromoteToRelay

    def process(
        version: ApiVersion,
        path:    ApiPath,
        nodeId:  String,
        req:     Req,
        params:  DefaultParams,
        authz:   AuthzToken
    ): LiftResponse = {
      implicit val cc = ChangeContext(
        ModificationId(uuidGen.newUuid),
        authz.qc.actor,
        Instant.now(),
        params.reason.orElse(Some(s"Promote node ${nodeId} to relay")),
        Some(req.remoteAddr),
        authz.qc.nodePerms
      )
      scaleOutRelayService
        .promoteNodeToRelay(NodeId(nodeId))
        .chainError(s"Error when trying to promote mode $nodeId")
        .as(nodeId)
        .toLiftResponseOne(params, schema, None)
    }
  }
  object DemoteToNode   extends LiftApiModule {
    val schema: ScaleOutRelayApi.DemoteToNode.type = ScaleOutRelayApi.DemoteToNode

    def process(
        version: ApiVersion,
        path:    ApiPath,
        nodeId:  String,
        req:     Req,
        params:  DefaultParams,
        authz:   AuthzToken
    ): LiftResponse = {
      implicit val cc = ChangeContext(
        ModificationId(uuidGen.newUuid),
        authz.qc.actor,
        Instant.now(),
        params.reason.orElse(Some(s"Demote relay ${nodeId}")),
        Some(req.remoteAddr),
        authz.qc.nodePerms
      )
      scaleOutRelayService
        .demoteRelayToNode(NodeId(nodeId))
        .chainError(s"Error when trying to demote mode $nodeId")
        .as(nodeId)
        .toLiftResponseOne(params, schema, None)
    }
  }
}
