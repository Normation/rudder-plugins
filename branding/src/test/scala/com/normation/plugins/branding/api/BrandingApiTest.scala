package com.normation.plugins.branding

import better.files.*
import com.normation.plugins.branding.api.BrandingApi
import com.normation.plugins.branding.api.BrandingApiService
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.utils.StringUuidGeneratorImpl
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class BrandingApiTest extends ZIOSpecDefault {

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "branding_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val fakeBrandingConf: String = s"""
    {
      "displayBar": true,
      "barColor": {
        "red": 0.0,
        "green": 0.0,
        "blue": 0.0,
        "alpha": 0.0
      },
      "displayLabel": true,
      "labelText": "Sample Text",
      "labelColor": {
        "red": 1.0,
        "green": 1.0,
        "blue": 1.0,
        "alpha": 1.0
      },
      "wideLogo": {
        "enable": true,
        "name": "logo.png",
        "data": "base64encodeddata"
      },
      "smallLogo": {
        "enable": true,
        "name": "logo.png",
        "data": "base64encodeddata"
      },
      "displayBarLogin": false,
      "displayMotd": true,
      "motd": "Welcome to our application"
    }"""

  // file with overrwriten config
  val brandingConfFile: File = tmpDir.createChild("config.json").overwrite(fakeBrandingConf)

  val modules = List(
    new BrandingApi(
      new BrandingApiService(
        new BrandingConfService(brandingConfFile.pathAsString)
      ),
      new StringUuidGeneratorImpl()
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
      } yield s
    })
  }

}
