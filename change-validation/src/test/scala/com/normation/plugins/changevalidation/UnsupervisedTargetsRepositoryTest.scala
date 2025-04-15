package com.normation.plugins.changevalidation

import better.files.File
import com.normation.JsonSpecMatcher
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies.GroupTarget
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.zio.UnsafeRun
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import scala.jdk.CollectionConverters.*

@RunWith(classOf[JUnitRunner])
class UnsupervisedTargetsRepositoryTest extends Specification with JsonSpecMatcher {
  sequential
  isolated

  "UnsupervisedTargetsRepository" should {
    "create the directory and file if it does not exist" in {
      val tmpDir           = File(Files.createTempDirectory("rudder-test-"))
      val tmpDirPath       = tmpDir.delete().path
      val unsupervisedRepo = new UnsupervisedTargetsRepository(tmpDirPath, "unsupervised-targets.json")

      unsupervisedRepo.checkPathAndInitRepos().runNow
      val file = tmpDir / "unsupervised-targets.json"
      file.exists must beTrue
      file.contentAsString must equalsJsonSemantic("""{"unsupervised":[]}""")
    }

    val tmpDir     = File(Files.createTempDirectory("rudder-test-"))
    val tmpDirPath = tmpDir.path
    val file       = tmpDir / "unsupervised-targets.json"

    "do nothing if the directory is not writable" in {
      skipped("Permissions testing could fail on CI in temporary folder, skipping this for now")
      val unsupervisedRepo = new UnsupervisedTargetsRepository(tmpDirPath, "unsupervised-targets.json")
      tmpDir.setPermissions(PosixFilePermissions.fromString("r--r--r--").asScala.toSet)

      unsupervisedRepo.checkPathAndInitRepos().either.runNow must beLeft
      val file = tmpDir / "unsupervised-targets.json"
      file.exists must beFalse
    }

    "do nothing if the file exists" in {
      val unsupervisedRepo = new UnsupervisedTargetsRepository(tmpDirPath, "unsupervised-targets.json")
      file.createIfNotExists().overwrite("foo")
      unsupervisedRepo.checkPathAndInitRepos().runNow
      file.contentAsString must beEqualTo("foo")
    }

    val targets = Set("foo", "bar").map(id => GroupTarget(NodeGroupId(NodeGroupUid(id))): SimpleTarget)
    "save targets with natural string sorting order" in {
      val unsupervisedRepo = new UnsupervisedTargetsRepository(tmpDirPath, "unsupervised-targets.json")

      unsupervisedRepo.save(targets).runNow
      file.exists must beTrue
      file.contentAsString must equalsJsonSemantic("""{"unsupervised":["group:bar","group:foo"]}""")
    }

    "load targets" in {
      val unsupervisedRepo = new UnsupervisedTargetsRepository(tmpDirPath, "unsupervised-targets.json")

      unsupervisedRepo.save(targets).runNow
      unsupervisedRepo.load().runNow must beEqualTo(targets)
    }
  }
}
