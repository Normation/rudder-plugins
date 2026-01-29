/*
 *************************************************************************************
 * Copyright 2025 Normation SAS
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
import com.normation.rudder.services.modification.DiffServiceImpl
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
      new DiffServiceImpl,
      restTestSetUp.mockTechniques.techniqueRepo,
      restTestSetUp.workflowLevelService,
      mockServices.changeRequestRepository,
      restTestSetUp.eventLogDetailsService,
      mockServices.changeRequestEventLogService,
      mockServices.commitAndDeployChangeRequest,
      mockServices.workflowEventLogService,
      restTestSetUp.mockCompliance.nodeFactRepo,
      restTestSetUp.mockDirectives.directiveRepo,
      restTestSetUp.mockNodeGroups.groupsRepo,
      restTestSetUp.ruleCategoryService,
      restTestSetUp.mockRules.ruleCategoryRepo
    )
  )

  val apiVersions = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil

  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)
  val transformations: Map[String, String => String] = Map()

  override def spec: Spec[TestEnvironment & Scope, Any] = {
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
