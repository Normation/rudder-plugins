package com.normation.plugins.apiauthorizations

import better.files._
import com.normation.rudder.AuthorizationType
import com.normation.rudder.api.ApiAccount
import com.normation.rudder.api.ApiAccountId
import com.normation.rudder.api.ApiAccountKind
import com.normation.rudder.api.ApiAccountName
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.api.ApiToken
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.rudder.users.AuthenticatedUser
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.UserService
import java.nio.file.Files
import net.liftweb.common.Loggable
import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterAll

@RunWith(classOf[JUnitRunner])
class UserApiTest extends Specification with TraitTestApiFromYamlFiles with Loggable with BeforeAfterAll {

  sequential

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "authorizations_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

  // date used when `DateTime.now` is called e.g. when creating a new token
  val fixedDate = DateTime.parse("2023-12-12T12:12:12.000Z")

  val accountCreationDate = DateTime.parse("2023-10-10T10:10:10.000Z")
  val accounts            = Map(
    ApiAccountId("user1") -> ApiAccount(
      ApiAccountId("user1"),
      ApiAccountKind.System, // so that we have access to the plugin endpoints
      ApiAccountName("user1"),
      ApiToken("v2:some-hashed-token"),
      "number one user",
      isEnabled = true,
      creationDate = accountCreationDate,
      tokenGenerationDate = accountCreationDate
    )
  )

  val mockServices = new MockServices(newToken = "generated-test-token", accounts = accounts)

  val userService: UserService = new UserService {
    // use an user that has access to the api, we do not test authorization checks in this file
    val user1          = new AuthenticatedUser {
      val account                              = RudderAccount.Api(accounts(ApiAccountId("user1")))
      def checkRights(auth: AuthorizationType) = true
      def getApiAuthz                          = ApiAuthorization.RW
      def nodePerms                            = NodeSecurityContext.All
    }
    val getCurrentUser = user1
  }

  val modules = List(
    new UserApi(
      mockServices.apiAccountRepository,
      mockServices.apiAccountRepository,
      mockServices.tokenGenerator,
      restTestSetUp.uuidGen
    )
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, Some(userService))

  override def transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def beforeAll(): Unit = {
    // set current time to the fixed date so that we can test dates in yaml files
    DateTimeUtils.setCurrentMillisFixed(fixedDate.getMillis)
  }

  override def afterAll(): Unit = {
    tmpDir.delete()
  }

  doTest(semanticJson = true)

}
