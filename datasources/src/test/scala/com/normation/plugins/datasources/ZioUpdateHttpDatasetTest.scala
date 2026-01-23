/*
 *************************************************************************************
 * Copyright 2026 Normation SAS
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

package com.normation.plugins.datasources

import ch.qos.logback.classic.Level
import com.normation.errors.*
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.plugins.*
import com.normation.plugins.datasources.DataSourceSchedule.*
import com.normation.plugins.datasources.ZioCmdbServer.ZioServerStarted
import com.normation.rudder.domain.eventlog.*
import com.normation.rudder.domain.policies.GlobalPolicyMode
import com.normation.rudder.domain.policies.PolicyMode
import com.normation.rudder.domain.policies.PolicyModeOverrides
import com.normation.rudder.domain.properties.GenericProperty
import com.normation.rudder.domain.properties.GenericProperty.*
import com.normation.rudder.domain.properties.GlobalParameter
import com.normation.rudder.domain.properties.NodeProperty
import com.normation.rudder.facts.nodes.*
import com.normation.rudder.repository.RoParameterRepository
import com.normation.rudder.services.nodes.PropertyEngineServiceImpl
import com.normation.rudder.services.policies.InterpolatedValueCompilerImpl
import com.normation.rudder.services.policies.NodeConfigData
import com.normation.rudder.tenants.QueryContext
import com.normation.utils.StringUuidGeneratorImpl
import com.normation.zio.*
import com.softwaremill.quicklens.*
import com.typesafe.config.*
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import scala.collection.MapView
import scala.util.Random
import zhttp.http.*
import zhttp.http.Method.*
import zhttp.service.EventLoopGroup
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zio.{System as _, *}
import zio.syntax.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.junit.ZTestJUnitRunner

/*
 * Create a test server that will act as the datasource endpoint.
 * The server answers to three main URLs set:
 * - /status: just to check server is up
 * - /datasources/...: test json query, expected answers, what happens on 404, special cases with arrays, etc
 * - /datasources/parallel: test that datasource parallel limits are well set and respected
 */

object ZioCmdbServer {
  val serverPort = Random.nextInt(9999) + 40000 // [40000 - 49999]

  /*
   * Service that counts the max number of parallel request we get.
   * For that,
   */
  def ioCountService(maxPar: Ref[Int]) = {
    for {
      currentConcurrent <- Ref.make(0)
      maxConcurrent     <- Ref.Synchronized.make(0)
    } yield Http.collectZIO[Request] {
      case GET -> !! / x =>
        for {
          c <- currentConcurrent.updateAndGet(_ + 1)
          _ <- maxConcurrent.updateZIO { m =>
                 val max = if (c > m) c else m
                 maxPar.set(max) *>
                 max.succeed
               }
          x <- ZIO.succeed(Response.text(Data.nodeJson(x)))
          _ <- currentConcurrent.update(_ - 1)
        } yield x
    }
  }

  def make() = for {
    a      <- Ref.make(0)
    b      <- Ref.make(0)
    maxPar <- Ref.make(0)
    count  <- ioCountService(maxPar)
  } yield new ZioCmdbServer(a, b, maxPar, count)

  val layer: ZLayer[Scope, Nothing, ZioServerStarted] = ZLayer {
    (for {
      server <- make()
      f      <- server.serverR.forkDaemon
      _      <- CmdbServerStarted.isStarted.await
    } yield ZioServerStarted(server, f))
      // interruptible is necessary else the forked process won't be interruptible because
      // the fork is in acquire/release for a ZLayer which are uninterruptible,
      // see https://stackoverflow.com/questions/77631198/how-to-properly-interrupt-a-fiber-in-zio-test
      .interruptible
      // this one needs a live clock to progress
      .withClock(Clock.ClockLive)
      .withFinalizer { s =>
        s.fiber.interrupt *>
        effectUioUnit(println(s"Server on port ${ZioCmdbServer.serverPort} stopped"))
      }
  }

  case class ZioServerStarted(server: ZioCmdbServer, fiber: Fiber[RudderError, Boolean])
}

// a promise to communicate the fact that the server is started
object CmdbServerStarted {
  val isStarted = Promise.make[Nothing, Unit].runNow
}

