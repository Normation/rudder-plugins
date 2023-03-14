package com.normation.plugins.changevalidation

import com.normation.cfclerk.xmlparsers.SectionSpecParser
import com.normation.cfclerk.xmlparsers.VariableSpecParser
import com.normation.cfclerk.xmlwriters.SectionSpecWriter
import com.normation.cfclerk.xmlwriters.SectionSpecWriterImpl
import com.normation.eventlog.EventActor
import com.normation.inventory.domain._
import com.normation.rudder.MockGitConfigRepo
import com.normation.rudder.MockNodeGroups
import com.normation.rudder.MockNodes
import com.normation.rudder.MockRules
import com.normation.rudder.MockTechniques
import com.normation.rudder.db.Doobie
import com.normation.rudder.domain.Constants
import com.normation.rudder.domain.nodes.NodeState.Enabled
import com.normation.rudder.domain.nodes._
import com.normation.rudder.domain.policies._
import com.normation.rudder.domain.queries.ObjectCriterion
import com.normation.rudder.reports.ReportingConfiguration
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.rudder.repository.jdbc.RudderDatasourceProvider
import com.normation.rudder.rule.category.RuleCategoryId
import com.normation.rudder.services.marshalling._
import com.normation.rudder.services.nodes.NodeInfoServiceCachedImpl
import com.normation.rudder.services.queries.CmdbQueryParser
import com.normation.rudder.services.queries.DefaultStringQueryParser
import com.normation.rudder.services.queries.JsonQueryLexer
import com.normation.rudder.services.workflows.RuleChangeRequest
import com.normation.rudder.services.workflows.RuleModAction
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Properties
import scala.concurrent.duration.FiniteDuration

@RunWith(classOf[JUnitRunner])
class TestWorkflowService extends Specification with Loggable {

  val mockGitRepo = new MockGitConfigRepo("")
  val mockRules = new MockRules()
  val mockNodes = new MockNodes()
  val mockTechniques = MockTechniques(mockGitRepo)

  val mockNodeGroups = new MockNodeGroups(mockNodes)

//  val nodeInfoService = RestTestSetUp.nodeInfoService

  val nodeInfoService = new NodeInfoServiceCachedImpl(
    null
    , null
    , null
    , null
    , null
    , null
    , null
    , FiniteDuration(100, "millis")
  )

  val properties: Properties = {
    val p = new Properties()
    val in = new ByteArrayInputStream(
      """ldap.host=localhost
        |ldap.port=1389
        |ldap.authdn=cn=manager,cn=rudder-configuration
        |ldap.authpw=secret
        |ldap.rudder.base=ou=Rudder, cn=rudder-configuration
        |ldap.node.base=cn=rudder-configuration
        |rudder.jdbc.driver=org.postgresql.Driver
        |rudder.jdbc.url=jdbc:postgresql://localhost:15432/rudder
        |rudder.jdbc.username=rudder
        |rudder.jdbc.password=Normation
        |rudder.jdbc.maxPoolSize=25
      """.stripMargin.getBytes(StandardCharsets.UTF_8))
    p.load(in)
    in.close
    p
  }

  val supervisedTargetRepo = new SupervisedTargetsReposiory(
    directory = Paths.get("/var/rudder/plugin-resources/")
    , filename  = "supervised-targets.json"
  )
  val dataSource = {
    val config = new RudderDatasourceProvider(
      properties.getProperty("rudder.jdbc.driver")
      , properties.getProperty("rudder.jdbc.url")
      , properties.getProperty("rudder.jdbc.username")
      , properties.getProperty("rudder.jdbc.password")
      , properties.getProperty("rudder.jdbc.maxPoolSize").toInt
    )
    config.datasource
  }

  val variableSpecParser = new VariableSpecParser
  val sectionSpecParser = new SectionSpecParser(variableSpecParser)
  val queryParser = new CmdbQueryParser with DefaultStringQueryParser with JsonQueryLexer {
    override val criterionObjects = Map[String, ObjectCriterion]()
  }
  val directiveUnserialisation = new DirectiveUnserialisationImpl
  val nodeGroupUnserialisation = new NodeGroupUnserialisationImpl(queryParser)
  val ruleUnserialisation = new RuleUnserialisationImpl
  val globalParameterUnserialisation = new GlobalParameterUnserialisationImpl


