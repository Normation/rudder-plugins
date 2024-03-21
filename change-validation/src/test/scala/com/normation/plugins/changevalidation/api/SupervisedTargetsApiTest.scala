package com.normation.plugins.changevalidation.api

import better.files.File
import com.normation.plugins.changevalidation.MockSupervisedTargets
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class SupervisedTargetsApiTest extends TraitTestApiFromYamlFiles with AfterAll {
  sequential

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "changevalidation_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

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
      targetInfos = List(
        restTestSetUp.mockNodeGroups.groupsTargetInfos(restTestSetUp.mockNodeGroups.g0.id),
        restTestSetUp.mockNodeGroups.groupsTargetInfos(restTestSetUp.mockNodeGroups.g1.id)
      )
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

  override def transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def afterAll(): Unit = {
    tmpDir.delete()
  }

  doTest(List("api_supervisedtargets.yml"), semanticJson = true)

}
