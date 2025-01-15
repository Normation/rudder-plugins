/*
 *************************************************************************************
 * Copyright 2024 Normation SAS
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

package com.normation.plugins.datasources.api

import better.files.File
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.plugins.datasources.*
import com.normation.rudder.MockNodes
import com.normation.rudder.rest.RestTest
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.zio.*
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class RestDataSourceFilesTest extends ZIOSpecDefault {

  val restTestSetUp = RestTestSetUp.newEnv

  val datasourceRepo = new MemoryDataSourceRepository with NoopDataSourceCallbacks

  val mockNodes      = new MockNodes()
  val dataSourceApi9 = new DataSourceApiImpl(
    datasourceRepo,
    mockNodes.nodeInfoService,
    null,
    restTestSetUp.uuidGen
  )

  val (rudderApi, liftRules) =
    TraitTestApiFromYamlFiles.buildLiftRules(dataSourceApi9 :: Nil, restTestSetUp.apiVersions, Some(restTestSetUp.userService))
  val restTest               = new RestTest(liftRules)
  restTestSetUp.rudderApi.addModules(dataSourceApi9.getLiftEndpoints())

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "datasources_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val transformations: Map[String, String => String] = Map()

  val baseSourceType = DataSourceType.HTTP(
    "",
    Map(),
    HttpMethod.GET,
    Map(),
    false,
    "",
    DataSourceType.HTTP.defaultMaxParallelRequest,
    HttpRequestMode.OneRequestByNode,
    DataSource.defaultDuration,
    MissingNodeBehavior.Delete
  )
  val baseRunParam   = DataSourceRunParameters(DataSourceSchedule.NoSchedule(DataSource.defaultDuration), false, false)

  val datasource1 = DataSource(
    DataSourceId("datasource1"),
    DataSourceName(""),
    baseSourceType,
    baseRunParam,
    "",
    false,
    DataSource.defaultDuration
  )

  datasourceRepo.save(datasource1).runNow

  // Execute API request/response test cases from .yml files
  override def spec: Spec[TestEnvironment with Scope, Any] = {
    (suite("All REST tests defined in files") {

      for {
        s <- TraitTestApiFromYamlFiles.doTest(
               yamlSourceDirectory,
               yamlDestTmpDirectory,
               liftRules,
               Nil,
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
