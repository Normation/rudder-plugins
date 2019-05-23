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

import cats.data._
import cats.implicits._
import com.normation.inventory.domain.AgentType.CfeCommunity
import com.normation.inventory.domain.AgentType.Dsc
import com.normation.inventory.domain._
import com.normation.plugins.createnodeapi.Creation.CreationError
import com.normation.plugins.createnodeapi.NodeTemplate.AcceptedNodeTemplate
import com.normation.plugins.createnodeapi.NodeTemplate.PendingNodeTemplate
import com.normation.rudder.domain.nodes.NodeProperty
import com.normation.rudder.domain.nodes.NodeState
import com.normation.rudder.domain.policies.PolicyMode
import net.liftweb.common.Box
import net.liftweb.common.Empty
import net.liftweb.common.EmptyBox
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.common.Logger
import net.liftweb.json.JsonAST.JValue
import org.slf4j.LoggerFactory

/**
 * Applicative log of interest for Rudder ops.
 */
object CreatenodeapiLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("create-node-api")
}



/*
 * This is the RestNodeDetails as provided by RestDataSerializer, but in a Scala format.
 * Do not change it without versionning, it's an actual API definition.
 */
object Rest {

  final case class OS(
      `type`       : String
    , name         : String
    , version      : String
    , fullName     : String
    , servicePack  : Option[String]
  )

  final case class AgentKey(
      token : JValue
    , status: Option[String]
  )

  final case class Timezone(
      name  : String
    , offset: String
  )

  final case class NodeDetails(
      id              : String
    , hostname        : String
    , status          : String
    , os              : OS
    , policyServerId  : Option[String]
    , machineType     : String
    , state           : Option[String]
    , policyMode      : Option[String]
    , agentKey        : Option[AgentKey]
    , properties      : Map[String, JValue]
    , ipAddresses     : List[String]
    , timezone        : Option[Timezone]
  )
}


/*
 * We need to be able to identify what the user actually asked
 * for in the rudder part of the node (state, policy mode, properties)
 * so that we can set that correctly after acceptation, or else let
 * default as they are.
 */
final case class NodeSetup(
    properties: List[NodeProperty]
  , policyMode: Option[PolicyMode]
  , state     : Option[NodeState]
)

/*
 * An object that hold the will-be-created node:
 * - inventory
 * - node info like properties, etc
 * - the target node status (it's created in "pending" but can be accepted)
 */
sealed trait NodeTemplate {
  def inventory : FullInventory
  def properties: List[NodeProperty]
}

object NodeTemplate {
  final case class PendingNodeTemplate(
      inventory : FullInventory
    , properties: List[NodeProperty]
  ) extends NodeTemplate

  final case class AcceptedNodeTemplate(
      inventory : FullInventory
    , properties: List[NodeProperty]
    , policyMode: Option[PolicyMode]
    , state     : Option[NodeState]
  ) extends NodeTemplate
}

object Serialize {
  import net.liftweb.json._

  implicit val formats = DefaultFormats


  def parseAll(json: JValue): Box[List[Rest.NodeDetails]] = {
    try {
      Full(json.extract[List[Rest.NodeDetails]])
    } catch {
      case ex: Exception => Failure(s"Error when deserializing a nodes for creation API: ${ex.getMessage}", Empty, Empty)
    }
  }

    /*
     * for each will-be-created node, we want to store if the result is success
     * of error. We will decide what to return from the API based on thatm.
     */
  final case class ResultHolder(
      //lists because we want to be able to have several time the same id
      created: List[NodeId]
    , failed : List[(String, CreationError)] // string because perhaps it's not yet a nodeId
  )


  implicit class ResultHolderToJson(res: ResultHolder) {

    def toJson(): JValue = {
      import net.liftweb.json.JsonDSL._
      (
        ("created" -> JArray(res.created.map(id => JString(id.value))))
      ~ ("failed"  -> JArray(res.failed.map { case (id, error) =>
                        JObject(JField(id, errorToJson(error)))
                      })
        )
      )
    }

    def errorToJson(error: CreationError): JValue = {
      error match {
        case CreationError.OnAcceptation(msg)   => JString(s"[accept] ${msg}")
        case CreationError.OnSaveInventory(msg) => JString(s"[save inventory] ${msg}")
        case CreationError.OnSaveNode(msg)      => JString(s"[save node] ${msg}")
        case CreationError.OnValidation(nel)    => JArray(nel.map(e => JString(s"[validation] ${e.msg}")).toList)
      }
    }
  }

}

