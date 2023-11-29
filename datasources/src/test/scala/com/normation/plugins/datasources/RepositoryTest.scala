package com.normation.plugins.datasources

import cats.effect
import cats.syntax.apply._
import com.normation.errors.Inconsistency
import com.normation.rudder.db.DBCommon
import com.normation.zio.UnsafeRun
import doobie.Transactor
import doobie.implicits._
import doobie.specs2.analysisspec.IOChecker
import doobie.util.query.Query0
import doobie.util.update.Update0
import io.scalaland.chimney.syntax._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import zio._
import zio.interop.catz._

@RunWith(classOf[JUnitRunner])
class RepositoryTest extends Specification with DBCommon with IOChecker {
  sequential

  override def transactor: Transactor[effect.IO] = doobie.xaio

  override def initDb(): Unit = {
    // get the resource DDL definition which consist of an function definition and a function call
    val is      = this.getClass().getClassLoader().getResourceAsStream("datasources-schema.sql")
    val sqlText = scala.io.Source
      .fromInputStream(is)
      .getLines()
      .toSeq
      .filterNot { line =>
        val s = line.trim(); s.isEmpty || s.startsWith("/*") || s.startsWith("*")
      }
      .map(s => {
        s
          // using toLowerCase is safer, it will always replace create table by a temp one,
          // but it also mean that we will not know when we won't be strict with ourselves
          .toLowerCase
          .replaceAll("create table", "create temp table")
          .replaceAll("create sequence", "create temp sequence")
          .replaceAll("alter database rudder", "alter database test")
      })
      .mkString("\n")
    is.close()

    // function declaration is an 'update' operation and function call is a 'select' one
    val (update, select) = sqlText.splitAt(sqlText.indexOf("select create_datasources()"))

    doobie.transactRunEither(xa => (Update0(update, None).run <* Query0(select).option).transact(xa)) match {
      case Right(_) => ()
      case Left(ex) => throw ex
    }
  }

  lazy val repo = new DataSourceJdbcRepository(doobie)

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
  val fakeDataSource         = DataSource(
    DataSourceId("test-my-datasource"),
    DataSourceName("test-my-datasource"),
    httpDatasourceTemplate,
    DataSourceRunParameters(
      DataSourceSchedule.Scheduled(300.seconds),
      true,
      true
    ),
    "a test datasource to test datasources",
    true,
    5.minutes
  )
  val compactExpectedJson    =
    """{"name":"test-my-datasource","id":"test-my-datasource","description":"a test datasource to test datasources","type":{"name":"HTTP","parameters":{"url":"CHANGE MY URL","headers":[],"params":[],"path":"CHANGE MY PATH","checkSsl":true,"maxParallelReq":10,"requestTimeout":30,"requestMethod":"GET","requestMode":{"name":"byNode"},"onMissing":{"name":"delete"}}},"runParameters":{"onGeneration":true,"onNewNode":true,"schedule":{"type":"scheduled","duration":300}},"updateTimeout":300,"enabled":true}"""

  "DataSourceRepository" should {

    "type-check queries" in {
      check(DataSourceJdbcRepository.insertDataSourceSQL(fakeDataSource.id, fakeDataSource.transformInto[FullDataSource]))
      check(DataSourceJdbcRepository.updateDataSourceSQL(fakeDataSource.id, fakeDataSource.transformInto[FullDataSource]))
      check(DataSourceJdbcRepository.getAllSQL)
      check(DataSourceJdbcRepository.getIdsSQL)
      check(DataSourceJdbcRepository.getSQL(fakeDataSource.id))
    }

    "insert datasource" in {
      repo.save(fakeDataSource).runNow must beEqualTo(fakeDataSource)
    }

    "get inserted datasource" in {
      repo.get(fakeDataSource.id).runNow must beSome(fakeDataSource)
    }

    "have a compact json properties stored" in {
      doobie.transactRunEither(
        sql"""select properties from datasources where id=${fakeDataSource.id}""".query[String].unique.transact(_)
      ) must beRight(compactExpectedJson)
    }

    "update datasource" in {
      val updated = fakeDataSource.copy(
        name = DataSourceName("test-my-datasource-updated"),
        description = "updated description"
      )
      repo.save(updated).runNow must beEqualTo(updated)
    }

    "not save datasource with a reserved id" in {
      val reservedId  = DataSource.reservedIds.keys.head
      val reservedMsg = DataSource.reservedIds(reservedId)
      repo.save(fakeDataSource.copy(id = reservedId)).either.runNow must beLeft(
        Inconsistency(s"You can't use the reserved data sources id '${reservedId.value}': ${reservedMsg}")
      )
    }
  }
}
