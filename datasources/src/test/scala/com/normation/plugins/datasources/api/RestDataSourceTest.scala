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

import com.normation.plugins.datasources.*
import com.normation.rudder.domain.properties.GenericProperty.fromJsonValue
import com.normation.rudder.rest.JsonResponsePrettify
import com.normation.rudder.rest.RestTest
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.zio.*
import java.util.concurrent.TimeUnit.SECONDS
import net.liftweb.common.Box
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.http.LiftResponse
import net.liftweb.json.JsonAST.*
import net.liftweb.json.JsonParser
import net.liftweb.json.JValue
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import zio.*

@RunWith(classOf[JUnitRunner])
class RestDataSourceTest extends Specification with Loggable {

  val restTestSetUp = RestTestSetUp.newEnv

  def extractDataFromResponse(response: LiftResponse, kind: String): Box[List[JValue]] = {
    response match {
      case JsonResponsePrettify(json, _, _, 200, _) =>
        json \ "data" \ kind match {
          case JArray(data) => Full(data)
          case _            => Failure(json.toString())
        }
      case _                                        => ???
    }
  }
  val datasourceRepo = new MemoryDataSourceRepository with NoopDataSourceCallbacks

  val dataSourceApi9 = new DataSourceApiImpl(
    restTestSetUp.restExtractorService,
    restTestSetUp.restDataSerializer,
    datasourceRepo,
    null,
    null,
    restTestSetUp.uuidGen
  )

  val liftRules =
    TraitTestApiFromYamlFiles.buildLiftRules(dataSourceApi9 :: Nil, restTestSetUp.apiVersions, Some(restTestSetUp.userService))
  val restTest  = new RestTest(liftRules._2)
  restTestSetUp.rudderApi.addModules(dataSourceApi9.getLiftEndpoints())

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

  val d1Json = DataSourceJsonSerializer.serialize(datasource1)

  datasourceRepo.save(datasource1).runNow

  val datasource2 = datasource1.copy(id = DataSourceId("datasource2"))
  val d2Json      = DataSourceJsonSerializer.serialize(datasource2)

  val dataSource2Updated = datasource2.copy(
    description = "new description",
    sourceType = baseSourceType.copy(headers = Map(("new header 1" -> "new value 1"), ("new header 2" -> "new value 2"))),
    runParam = baseRunParam.copy(DataSourceSchedule.Scheduled(Duration(70, SECONDS)))
  )
  val d2updatedJson      = DataSourceJsonSerializer.serialize(dataSource2Updated)

  val d2modJson = {
    import net.liftweb.json.JsonDSL.*
    (("type"           ->
    (("name"           -> "HTTP")
    ~ ("parameters"    ->
    ("headers"         -> JArray(
      (("name" -> "new header 1") ~ ("value" -> "new value 1")) ::
      (("name" -> "new header 2") ~ ("value" -> "new value 2")) ::
      Nil
    )))))
    ~ ("description"   -> "new description")
    ~ ("runParameters" -> ("schedule" -> ("type" -> "scheduled") ~ ("duration" -> 70))))
  }