object Creation {

  sealed trait CreationError

  object CreationError {

    final case class OnValidation(validations: NonEmptyList[Validation.NodeValidationError]) extends CreationError
    final case class OnSaveInventory(message: String)                                                 extends CreationError
    final case class OnSaveNode(message: String)                                                 extends CreationError
    final case class OnAcceptation(message: String)                                          extends CreationError
  }

}

object Validation {
  import Rest._
  import Validated._
  import com.normation.inventory.domain.AcceptedInventory
  import com.normation.inventory.domain.PendingInventory
  import com.normation.rudder.domain.nodes.NodeState
  import com.normation.rudder.domain.policies.{PolicyMode => PM}

  type Validation[T] = ValidatedNel[NodeValidationError, T]


  /*
   * Validate user provided node details and create the corresponding inventory
   */
  def toNodeTemplate(nodeDetails: Rest.NodeDetails): ValidatedNel[NodeValidationError, NodeTemplate] = {
    val os = getos(nodeDetails.os.`type`, nodeDetails.os.name, nodeDetails.os.version, nodeDetails.os.fullName, nodeDetails.os.servicePack)

    val checkedAgent : Validation[AgentInfo] = nodeDetails.agentKey match {
      case Some(a) => checkAgent(os.os, a)
      case None    => AgentInfo(AgentType.CfeCommunity, None, PublicKey("placeholder-value - not a real key")).validNel
    }


    ( checkId(nodeDetails.id.toLowerCase)
    , checkStatus(nodeDetails.status)
    ).mapN(Tuple2(_,_)) andThen { case (id, status) =>

      val machine = getMachine(id, PendingInventory, nodeDetails.machineType)

      (
        checkSummary(nodeDetails, id, PendingInventory, os)
      , checkedAgent
      , checkPolicyMode(status, nodeDetails.policyMode)
      , checkState(status, nodeDetails.state)
      ).mapN { case(summary, agent, mode, state) =>
        val inventory = FullInventory(NodeInventory(summary, agents = Seq(agent), machineId = Some((machine.id, PendingInventory))), Some(machine))
        val properties = nodeDetails.properties.map {case (k,v) => NodeProperty(k, v, None) }.toList
        if(status == PendingInventory) {
          PendingNodeTemplate(inventory, properties)
        } else {
          AcceptedNodeTemplate(inventory, properties, mode, state)
        }
      }
    }
  }



  sealed trait Machine {
    def tpe: MachineType
    final def name: String = tpe match {
      case PhysicalMachineType               => "physical"
      case VirtualMachineType(UnknownVmType) => "vm"
      case VirtualMachineType(vm)            => vm.name
    }
  }
  object Machine {
    case object MPhysical      extends Machine { val tpe = PhysicalMachineType               }
    case object MUnknownVmType extends Machine { val tpe = VirtualMachineType(UnknownVmType) }
    case object MSolarisZone   extends Machine { val tpe = VirtualMachineType(SolarisZone)   }
    case object MVirtualBox    extends Machine { val tpe = VirtualMachineType(VirtualBox)    }
    case object MVMWare        extends Machine { val tpe = VirtualMachineType(VMWare)        }
    case object MQEmu          extends Machine { val tpe = VirtualMachineType(QEmu)          }
    case object MXen           extends Machine { val tpe = VirtualMachineType(Xen)           }
    case object MAixLPAR       extends Machine { val tpe = VirtualMachineType(AixLPAR)       }
    case object MHyperV        extends Machine { val tpe = VirtualMachineType(HyperV)        }
    case object MBSDJail       extends Machine { val tpe = VirtualMachineType(BSDJail)       }

    val values = ca.mrvisser.sealerate.values[Machine]
  }