  ///// items serializer - service that transforms items to XML /////
  val ruleSerialisation: RuleSerialisation = new RuleSerialisationImpl("6")
  val rootSectionSerialisation : SectionSpecWriter = new SectionSpecWriterImpl()
  val directiveSerialisation: DirectiveSerialisation =
    new DirectiveSerialisationImpl(Constants.XML_CURRENT_FILE_FORMAT.toString)
  val nodeGroupSerialisation: NodeGroupSerialisation =
    new NodeGroupSerialisationImpl("6")
  val globalParameterSerialisation: GlobalParameterSerialisation =
    new GlobalParameterSerialisationImpl("6")
    new GlobalPropertySerialisationImpl("6")
  val changeRequestChangesSerialisation : ChangeRequestChangesSerialisation =
    new ChangeRequestChangesSerialisationImpl(
      Constants.XML_CURRENT_FILE_FORMAT.toString
      , nodeGroupSerialisation
      , directiveSerialisation
      , ruleSerialisation
      , globalParameterSerialisation
      , mockTechniques.techniqueRepo
      , rootSectionSerialisation
    )

  val changeRequestChangesUnserialisation = new ChangeRequestChangesUnserialisationImpl(
    nodeGroupUnserialisation
    , directiveUnserialisation
    , ruleUnserialisation
    , globalParameterUnserialisation
    , mockTechniques.techniqueRepo
    , sectionSpecParser
  )

  val doobie = new Doobie(dataSource)
  val changeRequestMapper = new ChangeRequestMapper(changeRequestChangesUnserialisation, changeRequestChangesSerialisation)

  val roChangeRequestRepository : RoChangeRequestRepository = {
    new RoChangeRequestJdbcRepository(doobie, changeRequestMapper)
  }

  val pluginStatusService =  new CheckRudderPluginEnableImpl(nodeInfoService)

  val nodeGroupValid = new NodeGroupValidationNeeded(
      supervisedTargetRepo.load _
      , roChangeRequestRepository
      , mockRules.ruleRepo
      , mockNodeGroups.groupsRepo
      , nodeInfoService
      )

  val nodeIds = (for {
    i <- 0 to 10
  } yield {
    NodeId(s"${i}")
  }).toSet

  def newNode(id : NodeId) = Node(id,"" ,"", NodeState.Enabled, false, false, DateTime.now, ReportingConfiguration(None,None, None), List(), None)

  val allNodeIds = nodeIds + NodeId("root")
  val nodes = allNodeIds.map {
    id =>
      (
        id
        , NodeInfo (
        newNode(id)
        , s"Node-${id}"
        , None
        , Linux(Debian, "Jessie", new Version("7.0"), None, new Version("3.2"))
        , Nil, DateTime.now
        , UndefinedKey, Seq(), NodeId("root")
        , "" , Set(), None, None, None
      )
      )
  }.toMap

  val g1 = NodeGroup (
    NodeGroupId("1"), "Empty group", "", Nil, None, false, Set(), true
  )
  val g2 = NodeGroup (
    NodeGroupId("2"), "only root", "", Nil, None, false, Set(NodeId("root")), true
  )
  val g3 = NodeGroup (
    NodeGroupId("3"), "Even nodes", "", Nil, None, false, nodeIds.filter(_.value.toInt == 2), true
  )
  val g4 = NodeGroup (
    NodeGroupId("4"), "Odd nodes", "", Nil, None, false, nodeIds.filter(_.value.toInt != 2), true
  )
  val g5 = NodeGroup (
    NodeGroupId("5"), "Nodes id divided by 3", "", Nil, None, false, nodeIds.filter(_.value.toInt == 3), true
  )
  val g6 = NodeGroup (
    NodeGroupId("6"), "Nodes id divided by 5", "", Nil, None, false, nodeIds.filter(_.value.toInt == 5), true
  )

  val groups = Set(g1, g2, g3, g4, g5, g6 )

  val groupTargets = groups.map(g => (GroupTarget(g.id),g))

  val fullRuleTargetInfos = (groupTargets.map(
    gt =>
      FullRuleTargetInfo(
        FullGroupTarget(gt._1,gt._2)
        , ""
        , ""
        , true
        , false
      )
  )).toList

  val groupLib = FullNodeGroupCategory (
    NodeGroupCategoryId("test_root")
    , ""
    , ""
    , Nil
    , fullRuleTargetInfos
  )