// ref are for debug info, but of course they need to be accessed in a sequential way
class ZioCmdbServer(
    val counterError:   Ref[Int],
    val counterSuccess: Ref[Int],
    val maxPar:         Ref[Int],
    val ioCountService: Http[Any, Throwable, Request, Response]
) {
  import com.normation.plugins.datasources.Data.*

  // a delay methods that use the scheduler
  def delayResponse[V, E](resp: ZIO[V, E, Response]): ZIO[V, E, Response] = {
    resp.delay(Random.nextInt(1000).millis)
  }

  def reset(): UIO[Unit] = {
    counterError.set(0) *>
    counterSuccess.set(0)
  }

  /*
   * datasource services
   */
  def service[R]: HttpApp[R, Throwable] = Http.collectZIO[Request] {
    case _ -> !! =>
      ZIO.fail(new IllegalArgumentException("You cannot access root in test"))

    case _ -> !! / "status" =>
      Response.text("datasources:ok").succeed

    case GET -> !! / "single_node1" =>
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        Response.text(booksJson)
      }

    case GET -> !! / "testarray" / x =>
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        Response.text(testArray(x.toInt)._1)
      }

    case GET -> !! / "single_node2" =>
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        Response.text("""{"foo":"bar"}""")
      }

    case GET -> !! / "server" =>
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        Response.text("""{"hostname":"server.rudder.local"}""")
      }

    case GET -> !! / "hostnameJson" =>
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        Response.text(hostnameJson)
      }

    case GET -> !! / "lifecycle" / id =>
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        if (id == "node1") Response.status(Status.NotFound)
        else Response.text("1")
      }

    case GET -> !! / "404" =>
      ZIO.succeed(Response.status(Status.NotFound))

    case GET -> !! / "faileven" / x =>
      // x === "nodeXX" or root
      counterSuccess.update(_ + 1) *>
      (if (x != "root" && x.replaceAll("node", "").toInt % 2 == 0) {
         ZIO.succeed {
           Response.html("Not authorized", Status.Forbidden)
         }
       } else {
         ZIO.succeed {
           Response.text(nodeJson(x))
         }
       })

    case r @ GET -> !! / "delay" / x =>
      r.headers.toList.toMap.get("nodeId") match {
        case Some(`x`) =>
          delayResponse(
            counterSuccess.update(_ + 1) *>
            ZIO.succeed {
              Response.text(nodeJson(x))
            }
          )

        case _ =>
          counterSuccess.update(_ + 1) *>
          ZIO.succeed {
            Response.html("node id was not found in the 'nodeid' header", Status.Forbidden)
          }
      }

    case GET -> !! / x =>
      counterSuccess.update(_ + 1) *> ZIO.succeed(Response.text(nodeJson(x)))

    case r @ POST -> !! / "delay" =>
      val headerId = r.headers.toList.toMap.get("nodeId")

      for {
        body   <- r.body.asString // we should correctly decode POST form data, but here we only have one field nodeId=nodexxxx
        formId <- (body.split('=').toList match {
                    case _ :: nodeId :: Nil => ZIO.succeed(Some(nodeId))
                    case _                  => ZIO.fail(throw new IllegalArgumentException(s"Error, can't decode POST form data body: ${body}"))
                  })
        res    <- (headerId, formId) match {
                    case (Some(x), Some(y)) if x == y =>
                      delayResponse(
                        counterSuccess.update(_ + 1) *>
                        ZIO.succeed {
                          Response.text(nodeJson("plop"))
                        }
                      )

                    case _ =>
                      counterSuccess.update(_ + 1) *>
                      ZIO.succeed {
                        Response.html(
                          s"node id was not found in post form (key=nodeId)[\n  headers: ${r.headers.toList}\n  body:${body}]",
                          Status.Forbidden
                        )
                      }
                  }
      } yield res
  }

  val nThreads: Int = 10

  val serverR: ZIO[Any, RudderError, Boolean] = {
    // in zio-http 2.0.x, root segment must be explicitly pattern matched in left extraction and
    // set explicitly in the extracted path.

    val router: Http[Any, Throwable, Request, Response] = Http.collectHttp[Request] {
      case GET -> !! / "status"                           =>
        Http.collectZIO[Request] { case _ => Response.text("ok").succeed }
      case _ -> "" /: "datasources" /: "parallel" /: path =>
        ioCountService.contramap[Request](_.setPath(Path.root.concat(path)))

      case _ -> "" /: "datasources" /: path =>
        service.contramap[Request](_.setPath(Path.root.concat(path)))

      case _ -> other =>
        Http.collectZIO[Request] {
          case _ => Console.printLine(s"I didn't handle: '${other}'") *> Response.status(Status.Forbidden).succeed
        }
    }
    Server.port(ZioCmdbServer.serverPort) ++ // Setup port - should be next available
    Server.app(router) // Setup the Http app
  }.make.flatMap { start =>
    // Waiting for the server to start
    ZIO.consoleWith(_.printLine(s"Server started on port ${start.port}")) *>
    com.normation.plugins.datasources.CmdbServerStarted.isStarted.complete(ZIO.unit) *>
    // Ensures the server doesn't die after printing
    ZIO.never
  }.mapError(err => SystemError("Error starting server", err))
    .provideSomeLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(nThreads) ++ Scope.default)

}

@RunWith(classOf[ZTestJUnitRunner])
class ZioUpdateHttpDatasetTest extends ZIOSpecDefault {
  import com.normation.plugins.datasources.Data.*