  sealed trait NodeValidationError { def msg: String }
  object NodeValidationError {
    def names[T](values: Iterable[T])(show: T => String) = values.toSeq.map(show).sorted.mkString("'", ", ", "'")
    final case class  UUID(x: String)          extends NodeValidationError {val msg = s"Only ID matching the shape of an UUID (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx) are authorized but '${x}' provided" }
    final case object Hostname                 extends NodeValidationError {val msg = "Hostname can't be empty" }
    final case class  Status(x: String)        extends NodeValidationError {val msg = s"Node 'status' must be one of ${names(allStatus)(_.name)} but '${x}' provided" }
    final case class  PolicyMode(x: String)    extends NodeValidationError {val msg = s"Node's policy mode must be one of ${names(PM.allModes)(_.name)} but '${x}' provided" }
    final case object PolicyModeOnBadStatus    extends NodeValidationError {val msg = s"Policy mode can not be specified when status=pending" }
    final case class  State(x: String)         extends NodeValidationError {val msg = s"Node 'state' must be one of ${names(NodeState.values)(_.name)} but '${x}' provided" }
    final case object StateOnBadStatus         extends NodeValidationError {val msg = s"Node 'state' can not be specified when status=pending" }
    final case class  Ram(x: String)           extends NodeValidationError {val msg = s"Provided RAM '${x}'can not be parsed as a memory amount" }
    final case class  MachineTpe(x: String)    extends NodeValidationError {val msg = s"Machine type must be one of ${names(Machine.values)(_.name)} but '${x}' provided" }
    final case class  Agent(x: String)         extends NodeValidationError {val msg = s"Management technologie agent must be one of ${names(AgentType.allValues)(_.id)} but '${x}' provided" }
    final case class  KeyStatus(x: String)     extends NodeValidationError {val msg = s"Key status must be '${CertifiedKey.value}' or '${UndefinedKey.value}' but '${x}' provided" }
    final case class  SecurityVal(msg: String) extends NodeValidationError
  }

  // only check shape alike bd645dfe-b4ce-4475-8d52-b7cfa106e333
  // but with any chars in [a-z0-9]
  val idregex = """[a-z0-9]{8}-([a-z0-9]{4}-){3}[a-z0-9]{12}""".r.pattern
  def checkId(id: String): ValidatedNel[NodeValidationError, NodeId] =
    if(idregex.matcher(id).matches()) NodeId(id).validNel
    else NodeValidationError.UUID(id).invalidNel

  def checkPolicyId(id: String): Validation[NodeId] = if(id == "root") NodeId("root").validNel else checkId(id)

  def checkHostname(h: String): Validation[String] = if(h.nonEmpty) h.validNel else NodeValidationError.Hostname.invalidNel

  def checkSummary(nodeDetails: NodeDetails, id: NodeId, status: InventoryStatus, os: OsDetails): Validation[NodeSummary] = {
    val root = os.os match {
      case _: WindowsType => "administrator"
      case _              => "root"
    }

    val policyServerId = nodeDetails.policyServerId.getOrElse("root")

    (
        checkHostname(nodeDetails.hostname)
      , checkPolicyId(policyServerId.toLowerCase)
      , nodeDetails.agentKey.flatMap( _.status ) match {
                   case None    => UndefinedKey.validNel
                   case Some(x) => checkKeyStatus(x)
                 }
    ).mapN(NodeSummary(id, status, root, _, os, _, _))
  }

  def getMachine(id: NodeId, status: InventoryStatus, machineType: String): MachineInventory = {
    val machineId = MachineUuid(IdGenerator.md5Hash(id.value))
    val machine = machineType.toLowerCase
    MachineInventory(
        machineId
      , status
      , Machine.values.find(_.name == machine).getOrElse(Machine.MPhysical).tpe
      , Some(machineId.value)
    )
  }

  def checkAgent(osType: OsType, agent: AgentKey): Validation[AgentInfo] = {
    def checkSecurityToken(agent: AgentType, token: JValue) : Validation[SecurityToken] = AgentInfoSerialisation.parseSecurityToken(agent, token, None) match {
      case eb: EmptyBox => NodeValidationError.SecurityVal((eb ?~! "").messageChain).invalidNel
      case Full(x)      => x.validNel
    }
    val tpe = osType match {
      case _: WindowsType => Dsc
      case _              => CfeCommunity
    }

    checkSecurityToken(tpe, agent.token) andThen { token =>
      AgentInfo(tpe, None, token).validNel
    }
  }

  def checkKeyStatus(s: String): Validation[KeyStatus] = KeyStatus(s) match {
    case eb: EmptyBox => NodeValidationError.KeyStatus(s).invalidNel
    case Full(x)      => x.validNel
  }

