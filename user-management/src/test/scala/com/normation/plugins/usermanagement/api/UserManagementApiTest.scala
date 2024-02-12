package com.normation.plugins.usermanagement.api

import better.files.File
import better.files.Resource
import bootstrap.liftweb.AuthBackendProvidersManager
import com.normation.plugins.usermanagement.MockServices
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.rudder.users.UserInfo
import com.normation.rudder.users.UserStatus
import java.nio.file.Files
import net.liftweb.common.Loggable
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll
import zio.json.ast.Json

@RunWith(classOf[JUnitRunner])
class UserManagementApiTest extends Specification with TraitTestApiFromYamlFiles with Loggable with AfterAll {
  sequential

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "usermanagement_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

  val testUserFile = Resource
    .url("test-users.xml")
    .map(File(_))
    .map(_.copyTo(tmpDir / "test-users.xml"))
    .getOrElse(throw new Exception("Cannot find test-users.xml in test resources"))

  val mockServices = new MockServices(
    List(
      UserInfo( // user3 not in the file will get empty permissions and authz
        "user3",
        DateTime.parse("2024-02-01T01:01:01Z"),
        UserStatus.Active,
        "manager",
        None,
        None,
        None,
        List.empty,
        Json.Obj()
      ),
      UserInfo(
        "user2",
        DateTime.parse("2024-02-01T01:01:01Z"),
        UserStatus.Active,
        "manager",
        None,
        None,
        None,
        List.empty,
        Json.Obj()
      ),
      UserInfo(
        "user1",
        DateTime.parse("2024-02-01T01:01:01Z"),
        UserStatus.Active,
        "manager",
        None,
        None,
        None,
        List.empty,
        Json.Obj()
      )
    ),
    testUserFile
  )
  val modules      = List(
    new UserManagementApiImpl(
      mockServices.userRepo,
      mockServices.userService,
      new AuthBackendProvidersManager(),
      mockServices.userManagementService
    )
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)

  override def transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def afterAll(): Unit = {
    tmpDir.delete()
  }

  doTest()

}
