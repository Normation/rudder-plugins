package com.normation.plugins.changevalidation.api

import better.files.File
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.eventlog.EventActor
import com.normation.plugins.changevalidation.MockValidatedUsers
import com.normation.plugins.changevalidation.WorkflowUsers
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class ValidatedUserApiTest extends ZIOSpecDefault {
  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "changevalidation_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

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
               List("api_validateduser.yml"),
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