  /*
   * Check is string can be transformed to a T
   */
  def checkFind[T](input: String, error: NodeValidationError)(find: String => Option[T]): ValidatedNel[NodeValidationError, T] = {
    val i = input.toLowerCase()
    find(i) match {
      case None    => error.invalidNel
      case Some(x) => x.validNel
    }
  }


  // we only accept node in pending / accepted
  val allStatus = Set(AcceptedInventory, PendingInventory)
  def checkStatus(status: String): Validation[InventoryStatus] = checkFind(status, NodeValidationError.Status(status)){ x =>
    allStatus.find(_.name == x)
  }

  // state can not be set on pending status
  def checkState(status: InventoryStatus, state: Option[String]) : Validation[Option[NodeState]] = state match {
    case None    => None.validNel
    case Some(s) =>
      if(status == PendingInventory) NodeValidationError.StateOnBadStatus.invalidNel
      else checkFind(s, NodeValidationError.State(s)){ x =>
        NodeState.values.find(_.name == x)
      }.map(Some(_))
  }

  def checkPolicyMode(status: InventoryStatus, mode: Option[String]) : Validation[Option[PolicyMode]] = mode match {
    case None     => None.validNel
    case Some(pm) =>
      if(status == PendingInventory) NodeValidationError.PolicyModeOnBadStatus.invalidNel
      else checkFind(pm, NodeValidationError.PolicyMode(pm)){ x =>
        PM.allModes.find( _.name == x).map(Some(_))
      }
  }

  // this part is adapted from inventory extraction. It should be factored out
  // and not duplicated, obviously
  def getos(tpe: String, name: String, vers: String, fullName: String, srvPk: Option[String]): OsDetails = {
    val osType = tpe.toLowerCase
    val osName = name.toLowerCase

    val kernelVersion = new Version("N/A")

    val version = new Version(vers match {
      case "" => "N/A"
      case x  => x
    })

    val servicePack = srvPk match {
      case None     => None
      case Some("") => None
      case Some(x)  => Some(x)
    }

    //find os type, and name
    (osType, osName) match {
      case ("mswin32"|"windows", _ ) =>
        val x = osName + " "+ fullName.toLowerCase
        //in windows, relevant information are in the fullName string
        val os =
          if     (x contains "xp"     )   WindowsXP
          else if(x contains "vista"  )   WindowsVista
          else if(x contains "seven"  )   WindowsSeven
          else if(x contains "2000"   )   Windows2000
          else if(x contains "2003"   )   Windows2003
          else if(x contains "2008 r2")   Windows2008R2 //must be before 2008 for obvious reason
          else if(x contains "2008"   )   Windows2008
          else if(x contains "2012 r2")   Windows2012R2
          else if(x contains "2012"   )   Windows2012
          else if(x contains "2016 r2")   Windows2016R2
          else if(x contains "2016"   )   Windows2016
          else                            UnknownWindowsType

        Windows(
            os                  = os
          , fullName            = fullName
          , version             = version
          , servicePack         = servicePack
          , kernelVersion       = kernelVersion
          , userDomain          = None
          , registrationCompany = None
          , productId           = None
          , productKey          = None
        )

      case ("linux"  , x ) =>
        val distrib =
          if     (x contains "debian"    ) Debian
          else if(x contains "ubuntu"    ) Ubuntu
          else if(x contains "redhat"    ) Redhat
          else if(x contains "centos"    ) Centos
          else if(x contains "fedora"    ) Fedora
          else if(x contains "suse"      ) Suse
          else if(x contains "android"   ) Android
          else if(x contains "oracle"    ) Oracle
          else if(x contains "scientific") Scientific
          else if(x contains "slackware" ) Slackware
          else                             UnknownLinuxType

        Linux(
            os = distrib
          , fullName = fullName
          , version = version
          , servicePack = servicePack
          , kernelVersion = kernelVersion
        )

      case("solaris", _) =>
        Solaris(
            fullName = fullName
          , version = version
          , servicePack = servicePack
          , kernelVersion = kernelVersion
        )

      case("aix", _) => AixOS
        Aix(
            fullName = fullName
          , version = version
          , servicePack = servicePack
          , kernelVersion = kernelVersion
        )

      case ("freebsd", _) =>
        Bsd(
            os = FreeBSD
          , fullName = fullName
          , version = version
          , servicePack = servicePack
          , kernelVersion = kernelVersion
        )

      case _  =>
        UnknownOS(fullName, version, servicePack, kernelVersion)
    }
  }


}
