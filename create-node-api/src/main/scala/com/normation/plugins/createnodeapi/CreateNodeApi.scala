/*
*************************************************************************************
* Copyright 2019 Normation SAS
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

package com.normation.plugins.createnodeapi

import cats.data.ValidatedNel
import cats.data._
import cats.implicits._
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.AcceptedInventory
import com.normation.inventory.domain.FullInventory
import com.normation.inventory.domain.NodeId
import com.normation.inventory.domain.PendingInventory
import com.normation.inventory.ldap.core.LDAPFullInventoryRepository
import com.normation.inventory.ldap.core.InventoryDit
import com.normation.ldap.sdk.LDAPConnectionProvider
import com.normation.ldap.sdk.RwLDAPConnection
import com.normation.plugins.PluginStatus
import com.normation.plugins.createnodeapi.NodeTemplate.AcceptedNodeTemplate
import com.normation.plugins.createnodeapi.NodeTemplate.PendingNodeTemplate
import com.normation.plugins.createnodeapi.Serialize.ResultHolder
import com.normation.plugins.createnodeapi.Validation.NodeValidationError
import com.normation.rudder.api.HttpAction.PUT
import com.normation.rudder.domain.NodeDit
import com.normation.rudder.domain.nodes.Node
import com.normation.rudder.domain.nodes.NodeState
import com.normation.rudder.reports.ReportingConfiguration
import com.normation.rudder.repository.ldap.LDAPEntityMapper
import com.normation.rudder.rest.EndpointSchema.syntax._
import com.normation.rudder.rest._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.servers.NewNodeManager
import com.normation.utils.StringUuidGenerator
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JString
import net.liftweb.json.NoTypeHints
import org.joda.time.DateTime
import sourcecode.Line
import com.normation.box._
import zio._
import zio.syntax._
import com.normation.errors._
import com.normation.inventory.domain.InventoryStatus
import com.normation.zio.ZioRuntime

/*
 * This file contains the internal API used to discuss with the JS application.
 *
 * It gives the list of currently configured authentication backends.
 */
sealed trait CreateNodeApi extends EndpointSchema with GeneralApi with SortIndex
object CreateNodeApi extends ApiModuleProvider[CreateNodeApi] {

  final case object CreateNodes extends CreateNodeApi with ZeroParam with StartsAtVersion10 {
    val z = implicitly[Line].value
    val description    = "Create one of more new nodes"
    val (action, path) = PUT / "createnodes"
  }

  def endpoints = ca.mrvisser.sealerate.values[CreateNodeApi].toList.sortBy( _.z )
}