  val d2DeletedJson = {
    import net.liftweb.json.JsonDSL.*
    (("id"       -> datasource2.id.value)
    ~ ("message" -> s"Data source ${datasource2.id.value} deleted"))
  }

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
    sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromJsonValue(JString("{\"%}hel;lo\"!\"\""))))
  )
  val dsgDefaultValueJson           = DataSourceJsonSerializer.serialize(datasourceMissingDefaultValue)

  ///// the actual tests /////

  sequential

  "Serialisation then deserialisation" should {

    "be isomorphic" in {
      DataSourceExtractor.CompleteJson.extractDataSource(datasource1.id, d1Json) must_=== (Full(datasource1))
    }

    "be isomorphic with 'default value' missing behavior" in {
      DataSourceExtractor.CompleteJson.extractDataSource(datasourceMissingDefaultValue.id, dsgDefaultValueJson) must_=== (Full(
        datasourceMissingDefaultValue
      ))
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

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(source))
    }

    "accept datasource with a max number of parallel request" in {
      val json     = getJson(""", "maxParallelReq":42 """, "")
      val expected = source.copy(sourceType = sourceType.copy(maxParallelRequest = 42))

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(expected))
    }

    "accept datasource with 'delete' missing behavior" in {
      val json                    = getJson("", """, "onMissing":{"name":"delete"} """)
      val datasourceMissingDelete = source.copy(sourceType = sourceType.copy(missingNodeBehavior = MissingNodeBehavior.Delete))

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(
        datasourceMissingDelete
      ))
    }

    "accept datasource with 'no change' missing behavior" in {
      val json                      = getJson("", """, "onMissing":{"name":"noChange"} """)
      val datasourceMissingNoChange =
        source.copy(sourceType = sourceType.copy(missingNodeBehavior = MissingNodeBehavior.NoChange))

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(
        datasourceMissingNoChange
      ))
    }

    "accept datasource with 'default value' missing behavior" in {
      val json                   = getJson("", """, "onMissing":{"name":"defaultValue", "value":"toto"} """)
      val datasourceMissingValue = source.copy(sourceType =
        sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromJsonValue(JString("toto"))))
      )

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(
        datasourceMissingValue
      ))
    }

    "accept datasource with 'default value' escaped in missing behavior" in {
      val json                   = getJson("", """, "onMissing":{"name":"defaultValue", "value":"\"toto\""} """)
      val datasourceMissingValue = source.copy(sourceType =
        sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromJsonValue(JString("\"toto\""))))
      )

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(
        datasourceMissingValue
      ))
    }

    "accept datasource with 'default value' special characters in missing behavior" in {
      val json                   = getJson("", """, "onMissing":{"name":"defaultValue", "value":"{\"%}hel;lo\"!\"\""} """)
      val datasourceMissingValue = source.copy(sourceType =
        sourceType.copy(missingNodeBehavior = MissingNodeBehavior.DefaultValue(fromJsonValue(JString("{\"%}hel;lo\"!\"\""))))
      )

      DataSourceExtractor.CompleteJson.extractDataSource(DataSourceId("source"), JsonParser.parse(json)) must_=== (Full(
        datasourceMissingValue
      ))
    }

  }

  "Data source api" should {

    "Get all base data source" in {
      restTest.testGET("/api/latest/datasources") { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")
        } yield {
          data
        }

        result must beEqualTo(Full(d1Json :: Nil))
      }
    }

    "Accept new data source as json" in {
      restTest.testPUT("/api/latest/datasources", d2Json) { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")
        } yield {
          data
        }

        result must beEqualTo(Full(d2Json :: Nil))
      }
    }

    "List new data source" in {
      restTest.testGET("/api/latest/datasources") { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")

        } yield {
          data
        }
        result.getOrElse(Nil) must contain(exactly(d1Json, d2Json))
      }
    }

    "Accept modification as json" in {
      restTest.testPOST(s"/api/latest/datasources/${datasource2.id.value}", d2modJson) { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")
        } yield {
          data
        }

        result must beEqualTo(Full(d2updatedJson :: Nil))
      }
    }

    "Get updated data source" in {
      restTest.testGET(s"/api/latest/datasources/${datasource2.id.value}") { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")

        } yield {
          data
        }
        result.getOrElse(Nil) must contain(exactly(d2updatedJson))
      }
    }

    "Delete the newly added data source" in {
      restTest.testDELETE(s"/api/latest/datasources/${datasource2.id.value}") { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")

        } yield {
          data
        }
        result must beEqualTo(Full(d2DeletedJson :: Nil))
      }
    }

    "Be removed from list of all data sources" in {
      restTest.testGET("/api/latest/datasources") { req =>
        val result = for {
          answer <- restTestSetUp.rudderApi.getLiftRestApi().apply(req).apply()
          data   <- extractDataFromResponse(answer, "datasources")

        } yield {
          data
        }
        result.getOrElse(Nil) must contain(exactly(d1Json))
      }
    }
  }
}
