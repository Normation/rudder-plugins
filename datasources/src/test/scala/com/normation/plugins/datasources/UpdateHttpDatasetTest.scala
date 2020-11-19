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

import java.util.concurrent.Executors

import com.normation.plugins.PluginEnableImpl
import ch.qos.logback.classic.Level
import com.normation.BoxSpecMatcher
import com.normation.eventlog.EventActor
import com.normation.eventlog.ModificationId
import com.normation.inventory.domain.{KeyStatus, NodeId, SecurityToken}
import com.normation.plugins.datasources.DataSourceSchedule._
import com.normation.rudder.domain.eventlog._
import com.normation.rudder.domain.nodes.CompareProperties
import com.normation.rudder.domain.nodes.Node
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.nodes.NodeProperty
import com.normation.rudder.repository.RoParameterRepository
import com.normation.rudder.repository.WoNodeRepository
import com.normation.rudder.services.nodes.NodeInfoService
import com.normation.rudder.services.policies.InterpolatedValueCompilerImpl
import com.normation.rudder.services.policies.NodeConfigData
import com.normation.utils.StringUuidGeneratorImpl
import net.liftweb.common._
import org.http4s.util._
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.specs2.matcher.MatchResult
import org.specs2.mutable._
import org.specs2.specification.AfterAll
import org.specs2.runner.JUnitRunner

import scala.concurrent.ExecutionContext
import scala.util.Random
import com.normation.rudder.domain.queries.CriterionComposition
import com.normation.rudder.domain.queries.NodeInfoMatcher
import com.normation.rudder.domain.policies.GlobalPolicyMode
import com.normation.rudder.domain.policies.PolicyMode
import com.normation.rudder.domain.policies.PolicyModeOverrides
import org.http4s.HttpService
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import cats.effect._
import cats.effect.concurrent.Ref
import com.github.ghik.silencer.silent
import com.normation.zio.ZioRuntime
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import zio.syntax._
import zio.{IO => _, _}
import com.normation.errors._
import com.normation.rudder.domain.nodes.GenericProperty
import com.normation.rudder.domain.nodes.GenericProperty._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import org.specs2.matcher.EqualityMatcher
import zio.test.environment._
import zio.duration._
import org.specs2.specification.core.Fragment
import com.normation.zio._
import zio.test.Annotations

object TheSpaced {

  val makeTestClock = TestClock.default.build

  val prog = makeTestClock.use(testClock =>
    for {
      queue <- Queue.unbounded[Unit]
      tc = testClock.get[TestClock.Service]
      f <- (UIO(println("Hello!")) *> queue.offer(())).repeat(Schedule.fixed(5.minutes)).provide(testClock).forkDaemon
      _ <- UIO(println("set to 0 min")) *> tc.adjust(0.nano) *> queue.take
      _ <- UIO(println("set to 1 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 2 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 3 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 4 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 5 min")) *> tc.adjust(1.minute) *> queue.take
      _ <- UIO(println("set to 6 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 7 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 8 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 9 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 10 min")) *> tc.adjust(1.minute) *> queue.take
      _ <- UIO(println("set to 11 min")) *> tc.adjust(1.minute)
      _ <- UIO(println("set to 25 min")) *> tc.adjust(10.minute)
      _ <- f.join
    } yield ()
  ).provideLayer(testEnvironment)

  val prog2 = makeTestClock.use(testClock => for {
    q <- Queue.unbounded[Unit]
    _ <- (q.offer(()).delay(60.minutes)).forever.provide(testClock).forkDaemon
    a <- q.poll.map(_.isEmpty)
    _ <- testClock.get[TestClock.Service].adjust(60.minutes)
    x <- q.poll.map(_.nonEmpty)
    b <- q.take.as(true)
    c <- q.poll.map(_.isEmpty)
    _ <- testClock.get[TestClock.Service].adjust(60.minutes)
    d <- q.take.as(true)
    e <- q.poll.map(_.isEmpty)
  } yield a && b && c && d && e && x).provideLayer(testEnvironment)

  def main(args: Array[String]): Unit = {
    println(ZioRuntime.unsafeRun(prog))
  }
}

@RunWith(classOf[JUnitRunner])
class UpdateHttpDatasetTest extends Specification with BoxSpecMatcher with Loggable with AfterAll {
  val makeTestClock = TestClock.default.build

