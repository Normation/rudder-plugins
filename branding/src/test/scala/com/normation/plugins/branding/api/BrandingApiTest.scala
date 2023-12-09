package com.normation.plugins.branding

import better.files._
import com.normation.plugins.branding.api.BrandingApi
import com.normation.plugins.branding.api.BrandingApiService
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.utils.StringUuidGeneratorImpl
import java.nio.file.Files
import net.liftweb.common.Loggable
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class BrandingApiTest extends Specification with TraitTestApiFromYamlFiles with Loggable with AfterAll {
  sequential

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "branding_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

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
