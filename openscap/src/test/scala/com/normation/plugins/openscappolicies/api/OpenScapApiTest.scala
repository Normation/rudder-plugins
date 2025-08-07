package com.normation.plugins.openscappolicies.api

import better.files.*
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.plugins.openscappolicies.services.OpenScapReportReader
import com.normation.rudder.MockNodes
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import java.nio.file.Files
import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class OpenScapApiTest extends ZIOSpecDefault {

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "openscap_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val policySanitizationFile = better.files.Resource
    .url("antisamy.xml")
    // here, we need to use a toURI, because antisamy will do a File(policyFile) etc, and "@" is changed to "%40"
    // See: https://issues.rudder.io/issues/25193
    .map(_.toURI.getPath().toString)
    .getOrElse("non_existing_file")

  val openScapReportDir = tmpDir
  val node1ReportFile   = openScapReportDir
    .createChild("node1/", asDirectory = true)
    .createChild(OpenScapReportReader.OPENSCAP_REPORT_FILENAME)
    .overwrite("<html><head><title>OpenSCAP Report example</title></head><body><h1>OpenSCAP Report</h1></body></html>")

  val node2ReportFile = openScapReportDir
    .createChild("node2/", asDirectory = true)
    .createChild(OpenScapReportReader.OPENSCAP_REPORT_FILENAME)
    // upon sanitization, should have rel="nofollow" added and js action removed
    .overwrite(
      """<script>bad js</script><a href="https://example.com">not a trap!</a><div onMouseover="bad things;">content</div>"""
    )

  val rootReportFile = openScapReportDir
    .createChild("root/", asDirectory = true)
    // create a directory instead of a file, attempting to read the content should throw an exception
    .createChild(OpenScapReportReader.OPENSCAP_REPORT_FILENAME, asDirectory = true)

  val mockNodes            = new MockNodes()
  val openScapReportReader = new OpenScapReportReader(
    mockNodes.nodeFactRepo,
    restTestSetUp.mockDirectives.directiveRepo,
    null,
    null,
    openScapReportDirPath = openScapReportDir.pathAsString
  )

  val modules = List(
    new OpenScapApiImpl(openScapReportReader)
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, None)

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