  implicit class ForceGet(json: String) {
    def forceParse = GenericProperty.parseValue(json) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"Error in parsing value: ${err.fullMsg}")
    }
  }

  implicit class DurationToScala(d: Duration) {
    def toScala = scala.concurrent.duration.FiniteDuration(d.toMillis, "millis")
  }

  implicit class RunNowTimeout[A](effect: ZIO[ZioServerStarted & Annotations & zio.test.Live, RudderError, A]) {
    def runTimeout(d: Duration): ZIO[Scope & ZioServerStarted, RudderError, A] =
      effect.timeout(d).notOptional(s"The test timed-out after ${d}").provideSome[ZioServerStarted](zio.test.testEnvironment)
  }

  // a timer
  // implicit val timer: Timer[IO] = cats.attempt.IO.timer(blockingExecutionContext)

  val actor = EventActor("Test-actor")
  def modId = ModificationId("test-id-@" + System.currentTimeMillis)

  implicit val qc: QueryContext = QueryContext.testQC

  val interpolation = new InterpolatedValueCompilerImpl(
    new PropertyEngineServiceImpl(
      List.empty
    )
  )
  val fetch: GetDataset = new GetDataset(interpolation)

  val parameterRepo = new RoParameterRepository() {
    def getAllGlobalParameters(): IOResult[Seq[GlobalParameter]] = Seq().succeed
    def getGlobalParameter(parameterName: String): IOResult[Option[GlobalParameter]] = None.succeed
  }

  def buildNodeRepo(initNodeInfo: Map[NodeId, CoreNodeFact]): UIO[(Ref[Map[NodeId, Int]], NodeFactRepository)] = {

    // used for test
    // number of time each node is updated
    (for {
      updates       <- Ref.make(Map[NodeId, Int]())
      updateCallback = new NodeFactChangeEventCallback {
                         override def name: String = {
                           "update number of update"
                         }

                         override def run(change: NodeFactChangeEventCC): IOResult[Unit] = {
                           change.event match {
                             case NodeFactChangeEvent.Updated(oldNode, newNode, _)        =>
                               updates.update(m => m + (newNode.id -> (1 + m.getOrElse(newNode.id, 0))))
                             case NodeFactChangeEvent.UpdatedPending(oldNode, newNode, _) =>
                               updates.update(m => m + (newNode.id -> (1 + m.getOrElse(newNode.id, 0))))
                             case x                                                       => ZIO.unit
                           }
                         }
                       }
      repo          <- CoreNodeFactRepository.makeNoop(
                         initNodeInfo,
                         callbacks = Chunk(updateCallback)
                       )
    } yield (updates, repo))
  }

  val root = NodeConfigData.factRoot
  val n1   = {
    NodeConfigData.fact1.modify(_.properties).setTo(Chunk(DataSource.nodeProperty("get-that", "book".toConfigValue)))
  }

  val httpDatasourceTemplate = DataSourceType.HTTP(
    "CHANGE MY URL",
    Map(),
    HttpMethod.GET,
    Map(),
    true,
    "CHANGE MY PATH",
    DataSourceType.HTTP.defaultMaxParallelRequest,
    HttpRequestMode.OneRequestByNode,
    30.second,
    MissingNodeBehavior.Delete
  )

  val datasourceTemplate = DataSource(
    DataSourceId("test-my-datasource"),
    DataSourceName("test-my-datasource"),
    httpDatasourceTemplate,
    DataSourceRunParameters(
      Scheduled(300.seconds),
      true,
      true
    ),
    "a test datasource to test datasources",
    true,
    5.minutes
  )
  // create a copy of template, updating some properties
  def NewDataSource(
      name:      String,
      url:       String = httpDatasourceTemplate.url,
      path:      String = httpDatasourceTemplate.path,
      schedule:  DataSourceSchedule = datasourceTemplate.runParam.schedule,
      method:    HttpMethod = httpDatasourceTemplate.httpMethod,
      params:    Map[String, String] = httpDatasourceTemplate.params,
      headers:   Map[String, String] = httpDatasourceTemplate.headers,
      onMissing: MissingNodeBehavior = httpDatasourceTemplate.missingNodeBehavior,
      maxPar:    Int = httpDatasourceTemplate.maxParallelRequest
  ) = {
    val http = httpDatasourceTemplate.copy(
      url = url,
      path = path,
      httpMethod = method,
      params = params,
      headers = headers,
      missingNodeBehavior = onMissing,
      maxParallelRequest = maxPar
    )
    val run  = datasourceTemplate.runParam.copy(schedule = schedule)
    datasourceTemplate.copy(id = DataSourceId(name), sourceType = http, runParam = run)

  }

  val noPostHook = (nodeIds: Set[NodeId], cause: UpdateCause) => ZIO.unit

  val alwaysEnforce = GlobalPolicyMode(PolicyMode.Enforce, PolicyModeOverrides.Always)

  val realClock = ZioRuntime.environment

  object MyDatasource {
    val http = for {
      repo <- buildNodeRepo(NodeConfigData.allNodeFacts)
    } yield new HttpQueryDataSourceService(
      repo._2,
      parameterRepo,
      interpolation,
      noPostHook,
      () => alwaysEnforce.succeed
      //   , realClock // this one need a real clock to be able to do the requests
    )

    val uuidGen = new StringUuidGeneratorImpl()
  }

  object Enabled extends PluginEnableImpl

  val REST_SERVER_URL_ROOT = s"http://localhost:${ZioCmdbServer.serverPort}"
  val REST_SERVER_URL      = s"${REST_SERVER_URL_ROOT}/datasources"

  def nodeUpdatedMatcher(nodeIds: Set[NodeId]): Set[NodeUpdateResult] = {
    nodeIds.map(n => NodeUpdateResult.Updated(n))
  }

  implicit class QueueFailIfNonEmpty[A](queue: Queue[A]) {
    def failIfNonEmpty: IOResult[Unit] = queue.poll.flatMap {
      case None    => ().succeed
      case Some(_) => Inconsistency(s"queue should be empty but size = ${1 + queue.size.runNow}").fail
    }
  }

  // wait for server to be started
  // CmdbServerStarted.isStarted.await.runNow

  def spec: Spec[Scope, Any] = {

    val mainSuite: Spec[Scope & ZioServerStarted, RudderError] = (suite("ZioUpdateHttpDatasetTest")(
      suite("Check that the server is up and running fine")(test("check") {
        for {
          res1 <- QueryHttp.QUERY(HttpMethod.GET, REST_SERVER_URL_ROOT + "/status", Map(), Map(), false, 1.second, 1.seconds)
          res2 <- QueryHttp.QUERY(HttpMethod.GET, REST_SERVER_URL + "/status", Map(), Map(), false, 1.second, 1.seconds)
        } yield assert(res1)(isSome(equalTo("ok"))) && assert(res2)(isSome(equalTo("datasources:ok")))

      }),
      suite("Array validation with [*]")(
        suite("with [*]")((0 until testArray.size).map(i => testOneArrayValidation("$.[*]", i, _._2))),
        suite("with [:1]")((0 until testArray.size).map(i => testOneArrayValidation("$.[:1]", i, _._3)))
      ),
      updateDatasourceSuite,
      updateDatasourceSuite2,
      createDatasource,
      queryingLotOfNode,
      gettingANode,
      fullHttpService,
      behavior404
    ) @@ TestAspect.afterAll(
      ZIO.serviceWithZIO[ZioCmdbServer.ZioServerStarted](_.fiber.interrupt.unit)
    ) @@ TestAspect.beforeAll(
      ZIO.serviceWithZIO[ZioCmdbServer.ZioServerStarted](_ => ZIO.unit)
    ) @@ TestAspect.sequential)

    mainSuite.provideSomeShared[Scope](ZioCmdbServer.layer)
  }

  def testOneArrayValidation(path: String, i: Int, get: ((String, String, String)) => String) = {
    test(s"for case: ${testArray(i)._1} -> ${get(testArray(i))}") {
      val datasource = NewDataSource(
        "test-http-service",
        url = s"${REST_SERVER_URL}/testarray/$i",
        path = path
      )

      for {
        (updates, infos) <- buildNodeRepo(NodeConfigData.allNodeFacts)
        http              = new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)
        _                <- updates.set(Map())
        res              <- http.queryAll(datasource, UpdateCause(modId, actor, None))
        // let hooks happens - this is magical, it tells zio to let finish things started
        // before that duration before continuing
        u                <- TestClock.adjust(5.seconds) *> updates.get
        v                <- infos.getAll()
        nodeIds           = v.keySet.toSet
      } yield {
        assert(res)(equalTo(nodeUpdatedMatcher(nodeIds)))
        && assert(u)(hasSubset[(NodeId, Int)](nodeIds.map(x => (x, 1))))
        && assert(v.get(root.id).flatMap(n => n.properties.find(_.name == "test-http-service")))(
          isSome(
            equalTo(
              NodeProperty.apply("test-http-service", get(testArray(i)).forceParse, None, Some(DataSource.providerName))
            )
          )
        )
      }
    }
  }

  def updateDatasourceSuite: Spec[Scope & ZioServerStarted, RudderError] = {
    val datasource = NewDataSource(
      name = "test-scheduler",
      url = s"${REST_SERVER_URL}/$${rudder.node.id}",
      path = "$.hostname",
      schedule = Scheduled(5.minute)
    )
    val action     = (c: UpdateCause) => {
      // here we need to give him the default scheduler, not the test one,
      // to actually have the fetch logic done
      for {
        http <- MyDatasource.http
        res  <- http.queryAll(datasource, c)
      } yield res
    }

    // test clock needs explicit await to works, so we add them with a queue offer/take
    val testAction = (q: Queue[Unit]) => (c: UpdateCause) => action(c) *> q.offer(()).unit

    suite("Update on datasource")(
      test("does nothing if scheduler is disabled") {
        (for {
          queue  <- Queue.unbounded[Unit]
          dss     = new DataSourceScheduler(
                      datasource.copy(enabled = false),
                      Enabled,
                      () => ModificationId(MyDatasource.uuidGen.newUuid),
                      testAction(queue)
                    )
          server <- ZIO.service[ZioServerStarted].map(_.server)
          _      <- server.reset()
          ce_0   <- server.counterError.get
          cs_0   <- server.counterSuccess.get
          total_0 = ce_0 + cs_0
          _      <- dss.restartScheduleTask()
          // then, event after days, nothing is done
          _      <- TestClock.adjust(1.day)
          ce_1d  <- server.counterError.get
          cs_1d  <- server.counterSuccess.get
        } yield {
          (total_0, ce_1d + cs_1d)
        })
          .flatMap(x => assert(x)(equalTo((0, 0))))
      },
      test("allows interactive updates with disabled scheduler (but not data source)") {
        val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
        logger.setLevel(Level.TRACE)
        // reset counter
        // before start, nothing is done
        (for {
          queue  <- Queue.unbounded[Unit]
          dss     = new DataSourceScheduler(
                      datasource.copy(runParam = datasource.runParam.copy(schedule = NoSchedule(1.second))),
                      Enabled,
                      () => ModificationId(MyDatasource.uuidGen.newUuid),
                      testAction(queue)
                    )
          server <- ZIO.service[ZioServerStarted].map(_.server)
          _      <- server.reset()
          _      <- queue.failIfNonEmpty
          ce_0   <- server.counterError.get
          cs_0   <- server.counterSuccess.get
          total_0 = ce_0 + cs_0
          _      <- dss.restartScheduleTask()
          // then, event after days, nothing is done
          _      <- TestClock.adjust(1.day)
          _      <- queue.failIfNonEmpty
          ce_1   <- server.counterError.get
          cs_1   <- server.counterSuccess.get
          total_1 = ce_1 + cs_1
          // but asking for a direct update do the queries immediately - task need at least 1ms to notice it should run
          _      <- dss.doActionAndSchedule(action(UpdateCause(ModificationId("plop"), RudderEventActor, None)).unit)
          _      <- TestClock.adjust(1.second)
          _      <- queue.failIfNonEmpty
          ce_2   <- server.counterError.get
          cs_2   <- server.counterSuccess.get
          total_2 = ce_2 + cs_2
        } yield (total_0, total_1, total_2)).flatMap { x =>
          val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
          logger.setLevel(Level.OFF)
          assert(x)(equalTo((0, 0, NodeConfigData.allNodesInfo.size)))
        }
      },
      test("create a new schedule from data source information") {
        (
          for {
            queue   <- Queue.unbounded[Unit]
            dss      = new DataSourceScheduler(
                         datasource.copy(name = DataSourceName("create a new schedule")),
                         Enabled,
                         () => ModificationId(MyDatasource.uuidGen.newUuid),
                         testAction(queue)
                       )
            server  <- ZIO.service[ZioServerStarted].map(_.server)
            _       <- server.reset()
            // before start, nothing is done
            _       <- queue.failIfNonEmpty
            ce_0    <- server.counterError.get
            cs_0    <- server.counterSuccess.get
            total_0  = ce_0 + cs_0
            _       <- dss.restartScheduleTask()
            // then just after, we have the first exec - it still need at least a ms to tick
            // still nothing here
            _       <- TestClock.adjust(1.second)
            // here we have results
            _       <- queue.take
            ce_0s   <- server.counterError.get
            cs_0s   <- server.counterSuccess.get
            total_0s = ce_0s + cs_0s
            // then nothing happens before 5 minutes
            _       <- TestClock.adjust(1.second)
            _       <- queue.failIfNonEmpty
            ce_1s   <- server.counterError.get
            cs_1s   <- server.counterSuccess.get
            total_1s = ce_1s + cs_1s
            _       <- TestClock.adjust(4.minute)
            _       <- queue.failIfNonEmpty
            ce_4m   <- server.counterError.get
            cs_4m   <- server.counterSuccess.get
            total_4m = ce_4m + cs_4m
            // then all the nodes gets their info
            _       <- TestClock.adjust(1.minute) // 5 minutes
            _       <- queue.take
            ce_5m   <- server.counterError.get
            cs_5m   <- server.counterSuccess.get
            total_5m = ce_5m + cs_5m
            // then nothing happen anymore
            _       <- TestClock.adjust(3.minute) // 8 minutes
            _       <- queue.failIfNonEmpty
            ce_8m   <- server.counterError.get
            cs_8m   <- server.counterSuccess.get
            total_8m = ce_8m + cs_8m
          } yield (total_0, total_0s, total_1s, total_4m, total_5m, total_8m)
        ).flatMap { x =>
          val size = NodeConfigData.allNodesInfo.size
          assert(x)(equalTo((0, size, size, size, size * 2, size * 2))) // and
          // (f1 must beEqualTo(None)) and (r1 === Fiber.Status.Running(interrupting = false)) and (r2 === Fiber.Status.Done)
        }
      }
    )
  }

  def updateDatasourceSuite2: Spec[Scope & ZioServerStarted, RudderError] = {

    def fiberRunning: Assertion[Fiber.Status] = {
      Assertion(TestArrow.make[Fiber.Status, Boolean] {
        case x @ Fiber.Status.Running(_, _)      => TestTrace.succeed(true)
        case x @ Fiber.Status.Suspended(_, _, _) => TestTrace.succeed(true)
        case x                                   => TestTrace.fail(ErrorMessage.text(s"Invalid state for fiber: ${x}"))
      })
    }

    test("When we update a datasource with repo operation, its live instance must be reloaded (old fiber killed)") {

      (for {
        (updates, infos) <- buildNodeRepo(NodeConfigData.allNodeFacts)
        repos             = new DataSourceRepoImpl(
                              new MemoryDataSourceRepository(),
                              new HttpQueryDataSourceService(
                                infos,
                                parameterRepo,
                                interpolation,
                                noPostHook,
                                () => alwaysEnforce.succeed
                              ),
                              MyDatasource.uuidGen,
                              AlwaysEnabledPluginStatus
                            )
        id                = DataSourceId("test-repos-save")
        datasource        = NewDataSource(
                              name = id.value,
                              url = s"${REST_SERVER_URL}/$${rudder.node.id}",
                              path = "$.hostname",
                              schedule = Scheduled(5.minute)
                            )
        _                <- repos.save(datasource) // init
        f1               <- repos.datasources.all().flatMap(_(id).scheduledTask.get).notOptional("error in test: f1 is none")
        // here, it can be either Running (if the init takes some time) or Suspended (if init ended and won't run before 5 minutes)
        r11              <- f1.fold(_.status, _ => Unexpected("Datasource scheduler fiber should not be synthetic").fail)
        _                <- repos.save(datasource.copy(name = DataSourceName("updated name")))
        f2               <- repos.datasources.all().flatMap(_(id).scheduledTask.get).notOptional("error in test: f2 is none")
        // here, since we updated repos, f1 was terminated
        r12              <- f1.fold(_.status, _ => Unexpected("Datasource scheduler fiber should not be synthetic").fail)
        // and f2 is running or suspended (if waiting for next schedule)
        r21              <- f2.fold(_.status, _ => Unexpected("Datasource scheduler fiber should not be synthetic").fail)
      } yield (r11, r12, r21)).flatMap {
        case (r11, r12, r21) =>
          assert(r11)(fiberRunning) && assert(r12)(equalTo(Fiber.Status.Done)) && assert(r21)(fiberRunning)
      }
    }
  }

  def createDatasource: Spec[Scope & ZioServerStarted, RudderError] = {
    // set the variable by hand for node1 and node2. Node2 will have it overridden and then deleted, node1 kept (b/c 404 for datasources)
    val id = DataSourceId("test-ds-lifecycle")

    def getProps(nodes: MapView[NodeId, CoreNodeFact]) = {
      nodes.collect {
        case (k, n) => n.properties.find(_.name == id.value).map(p => (k.value, p.value.unwrapped()))
      }.flatten.toMap
    }

    test("a datasource creation, node update, deletion should create properties and then delete them") {
      (for {

        (updates, infos) <-
          buildNodeRepo(NodeConfigData.allNodeFacts.map {
            case (NodeId("node1"), n) =>
              (n.id, n.modify(_.properties).using(NodeProperty(id.value, "do not touch".toConfigValue, None, None) +: _))
            case (NodeId("node2"), n) =>
              (n.id, n.modify(_.properties).using(NodeProperty(id.value, "should be updated".toConfigValue, None, None) +: _))
            case (k, v)               => (k, v)
          })
        repos             = new DataSourceRepoImpl(
                              new MemoryDataSourceRepository(),
                              new HttpQueryDataSourceService(
                                infos,
                                parameterRepo,
                                interpolation,
                                noPostHook,
                                () => alwaysEnforce.succeed
                              ),
                              MyDatasource.uuidGen,
                              AlwaysEnabledPluginStatus
                            )
        datasource        = NewDataSource(
                              name = id.value,
                              url =
                                s"${REST_SERVER_URL}/lifecycle/$${rudder.node.id}", // this one does not set the prop for node1 else return 1
                              path = "$",
                              schedule = Scheduled(5.minute),
                              onMissing = MissingNodeBehavior.NoChange
                            )
        nodes0           <- infos.getAll()
        _                <- repos.save(datasource) // init
        _                <- repos.onUserAskUpdateAllNodesFor(actor, id)
        // check nodes have the property now
        nodes1           <- infos.getAll()
        // now delete datasource
        _                <- repos.delete(datasource.id, UpdateCause(ModificationId("test"), actor, None))
        // property must be deleted now
        nodes2           <- infos.getAll()
      } yield (nodes0, nodes1, nodes2)).flatMap((n0, n1, n2) => {
        assert(getProps(n0))(hasSameElements(List("node1" -> "do not touch", "node2" -> "should be updated"))) &&
        assert(getProps(n1))(hasSameElements(List("root" -> "1", "node1" -> "do not touch", "node2" -> "1"))) &&
        assert(getProps(n2))(hasSameElements(List("node1" -> "do not touch")))
      })
    }
  }

  def queryingLotOfNode: Spec[Scope & ZioServerStarted, RudderError] = {

    // test on 100 nodes. With 30s timeout, even on small hardware it will be ok.
    val nodes = (NodeConfigData.factRoot :: List.fill(100)(NodeConfigData.fact1).zipWithIndex.map {
      case (n, i) =>
        val name = "node" + i
        n.modify(_.id).setTo(NodeId(name)).modify(_.fqdn).setTo(name + ".localhost")
    }).map(n => (n.id, n)).toMap

    def maxParDataSource(n: Int) = NewDataSource(
      "test-lot-of-nodes-max-parallel-GET",
      url = s"${REST_SERVER_URL}/parallel/$${rudder.node.id}",
      path = "$.hostname",
      headers = Map("nodeId" -> "${rudder.node.id}"),
      maxPar = n
    )

    suite("querying a lot of nodes")(
      for {
        (updates, infos) <- buildNodeRepo(nodes)
        http              = new HttpQueryDataSourceService(
                              infos,
                              parameterRepo,
                              interpolation,
                              noPostHook,
                              () => alwaysEnforce.succeed
                            )
      } yield Chunk(
        test("comply with the limit of parallel queries") {
          // Max parallel is the minimum of 2 and the available thread on the machine
          // So tests don't fait if the build machine has one core
          val MAX_PARALLEL = Math.min(2, java.lang.Runtime.getRuntime.availableProcessors)
          val ds           = maxParDataSource(MAX_PARALLEL)
          (for {
            nodeIds <- infos.getAll().map(_.keySet.toSet)
            server  <- ZIO.service[ZioServerStarted].map(_.server)
            _       <- server.reset()

            // all node updated one time
            res    <- updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))
            errors <- server.counterError.get
            maxPar <- server.maxPar.get
          } yield (nodeIds, res, errors, maxPar)).flatMap((nodeIds, res, errors, maxPar) => {
            assert(res)(equalTo(nodeUpdatedMatcher(nodeIds))) &&
            assert(errors)(equalTo(0)) &&
            assert(maxPar)(equalTo(MAX_PARALLEL))
          })
        },
        test("work even if nodes don't reply at same speed with GET") {
          val ds = NewDataSource(
            "test-lot-of-nodes-GET",
            url = s"${REST_SERVER_URL}/delay/$${rudder.node.id}",
            path = "$.hostname",
            headers = Map("nodeId" -> "${rudder.node.id}")
          )
          (for {
            nodeIds <- infos.getAll().map(_.keySet.toSet)
            server  <- ZIO.service[ZioServerStarted].map(_.server)
            _       <- server.reset()
            // all node updated one time
            res1    <- updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))
            // let hooks happens - this is magical, it tells zio to let finish things started
            // before that duration before continuing
            res2    <- TestClock.adjust(5.seconds) *> updates.get
            errors  <- server.counterError.get
            success <- server.counterSuccess.get
          } yield (nodeIds, res1, res2, errors, success)).flatMap((nodeIds, res1, res2, errors, success) => {
            assert(res1)(equalTo(nodeUpdatedMatcher(nodeIds))) &&
            assert(res2.toList)(hasSameElements(nodeIds.map(x => (x, 1)))) &&
            assert(errors)(equalTo(0)) &&
            assert(success)(equalTo(nodeIds.size))
          })
        },
        test("work even if nodes don't reply at same speed with POST") {
          val ds = NewDataSource(
            "test-lot-of-nodes-POST",
            url = s"${REST_SERVER_URL}/delay",
            path = "$.hostname",
            method = HttpMethod.POST,
            params = Map("nodeId" -> "${rudder.node.id}"),
            headers = Map("nodeId" -> "${rudder.node.id}")
          )
          (for {
            nodeIds <- infos.getAll().map(_.keySet.toSet)
            server  <- ZIO.service[ZioServerStarted].map(_.server)
            _       <- server.reset()
            // all node updated one time
            res1    <- updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))
            // let hooks happens - this is magical, it tells zio to let finish things started
            // before that duration before continuing
            res2    <- TestClock.adjust(5.seconds) *> updates.get
            errors  <- server.counterError.get
            success <- server.counterSuccess.get
          } yield (nodeIds, res1, res2, errors, success)).flatMap((nodeIds, res1, res2, errors, success) => {
            assert(res1)(equalTo(nodeUpdatedMatcher(nodeIds))) &&
            assert(res2.toList)(hasSameElements(nodeIds.map(x => (x, 1)))) &&
            assert(errors)(equalTo(0)) &&
            assert(success)(equalTo(nodeIds.size))
          })
        },
        test("work for odd node even if even nodes fail") {
          // but that's chatty, disable datasources logger for that one
          val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
          logger.setLevel(Level.OFF)

          val ds        = NewDataSource(
            "test-even-fail",
            url = s"${REST_SERVER_URL}/faileven/$${rudder.node.id}",
            path = "$.hostname"
          )
          val nodeRegEx = "node(.*)".r

          (
            for {
              nodeIds <- infos
                           .getAll()
                           .map(_.keySet.toSet.filter { n =>
                             n.value match {
                               case "root"       => true
                               case nodeRegEx(i) => i.toInt % 2 == 1
                               case _            => throw new IllegalArgumentException(s"Unrecognized name for test node: " + n.value)
                             }
                           })
              server  <- ZIO.service[ZioServerStarted].map(_.server)
              _       <- server.reset()
              // all node updated one time
              _       <- updates.set(Map())
              res1    <- http.queryAll(ds, UpdateCause(modId, actor, None)).either
              // let hooks happens - this is magical, it tells zio to let finish things started
              // before that duration before continuing
              res2    <- TestClock.adjust(5.seconds) *> updates.get // only even nodes get a result
              errors  <- server.counterError.get
              success <- server.counterSuccess.get
            } yield (nodeIds, res1, res2, errors, success)
          ).flatMap((nodeIds, res1, res2, errors, success) => {
            assert(res1)(isLeft) &&
            assert(res2.toList)(hasSameElements(nodeIds.map(x => (x, 1)))) &&
            assert(errors)(equalTo(0))
            assert(success)(equalTo(nodes.size)) // we count one success for each query here
          })
        }
      )
    )
  }

  def gettingANode: Spec[Scope & ZioServerStarted, RudderError] = {
    val datasource = httpDatasourceTemplate.copy(
      url = s"${REST_SERVER_URL}/single_$${rudder.node.id}",
      path = "$.store.${node.properties[get-that]}[:1]"
    )
    test("Getting a node") {
      (for {
        res <- fetch.getNode(
                 DataSourceId("test-get-one-node"),
                 datasource,
                 n1,
                 root,
                 alwaysEnforce,
                 Set(),
                 1.second,
                 5.seconds
               )
      } yield res).flatMap(res => {
        assert(res)(
          isSome(
            equalTo(
              DataSource.nodeProperty(
                "test-get-one-node",
                ConfigFactory
                  .parseString(
                    """{ "x" :
                      {
                          "author" : "Nigel Rees",
                          "category" : "reference",
                          "price" : 8.95,
                          "title" : "Sayings of the Century"
                      }
                      }"""
                  )
                  .getValue("x")
              )
            )
          )
        )
      })
    }
  }

  def fullHttpService: Spec[Scope & ZioServerStarted, RudderError] = {
    suite("The full http service")(
      for {
        (updates, infos) <- buildNodeRepo(NodeConfigData.allNodeFacts)
        http              = new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)
      } yield Chunk(
        test("correctly update all nodes") {
          val datasource = NewDataSource(
            "test-http-service",
            url = s"${REST_SERVER_URL}/single_node1",
            path = "$.store.book"
          )

          (for {
            // all node updated one time
            nodeIds <- infos.getAll().map(_.keySet.toSet)
            _       <- updates.set(Map())
            res1    <- http.queryAll(datasource, UpdateCause(modId, actor, None))
            // let hooks happens - this is magical, it tells zio to let finish things started
            // before that duration before continuing
            res2    <- TestClock.adjust(5.seconds) *> updates.get
          } yield (nodeIds, res1, res2)).flatMap((nodeIds, res1, res2) => {
            assert(res1)(equalTo(nodeUpdatedMatcher(nodeIds))) &&
            assert(res2.toList)(hasSameElements(nodeIds.map(x => (x, 1))))
          })
        },
        test("correctly update one node") {
          // all node updated one time
          val d2 = NewDataSource(
            "test-http-service",
            url = s"${REST_SERVER_URL}/single_node2",
            path = "$.foo"
          )

          (for {
            _    <- updates.set(Map())
            res1 <- http.queryOne(d2, root.id, UpdateCause(modId, actor, None))
            r    <- infos.getAll().map(m => m(root.id))
          } yield (res1, r)).flatMap((res1, r) => {
            assert(res1)(equalTo(NodeUpdateResult.Updated(root.id))) &&
            assert(r.properties.find(_.name == "test-http-service"))(
              isSome(
                equalTo(
                  NodeProperty.apply("test-http-service", "bar".toConfigValue, None, Some(DataSource.providerName))
                )
              )
            )
          })
        },
        test("understand ${node.properties[datasources-injected][short-hostname]} in API") {
          // all node updated one time
          val d2 = NewDataSource(
            "test-http-service",
            url = s"""${REST_SERVER_URL}/$${node.properties[datasources-injected][short-hostname]}""",
            path = "$.hostname"
          )
          // root hostname is server.rudder.local, so short hostname is "server"
          (for {
            _    <- updates.set(Map())
            res1 <- http.queryOne(d2, root.id, UpdateCause(modId, actor, None))
            r    <- infos.getAll().map(m => m(root.id))
          } yield (res1, r)).flatMap((res1, r) => {
            assert(res1)(equalTo(NodeUpdateResult.Updated(root.id))) &&
            assert(r.properties.find(_.name == "test-http-service"))(
              isSome(
                equalTo(
                  NodeProperty.apply(
                    "test-http-service",
                    "server.rudder.local".toConfigValue,
                    None,
                    Some(DataSource.providerName)
                  )
                )
              )
            )
          })
        },
        test("understand ${node.properties[datasources-injected][short-hostname]} in JSON path") {
          val d2 = NewDataSource(
            "test-http-service",
            url = s"""${REST_SERVER_URL}/hostnameJson""",
            path = "$.['nodes']['${node.properties[datasources-injected][short-hostname]}']"
          )

          (for {
            _    <- updates.set(Map())
            res1 <- http.queryOne(d2, root.id, UpdateCause(modId, actor, None))
            r    <- infos.getAll().map(m => m(root.id))
          } yield (res1, r)).flatMap((res1, r) => {
            assert(res1)(equalTo(NodeUpdateResult.Updated(root.id))) &&
            assert(r.properties.find(_.name == "test-http-service"))(
              isSome(
                equalTo(
                  NodeProperty.apply(
                    "test-http-service",
                    """{ "environment": "DEV_INFRA", "mergeBucket" : { "test_merge2" : "aPotentialMergeValue1" } }""".forceParse,
                    None,
                    Some(DataSource.providerName)
                  )
                )
              )
            )
          })
        }
      )
    )
  }

  def behavior404: Spec[Scope & ZioServerStarted, RudderError] = {

    /*
     * Utility method that:
     * - set a node property to a value (if defined)
     * - query an url returning 404
     * - get the props for the value and await a test on them
     *
     * In finalStatCond, the implementation ensures that all nodes are in the map, i.e
     *   PROPS.keySet() == infos.getAll().map(_.keySet())
     */
    type PROPS = Map[NodeId, Option[ConfigValue]]

    def test404prop(propName: String, initValue: Option[String], onMissing: MissingNodeBehavior, expectMod: Boolean)(
        finalStateCond: PROPS => TestResult
    ) = {
      val initNodeFacts = initValue match {
        case None       => NodeConfigData.allNodeFacts
        case Some(init) =>
          NodeConfigData.allNodeFacts.view
            .mapValues(n => n.copy(properties = n.properties :+ NodeProperty.apply(propName, init.toConfigValue, None, None)))
            .toMap
      }

      (for {
        (updates, infos) <- buildNodeRepo(initNodeFacts)
        http              =
          new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)
        datasource        = NewDataSource(propName, url = s"${REST_SERVER_URL}/404", path = "$.some.prop", onMissing = onMissing)

        nodes  <- infos.getAll()
        // set a value for all propName if asked
        modId   = ModificationId("set-test-404")
        nodeIds = nodes.keySet
        res    <- http.queryAll(datasource, UpdateCause(modId, actor, None))
        // let hooks happens - this is magical, it tells zio to let finish things started
        // before that duration before continuing
        u      <- TestClock.adjust(5.seconds) *> updates.get
        props  <- infos.getAll().map(_.map { case (id, n) => (id, n.properties.find(_.name == propName).map(_.value)) }.toMap)
      } yield (nodeIds, res, u, props)).flatMap((nodeIds, res, u, props) => {
        assert(res)(
          equalTo(
            nodeIds.map(n => if (expectMod) NodeUpdateResult.Updated(n) else NodeUpdateResult.Unchanged(n): NodeUpdateResult)
          )
        ) &&
        assert(u)(hasSubset(if (expectMod) nodeIds.map(x => (x, 1)) else Nil)) &&
        finalStateCond(props)
      })
    }

    suite("The behavior on 404 ")(
      test("have a working 'delete property' option")(
        test404prop(
          propName = "test-404",
          initValue = Some("test-404"),
          onMissing = MissingNodeBehavior.Delete,
          expectMod = true
        )(props => assert(props)(hasSameElements(props.keySet.map(x => (x, None)))))
      ),
      test("have a working 'default value property' option")(
        test404prop(
          propName = "test-404",
          initValue = Some("test-404"),
          onMissing = MissingNodeBehavior.DefaultValue("foo".toConfigValue),
          expectMod = true
        )(props => assert(props)(hasSameElements(props.keySet.map(x => (x, Some("foo".toConfigValue))))))
      ),
      test("have a working 'don't touch - not exists' option")(
        test404prop(propName = "test-404", initValue = None, onMissing = MissingNodeBehavior.NoChange, expectMod = false)(props =>
          assert(props)(hasSameElements(props.keySet.map(x => (x, None))))
        )
      ),
      test("have a working 'don't touch - exists' option")(
        test404prop(
          propName = "test-404",
          initValue = Some("test-404"),
          onMissing = MissingNodeBehavior.NoChange,
          expectMod = false
        )(props => assert(props)(hasSameElements(props.keySet.map(x => (x, Some("test-404".toConfigValue))))))
      )
    )
  }
}

