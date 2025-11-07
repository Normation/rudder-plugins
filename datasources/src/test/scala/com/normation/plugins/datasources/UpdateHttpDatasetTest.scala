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

package com.normation.plugins.datasources

import ch.qos.logback.classic.Level
import com.normation.BoxSpecMatcher
import com.normation.box.*
import com.normation.errors.*
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.NodeId
import com.normation.plugins.AlwaysEnabledPluginStatus
import com.normation.plugins.PluginEnableImpl
import com.normation.plugins.datasources.DataSourceSchedule.*
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
import com.normation.utils.StringUuidGeneratorImpl
import com.normation.zio.*
import com.softwaremill.quicklens.*
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import net.liftweb.common.*
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.specs2.matcher.EqualityMatcher
import org.specs2.matcher.MatchResult
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll
import org.specs2.specification.core.Fragment
import scala.annotation.nowarn
import scala.collection.MapView
import scala.util.Random
import zhttp.http.*
import zhttp.http.Method.*
import zhttp.service.EventLoopGroup
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zio.{System as _, *}
import zio.syntax.*
import zio.test.Annotations
import zio.test.TestClock

/**
 *  This is just an example test server to run by hand and see how things work.
 */
object TestingZioHttpServer {

  def testService[R]: HttpApp[R, Nothing] = Http.collectZIO[Request] {
    case _ -> !! =>
      ZIO.die(new IllegalArgumentException("You cannot access root in test"))

    case _ -> !! / "status" =>
      Response.text("datasources:ok").succeed

    case _ -> other =>
      ZIO.succeed(Response.text(s"service didn't handle: '${other.segments.map(_.toString).mkString("';'")}'"))
  }

  val serverPort = 49999
  val router     = Http.collectHttp[Request] {
    case GET -> !! / "status" =>
      Http.collectZIO[Request] { case _ => ZIO.succeed(Response.text("ok")) }

    case _ -> "" /: "datasources" /: path =>
      testService.contramap[Request](_.setPath(Path.root.concat(path)))

    case _ -> other =>
      Http.collectZIO[Request] { case _ => ZIO.succeed(Response.text(s"rooter didn't handle: '${other}'")) }
  }

  val server = Server.port(serverPort) ++ Server.app(router)

  def main(args: Array[String]): Unit = {
    val fib = Unsafe.unsafe(implicit unsafe => {
      Runtime.default.unsafe
        .run(
          server.make
            .flatMap(start =>
              ZIO.consoleWith(_.printLine(s"server started on port ${start.port}"))
              *> ZIO.never
            )
            .provideSomeLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(1) ++ Scope.default)
            .forkDaemon
        )
        .getOrThrowFiberFailure()
    })

    println(s"wait for start...")
    Thread.sleep(1000)

    val res1 = QueryHttp.QUERY(HttpMethod.GET, s"http://localhost:49999/status", Map(), Map(), false, 1.second, 1.seconds).runNow
    println(s"res1: ${res1}")
    val res2 = QueryHttp
      .QUERY(HttpMethod.GET, s"http://localhost:49999/datasources/status", Map(), Map(), false, 1.second, 1.seconds)
      .runNow
    println(s"res2: ${res2}")

    Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(fib.interrupt))
  }
}

/**
 * This is just a test program to see how test clock works
 */
object TestingSpacedClock {

  val makeTestClock = TestClock.default.build

  val prog = ZIO.scoped(
    makeTestClock.flatMap(testClock => {
      for {
        queue <- Queue.unbounded[Unit]
        tc     = testClock.get[TestClock]
        f     <- (ZIO.attempt(println("Hello!")) *> queue.offer(())).repeat(Schedule.fixed(5.minutes)).forkDaemon
        _     <- ZIO.attempt(println("set to 0 min")) *> tc.adjust(0.nano) *> queue.take
        _     <- ZIO.attempt(println("set to 1 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 2 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 3 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 4 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 5 min")) *> tc.adjust(1.minute) *> queue.take
        _     <- ZIO.attempt(println("set to 6 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 7 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 8 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 9 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 10 min")) *> tc.adjust(1.minute) *> queue.take
        _     <- ZIO.attempt(println("set to 11 min")) *> tc.adjust(1.minute)
        _     <- ZIO.attempt(println("set to 25 min")) *> tc.adjust(10.minute)
        _     <- f.join
      } yield ()
    })
  )