class CreateNodeApiImpl(
    restExtractorService: RestExtractorService
  , inventoryRepos      : LDAPFullInventoryRepository
  , ldapConnection      : LDAPConnectionProvider[RwLDAPConnection]
  , ldapEntityMapper    : LDAPEntityMapper
  , newNodeManager      : NewNodeManager
  , uuidGen             : StringUuidGenerator
  , nodeDit             : NodeDit
  , status              : PluginStatus
  , pendingDit          : InventoryDit
  , acceptedDit         : InventoryDit
) extends LiftApiModuleProvider[CreateNodeApi] {
  api =>

  implicit val formats = net.liftweb.json.Serialization.formats(NoTypeHints)

  def schemas = CreateNodeApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    CreateNodeApi.endpoints.map(e => e match {
        case CreateNodeApi.CreateNodes => CreateNodes
    }).toList
  }

  /*
   * Return a Json Object that list available backend,
   * their state of configuration, and what are the current
   * enabled ones.
   */
  object CreateNodes extends LiftApiModule0 {
    import Creation.CreationError

    val schema = CreateNodeApi.CreateNodes
    val restExtractor = api.restExtractorService
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        enabled <- if(status.isEnabled()) UIO.unit else Inconsistency(s"The plugin for that API is disable. Please check installation and licence information.").fail
        json    <- (req.json ?~! "This API only Accept JSON request").toIO
        nodes   <- Serialize.parseAll(json)
      } yield {
        import com.softwaremill.quicklens._
        nodes.foldLeft(ResultHolder(Nil, Nil)) { case (res, node) =>
          // now, try to save each node
          ZioRuntime.unsafeRun(saveNode(node, authzToken.actor).either) match {
            case Right(id) => res.modify(_.created).using(_ :+id)
            case Left(err) => res.modify(_.failed ).using(_ :+ ((node.id, err)) )
          }
        }
      }).toBox match {
        case Full(resultHolder) =>
          // if all succes, return success.
          // Or if at least one is not failed, return success ?
          if(resultHolder.failed.isEmpty) {
            RestUtils.toJsonResponse(None, resultHolder.toJson())(schema.name, params.prettify)
          } else {
            RestUtils.toJsonError(None, resultHolder.toJson())(schema.name, params.prettify)
          }
        case eb: EmptyBox =>
          val err = eb ?~! "Error when trying to parse node creation request"
          RestUtils.toJsonError(None, JString(err.messageChain))(schema.name, params.prettify)
      }
    }

    /// utility functions ///

    /*
     * for a given nodedetails, we:
     * - try to convert it to inventory/node setup info
     * - save inventory
     * - if needed, accept
     * - now, setup node info (property, state, etc)
     */
    def saveNode(nodeDetails: Rest.NodeDetails, eventActor: EventActor): IO[CreationError, NodeId] = {
      import Validated._
      def toCreationError(res: ValidatedNel[NodeValidationError, NodeTemplate]) = {
        res match {
          case Invalid(nel) => CreationError.OnValidation(nel).fail
          case Valid(r)     => r.succeed
        }
      }

      for {
        validated <- toCreationError(Validation.toNodeTemplate(nodeDetails))
        _         <- checkUuid(validated.inventory.node.main.id)
        created   <- saveInventory(validated.inventory)
        nodeSetup <- accept(validated, eventActor)
        nodeId    <- saveRudderNode(validated.inventory.node.main.id, nodeSetup)
      } yield {
        nodeId
      }
    }

    /*
     * You can't use an existing UUID (neither pending nor accepted)
     */
    def checkUuid(nodeId: NodeId): IO[CreationError, Unit] = {
      // we don't want a node in pending/accepted
      def inventoryExists(con: RwLDAPConnection, id:NodeId) = {
        ZIO.foldLeft(Seq((acceptedDit, AcceptedInventory), (pendingDit, PendingInventory)))(Option.empty[InventoryStatus]) { case(current, (dit, s)) =>
          current match {
            case None    => con.exists(dit.NODES.NODE.dn(id)).map(exists => if(exists) Some(s) else None)
            case Some(v) => Some(v).succeed
          }
        }.flatMap {
          case None => // ok, it doesn't exists
            UIO.unit
          case Some(s) => // oups, already present
            Inconsistency(s"A node with id '${nodeId.value}' already exists with status '${s.name}'").fail
        }
      }

      (for {
        con <- ldapConnection
        _   <- inventoryExists(con, nodeId)
      } yield ()).mapError(err => CreationError.OnSaveInventory(s"Error during node ID check: ${err.fullMsg}"))
    }

    /*
     * Save the inventory part. Alway save in "pending", acceptation
     * is done aftrward if needed.
     */
    def saveInventory(inventory: FullInventory): IO[CreationError, NodeId] = {
      inventoryRepos.save(inventory).map(_ => inventory.node.main.id).mapError(err => CreationError.OnSaveInventory(s"Error during node creation: ${err.fullMsg}"))
    }

    def accept(template: NodeTemplate, eventActor: EventActor): IO[CreationError, NodeSetup] = {
      val id = template.inventory.node.main.id

      newNodeManager.accept(id, ModificationId(uuidGen.newUuid), eventActor).toIO.mapError(err => CreationError.OnAcceptation((s"Can not accept node '${id.value}': ${err.fullMsg}"))) *> (template match {
            case AcceptedNodeTemplate(_, properties, policyMode, state) =>
              NodeSetup(properties, policyMode, state)
            case PendingNodeTemplate(_, properties) =>
              NodeSetup(properties, None, None)
          }).succeed
    }

    def mergeNodeSetup(node: Node, changes: NodeSetup): Node = {
      import com.softwaremill.quicklens._

      // for properties, we don't want to modify any of the existing one because
      // we were put during acceptation (or since node is live).
      val keep = node.properties.map(p => (p.name, p)).toMap
      val user = changes.properties.map(p => (p.name, p)).toMap
      // override user prop with keep
      val properties = (user ++ keep).values.toList

      node.modify(_.policyMode).using(x => changes.policyMode.fold(x)(Some(_)))
          .modify(_.state     ).using(x => changes.state.fold(x)(identity))
          .modify(_.properties).setTo(properties)
    }

    /*
     * Save rudder node part. Must be done after acceptation if
     * acceptation is needed. If no acceptation is wanted, then
     * we provide a default node context but we can't ensure that
     * policyMode / node state will be set (validation must forbid that)
     */
    def saveRudderNode(id: NodeId, setup: NodeSetup): IO[CreationError, NodeId] = {
      // a default Node
      def default() = Node(id, id.value, "", NodeState.Enabled, false, false, DateTime.now, ReportingConfiguration(None, None, None), Nil, None)

      (for {
        ldap    <- ldapConnection
        // try t get node
        entry   <- ldap.get(nodeDit.NODES.NODE.dn(id.value), NodeInfoService.nodeInfoAttributes:_*)
        current <- entry match {
                     case Some(x) => ldapEntityMapper.entryToNode(x).toIO
                     case None    => default().succeed
                   }
        merged  =  mergeNodeSetup(current, setup)
        // we ony want to touch things that were asked by the user
        nSaved  <- ldap.save(ldapEntityMapper.nodeToEntry(merged))
      } yield {
        merged.id
      }).mapError(err => CreationError.OnSaveNode(s"Error during node creation: ${err.fullMsg}"))
    }
  }
}