object Data {

  /*
   * Array rules:
   * - an empty array is special: remove value, processed elsewhere
   * - an array with one value (whatever the value) => remove array, get value as JSON
   *   - so an array of array will return an array
   * - an array with several values => keep array
   */
  // testing arrays, array of arrays, etc
  lazy val testArray = List(
    // [] is a special case handled elsewhere
    // what API give                             -   expected with [*]                          - expected with [:1]
    ("""[ 1 ]""", """1""", """1"""),
    ("""[ "a" ]""", """a""", """a"""),
    ("""[ { "a" : 1 }]""", """{"a":1}""", """{"a":1}"""),
    ("""[ 1, 2 ]""", """[1,2]""", """1""")                                     // array of size 1 are lifted
    ,
    ("""[ "a", "b" ]""", """["a","b"]""", """a""")                             // array of size 1 are lifted
    ,
    ("""[ { "a": 1 }, { "b": 2} ]""", """[{"a":1}, {"b":2}]""", """{"a":1}""") // array of size 1 are lifted
    ,
    ("""[[]]""", """[]""", """[]"""),
    ("""[ [ 1 ] ]""", """[1]""", """[1]"""),
    ("""[ [ { "a": 1 } ] ]""", """[{"a":1}]""", """[{"a":1}]"""),
    ("""[ [ 1, 2 ] ]""", """[1,2]""", """[1,2]"""),
    ("""[ [ { "a": 1 }, {"b": 2 } ] ]""", """[{"a":1}, {"b":2}]""", """[{"a":1}, {"b":2}]"""),
    ("""[[1],[2]]""", """[[1],[2]]""", """[1]""")                              // array of size 1 are lifted
    ,
    ("""[ {"a": []} ]""", """{"a": []}""", """{"a": []}"""),
    ("""[ {"a": [{"v": 1}]} ]""", """{"a": [{"v": 1}]}""", """{"a": [{"v": 1}]}"""),
    ("""[ {"a": [{"v": 1}, {"v": 2}]} ]""", """{"a": [{"v": 1}, {"v": 2}]}""", """{"a": [{"v": 1}, {"v": 2}]}"""),
    (
      """[ {"a": [{"v": 1}]}, {"b":[{"v":2}]} ]""",
      """[ {"a": [{"v": 1}]}, {"b":[{"v":2}]} ]""",
      """{"a": [{"v": 1}]}"""
    )                                                                          // array of size 1 are lifted
    ,
    ("""[ {"a": [{"v": 1}, {"v":2}]} ]""", """{"a": [{"v": 1}, {"v":2}]}""", """{"a": [{"v": 1}, {"v":2}]}""")
  )