  val prog2 = ZIO.scoped(
    makeTestClock.flatMap(testClock => {
      for {
        q <- Queue.unbounded[Unit]
        _ <- ZIO.consoleWith(_.printLine("1"))
        _ <- (q.offer(()).delay(60.minutes)).forever.forkDaemon
        _ <- ZIO.consoleWith(_.printLine("2"))
        a <- q.poll.map(_.isEmpty)
        _ <- ZIO.consoleWith(_.printLine(s"3:$a"))
        _ <- testClock.get[TestClock].adjust(60.minutes)
        _ <- ZIO.consoleWith(_.printLine("4"))
        b <- q.take.as(true)
        _ <- ZIO.consoleWith(_.printLine(s"6:b"))
        c <- q.poll.map(_.isEmpty)
        _ <- ZIO.consoleWith(_.printLine(s"7:c"))
        _ <- testClock.get[TestClock].adjust(60.minutes)
        _ <- ZIO.consoleWith(_.printLine("8"))
        d <- q.take.as(true)
        _ <- ZIO.consoleWith(_.printLine(s"9:$d"))
        e <- q.poll.map(_.isEmpty)
        _ <- ZIO.consoleWith(_.printLine(s"0:$e"))
      } yield a && b && c && d && e
    })
  )

  def main(args: Array[String]): Unit = {
    val res = ZioRuntime.unsafeRun(prog2.provideLayer(zio.test.testEnvironment))
    println(res)
  }
}

object CmdbServerStarted {

  val isStarted = Promise.make[Nothing, Unit].runNow

}

/*
 * Create a test server that will act as the datasource endpoint.
 * The server answers to three main URLs set:
 * - /status: just to check server is up
 * - /datasources/...: test json query, expected answers, what happens on 404, special cases with arrays, etc
 * - /datasources/parallel: test that datasource parallel limits are well set and respected
 */
object CmdbServer {
  import com.normation.plugins.datasources.Data.*

  // for debugging - of course works correctly only if sequential
  val counterError   = zio.Ref.make(0).runNow
  val counterSuccess = zio.Ref.make(0).runNow
  val maxPar         = zio.Ref.make(0).runNow

  // a delay methods that use the scheduler
  def delayResponse[V, E](resp: ZIO[V, E, Response]): ZIO[V, E, Response] = {
    resp.delay(Random.nextInt(1000).millis)
  }

  def reset(): Unit = {
    (
      counterError.set(0) *>
      counterSuccess.set(0)
    ).runNow
  }

  /*
   * datasource services
   */
  def service[R]: HttpApp[R, Nothing] = Http.collectZIO[Request] {
    case _ -> !! =>
      ZIO.die(new IllegalArgumentException("You cannot access root in test"))

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
      counterSuccess.update(_ + 1) *>
      ZIO.succeed {
        Response.text(nodeJson(x))
      }

    case r @ POST -> !! / "delay" =>
      val headerId = r.headers.toList.toMap.get("nodeId")

      for {
        body   <-
          r.body.asString.orDie // we should correctly decode POST form data, but here we only have one field nodeId=nodexxxx
        formId <- (body.split('=').toList match {
                    case _ :: nodeId :: Nil => ZIO.succeed(Some(nodeId))
                    case _                  => ZIO.die(throw new IllegalArgumentException(s"Error, can't decode POST form data body: ${body}"))
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

  /*
   * Service that counts the max number of parallel request we get.
   * For that,
   */
  def ioCountService = {
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
          x <- ZIO.succeed(Response.text(nodeJson(x)))
          _ <- currentConcurrent.update(_ - 1)
        } yield x
    }
  }

  val serverPort = 49999 // should be random
  // start server on a free port
  // @silent // deprecation warning
  val serverR    = {
    (
      for {
        count <- ioCountService
      } yield {

        // in zio-http 2.0.x, root segment must be explicitly pattern matched in left extraction and
        // set explicitly in the extracted path.

        val router = Http.collectHttp[Request] {
          case GET -> !! / "status"                           =>
            Http.collectZIO[Request] { case _ => Response.text("ok").succeed }
          case _ -> "" /: "datasources" /: "parallel" /: path =>
            count.contramap[Request](_.setPath(Path.root.concat(path)))

          case _ -> "" /: "datasources" /: path =>
            service.contramap[Request](_.setPath(Path.root.concat(path)))

          case _ -> other =>
            Http.collectZIO[Request] {
              case _ => Console.printLine(s"I didn't handle: '${other}'").orDie *> Response.status(Status.Forbidden).succeed
            }
        }
        Server.port(serverPort) ++ // Setup port - should be next available
        Server.app(router) // Setup the Http app
      }
    )
  }
}

@nowarn("msg=a type was inferred to be `\\w+`; this may indicate a programming error.")
@RunWith(classOf[JUnitRunner])
class UpdateHttpDatasetTest extends Specification with BoxSpecMatcher with Loggable with AfterAll {
  import com.normation.plugins.datasources.Data.*
  val makeTestClock = TestClock.default.build

//  implicit val blockingExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
//  implicit val cs: ContextShift[IO] = IO.contextShift(blockingExecutionContext)
  implicit class ForceGet(json: String) {
    def forceParse = GenericProperty.parseValue(json) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"Error in parsing value: ${err.fullMsg}")
    }
  }

  implicit class DurationToScala(d: Duration) {
    def toScala = scala.concurrent.duration.FiniteDuration(d.toMillis, "millis")
  }

