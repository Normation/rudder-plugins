package com.normation.plugins.changevalidation.api

import better.files.File
import com.normation.eventlog.EventActor
import com.normation.plugins.changevalidation.MockValidatedUsers
import com.normation.plugins.changevalidation.WorkflowUsers
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class ValidatedUserApiTest extends TraitTestApiFromYamlFiles with AfterAll {
  sequential

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "changevalidation_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

  val mockServices = new MockValidatedUsers(
    List(
      WorkflowUsers(EventActor("admin-user"), isValidated = true, userExists = true),
      WorkflowUsers(EventActor("validated-user"), isValidated = true, userExists = false),
      WorkflowUsers(EventActor("not-validated-user"), isValidated = false, userExists = true)
    ).map(u => u.actor -> u).toMap
  )

  val modules = List(
    new ValidatedUserApiImpl(
      mockServices.validatedUserRepo,
      mockServices.validatedUserRepo
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

  doTest(List("api_validateduser.yml"), semanticJson = true)

}