  lazy val hostnameJson =
    """{ "nodes":
      {
        "some.node.com":
          { "environment": "DEV_INFRA", "mergeBucket" : { "test_merge2" : "aPotentialMergeValue1" } },
        "server":
          { "environment": "DEV_INFRA", "mergeBucket" : { "test_merge2" : "aPotentialMergeValue1" } }
      }
    }
    """

  lazy val booksJson = """
  {
    "store": {
        "book": [
            {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            },
            {
                "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            },
            {
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
            }
        ],
        "bicycle": {
            "color": "red",
            "price": 19.95
        }
    },
    "expensive": 10
  }
  """

  // expample of what a CMDB could return for a node.
  def nodeJson(name: String) = s""" {
    "hostname" : "$name",
    "ad_groups" : [ "ad-grp1 " ],
    "ssh_groups" : [ "ssh-power-users" ],
    "sudo_groups" : [ "sudo-masters" ],
    "hostnames" : {
     "fqdn" : "$name.some.host.com $name",
     "local" : "localhost.localdomain localhost localhost4 localhost4.localdomain4"
    },
    "netfilter4_rules" : {
     "all" : "lo",
     "ping" : "eth0",
     "tcpint" : "",
     "udpint" : "",
     "exceptions" : "",
     "logdrop" : false,
     "gateway" : false,
     "extif" : "eth0",
     "intif" : "eth1"
    },
  "netfilter6_rules" : {
     "all" : "lo",
     "ping" : "eth0",
     "tcpint" : "",
     "udpint" : "",
     "exceptions" : "",
     "logdrop" : false,
     "gateway" : false,
     "extif" : "eth0",
     "intif" : "eth1"
    }
  }
  """

}
