package com.normation.plugins.scaleoutrelay.api

import better.files.*
import com.normation.plugins.scaleoutrelay.MockServices
import com.normation.plugins.scaleoutrelay.ScaleOutRelayService
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import net.liftweb.common.Loggable
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class ScaleOutRelayApiTest extends Specification with TraitTestApiFromYamlFiles with Loggable with AfterAll {
  sequential
  isolated

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "scaleoutrelay_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

  val mockServices = new MockServices(Map.empty)
  val modules      = List(
    new ScaleOutRelayApiImpl(
      new ScaleOutRelayService(
        mockServices.woLDAPNodeGroupRepository,
        restTestSetUp.mockNodes.nodeFactRepo,
        restTestSetUp.mockDirectives.directiveRepo,
        restTestSetUp.mockRules.ruleRepo,
        mockServices.policyServerManagementService,
        mockServices.eventLogRepo,
        restTestSetUp.asyncDeploymentAgent
      ),
      restTestSetUp.uuidGen
    )
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)

  override def transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def afterAll(): Unit = {
    tmpDir.delete()
  }

  doTest(semanticJson = true)

}
