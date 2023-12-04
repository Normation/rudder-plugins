package bootstrap.rudder.plugin

import better.files.File
import com.normation.JsonSpecMatcher
import com.normation.plugins.changevalidation.MockSupervisedTargets
import com.normation.rudder.rest.RestTestSetUp
import com.normation.zio.UnsafeRun
import java.nio.file.Files
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll

@RunWith(classOf[JUnitRunner])
class MigrateSupervisedGroupsTests extends Specification with JsonSpecMatcher with AfterAll {
  sequential

  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir = File(Files.createTempDirectory("rudder-test-"))

  override def afterAll(): Unit = {
    tmpDir.delete()
  }

  val supervisedFile   = tmpDir / "supervised-targets.json"
  val unsupervisedFile = tmpDir / "unsupervised-targets.json"

  val mockNodeGroups = restTestSetUp.mockNodeGroups
  val mockServices   = new MockSupervisedTargets(
    tmpDir,
    "unsupervised-targets.json",
    mockNodeGroups.groupLib.copy(
      targetInfos =
        List(mockNodeGroups.groupsTargetInfos(mockNodeGroups.g0.id), mockNodeGroups.groupsTargetInfos(mockNodeGroups.g1.id))
    )
  )

  val migrateSupervisedGroups = new MigrateSupervisedGroups(
    mockServices.nodeGroupRepo,
    mockServices.unsupervisedRepo,
    tmpDir.path,
    "supervised-targets.json"
  )

  "MigrateSupervisedGroupsTests" should {
    "do nothing if old file does not exist" in {
      migrateSupervisedGroups.migrate().runNow
      unsupervisedFile.exists must beFalse
    }

    "migrate from old file format and rename old file" in {
      unsupervisedFile.exists must beFalse
      supervisedFile.overwrite(
        """{
          |  "supervised": [
          |    "group:0000f5d3-8c61-4d20-88a7-bb947705ba8a"
          |  ]
          |}""".stripMargin
      )
      migrateSupervisedGroups.migrate().runNow
      unsupervisedFile.exists must beTrue
      unsupervisedFile.contentAsString must equalsJsonSemantic(
        """{
          |  "unsupervised": [
          |    "group:1111f5d3-8c61-4d20-88a7-bb947705ba8a"
          |  ]
          |}""".stripMargin
      )
      val renamedFile = File(supervisedFile.parent, supervisedFile.name + "_migrated")
      renamedFile.exists must beTrue
      supervisedFile.exists must beFalse
      renamedFile.contentAsString must equalsJsonSemantic(
        """{
          |  "supervised": [
          |    "group:0000f5d3-8c61-4d20-88a7-bb947705ba8a"
          |  ]
          |}""".stripMargin
      )
    }
  }
}
