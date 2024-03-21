package com.normation.plugins.openscappolicies.api

import better.files.*
import com.normation.plugins.openscappolicies.services.OpenScapReportReader
import com.normation.plugins.openscappolicies.services.ReportSanitizer
import com.normation.rudder.MockNodes
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import net.liftweb.common.Loggable
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class OpenScapApiTest extends Specification with TraitTestApiFromYamlFiles with Loggable with AfterAll {
  sequential

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  override def yamlSourceDirectory  = "openscap_api"
  override def yamlDestTmpDirectory = tmpDir / "templates"

  val policySanitizationFile = better.files.Resource
    .url("antisamy.xml")
    .map(_.getPath().toString)
    .getOrElse("non_existing_file")
  val reportSanitizer        = new ReportSanitizer(policySanitizationFile)

  val openScapReportDir = tmpDir
  val node1ReportFile   = openScapReportDir
    .createChild("node1/", asDirectory = true)
    .createChild(OpenScapReportReader.OPENSCAP_REPORT_FILENAME)
    .overwrite("<html><head><title>OpenSCAP Report example</title></head><body><h1>OpenSCAP Report</h1></body></html>")
  val node2ReportFile   = openScapReportDir
    .createChild("node2/", asDirectory = true)
    .createChild(OpenScapReportReader.OPENSCAP_REPORT_FILENAME)
    .overwrite("""<a href="https://example.com">""") // upon sanitization, should have rel="nofollow" added

  val mockNodes            = new MockNodes()
  val openScapReportReader = new OpenScapReportReader(
    mockNodes.nodeInfoService,
    restTestSetUp.mockDirectives.directiveRepo,
    null,
    null,
    openScapReportDirPath = openScapReportDir.pathAsString
  )

  val modules = List(
    new OpenScapApiImpl(
      openScapReportReader,
      reportSanitizer
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
