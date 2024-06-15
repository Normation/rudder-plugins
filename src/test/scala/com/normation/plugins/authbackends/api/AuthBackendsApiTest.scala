package com.normation.plugins.authbackends
package api

import better.files.File
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class AuthBackendsApiTest extends ZIOSpecDefault {
  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "authbackends_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val fakeJsonAuthConfiguration = JsonAuthConfiguration(
    declaredProviders = "provider1, provider2",
    computedProviders = Seq("provider1", "provider2"),
    adminConfig = JsonAdminConfig(
      description = "Admin configuration",
      login = ConfigOption("Login description", "loginKey", "loginValue"),
      password = ConfigOption("Password description", "passwordKey", "passwordValue"),
      enabled = true
    ),
    fileConfig = JsonFileConfig(
      providerId = "fileProvider",
      description = "File configuration",
      filePath = "/path/to/file"
    ),
    ldapConfig = JsonLdapConfig(
      providerId = "ldapProvider",
      description = "LDAP configuration",
      ldapUrl = ConfigOption("LDAP URL description", "ldapUrlKey", "ldapUrlValue"),
      bindDn = ConfigOption("Bind DN description", "bindDnKey", "bindDnValue"),
      bindPassword = ConfigOption("Bind password description", "bindPasswordKey", "bindPasswordValue"),
      searchBase = ConfigOption("Search base description", "searchBaseKey", "searchBaseValue"),
      ldapFilter = ConfigOption("LDAP filter description", "ldapFilterKey", "ldapFilterValue")
    )
  )

  val mockAuthBackendsRepository = new AuthBackendsRepository {
    override def getConfigOption(): JsonAuthConfiguration = fakeJsonAuthConfiguration
  }
  val modules                    = List(
    new AuthBackendsApiImpl(
      mockAuthBackendsRepository
    )
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)

  val transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
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