  def createNodeInfo(
    id: NodeId
    , machineUuid: Option[MachineUuid]) : NodeInfo = {
    NodeInfo(
      Node(id, id.value, id.value, Enabled, false, false, new DateTime(), null, null, None)
      , id.value
      , machineUuid.map(x => MachineInfo(x, null, None, None)), null, List(), new DateTime(0), null, Seq(), NodeId("root"), "root", Set(), None, None, None
    )
  }
//  def checkNodeTargetByRule(groups: FullNodeGroupCategory, allNodeInfo: Map[NodeId, NodeInfo], monitored: Set[SimpleTarget], rules: Set[Rule]): Boolean = {

  "Checking for workflow validation with supervised group" should {
    "trigger workflow when applying on a rule with an group with one node" in {
      val rule = Rule(RuleId("rule"), "rule", RuleCategoryId("rootcat"), Set(GroupTarget(g2.id)), Set(DirectiveId("directive-1")),"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups        = groupLib
        , allNodeInfo = nodes
        , monitored   = Set(GroupTarget(g2.id))
        , rules       = Set(rule)
      ) must beTrue
    }

    "not trigger workflow when applying on a rule with an empty group" in {
      val emptyRule = Rule(RuleId("rule-with-no-node"), "rule", RuleCategoryId("rootcat"), Set.empty, Set(DirectiveId("directive-1")),"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
          groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set(GroupTarget(g1.id))
        , rules       = Set(emptyRule)
      ) must beFalse
    }

    "trigger workflow when applying on a rule without directive but with node" in {
      val ruleWithNoDirective = Rule(RuleId("rule-with-no-directive"), "rule", RuleCategoryId("rootcat"), Set(GroupTarget(g2.id)), Set.empty,"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set(GroupTarget(g2.id))
        , rules       = Set(ruleWithNoDirective)
      ) must beTrue
    }

    "not trigger workflow when applying on a rule without directive and without node" in {
      val ruleWithNoDirective = Rule(RuleId("rule-with-no-directive"), "rule", RuleCategoryId("rootcat"), Set.empty, Set.empty,"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set(GroupTarget(g2.id))
        , rules       = Set(ruleWithNoDirective)
      ) must beFalse
    }
  }

  "Checking for workflow validation without supervised group" should {
    "not trigger workflow when applying on a rule with an group with one node" in {
      val rule = Rule(RuleId("rule"), "rule", RuleCategoryId("rootcat"), Set(GroupTarget(g2.id)), Set(DirectiveId("directive-1")),"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set.empty
        , rules       = Set(rule)
      ) must beFalse
    }

    "not trigger workflow when there is no rules" in {
      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set.empty
        , rules       = Set.empty
      ) must beFalse
    }

    "not trigger workflow when applying on a rule with an empty group" in {
      val emptyRule = Rule(RuleId("rule-with-no-node"), "rule", RuleCategoryId("rootcat"), Set.empty, Set(DirectiveId("directive-1")),"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set.empty
        , rules       = Set(emptyRule)
      ) must beFalse
    }


    "not trigger workflow when applying on a rule without directive but with node" in {
      val ruleWithNoDirective = Rule(RuleId("rule-with-no-directive"), "rule", RuleCategoryId("rootcat"), Set(GroupTarget(g2.id)), Set.empty,"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set.empty
        , rules       = Set(ruleWithNoDirective)
      ) must beFalse
    }

    "not trigger workflow when applying on a rule without directive and without node" in {
      val ruleWithNoDirective = Rule(RuleId("rule-with-no-directive"), "rule", RuleCategoryId("rootcat"), Set.empty, Set.empty,"","", true, true)

      nodeGroupValid.checkNodeTargetByRule(
        groups      = groupLib
        , allNodeInfo = nodes
        , monitored   = Set.empty
        , rules       = Set(ruleWithNoDirective)
      ) must beFalse
    }
  }

  "Checking for rule workflow" should {
    "trigger workflow when creating one rule with one node supervised" in {
      //        final case class RuleChangeRequest(action: RuleModAction, newRule: Rule, previousRule: Option[Rule])
      val rule = Rule(RuleId("rule"), "rule", RuleCategoryId("rootcat"), Set(GroupTarget(g2.id)), Set(DirectiveId("directive-1")),"","", true, true)

      val change = RuleChangeRequest(
        RuleModAction.Create
        , newRule = rule
        , previousRule = None
      )
      nodeGroupValid.forRule(EventActor("testActor"), change) shouldEqual  Full(true)
    }

  }







}
