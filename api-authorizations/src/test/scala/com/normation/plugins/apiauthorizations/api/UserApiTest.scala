package com.normation.plugins.apiauthorizations

import better.files.*
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Rights
import com.normation.rudder.api.*
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.rudder.tenants.TenantAccessGrant
import com.normation.rudder.users.*
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class UserApiTest extends ZIOSpecDefault {

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "authorizations_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  // date used when `Instant.now` is called e.g. when creating a new token
  val fixedDate = Instant.parse("2023-12-12T12:12:12.000Z")

  val accountCreationDate = Instant.parse("2023-10-10T10:10:10.000Z")
  val accounts            = Map(
    ApiAccountId("user1") -> ApiAccount(
      ApiAccountId("user1"),
      ApiAccountKind.System, // so that we have access to the plugin endpoints
      ApiAccountName("user1"),
      AccountToken(Some(ApiTokenHash.fromHashValue("v2:some-hashed-token")), accountCreationDate),
      "number one user",
      isEnabled = true,
      creationDate = accountCreationDate,
      lastAuthenticationDate = None,
      TenantAccessGrant.All
    )
  )

  val mockServices = new MockServices(newToken = "generated-test-token", accounts = accounts)

  val userService: UserService = new UserService {
    // use an user that has access to the api, we do not test authorization checks in this file
    val user1 = new AuthenticatedUser {
      override val account: RudderAccount = RudderAccount.Api(accounts(ApiAccountId("user1")))
      override def checkRights(auth: AuthorizationType) = true
      override def authz:       Rights            = Rights.AnyRights
      override def apiAuthz:    ApiAuthorization  = ApiAuthorization.RW
      override def accessGrant: TenantAccessGrant = TenantAccessGrant.All
      override def actorIp:     Option[String]    = Some("192.168.0.42")
    }
    override val getCurrentUser: Option[AuthenticatedUser] = Some(user1)
  }

  val modules = List(
    new UserApiImpl(
      mockServices.apiAccountRepository,
      mockServices.apiAccountRepository,
      null,
      null,
      mockServices.tokenGenerator,
      restTestSetUp.uuidGen,
      java.time.Clock.fixed(fixedDate, ZoneOffset.UTC)
    )
  )

  val apiVersions: List[ApiVersion] = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, Some(userService))

  val transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def spec: Spec[TestEnvironment & Scope, Any] = {
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