  implicit class RunNowTimeout[A](effect: ZIO[Annotations & zio.test.Live, RudderError, A]) {
    def runTimeout(d: Duration) =
      effect.timeout(d).notOptional(s"The test timed-out after ${d}").provideLayer(zio.test.testEnvironment).runNow
  }

  // a timer
  // implicit val timer: Timer[IO] = cats.attempt.IO.timer(blockingExecutionContext)

  // start server
  val nThreads: Int = 10

  // Create a new server
  ZioRuntime.unsafeRun(
    CmdbServer.serverR
      .flatMap(server => {
        server.make
          .flatMap(start => {
            // Waiting for the server to start
            ZIO.consoleWith(_.printLine(s"Server started on port ${start.port}")) *>
            com.normation.plugins.datasources.CmdbServerStarted.isStarted.complete(ZIO.unit) *>
            // Ensures the server doesn't die after printing
            ZIO.never
          })
          .provideSomeLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(nThreads) ++ Scope.default)
      })
      .forkDaemon
  )

  override def afterAll(): Unit = {}

  val actor = EventActor("Test-actor")
  def modId = ModificationId("test-id-@" + System.currentTimeMillis)

  val interpolation = new InterpolatedValueCompilerImpl(
    new PropertyEngineServiceImpl(
      List.empty
    )
  )
  val fetch: GetDataset = new GetDataset(interpolation)

  val parameterRepo = new RoParameterRepository() {
    override def getAllGlobalParameters(): IOResult[Seq[GlobalParameter]] = Seq().succeed
    override def getGlobalParameter(parameterName: String): IOResult[Option[GlobalParameter]] = None.succeed
  }

  def buildNodeRepo(initNodeInfo: Map[NodeId, CoreNodeFact]): (Ref[Map[NodeId, Int]], NodeFactRepository) = {

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
                             case _                                                       => ZIO.unit
                           }
                         }
                       }
      repo          <- CoreNodeFactRepository.makeNoop(
                         initNodeInfo,
                         callbacks = Chunk(updateCallback)
                       )
    } yield (updates, repo)).runNow
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
  val datasourceTemplate     = DataSource(
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
    val (updates, infos) = buildNodeRepo(NodeConfigData.allNodeFacts)
    val http             = new HttpQueryDataSourceService(
      infos,
      parameterRepo,
      interpolation,
      noPostHook,
      () => alwaysEnforce.succeed
      //   , realClock // this one need a real clock to be able to do the requests
    )
    val uuidGen          = new StringUuidGeneratorImpl()
  }

  object Enabled extends PluginEnableImpl

  val REST_SERVER_URL_ROOT = s"http://localhost:${CmdbServer.serverPort}"
  val REST_SERVER_URL      = s"${REST_SERVER_URL_ROOT}/datasources"

  def nodeUpdatedMatcher(nodeIds: Set[NodeId]): EqualityMatcher[Set[NodeUpdateResult]] = {
    ===(nodeIds.map(n => NodeUpdateResult.Updated(n)))
  }

  implicit class QueueFailIfNonEmpty[A](queue: Queue[A]) {
    def failIfNonEmpty: IOResult[Unit] = queue.poll.flatMap {
      case None    => ().succeed
      case Some(_) => Inconsistency(s"queue should be empty but size = ${1 + queue.size.runNow}").fail
    }
  }

  // must be sequential!
  sequential

  // wait for server to be started
  CmdbServerStarted.isStarted.await.runNow

  "Check that the server is up and running fine" >> {

    val res1 = QueryHttp.QUERY(HttpMethod.GET, REST_SERVER_URL_ROOT + "/status", Map(), Map(), false, 1.second, 1.seconds).runNow
    val res2 = QueryHttp.QUERY(HttpMethod.GET, REST_SERVER_URL + "/status", Map(), Map(), false, 1.second, 1.seconds).runNow

    (res1 === Some("ok")) && (res2 === Some("datasources:ok"))

  }

  "Array validation with [*]" >> {
    // THIS IS SKIPPED, see at the end of block
    Fragment.foreach(0 until testArray.size) { i =>
      s"for case: ${testArray(i)._1} -> ${testArray(i)._2}" >> {
        val datasource = NewDataSource(
          "test-http-service",
          url = s"${REST_SERVER_URL}/testarray/$i",
          path = "$.[*]"
        )

        val (updates, infos) = buildNodeRepo(NodeConfigData.allNodeFacts)
        val http             =
          new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)
        val nodeIds          = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw").keySet.toSet
        val res              = updates.set(Map()) *> http.queryAll(datasource, UpdateCause(modId, actor, None))

        res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
          updates.get.runNow must havePairs(nodeIds.map(x => (x, 1)).toSeq*)
        ) and (
          infos
            .getAll()(using QueryContext.testQC)
            .toBox
            .flatMap(m => m(root.id).properties.find(_.name == "test-http-service")) mustFullEq (
            NodeProperty.apply("test-http-service", testArray(i)._2.forceParse, None, Some(DataSource.providerName))
          )
        )
      }
    }
    skipped("Sometimes this is failing randomly on CI, it seems to be running fine locally")
  }
  "Array validation with [:1]" >> {
    // THIS IS SKIPPED, see at the end of block
    Fragment.foreach(0 until testArray.size) { i =>
      s"for case: ${testArray(i)._1} -> ${testArray(i)._3}" >> {
        val datasource = NewDataSource(
          "test-http-service",
          url = s"${REST_SERVER_URL}/testarray/$i",
          path = "$.[:1]"
        )

        val (updates, infos) = buildNodeRepo(NodeConfigData.allNodeFacts)
        val http             =
          new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)
        val nodeIds          = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw").keySet.toSet
        val res              = updates.set(Map()) *> http.queryAll(datasource, UpdateCause(modId, actor, None))

        res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
          updates.get.runNow must havePairs(nodeIds.map(x => (x, 1)).toSeq*)
        ) and (
          infos
            .getAll()(using QueryContext.testQC)
            .toBox
            .flatMap(m => m(root.id).properties.find(_.name == "test-http-service")) mustFullEq (
            NodeProperty.apply("test-http-service", testArray(i)._3.forceParse, None, Some(DataSource.providerName))
          )
        )
      }
    }
    skipped("Sometimes this is failing randomly on CI, it seems to be running fine locally")
  }

  "Update on datasource" should {
    val datasource = NewDataSource(
      name = "test-scheduler",
      url = s"${REST_SERVER_URL}/$${rudder.node.id}",
      path = "$.hostname",
      schedule = Scheduled(5.minute)
    )
    val action     = (c: UpdateCause) => {
      // here we need to give him the default scheduler, not the test one,
      // to actually have the fetch logic done
      IOResult.attempt(MyDatasource.http.queryAll(datasource, c).either.runNow match {
        case Right(_)  => // nothing
        case Left(err) => logger.error(s"oh no! Got a $err")
      })
    }

    // test clock needs explicit await to works, so we add them with a queue offer/take
    val testAction = (q: Queue[Unit]) => (c: UpdateCause) => action(c) *> q.offer(()).unit

    "does nothing if scheduler is disabled" in {
      val (total_0, total_1d): (Int, Int) = ZIO
        .scoped(makeTestClock.flatMap { testClock =>
          val queue = Queue.unbounded[Unit].runNow

          val dss = new DataSourceScheduler(
            datasource.copy(enabled = false),
            Enabled,
            () => ModificationId(MyDatasource.uuidGen.newUuid),
            testAction(queue)
          )

          // reset counter
          CmdbServer.reset()
          // before start, nothing is done
          for {
            ce_0   <- CmdbServer.counterError.get
            cs_0   <- CmdbServer.counterSuccess.get
            total_0 = ce_0 + cs_0
            _      <- dss.restartScheduleTask()
            // then, event after days, nothing is done
            _      <- testClock.get[TestClock].adjust(1.day)
            ce_1d  <- CmdbServer.counterError.get
            cs_1d  <- CmdbServer.counterSuccess.get
          } yield {
            (total_0, ce_1d + cs_1d)
          }
        })
        .runTimeout(1.minute)

      (total_0, total_1d) must beEqualTo((0, 0))
    }

    "allows interactive updates with disabled scheduler (but not data source)" in {
      val (total_0, total_1d, total_postGen) = ZIO
        .scoped(makeTestClock.flatMap { testClock =>
          val queue = Queue.unbounded[Unit].runNow

          val dss = new DataSourceScheduler(
            datasource.copy(runParam = datasource.runParam.copy(schedule = NoSchedule(1.second))),
            Enabled,
            () => ModificationId(MyDatasource.uuidGen.newUuid),
            testAction(queue)
          )

          val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
          logger.setLevel(Level.TRACE)
          // reset counter
          CmdbServer.reset()
          // before start, nothing is done
          for {
            _      <- queue.failIfNonEmpty
            ce_0   <- CmdbServer.counterError.get
            cs_0   <- CmdbServer.counterSuccess.get
            total_0 = ce_0 + cs_0
            _      <- dss.restartScheduleTask()
            // then, event after days, nothing is done
            _      <- testClock.get[TestClock].adjust(1.day)
            _      <- queue.failIfNonEmpty
            ce_1   <- CmdbServer.counterError.get
            cs_1   <- CmdbServer.counterSuccess.get
            total_1 = ce_1 + cs_1
            // but asking for a direct update do the queries immediately - task need at least 1ms to notice it should run
            _      <- dss.doActionAndSchedule(action(UpdateCause(ModificationId("plop"), RudderEventActor, None)))
            _      <- testClock.get[TestClock].adjust(1.second)
            _      <- queue.failIfNonEmpty
            ce_2   <- CmdbServer.counterError.get
            cs_2   <- CmdbServer.counterSuccess.get
            total_2 = ce_2 + cs_2
          } yield (total_0, total_1, total_2)
        })
        .runTimeout(1.minute)

      val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
      logger.setLevel(Level.OFF)
      (total_0, total_1d, total_postGen) must beEqualTo((0, 0, NodeConfigData.allNodesInfo.size))

    }

    "create a new schedule from data source information" in {
      val (total_0, total_0s, total_1s, total_4m, total_5m, total_8m) = ZIO
        .scoped(makeTestClock.flatMap { testClock =>
          // testClock need to know what fibers are doing something, and it' seems to be done easily with a queue.
          val queue = Queue.unbounded[Unit].runNow

          val dss = new DataSourceScheduler(
            datasource.copy(name = DataSourceName("create a new schedule")),
            Enabled,
            () => ModificationId(MyDatasource.uuidGen.newUuid),
            testAction(queue)
          )

          // reset counter
          CmdbServer.reset()
          for {
            // before start, nothing is done
            _       <- queue.failIfNonEmpty
            ce_0    <- CmdbServer.counterError.get
            cs_0    <- CmdbServer.counterSuccess.get
            total_0  = ce_0 + cs_0
            _       <- dss.restartScheduleTask()
            // then just after, we have the first exec - it still need at least a ms to tick
            // still nothing here
            _       <- testClock.get[TestClock].adjust(1.second)
            // here we have results
            _       <- queue.take
            ce_0s   <- CmdbServer.counterError.get
            cs_0s   <- CmdbServer.counterSuccess.get
            total_0s = ce_0s + cs_0s
            // then nothing happens before 5 minutes
            _       <- testClock.get[TestClock].adjust(1.second)
            _       <- queue.failIfNonEmpty
            ce_1s   <- CmdbServer.counterError.get
            cs_1s   <- CmdbServer.counterSuccess.get
            total_1s = ce_1s + cs_1s
            _       <- testClock.get[TestClock].adjust(4.minute)
            _       <- queue.failIfNonEmpty
            ce_4m   <- CmdbServer.counterError.get
            cs_4m   <- CmdbServer.counterSuccess.get
            total_4m = ce_4m + cs_4m
            // then all the nodes gets their info
            _       <- testClock.get[TestClock].adjust(1.minute) // 5 minutes
            _       <- queue.take
            ce_5m   <- CmdbServer.counterError.get
            cs_5m   <- CmdbServer.counterSuccess.get
            total_5m = ce_5m + cs_5m
            // then nothing happen anymore
            _       <- testClock.get[TestClock].adjust(3.minute) // 8 minutes
            _       <- queue.failIfNonEmpty
            ce_8m   <- CmdbServer.counterError.get
            cs_8m   <- CmdbServer.counterSuccess.get
            total_8m = ce_8m + cs_8m
          } yield (total_0, total_0s, total_1s, total_4m, total_5m, total_8m)
        })
        .runTimeout(1.minute)

      val size = NodeConfigData.allNodesInfo.size
      (total_0, total_0s, total_1s, total_4m, total_5m, total_8m) must beEqualTo((0, size, size, size, size * 2, size * 2)) // and
      // (f1 must beEqualTo(None)) and (r1 === Fiber.Status.Running(interrupting = false)) and (r2 === Fiber.Status.Done)
    }
  }

  "When we update a datasource with repo operation, its live instance must be reloaded (old fiber killed)" >> {
    val (updates, infos) = buildNodeRepo(NodeConfigData.allNodeFacts)
    val repos            = new DataSourceRepoImpl(
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

    val id = DataSourceId("test-repos-save")

    val datasource = NewDataSource(
      name = id.value,
      url = s"${REST_SERVER_URL}/$${rudder.node.id}",
      path = "$.hostname",
      schedule = Scheduled(5.minute)
    )

    val (r11, r12, r21) = RunNowTimeout(
      for {
        _   <- repos.save(datasource) // init
        f1  <- repos.datasources.all().flatMap(_(id).scheduledTask.get).notOptional("error in test: f1 is none")
        // here, it can be either Running (if the init takes some time) or Suspended (if init ended and won't run before 5 minutes)
        r11 <- f1.fold(_.status, _ => Unexpected("Datasource scheduler fiber should not be synthetic").fail)
        _   <- repos.save(datasource.copy(name = DataSourceName("updated name")))
        f2  <- repos.datasources.all().flatMap(_(id).scheduledTask.get).notOptional("error in test: f2 is none")
        // here, since we updated repos, f1 was terminated
        r12 <- f1.fold(_.status, _ => Unexpected("Datasource scheduler fiber should not be synthetic").fail)
        // and f2 is running or suspended (if waiting for next schedule)
        r21 <- f2.fold(_.status, _ => Unexpected("Datasource scheduler fiber should not be synthetic").fail)
      } yield (r11, r12, r21)
    ).runTimeout(1.minute)

    def fiberRunning(fs: Fiber.Status) = fs must beLike {
      case Fiber.Status.Running(_, _)      => ok
      case Fiber.Status.Suspended(_, _, _) => ok
    }

    fiberRunning(r11) and (r12 must beEqualTo(Fiber.Status.Done)) and fiberRunning(r21)
  }

  "a datasource creation, node update, deletion should create properties and then delete them" >> {
    // set the variable by hand for node1 and node2. Node2 will have it overridden and then deleted, node1 kept (b/c 404 for datasources)
    val id               = DataSourceId("test-ds-lifecycle")
    val (updates, infos) = buildNodeRepo(NodeConfigData.allNodeFacts.map {
      case (NodeId("node1"), n) =>
        (n.id, n.modify(_.properties).using(NodeProperty(id.value, "do not touch".toConfigValue, None, None) +: _))
      case (NodeId("node2"), n) =>
        (n.id, n.modify(_.properties).using(NodeProperty(id.value, "should be updated".toConfigValue, None, None) +: _))
      case (k, v)               => (k, v)
    })
    val repos            = new DataSourceRepoImpl(
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

    def getProps(nodes: MapView[NodeId, CoreNodeFact]) = {
      nodes.collect {
        case (k, n) => n.properties.find(_.name == id.value).map(p => (k.value, p.value.unwrapped()))
      }.flatten.toMap
    }

    val datasource = NewDataSource(
      name = id.value,
      url = s"${REST_SERVER_URL}/lifecycle/$${rudder.node.id}", // this one does not set the prop for node1 else return 1
      path = "$",
      schedule = Scheduled(5.minute),
      onMissing = MissingNodeBehavior.NoChange
    )

    val p0 = getProps(infos.getAll()(using QueryContext.testQC).runNow)

    val (n1, n2) = (for {
      _      <- repos.save(datasource) // init
      _      <- repos.onUserAskUpdateAllNodesFor(actor, id)
      // check nodes have the property now
      nodes1 <- infos.getAll()(using QueryContext.testQC)
      // now delete datasource
      _      <- repos.delete(datasource.id, UpdateCause(ModificationId("test"), actor, None))
      // property must be deleted now
      nodes2 <- infos.getAll()(using QueryContext.testQC)
    } yield (nodes1, nodes2))
      .runTimeout(1.minute)

    (p0 must containTheSameElementsAs(List("node1" -> "do not touch", "node2" -> "should be updated"))) and
    (getProps(n1) must containTheSameElementsAs(List("root" -> "1", "node1" -> "do not touch", "node2" -> "1"))) and
    (getProps(n2) must containTheSameElementsAs(List("node1" -> "do not touch")))
  }

  "querying a lot of nodes" should {

    // test on 100 nodes. With 30s timeout, even on small hardware it will be ok.
    val nodes            = (NodeConfigData.factRoot :: List.fill(100)(NodeConfigData.fact1).zipWithIndex.map {
      case (n, i) =>
        val name = "node" + i
        n.modify(_.id).setTo(NodeId(name)).modify(_.fqdn).setTo(name + ".localhost")
    }).map(n => (n.id, n)).toMap
    val (updates, infos) = buildNodeRepo(nodes)
    val http             = new HttpQueryDataSourceService(
      infos,
      parameterRepo,
      interpolation,
      noPostHook,
      () => alwaysEnforce.succeed
    )

    def maxParDataSource(n: Int) = NewDataSource(
      "test-lot-of-nodes-max-parallel-GET",
      url = s"${REST_SERVER_URL}/parallel/$${rudder.node.id}",
      path = "$.hostname",
      headers = Map("nodeId" -> "${rudder.node.id}"),
      maxPar = n
    )

    "comply with the limit of parallel queries" in {
      // Max parallel is the minimum of 2 and the available thread on the machine
      // So tests don't fait if the build machine has one core
      val MAX_PARALLEL = Math.min(2, java.lang.Runtime.getRuntime.availableProcessors)
      val ds           = maxParDataSource(MAX_PARALLEL)
      val nodeIds      = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw").keySet.toSet
      // all node updated one time
      CmdbServer.reset()
      val res          = updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        CmdbServer.counterError.get.runNow must_=== 0
      ) and (CmdbServer.maxPar.get.runNow must_=== MAX_PARALLEL)
    }

    "work even if nodes don't reply at same speed with GET" in {
      val ds      = NewDataSource(
        "test-lot-of-nodes-GET",
        url = s"${REST_SERVER_URL}/delay/$${rudder.node.id}",
        path = "$.hostname",
        headers = Map("nodeId" -> "${rudder.node.id}")
      )
      val nodeIds = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw").keySet.toSet
      // all node updated one time
      CmdbServer.reset()
      val res     = updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        updates.get.runNow must havePairs(nodeIds.map(x => (x, 1)).toSeq*)
      ) and (CmdbServer.counterError.get.runNow must_=== 0) and (CmdbServer.counterSuccess.get.runNow must_=== nodeIds.size)
    }

    "work even if nodes don't reply at same speed with POST" in {
      val ds      = NewDataSource(
        "test-lot-of-nodes-POST",
        url = s"${REST_SERVER_URL}/delay",
        path = "$.hostname",
        method = HttpMethod.POST,
        params = Map("nodeId" -> "${rudder.node.id}"),
        headers = Map("nodeId" -> "${rudder.node.id}")
      )
      val nodeIds = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw").keySet.toSet
      // all node updated one time
      CmdbServer.reset()
      val res     = updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        updates.get.runNow must havePairs(nodeIds.map(x => (x, 1)).toSeq*)
      ) and (CmdbServer.counterError.get.runNow must_=== 0) and (CmdbServer.counterSuccess.get.runNow must_=== nodeIds.size)
    }

    "work for odd node even if even nodes fail" in {
      // but that's chatty, disable datasources logger for that one
      val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
      logger.setLevel(Level.OFF)

      val ds        = NewDataSource(
        "test-even-fail",
        url = s"${REST_SERVER_URL}/faileven/$${rudder.node.id}",
        path = "$.hostname"
      )
      val nodeRegEx = "node(.*)".r
      val nodeIds   = infos
        .getAll()(using QueryContext.testQC)
        .toBox
        .openOrThrowException("test shall not throw")
        .keySet
        .toSet
        .filter(n => {
          n.value match {
            case "root"       => true
            case nodeRegEx(i) => i.toInt % 2 == 1
            case _            => throw new IllegalArgumentException(s"Unrecognized name for test node: " + n.value)
          }
        })
      // all node updated one time
      val res       = (updates.set(Map()) *> http.queryAll(ds, UpdateCause(modId, actor, None))).either.runNow

      // set back level
      logger.setLevel(Level.WARN)

      res must beLeft and (
        updates.get.runNow must havePairs(nodeIds.map(x => (x, 1)).toSeq*)
      )

    }
  }

  "Getting a node" should {
    val datasource = httpDatasourceTemplate.copy(
      url = s"${REST_SERVER_URL}/single_$${rudder.node.id}",
      path = "$.store.${node.properties[get-that]}[:1]"
    )
    "get the node" in {
      val res = fetch.getNode(DataSourceId("test-get-one-node"), datasource, n1, root, alwaysEnforce, Set(), 1.second, 5.seconds)

      res.either.runNow must beRight(
        ===(
          Some(
            DataSource.nodeProperty(
              "test-get-one-node",
              ConfigFactory
                .parseString("""{ "x" :
          {
              "author" : "Nigel Rees",
              "category" : "reference",
              "price" : 8.95,
              "title" : "Sayings of the Century"
          }
          }""").getValue("x")
            )
          ): Option[NodeProperty]
        )
      )
    }
  }

  "The full http service" should {
    val datasource = NewDataSource(
      "test-http-service",
      url = s"${REST_SERVER_URL}/single_node1",
      path = "$.store.book"
    )

    val (updates, infos) = buildNodeRepo(NodeConfigData.allNodeFacts)
    val http             = new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)

    "correctly update all nodes" in {
      // all node updated one time
      val nodeIds = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw").keySet.toSet
      val res     = updates.set(Map()) *> http.queryAll(datasource, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        updates.get.runNow must havePairs(nodeIds.map(x => (x, 1)).toSeq*)
      )
    }

    "correctly update one node" in {
      // all node updated one time
      val d2  = NewDataSource(
        "test-http-service",
        url = s"${REST_SERVER_URL}/single_node2",
        path = "$.foo"
      )
      val res = updates.set(Map()) *> http.queryOne(d2, root.id, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(===(NodeUpdateResult.Updated(root.id): NodeUpdateResult)) and (
        infos
          .getAll()(using QueryContext.testQC)
          .toBox
          .flatMap(m => m(root.id).properties.find(_.name == "test-http-service")) mustFullEq (
          NodeProperty.apply("test-http-service", "bar".toConfigValue, None, Some(DataSource.providerName))
        )
      )
    }

    "understand ${node.properties[datasources-injected][short-hostname]} in API" in {
      // all node updated one time
      val d2  = NewDataSource(
        "test-http-service",
        url = s"""${REST_SERVER_URL}/$${node.properties[datasources-injected][short-hostname]}""",
        path = "$.hostname"
      )
      // root hostname is server.rudder.local, so short hostname is "server"
      val res = updates.set(Map()) *> http.queryOne(d2, root.id, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(===(NodeUpdateResult.Updated(root.id): NodeUpdateResult)) and (
        infos
          .getAll()(using QueryContext.testQC)
          .toBox
          .flatMap(m => m(root.id).properties.find(_.name == "test-http-service")) mustFullEq (
          NodeProperty.apply("test-http-service", "server.rudder.local".toConfigValue, None, Some(DataSource.providerName))
        )
      )
    }
    "understand ${node.properties[datasources-injected][short-hostname]} in JSON path" in {
      // all node updated one time
      val d2  = NewDataSource(
        "test-http-service",
        url = s"""${REST_SERVER_URL}/hostnameJson""",
        path = "$.['nodes']['${node.properties[datasources-injected][short-hostname]}']"
      )
      // root hostname is server.rudder.local, so short hostname is "server"
      val res = updates.set(Map()) *> http.queryOne(d2, root.id, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(===(NodeUpdateResult.Updated(root.id): NodeUpdateResult)) and (
        infos
          .getAll()(using QueryContext.testQC)
          .toBox
          .flatMap(m => m(root.id).properties.find(_.name == "test-http-service")) mustFullEq (
          NodeProperty.apply(
            "test-http-service",
            """{ "environment": "DEV_INFRA", "mergeBucket" : { "test_merge2" : "aPotentialMergeValue1" } }""".forceParse,
            None,
            Some(DataSource.providerName)
          )
        )
      )
    }
  }

  "The behavior on 404 " should {

    /*
     * Utility method that:
     * - set a node property to a value (if defined)
     * - query an url returning 404
     * - get the props for the value and await a test on them
     *
     * In finalStatCond, the implementation ensures that all nodes are in the map, i.e
     *   PROPS.keySet() == infos.getAll()(using QueryContext.testQC).toBox.keySet()
     */
    type PROPS = Map[NodeId, Option[ConfigValue]]
    def test404prop(propName: String, initValue: Option[String], onMissing: MissingNodeBehavior, expectMod: Boolean)(
        finalStateCond: PROPS => MatchResult[PROPS]
    ): MatchResult[Any] = {
      val initNodeFacts    = initValue match {
        case None       => NodeConfigData.allNodeFacts
        case Some(init) =>
          NodeConfigData.allNodeFacts.view
            .mapValues(n => n.copy(properties = n.properties :+ NodeProperty.apply(propName, init.toConfigValue, None, None)))
            .toMap
      }
      val (updates, infos) = buildNodeRepo(initNodeFacts)
      val http             =
        new HttpQueryDataSourceService(infos, parameterRepo, interpolation, noPostHook, () => alwaysEnforce.succeed)
      val datasource       = NewDataSource(propName, url = s"${REST_SERVER_URL}/404", path = "$.some.prop", onMissing = onMissing)

      val nodes   = infos.getAll()(using QueryContext.testQC).toBox.openOrThrowException("test shall not throw")
      // set a value for all propName if asked
      val modId   = ModificationId("set-test-404")
      val nodeIds = nodes.keySet
      val matcher = ===(
        nodeIds.map(n => if (expectMod) NodeUpdateResult.Updated(n) else NodeUpdateResult.Unchanged(n): NodeUpdateResult)
      )

      (for {

        res <- http.queryAll(datasource, UpdateCause(modId, actor, None))

        result <-
          updates.get.map(u => {
            (res must matcher) and (
              u must (if (expectMod) havePairs(nodeIds.map(x => (x, 1)).toSeq*) else empty)
            )
          })
      } yield {
        result
      }).runNow and ({
        // none should have "test-404"
        val props = infos
          .getAll()(using QueryContext.testQC)
          .toBox
          .openOrThrowException("test shall not throw")
          .map { case (id, n) => (id, n.properties.find(_.name == propName).map(_.value)) }
          .toMap
        finalStateCond(props)
      })
    }

    "have a working 'delete property' option" in {
      test404prop(propName = "test-404", initValue = Some("test-404"), onMissing = MissingNodeBehavior.Delete, expectMod = true) {
        props => props must havePairs(props.keySet.map(x => (x, None)).toSeq*)
      }
    }
    "have a working 'default value property' option" in {
      test404prop(
        propName = "test-404",
        initValue = Some("test-404"),
        onMissing = MissingNodeBehavior.DefaultValue("foo".toConfigValue),
        expectMod = true
      )(props => props must havePairs(props.keySet.map(x => (x, Some("foo".toConfigValue))).toSeq*))
    }
    "have a working 'don't touch - not exists' option" in {
      test404prop(propName = "test-404", initValue = None, onMissing = MissingNodeBehavior.NoChange, expectMod = false) { props =>
        props must havePairs(props.keySet.map(x => (x, None)).toSeq*)
      }
    }
    "have a working 'don't touch - exists' option" in {
      test404prop(
        propName = "test-404",
        initValue = Some("test-404"),
        onMissing = MissingNodeBehavior.NoChange,
        expectMod = false
      )(props => props must havePairs(props.keySet.map(x => (x, Some("test-404".toConfigValue))).toSeq*))
    }
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
