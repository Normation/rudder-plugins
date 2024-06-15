package com.normation.plugins.changevalidation.api

import better.files.File
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.plugins.changevalidation.MockSupervisedTargets
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class SupervisedTargetsApiTest extends ZIOSpecDefault {
  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "changevalidation_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  import java.nio.file.attribute.PosixFilePermissions
  import scala.jdk.CollectionConverters.*
  val file         = (tmpDir / "unsupervised-targets.json")
    .createIfNotExists()
    .overwrite("""{"unsupervised":["group:0000f5d3-8c61-4d20-88a7-bb947705ba8a"]}""")
    .setPermissions(PosixFilePermissions.fromString("rw-r--r--").asScala.toSet)
  val mockServices = new MockSupervisedTargets(
    tmpDir,
    "unsupervised-targets.json",
    restTestSetUp.mockNodeGroups.groupLib.copy(
      targetInfos = restTestSetUp.mockNodeGroups.groupsTargetInfos.drop(1).take(1).toList // only the second
      // the first is also added through subcategory "category_1"
    )
  )

  val modules = List(
    new SupervisedTargetsApiImpl(
      mockServices.unsupervisedRepo,
      mockServices.nodeGroupRepo
    )
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)

  val transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    (suite("All REST tests defined in files") {

      for {
        s <- TraitTestApiFromYamlFiles.doTest(
               yamlSourceDirectory,
               yamlDestTmpDirectory,
               liftRules,
               List("api_supervisedtargets.yml"),
               transformations
             )
        _ <- effectUioUnit(
               if (java.lang.System.getProperty("tests.clean.tmp") != "false") IOResult.attempt(restTestSetUp.cleanup())
               else ZIO.unit
             )
      } yield s
    })
  }
}
