package com.normation.plugins.changevalidation

import better.files.File
import better.files.Resource
import cats.syntax.apply.*
import com.normation.eventlog.EventActor
import com.normation.rudder.db.DBCommon
import com.normation.rudder.rest.AuthorizationApiMapping
import com.normation.rudder.rest.RoleApiMapping
import com.normation.rudder.users.FileUserDetailListProvider
import com.normation.rudder.users.UserFile
import com.normation.zio.UnsafeRun
import doobie.Transactor
import doobie.implicits.*
import doobie.specs2.analysisspec.IOChecker
import doobie.util.query.Query0
import doobie.util.update.Update0
import java.nio.charset.StandardCharsets
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import zio.interop.catz.*

@RunWith(classOf[JUnitRunner])
class ValidatedUserJdbcRepositoryTest extends Specification with DBCommon with IOChecker {
  sequential

  override def transactor: Transactor[cats.effect.IO] = doobie.xaio

  override def initDb(): Unit = {
    // get the resource DDL definition which consist of an function definition and a function call
    val is      = this.getClass().getClassLoader().getResourceAsStream("change-validation-schema.sql")
    val sqlText = scala.io.Source
      .fromInputStream(is)
      .getLines()
      .toSeq
      .filterNot { line =>
        val s = line.trim(); s.isEmpty || s.startsWith("/*") || s.startsWith("*") || s.startsWith("--")
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
    val (update, select) = sqlText.splitAt(sqlText.indexOf("select create_change_validation_plugin_tables()"))

    doobie.transactRunEither(xa => (Update0(update, None).run *> Query0(select).option).transact(xa)) match {
      case Right(_) => ()
      case Left(ex) => throw ex
    }
  }

  val actor = EventActor("test-db-user")

  val fileUserDetailListProvider = {
    val getUsersInputStream = Resource
      .url("test-users.xml")
      .map(url => () => IOUtils.toInputStream(File(url).contentAsString(), StandardCharsets.UTF_8))
      .getOrElse(() => IOUtils.toInputStream("non-xml-content", StandardCharsets.UTF_8))

    val usersFile      = {
      UserFile("test-users.xml", getUsersInputStream)
    }
    val roleApiMapping = new RoleApiMapping(AuthorizationApiMapping.Core)

    val res = new FileUserDetailListProvider(roleApiMapping, usersFile)
    res.reload()
    res
  }

  lazy val roValidatedUserJdbcRepository = new RoValidatedUserJdbcRepository(doobie, fileUserDetailListProvider)
  lazy val woValidatedUserJdbcRepository = new WoValidatedUserJdbcRepository(doobie, roValidatedUserJdbcRepository)

  "ValidatedUserJdbcRepository" should {

    "type-check queries" in {
      check(ValidatedUserJdbcRepositorySQL.getSQL(actor))
      check(ValidatedUserJdbcRepositorySQL.getValidatedUsersSQL)
      check(ValidatedUserJdbcRepositorySQL.createUserSQL(actor))
      check(ValidatedUserJdbcRepositorySQL.deleteUserSQL(actor))
    }

    "create a user" in {
      woValidatedUserJdbcRepository.createUser(actor).runNow must beEqualTo(actor)
    }

    "get the user" in {
      roValidatedUserJdbcRepository.get(actor).runNow must beEqualTo(Some(actor))
    }

    "get validated users" in {
      roValidatedUserJdbcRepository.getValidatedUsers().runNow must containTheSameElementsAs(List(actor))
    }

    "get users" in {
      roValidatedUserJdbcRepository.getUsers().runNow must containTheSameElementsAs(
        List(
          WorkflowUsers(actor, isValidated = true, userExists = false),
          WorkflowUsers(EventActor("test-user"), isValidated = false, userExists = true),        // from test-users.xml file
          WorkflowUsers(EventActor("another-test-user"), isValidated = false, userExists = true) // from test-users.xml file
        )
      )
    }

    "save and validate users" in {
      woValidatedUserJdbcRepository.createUser(EventActor("delete-after-save-user")).runNow must beEqualTo(
        EventActor("delete-after-save-user")
      ) // will be deleted because it is not saved
      woValidatedUserJdbcRepository
        .saveWorkflowUsers(
          List(
            actor,
            EventActor("test-user") // will be validated
          )
        )
        .runNow must containTheSameElementsAs(
        List(
          WorkflowUsers(actor, isValidated = true, userExists = false),
          WorkflowUsers(EventActor("test-user"), isValidated = true, userExists = true),
          WorkflowUsers(EventActor("another-test-user"), isValidated = false, userExists = true)
        )
      )

      roValidatedUserJdbcRepository.get(EventActor("delete-after-save-user")).runNow must beEqualTo(None)
    }

    "delete a user" in {
      woValidatedUserJdbcRepository.deleteUser(EventActor("test-user")).runNow must beEqualTo(EventActor("test-user"))
      roValidatedUserJdbcRepository.get(EventActor("test-user")).runNow must beEqualTo(None)
    }

  }
}
