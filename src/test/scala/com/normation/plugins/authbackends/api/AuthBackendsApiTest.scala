package com.normation.plugins.authbackends
package api

import better.files.File
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import net.liftweb.common.Loggable
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

class AuthBackendsApiTest extends Specification with Loggable with TraitTestApiFromYamlFiles with AfterAll {
  sequential

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "authbackends_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

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

  override def transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def afterAll(): Unit = {
    tmpDir.delete()
  }

  doTest(semanticJson = true)
}
