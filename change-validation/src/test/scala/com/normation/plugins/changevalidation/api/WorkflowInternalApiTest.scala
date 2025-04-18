package com.normation.plugins.changevalidation.api

import better.files.File
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.plugins.changevalidation.MockServices
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Cancelled
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Deployment
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Validation
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ChangeRequestInfo
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.Scope
import zio.ZIO
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class WorkflowInternalApiTest extends ZIOSpecDefault {

  private def mockChangeRequest(id: Int): ChangeRequest = {
    ConfigurationChangeRequest(
      ChangeRequestId(id),
      None,
      ChangeRequestInfo(s"cr ${id}", "mock change request"),
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty
    )
  }

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "changevalidation_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val mockServices = new MockServices(
    Map(
      Validation.id -> List(mockChangeRequest(1), mockChangeRequest(2)),
      Deployment.id -> List(mockChangeRequest(3)),
      Cancelled.id  -> List(mockChangeRequest(4))
    )
  )

  val modules = List(
    new WorkflowInternalApiImpl(
      mockServices.workflowRepository,
      restTestSetUp.userService
    )
  )

  val apiVersions = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil

  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)
  val transformations: Map[String, String => String] = Map()

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("All REST tests defined in files") {

      for {
        s <- TraitTestApiFromYamlFiles.doTest(
               yamlSourceDirectory,
               yamlDestTmpDirectory,
               liftRules,
               List("api_workflowinternal.yml"),
               transformations
             )
        _ <- effectUioUnit(
               if (java.lang.System.getProperty("tests.clean.tmp") != "false") IOResult.attempt(restTestSetUp.cleanup())
               else ZIO.unit
             )
      } yield s
    }
  }
}