  implicit val blockingExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  implicit val cs: ContextShift[IO] = IO.contextShift(blockingExecutionContext)
  implicit class ForceGet(json: String) {
    def forceParse = GenericProperty.parseValue(json) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"Error in parsing value: ${err.fullMsg}")
    }
  }

  implicit class DurationToScala(d: Duration) {
    def toScala = scala.concurrent.duration.FiniteDuration(d.toMillis, "millis")
  }

  //utility to compact render a json string
  //will throws exceptions if errors
  def compact(json: String): String = {
    import net.liftweb.json._
    compactRender(parse(json))
  }

  implicit class RunNowTimeout[A](effect: ZIO[Live with Annotations, RudderError, A]) {
    def runTimeout(d: Duration) = effect.timeout(d).notOptional(s"The test timed-out after ${d}").provideLayer(testEnvironment).runNow
  }

  // a timer
  implicit val timer: Timer[IO] = cats.effect.IO.timer(blockingExecutionContext)


  //create a rest server for test
  object NodeDataset {

    //for debuging - of course works correctly only if sequential
    val counterError   = zio.Ref.make(0).runNow
    val counterSuccess = zio.Ref.make(0).runNow
    val maxPar = zio.Ref.make(0).runNow

    // a delay methods that use the scheduler
    def delayResponse(resp: IO[Response[IO]]): IO[Response[IO]] = {
      timer.sleep(Random.nextInt(1000).millis.toScala).flatMap(_ =>
        resp
      )
    }


    def reset(): Unit = {
      counterError.set(0).runNow
      counterSuccess.set(0).runNow
    }


    def service=  HttpRoutes.of[IO] {
      case _ -> Root =>
        IO.pure(Response(MethodNotAllowed))

      case GET -> Root / "single_node1" =>
        Ok{
          counterSuccess.update(_+1).runNow
          booksJson
        }

      case GET -> Root / "testarray" / x =>
        Ok{
          counterSuccess.update(_+1).runNow
          testArray(x.toInt)._1
        }


      case GET -> Root / "single_node2" =>
        Ok{
          counterSuccess.update(_+1).runNow
          """{"foo":"bar"}"""
        }

      case GET -> Root / "server" =>
        Ok{
          counterSuccess.update(_+1).runNow
          """{"hostname":"server.rudder.local"}"""
        }
      case GET -> Root / "hostnameJson" =>
        Ok{
          counterSuccess.update(_+1).runNow
          hostnameJson
        }

      case GET -> Root / "404" =>
        NotFound {}

      case GET -> Root / x =>
        Ok {
          counterSuccess.update(_+1).runNow
          nodeJson(x)
        }

      case r @ GET -> Root / "delay" / x =>
        r.headers.get(CaseInsensitiveString("nodeId")).map( _.value) match {
          case Some(`x`) =>
            delayResponse(Ok {
              counterSuccess.update(_+1).runNow
              nodeJson(x)
            })

          case _ =>
            Forbidden {
              counterError.update(_+1).runNow
              "node id was not found in the 'nodeid' header"
            }
        }

      case r @ POST -> Root / "delay" =>

        val headerId = r.headers.get(CaseInsensitiveString("nodeId")).map( _.value)

        r.decode[UrlForm] { data =>
          val formId = data.values.get("nodeId").flatMap(_.headOption)

          (headerId, formId) match {
            case (Some(x), Some(y)) if x == y =>
              delayResponse( Ok {
                  counterSuccess.update(_+1).runNow
                  nodeJson("plop")
              })

            case _ =>
              Forbidden {
                counterError.update(_+1).runNow
                "node id was not found in post form (key=nodeId)"
              }
          }
        }

      case GET -> Root / "faileven" / x =>
        // x === "nodeXX" or root
        if(x != "root" && x.replaceAll("node", "").toInt % 2 == 0) {
          Forbidden {
            counterError.update(_+1).runNow
            "Not authorized"
          }
        } else {
          Ok {
            counterSuccess.update(_+1).runNow
            nodeJson(x)
          }
        }
    }

    @silent // deprecation warning
    def ioCountService(implicit F: cats.effect.Async[IO]): IO[HttpService[IO]] = {
      for {
        currentConcurrent <- Ref.of[IO, Int](0)
        maxConcurrent     <- Ref.of[IO, Int](0)
        service           <- IO.pure(HttpService[IO] {
                               case GET -> Root / x =>
                                 for {
                                   - <- currentConcurrent.update(_ + 1)
                                   c <- currentConcurrent.get
                                   _ <- maxConcurrent.update(m => if(c > m) c else { maxPar.set(m).runNow ; m})
                                   m <- maxConcurrent.get
                                   x <- Ok { nodeJson(x) }
                                   _ <- currentConcurrent.update(_ - 1)
                                 } yield x
                             } )
      } yield service
    }

  }

  //start server on a free port
  @silent // deprecation warning
  val serverR =
    (for {
      count  <- NodeDataset.ioCountService
      httpApp = Router("/datasources" -> NodeDataset.service, "/datasources/parallel" -> count).orNotFound
      server = BlazeServerBuilder[IO].withExecutionContext(blockingExecutionContext).bindAny()
        .withConnectorPoolSize(1) //to make the server slow and validate that it still works
        .withHttpApp(httpApp)
    } yield {
      server.resource
    }).unsafeRunSync()



  override def afterAll(): Unit = {
  }

  val actor = EventActor("Test-actor")
  def modId = ModificationId("test-id-@" + System.currentTimeMillis)

  val interpolation = new InterpolatedValueCompilerImpl
  val fetch = new GetDataset(interpolation)

  val parameterRepo = new RoParameterRepository() {
    def getAllGlobalParameters() = Seq().succeed
    def getAllOverridable() = Seq().succeed
    def getGlobalParameter(parameterName: String) = None.succeed
  }

  class TestNodeRepoInfo(initNodeInfo: Map[NodeId, NodeInfo]) extends WoNodeRepository with NodeInfoService {

    private[this] var nodes = initNodeInfo

    //used for test
    //number of time each node is updated
    val updates = scala.collection.mutable.Map[NodeId, Int]()
    val semaphore = ZioRuntime.unsafeRun(Semaphore.make(1))

    // WoNodeRepository methods
    override def updateNode(node: Node, modId: ModificationId, actor: EventActor, reason: Option[String]) = {
      semaphore.withPermit(for {
        existing <- nodes.get(node.id).notOptional(s"Missing node with key ${node.id.value}")
        _        <- IOResult.effect {
                      this.updates += (node.id -> (1 + updates.getOrElse(node.id, 0) ) )
                      this.nodes = (nodes + (node.id -> existing.copy(node = node) ) )
                    }
      } yield {
        node
      })
    }


    // NodeInfoService
    def getAll() = synchronized(Full(nodes))
    def getNumberOfManagedNodes: Int = nodes.size - 1
    def getAllNodes()                         = throw new IllegalAccessException("Thou shall not used that method here")
    def getAllSystemNodeIds()                 = throw new IllegalAccessException("Thou shall not used that method here")
    def getDeletedNodeInfoPure(nodeId: NodeId)    = throw new IllegalAccessException("Thou shall not used that method here")
    def getDeletedNodeInfos()                 = throw new IllegalAccessException("Thou shall not used that method here")
    def getLDAPNodeInfo(nodeIds: Set[NodeId], predicates: Seq[NodeInfoMatcher], composition: CriterionComposition) = throw new IllegalAccessException("Thou shall not used that method here")
    def getNode(nodeId: NodeId)               = throw new IllegalAccessException("Thou shall not used that method here")
    def getNodeInfo(nodeId: NodeId)           = throw new IllegalAccessException("Thou shall not used that method here")
    def getNodeInfoPure(nodeId: NodeId)       = throw new IllegalAccessException("Thou shall not used that method here")
    def getPendingNodeInfoPure(nodeId: NodeId)= throw new IllegalAccessException("Thou shall not used that method here")
    def getPendingNodeInfos()                 = throw new IllegalAccessException("Thou shall not used that method here")

    override def createNode(node: Node, modId: ModificationId, actor: EventActor, reason: Option[String]): IOResult[Node] = ???

    override def deleteNode(node: Node, modId: ModificationId, actor: EventActor, reason: Option[String]): IOResult[Node] = ???

    def updateNodeKeyInfo(nodeId: NodeId, agentKey: Option[SecurityToken], agentKeyStatus: Option[KeyStatus], modId: ModificationId, actor:EventActor, reason:Option[String])                   = throw new IllegalAccessException("Thou shall not used that method here")
  }

  val root = NodeConfigData.root
  val n1 = {
    val n = NodeConfigData.node1.node
    NodeConfigData.node1.copy(node = n.copy(properties = DataSource.nodeProperty("get-that", "book".toConfigValue) :: Nil ))
  }

  val httpDatasourceTemplate = DataSourceType.HTTP(
      "CHANGE MY URL"
    , Map()
    , HttpMethod.GET
    , Map()
    , true
    , "CHANGE MY PATH"
    , DataSourceType.HTTP.defaultMaxParallelRequest
    , HttpRequestMode.OneRequestByNode
    , 30.second
    , MissingNodeBehavior.Delete
  )
  val datasourceTemplate = DataSource(
        DataSourceId("test-my-datasource")
      , DataSourceName("test-my-datasource")
      , httpDatasourceTemplate
      , DataSourceRunParameters(
            Scheduled(300.seconds)
          , true
          , true
        )
      , "a test datasource to test datasources"
      , true
      , 5.minutes
    )
  // create a copy of template, updating some properties
  def NewDataSource(
      name     : String
    , url      : String              = httpDatasourceTemplate.url
    , path     : String              = httpDatasourceTemplate.path
    , schedule : DataSourceSchedule  = datasourceTemplate.runParam.schedule
    , method   : HttpMethod          = httpDatasourceTemplate.httpMethod
    , params   : Map[String, String] = httpDatasourceTemplate.params
    , headers  : Map[String, String] = httpDatasourceTemplate.headers
    , onMissing: MissingNodeBehavior = httpDatasourceTemplate.missingNodeBehavior
    , maxPar   : Int                 = httpDatasourceTemplate.maxParallelRequest
  ) = {
    val http = httpDatasourceTemplate.copy(url = url, path = path, httpMethod = method, params = params, headers = headers
      , missingNodeBehavior = onMissing, maxParallelRequest = maxPar)
    val run  = datasourceTemplate.runParam.copy(schedule = schedule)
    datasourceTemplate.copy(id = DataSourceId(name), sourceType = http, runParam = run)

  }

  val noPostHook = (nodeIds: Set[NodeId], cause: UpdateCause) => UIO.unit

  val alwaysEnforce = GlobalPolicyMode(PolicyMode.Enforce, PolicyModeOverrides.Always)

  val realClock = ZioRuntime.environment

  object MyDatasource {
    val infos = new TestNodeRepoInfo(NodeConfigData.allNodesInfo)
    val http = new HttpQueryDataSourceService(
        infos
      , parameterRepo
      , infos
      , interpolation
      , noPostHook
      , () => alwaysEnforce.succeed
      , realClock // this one need a real clock to be able to do the requests
    )
    val uuidGen = new StringUuidGeneratorImpl()
  }

  object Enabled extends PluginEnableImpl

  import org.specs2.specification.core.Fragments
  def withResource[A](r: Resource[IO, A])(fs: A => Fragments): Fragments =
    r match {
      case Resource.Allocate(alloc) =>
        alloc
          .map {
            case (a, release) =>
              fs(a).append(step(release(ExitCase.Completed).unsafeRunSync()))
          }
          .unsafeRunSync()
      case Resource.Bind(r, f) =>
        withResource(r)(a => withResource(f(a))(fs))
      case Resource.Suspend(r) =>
        withResource(r.unsafeRunSync() /* ouch */ )(fs)
    }

  withResource(serverR) { server =>

  val REST_SERVER_URL = s"http://${server.address.getHostString}:${server.address.getPort}/datasources"

  def nodeUpdatedMatcher(nodeIds: Set[NodeId]): EqualityMatcher[Set[NodeUpdateResult]] = {
    ===(nodeIds.map(n => NodeUpdateResult.Updated(n)))
  }

  implicit class QueueFailIfNonEmpty[A](queue: Queue[A]) {
    def failIfNonEmpty = queue.poll.map(_.map(_ => ().fail))
  }


  sequential

  "Array validation with [*]" >> {
    Fragment.foreach(0 until testArray.size) { i =>
      s"for case: ${testArray(i)._1} -> ${testArray(i)._2}" >> {
        val datasource = NewDataSource(
            "test-http-service"
          , url  = s"${REST_SERVER_URL}/testarray/$i"
          , path = "$.[*]"
        )

        val infos = new TestNodeRepoInfo(NodeConfigData.allNodesInfo)
        val http = new HttpQueryDataSourceService(infos, parameterRepo, infos, interpolation, noPostHook, () => alwaysEnforce.succeed, realClock)
        val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet
        infos.updates.clear()
        val res = http.queryAll(datasource, UpdateCause(modId, actor, None))

        res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
          infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
        ) and (
          infos.getAll().flatMap( m => m(root.id).properties.find( _.name == "test-http-service") ) mustFullEq(
              NodeProperty.apply("test-http-service", testArray(i)._2.forceParse, None, Some(DataSource.providerName))
          )
        )
      }
    }
  }
  "Array validation with [:1]" >> {
    Fragment.foreach(0 until testArray.size) { i =>
      s"for case: ${testArray(i)._1} -> ${testArray(i)._3}" >> {
        val datasource = NewDataSource(
            "test-http-service"
          , url  = s"${REST_SERVER_URL}/testarray/$i"
          , path = "$.[:1]"
        )

        val infos = new TestNodeRepoInfo(NodeConfigData.allNodesInfo)
        val http = new HttpQueryDataSourceService(infos, parameterRepo, infos, interpolation, noPostHook, () => alwaysEnforce.succeed, realClock)
        val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet
        infos.updates.clear()
        val res = http.queryAll(datasource, UpdateCause(modId, actor, None))

        res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
          infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
        ) and (
          infos.getAll().flatMap( m => m(root.id).properties.find( _.name == "test-http-service") ) mustFullEq(
              NodeProperty.apply("test-http-service", testArray(i)._3.forceParse, None, Some(DataSource.providerName))
          )
        )
      }
    }
  }

  "Update on datasource" should {
    val datasource = NewDataSource(
        name = "test-scheduler"
      , url  = s"${REST_SERVER_URL}/$${rudder.node.id}"
      , path = "$.hostname"
      , schedule = Scheduled(5.minute)
    )
    val action = (c: UpdateCause) => {
      // here we need to give him the default scheduler, not the test one,
      // to actually have the fetch logic done
      IOResult.effect(MyDatasource.http.queryAll(datasource, c).either.runNow match {
        case Right(_)  => //nothing
        case Left(err) => logger.error(s"oh no! Got a $err")
      })
    }

    // test clock needs explicit await to works, so we add them with a queue offer/take
    val testAction = (q: Queue[Unit]) => (c: UpdateCause) => action(c) *> q.offer(()).unit

    "does nothing if scheduler is disabled" in {
      val (total_0, total_1d) : (Int,Int) = makeTestClock.use { testClock =>
        val queue = Queue.unbounded[Unit].runNow

        val dss = new DataSourceScheduler(
            datasource.copy(enabled = false)
          , testClock
          , Enabled
          , () => ModificationId(MyDatasource.uuidGen.newUuid)
          , testAction(queue)
       )

        //reset counter
        NodeDataset.reset()
        // before start, nothing is done
        for {
          ce_0    <- NodeDataset.counterError.get
          cs_0    <- NodeDataset.counterSuccess.get
          total_0 =  ce_0 + cs_0
          _       <- dss.restartScheduleTask()
                     //then, event after days, nothing is done
          _       <- testClock.get[TestClock.Service].adjust(1 day)
          ce_1d   <- NodeDataset.counterError.get
          cs_1d   <- NodeDataset.counterSuccess.get
        } yield {
          (total_0, ce_1d + cs_1d)
        }
      }.runTimeout(1 minute)

      (total_0, total_1d) must beEqualTo(
      (0      , 0       ))
    }

    "allows interactive updates with disabled scheduler (but not data source)" in {
      val (total_0, total_1d, total_postGen) = makeTestClock.use { testClock =>
        val queue = Queue.unbounded[Unit].runNow

        val dss = new DataSourceScheduler(
            datasource.copy(runParam = datasource.runParam.copy(schedule = NoSchedule(1.second)))
          , testClock
          , Enabled
          , () => ModificationId(MyDatasource.uuidGen.newUuid)
          , testAction(queue)
        )

       val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
       logger.setLevel(Level.TRACE)
        //reset counter
        NodeDataset.reset()
        // before start, nothing is done
        for {
          _       <- queue.failIfNonEmpty
          ce_0    <- NodeDataset.counterError.get
          cs_0    <- NodeDataset.counterSuccess.get
          total_0 =  ce_0 + cs_0
          _       <- dss.restartScheduleTask()
                     //then, event after days, nothing is done
          _       <- testClock.get[TestClock.Service].adjust(1 day)
          _       <- queue.failIfNonEmpty
          ce_1    <- NodeDataset.counterError.get
          cs_1    <- NodeDataset.counterSuccess.get
          total_1 =  ce_1 + cs_1
          //but asking for a direct update do the queries immediatly - task need at least 1ms to notice it should run
          _       <- dss.doActionAndSchedule(action(UpdateCause(ModificationId("plop"), RudderEventActor, None)))
          _       <- testClock.get[TestClock.Service].adjust(1 millis)
          _       <- queue.failIfNonEmpty
          ce_2    <- NodeDataset.counterError.get
          cs_2    <- NodeDataset.counterSuccess.get
          total_2 =  ce_2 + cs_2
        } yield (total_0, total_1, total_2)
      }.runTimeout(1 minute)



       val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
       logger.setLevel(Level.OFF)
      (total_0, total_1d, total_postGen                   ) must beEqualTo(
      (0      , 0       , NodeConfigData.allNodesInfo.size))

    }

    "create a new schedule from data source information" in {
      val (total_0, total_0s, total_1s, total_4m, total_5m, total_8m) = makeTestClock.use { testClock =>
        // testClock need to know what fibers are doing something, and it' seems to be done easily with a queue.
        val queue = Queue.unbounded[Unit].runNow

        val dss = new DataSourceScheduler(
            datasource.copy(name = DataSourceName("create a new schedule"))
          , testClock
          , Enabled
          , () => ModificationId(MyDatasource.uuidGen.newUuid)
          , testAction(queue)
        )

        //reset counter
        NodeDataset.reset()
        for {
          // before start, nothing is done
          _        <- queue.failIfNonEmpty
          ce_0     <- NodeDataset.counterError.get
          cs_0     <- NodeDataset.counterSuccess.get
          total_0  =  ce_0 + cs_0
          _        <- dss.restartScheduleTask()
          //then just after, we have the first exec - it still need at least a ms to tick
          //still nothing here
          _        <- testClock.get[TestClock.Service].setTime(1 millis)
          //here we have results
          _        <- queue.take
          ce_0s    <- NodeDataset.counterError.get
          cs_0s    <- NodeDataset.counterSuccess.get
          total_0s =  ce_0s + cs_0s
          //then nothing happens before 5 minutes
          _        <- testClock.get[TestClock.Service].setTime(1 second)
          _        <- queue.failIfNonEmpty
          ce_1s    <- NodeDataset.counterError.get
          cs_1s    <- NodeDataset.counterSuccess.get
          total_1s =  ce_1s + cs_1s
          _        <- testClock.get[TestClock.Service].setTime(4 minutes)
          _        <- queue.failIfNonEmpty
          ce_4m    <- NodeDataset.counterError.get
          cs_4m    <- NodeDataset.counterSuccess.get
          total_4m =  ce_4m + cs_4m
          //then all the nodes gets their info
          _        <- testClock.get[TestClock.Service].setTime(5 minutes)
          _        <- queue.take
          ce_5m    <- NodeDataset.counterError.get
          cs_5m    <- NodeDataset.counterSuccess.get
          total_5m =  ce_5m + cs_5m
          //then nothing happen anymore
          _        <- testClock.get[TestClock.Service].setTime(8 minutes)
          _        <- queue.failIfNonEmpty
          ce_8m    <- NodeDataset.counterError.get
          cs_8m    <- NodeDataset.counterSuccess.get
          total_8m =  ce_8m + cs_8m
        } yield (total_0, total_0s, total_1s, total_4m, total_5m, total_8m)
      }.runTimeout(1 minute)

      val size = NodeConfigData.allNodesInfo.size
      (total_0, total_0s, total_1s, total_4m, total_5m, total_8m) must beEqualTo(
      (0      , size    , size    , size    ,  size*2 , size*2  ))
    }

  }
  "querying a lot of nodes" should {

    // test on 100 nodes. With 30s timeout, even on small hardware it will be ok.
    val nodes = (NodeConfigData.root :: List.fill(100)(NodeConfigData.node1).zipWithIndex.map { case (n,i) =>
      val name = "node"+i
      n.copy(node = n.node.copy(id = NodeId(name), name = name), hostname = name+".localhost")
    }).map( n => (n.id, n)).toMap
    val infos = new TestNodeRepoInfo(nodes)
    val http = new HttpQueryDataSourceService(
        infos
      , parameterRepo
      , infos
      , interpolation
      , noPostHook
      , () => alwaysEnforce.succeed
      , realClock
    )

    def maxParDataSource(n: Int) = NewDataSource(
        "test-lot-of-nodes-max-parallel-GET"
      , url  = s"${REST_SERVER_URL}/parallel/$${rudder.node.id}"
      , path = "$.hostname"
      , headers = Map( "nodeId" -> "${rudder.node.id}" )
      , maxPar = n
    )

    "comply with the limit of parallel queries" in {
      // Max paraallel is the minimum of 2 and the available thread on the machine
      // So tests don't fait if the build machine has one core
      val MAX_PARALLEL = Math.min(2, java.lang.Runtime.getRuntime.availableProcessors)
      val ds = maxParDataSource(MAX_PARALLEL)
      val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet
      //all node updated one time
      infos.updates.clear()
      NodeDataset.reset()
      val res = http.queryAll(ds, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        NodeDataset.counterError.get.runNow must_===  0
      ) and (NodeDataset.maxPar.get.runNow must_===  MAX_PARALLEL)
    }

    "work even if nodes don't reply at same speed with GET" in {
      val ds = NewDataSource(
          "test-lot-of-nodes-GET"
        , url  = s"${REST_SERVER_URL}/delay/$${rudder.node.id}"
        , path = "$.hostname"
        , headers = Map( "nodeId" -> "${rudder.node.id}" )
      )
      val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet
      //all node updated one time
      infos.updates.clear()
      NodeDataset.reset()
      val res = http.queryAll(ds, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
      ) and (NodeDataset.counterError.get.runNow must_=== 0) and (NodeDataset.counterSuccess.get.runNow must_=== nodeIds.size)
    }

    "work even if nodes don't reply at same speed with POST" in {
      val ds = NewDataSource(
          "test-lot-of-nodes-POST"
        , url  = s"${REST_SERVER_URL}/delay"
        , path = "$.hostname"
        , method = HttpMethod.POST
        , params = Map( "nodeId" -> "${rudder.node.id}" )
        , headers = Map( "nodeId" -> "${rudder.node.id}" )
      )
      val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet
      //all node updated one time
      infos.updates.clear()
      NodeDataset.reset()
      val res = http.queryAll(ds, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
      ) and (NodeDataset.counterError.get.runNow must_=== 0) and (NodeDataset.counterSuccess.get.runNow must_=== nodeIds.size)
    }

    "work for odd node even if even nodes fail" in {
      //but that's chatty, disable datasources logger for that one
      val logger = LoggerFactory.getLogger("datasources").asInstanceOf[ch.qos.logback.classic.Logger]
      logger.setLevel(Level.OFF)

      val ds = NewDataSource(
          "test-even-fail"
        , url  = s"${REST_SERVER_URL}/faileven/$${rudder.node.id}"
        , path = "$.hostname"
      )
      val nodeRegEx = "node(.*)".r
      val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet.filter(n => n.value match {
        case "root"       => true
        case nodeRegEx(i) => i.toInt % 2 == 1
      })
      //all node updated one time
      infos.updates.clear()

      val res = http.queryAll(ds, UpdateCause(modId, actor, None)).either.runNow

      //set back level
      logger.setLevel(Level.WARN)

      res must beLeft and (
        infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
      )

    }
  }


  "Getting a node" should {
    val datasource = httpDatasourceTemplate.copy(
        url  = s"${REST_SERVER_URL}/single_$${rudder.node.id}"
      , path = "$.store.${node.properties[get-that]}[:1]"
    )
    "get the node" in  {
      val res = fetch.getNode(DataSourceId("test-get-one-node"), datasource, n1, root, alwaysEnforce, Set(), 1.second, 5.seconds)


      res.either.runNow must beRight(===(
          Some(DataSource.nodeProperty("test-get-one-node",  ConfigFactory.parseString("""{ "x" :  [
              # String: 1
              {
                  # String: 2
                  "author" : "Nigel Rees",
                  # String: 2
                  "category" : "reference",
                  # String: 2
                  "price" : 8.95,
                  # String: 2
                  "title" : "Sayings of the Century"
              },
              # String: 1
              {
                  # String: 2
                  "author" : "Evelyn Waugh",
                  # String: 2
                  "category" : "fiction",
                  # String: 2
                  "price" : 12.99,
                  # String: 2
                  "title" : "Sword of Honour"
              },
              # String: 1
              {
                  # String: 2
                  "author" : "Herman Melville",
                  # String: 2
                  "category" : "fiction",
                  # String: 2
                  "isbn" : "0-553-21311-3",
                  # String: 2
                  "price" : 8.99,
                  # String: 2
                  "title" : "Moby Dick"
              },
              # String: 1
              {
                  # String: 2
                  "author" : "J. R. R. Tolkien",
                  # String: 2
                  "category" : "fiction",
                  # String: 2
                  "isbn" : "0-395-19395-8",
                  # String: 2
                  "price" : 22.99,
                  # String: 2
                  "title" : "The Lord of the Rings"
              }
          ]}""").getValue("x"))):Option[NodeProperty]))
    }
  }

  "The full http service" should {
    val datasource = NewDataSource(
        "test-http-service"
      , url  = s"${REST_SERVER_URL}/single_node1"
      , path = "$.store.book"
    )

    val infos = new TestNodeRepoInfo(NodeConfigData.allNodesInfo)
    val http = new HttpQueryDataSourceService(infos, parameterRepo, infos, interpolation, noPostHook, () => alwaysEnforce.succeed, realClock)


    "correctly update all nodes" in {
      //all node updated one time
      val nodeIds = infos.getAll().openOrThrowException("test shall not throw").keySet
      infos.updates.clear()
      val res = http.queryAll(datasource, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(nodeUpdatedMatcher(nodeIds)) and (
        infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
      )
    }

    "correctly update one node" in {
      //all node updated one time
      val d2 = NewDataSource(
          "test-http-service"
        , url  = s"${REST_SERVER_URL}/single_node2"
        , path = "$.foo"
      )
      infos.updates.clear()
      val res = http.queryOne(d2, root.id, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(===(NodeUpdateResult.Updated(root.id):NodeUpdateResult)) and (
        infos.getAll().flatMap( m => m(root.id).properties.find( _.name == "test-http-service") ) mustFullEq(
            NodeProperty.apply("test-http-service", "bar".toConfigValue, None, Some(DataSource.providerName))
        )
      )
    }

    "understand ${node.properties[datasources-injected][short-hostname]} in API" in {
      //all node updated one time
      val d2 = NewDataSource(
          "test-http-service"
        , url  = s"""${REST_SERVER_URL}/$${node.properties[datasources-injected][short-hostname]}"""
        , path = "$.hostname"
      )
      infos.updates.clear()
      // root hostname is server.rudder.local, so short hostname is "server"
      val res = http.queryOne(d2, root.id, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(===(NodeUpdateResult.Updated(root.id):NodeUpdateResult)) and (
        infos.getAll().flatMap( m => m(root.id).properties.find( _.name == "test-http-service") ) mustFullEq(
            NodeProperty.apply("test-http-service", "server.rudder.local".toConfigValue, None, Some(DataSource.providerName))
        )
      )
    }
    "understand ${node.properties[datasources-injected][short-hostname]} in JSON path" in {
      //all node updated one time
      val d2 = NewDataSource(
          "test-http-service"
        , url  = s"""${REST_SERVER_URL}/hostnameJson"""
        , path = "$.['nodes']['${node.properties[datasources-injected][short-hostname]}']"
      )
      infos.updates.clear()
      // root hostname is server.rudder.local, so short hostname is "server"
      val res = http.queryOne(d2, root.id, UpdateCause(modId, actor, None))

      res.either.runNow must beRight(===(NodeUpdateResult.Updated(root.id):NodeUpdateResult)) and (
        infos.getAll().flatMap( m => m(root.id).properties.find( _.name == "test-http-service") ) mustFullEq(
            NodeProperty.apply("test-http-service", """{ "environment": "DEV_INFRA", "mergeBucket" : { "test_merge2" : "aPotentialMergeValue1" } }""".forceParse, None, Some(DataSource.providerName))
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
     *   PROPS.keySet() == infos.getAll().keySet()
     */
    type PROPS = Map[NodeId, Option[ConfigValue]]
    def test404prop(propName: String, initValue: Option[String], onMissing: MissingNodeBehavior, expectMod: Boolean)(finalStateCond: PROPS => MatchResult[PROPS]): MatchResult[Any] = {
      val infos = new TestNodeRepoInfo(NodeConfigData.allNodesInfo)
      val http = new HttpQueryDataSourceService(infos, parameterRepo, infos, interpolation, noPostHook, () => alwaysEnforce.succeed, realClock)
      val datasource = NewDataSource(propName, url  = s"${REST_SERVER_URL}/404", path = "$.some.prop", onMissing = onMissing)

      val nodes = infos.getAll().openOrThrowException("test shall not throw")
      //set a value for all propName if asked
      val modId = ModificationId("set-test-404")
      nodes.values.foreach { node =>
        import com.normation.box.EitherToBox
        val newProps = CompareProperties.updateProperties(node.node.properties, Some(List(NodeProperty.apply(propName, initValue.getOrElse("").toConfigValue, None, None)))).toBox.openOrThrowException("test must be able to set prop")
        val up = node.node.copy(properties = newProps)
        infos.updateNode(up, modId, actor, None).runNow
      }

      infos.updates.clear()
      val res = http.queryAll(datasource, UpdateCause(modId, actor, None))

      val nodeIds = nodes.keySet
      val matcher = ===(nodeIds.map(n => if(expectMod) NodeUpdateResult.Updated(n) else NodeUpdateResult.Unchanged(n)):Set[NodeUpdateResult])

      res.either.runNow must beRight(matcher) and (
        if(expectMod) {
          infos.updates.toMap must havePairs( nodeIds.map(x => (x, 1) ).toSeq:_* )
        } else {
          true must_=== true
        }
      ) and ({
        //none should have "test-404"
        val props = infos.getAll().openOrThrowException("test shall not throw").map { case(id, n) => (id, n.node.properties.find( _.name == propName ).map( _.value)) }
        finalStateCond(props)
      })
    }

    "have a working 'delete property' option" in {
      test404prop(propName = "test-404", initValue = Some("test-404"), onMissing = MissingNodeBehavior.Delete, expectMod = true) { props =>
        props must havePairs( props.keySet.map(x => (x, None) ).toSeq:_* )
      }
    }
    "have a working 'default value property' option" in {
      test404prop(propName = "test-404", initValue = Some("test-404"), onMissing = MissingNodeBehavior.DefaultValue("foo".toConfigValue), expectMod = true) { props =>
        props must havePairs( props.keySet.map(x => (x, Some("foo".toConfigValue)) ).toSeq:_* )
      }
    }
    "have a working 'don't touch - not exists' option" in {
      test404prop(propName = "test-404", initValue = None, onMissing = MissingNodeBehavior.NoChange, expectMod = false) { props =>
        props must havePairs( props.keySet.map(x => (x, None) ).toSeq:_* )
      }
    }
    "have a working 'don't touch - exists' option" in {
      test404prop(propName = "test-404", initValue = Some("test-404"), onMissing = MissingNodeBehavior.NoChange, expectMod = false) { props =>
        props must havePairs( props.keySet.map(x => (x, Some("test-404".toConfigValue)) ).toSeq:_* )
      }
    }
  }
}


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
    ("""[ 1 ]"""                                 , """1"""                                      , """1"""                                 )
  , ("""[ "a" ]"""                               , """"a""""                                    , """"a""""                               )
  , ("""[ { "a" : 1 }]"""                        , """{"a":1}"""                                , """{"a":1}"""                           )
  , ("""[ 1, 2 ]"""                              , """[1,2]"""                                  , """1"""                                 ) // array of size 1 are lifted
  , ("""[ "a", "b" ]"""                          , """["a","b"]"""                              , """"a""""                               ) // array of size 1 are lifted
  , ("""[ { "a": 1 }, { "b": 2} ]"""             , """[{"a":1}, {"b":2}]"""                     , """{"a":1}"""                           ) // array of size 1 are lifted
  , ("""[[]]"""                                  , """[]"""                                     , """[]"""                                )
  , ("""[ [ 1 ] ]"""                             , """[1]"""                                    , """[1]"""                               )
  , ("""[ [ { "a": 1 } ] ]"""                    , """[{"a":1}]"""                              , """[{"a":1}]"""                         )
  , ("""[ [ 1, 2 ] ]"""                          , """[1,2]"""                                  , """[1,2]"""                             )
  , ("""[ [ { "a": 1 }, {"b": 2 } ] ]"""         , """[{"a":1}, {"b":2}]"""                     , """[{"a":1}, {"b":2}]"""                )
  , ("""[[1],[2]]"""                             , """[[1],[2]]"""                              , """[1]"""                               ) // array of size 1 are lifted
  , ("""[ {"a": []} ]"""                         , """{"a": []}"""                              , """{"a": []}"""                         )
  , ("""[ {"a": [{"v": 1}]} ]"""                 , """{"a": [{"v": 1}]}"""                      , """{"a": [{"v": 1}]}"""                 )
  , ("""[ {"a": [{"v": 1}, {"v": 2}]} ]"""       , """{"a": [{"v": 1}, {"v": 2}]}"""            , """{"a": [{"v": 1}, {"v": 2}]}"""       )
  , ("""[ {"a": [{"v": 1}]}, {"b":[{"v":2}]} ]""", """[ {"a": [{"v": 1}]}, {"b":[{"v":2}]} ]""" , """{"a": [{"v": 1}]}"""                 ) // array of size 1 are lifted
  , ("""[ {"a": [{"v": 1}, {"v":2}]} ]"""        , """{"a": [{"v": 1}, {"v":2}]}"""             , """{"a": [{"v": 1}, {"v":2}]}"""        )
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

  //expample of what a CMDB could return for a node.
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
