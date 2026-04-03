package com.normation.plugins.scaleoutrelay.api

import better.files.*
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.inventory.domain.FullInventory
import com.normation.inventory.domain.NodeId
import com.normation.plugins.AlwaysEnabledPluginStatus
import com.normation.plugins.scaleoutrelay.DeleteNodeEntryService
import com.normation.plugins.scaleoutrelay.MockServices
import com.normation.plugins.scaleoutrelay.ScaleOutRelayService
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.facts.nodes.ChangeContext
import com.normation.rudder.facts.nodes.NodeFact
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class ScaleOutRelayApiTest extends ZIOSpecDefault {
  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "scaleoutrelay_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val mockServices = new MockServices(Map.empty)

  val modules = List(
    new ScaleOutRelayApiImpl(
      new ScaleOutRelayService(
        mockServices.woLDAPNodeGroupRepository,
        restTestSetUp.mockNodes.nodeFactRepo,
        restTestSetUp.mockDirectives.directiveRepo,
        restTestSetUp.mockRules.ruleRepo,
        mockServices.policyServerManagementService,
        mockServices.eventLogRepo,
        new DeleteNodeEntryService {
          override def delete(nodeId: NodeId): IOResult[Unit] = ZIO.unit
        }
      ),
      restTestSetUp.uuidGen
    )(using AlwaysEnabledPluginStatus)
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)

  val transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def spec: Spec[TestEnvironment & Scope, Any] = {
    (suite("All REST tests defined in files") {

      for {
        node1 <- restTestSetUp.mockNodes.nodeFactStorage
                   .getAccepted(NodeId("node1"))
                   .notOptional("node with id 'node1' cannot be missing")
        node3  = NodeFact.fromCompat(
                   node1
                     .copy(id = NodeId("node3"), rudderSettings = node1.rudderSettings.copy(policyServerId = NodeId("node-dsc")))
                     .toNodeInfo,
                   Right(FullInventory(node1.toFullInventory.node, None)),
                   List(),
                   None
                 )
        _     <- restTestSetUp.mockNodes.nodeFactRepo.save(node3)(using ChangeContext.newForRudder())
        s     <- TraitTestApiFromYamlFiles.doTest(
                   yamlSourceDirectory,
                   yamlDestTmpDirectory,
                   liftRules,
                   Nil,
                   transformations
                 )
        _     <- effectUioUnit(
                   if (java.lang.System.getProperty("tests.clean.tmp") != "false") IOResult.attempt(restTestSetUp.cleanup())
                   else ZIO.unit
                 )
      } yield s
    })
  }
}
