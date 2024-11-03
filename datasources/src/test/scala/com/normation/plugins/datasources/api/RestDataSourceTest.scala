/*
 *************************************************************************************
 * Copyright 2016 Normation SAS
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
import com.normation.plugins.datasources.*
import com.normation.plugins.datasources.DataSourceJsonCodec.*
import com.normation.rudder.MockNodes
import com.normation.rudder.domain.properties.GenericProperty.fromZioJson
import com.normation.rudder.rest.RestTest
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.zio.*
import io.scalaland.chimney.syntax.*
import java.nio.file.Files
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import zio.*
import zio.json.*
import zio.json.ast.Json

@RunWith(classOf[JUnitRunner])
class RestDataSourceTest extends Specification {

  val restTestSetUp = RestTestSetUp.newEnv

  val datasourceRepo = new MemoryDataSourceRepository with NoopDataSourceCallbacks

  val mockNodes      = new MockNodes()
  val dataSourceApi9 = new DataSourceApiImpl(
    datasourceRepo,
    mockNodes.nodeFactRepo,
    restTestSetUp.uuidGen
  )

  val (rudderApi, liftRules) =
    TraitTestApiFromYamlFiles.buildLiftRules(dataSourceApi9 :: Nil, restTestSetUp.apiVersions, Some(restTestSetUp.userService))
  val restTest               = new RestTest(liftRules)
  restTestSetUp.rudderApi.addModules(dataSourceApi9.getLiftEndpoints())

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))

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

  val sourceType = DataSourceType.HTTP(
    "http://jsonplaceholder.typicode.com/posts/1000",
    Map("Accept" -> "application/json"),
    HttpMethod.GET,
    Map(),
    false,
    "result.environment",
    DataSourceType.HTTP.defaultMaxParallelRequest,
    HttpRequestMode.OneRequestByNode,
    Duration(194, SECONDS),
    MissingNodeBehavior.Delete
  )

  val datasourceMissingDefaultValue = datasource1.copy(sourceType =
    sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromZioJson(Json.Str("{\"%}hel;lo\"!\"\""))))
  )

  ///// the actual tests /////

  sequential

  "Serialisation then deserialisation" should {

    "be isomorphic" in {
      DataSourceExtractor.CompleteJson.extractDataSource(datasource1.transformInto[FullDataSource].toJson) must beRight(
        datasource1
      )
    }

    "be isomorphic with 'default value' missing behavior" in {
      DataSourceExtractor.CompleteJson.extractDataSource(
        datasourceMissingDefaultValue.transformInto[FullDataSource].toJson
      ) must beRight(
        datasourceMissingDefaultValue
      )
    }

  }

  "Deserialization" should {

    val source = DataSource(
      DataSourceId("source"),
      DataSourceName("source"),
      sourceType,
      DataSourceRunParameters(
        DataSourceSchedule.Scheduled(Duration(4921, SECONDS)),
        onGeneration = true,
        onNewNode = false
      ),
      "",
      enabled = true,
      Duration(165, SECONDS)
    )

    def getJson(moreParam: String, missingBehavior: String) = s"""{
              "name":"source"
            , "id":"source"
            , "description":""
            , "type":{
                "name":"HTTP"
              , "parameters":{
                  "url":"http://jsonplaceholder.typicode.com/posts/1000"
                , "headers":[{"name":"Accept","value":"application/json"}]
                , "params":[]
                , "path":"result.environment"
                $moreParam
                , "checkSsl":false
                , "requestTimeout":194
                , "requestMethod":"GET"
                , "requestMode":{"name":"byNode"}
                $missingBehavior
                }
              }
            , "runParameters":{
                "onGeneration":true
              , "onNewNode":false
              , "schedule":{"type":"scheduled","duration":4921}
              }
            , "updateTimeout":165
            , "enabled":true

            }"""

    "accept datasource without a max number of parallel request" in {
      val json = getJson("", "")

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(source))
    }

    "accept datasource with a max number of parallel request" in {
      val json     = getJson(""", "maxParallelReq":42 """, "")
      val expected = source.copy(sourceType = sourceType.copy(maxParallelRequest = 42))

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(expected))
    }

    "accept datasource with 'delete' missing behavior" in {
      val json                    = getJson("", """, "onMissing":{"name":"delete"} """)
      val datasourceMissingDelete = source.copy(sourceType = sourceType.copy(missingNodeBehavior = MissingNodeBehavior.Delete))

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(
        datasourceMissingDelete
      ))
    }

    "accept datasource with 'no change' missing behavior" in {
      val json                      = getJson("", """, "onMissing":{"name":"noChange"} """)
      val datasourceMissingNoChange =
        source.copy(sourceType = sourceType.copy(missingNodeBehavior = MissingNodeBehavior.NoChange))

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(
        datasourceMissingNoChange
      ))
    }

    "accept datasource with 'default value' missing behavior" in {
      val json                   = getJson("", """, "onMissing":{"name":"defaultValue", "value":"toto"} """)
      val datasourceMissingValue = source.copy(sourceType =
        sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromZioJson(Json.Str("toto"))))
      )

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(
        datasourceMissingValue
      ))
    }

    "accept datasource with 'default value' escaped in missing behavior" in {
      val json                   = getJson("", """, "onMissing":{"name":"defaultValue", "value":"\"toto\""} """)
      val datasourceMissingValue = source.copy(sourceType =
        sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromZioJson(Json.Str("\"toto\""))))
      )

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(
        datasourceMissingValue
      ))
    }

    "accept datasource with 'default value' special characters in missing behavior" in {
      val json                   = getJson("", """, "onMissing":{"name":"defaultValue", "value":"{\"%}hel;lo\"!\"\""} """)
      val datasourceMissingValue = source.copy(sourceType =
        sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromZioJson(Json.Str("{\"%}hel;lo\"!\"\""))))
      )

      DataSourceExtractor.CompleteJson.extractDataSource(json) must_=== (Right(
        datasourceMissingValue
      ))
    }

  }
}
