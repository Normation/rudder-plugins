package com.normation.plugins.changevalidation

import com.normation.rudder.domain.policies.SimpleTarget

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import com.normation.rudder.repository.FullNodeGroupCategory

import net.liftweb.common.Box
import net.liftweb.common.Empty
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.json.NoTypeHints
import net.liftweb.json.Serialization
import org.apache.commons.io.FileUtils

import scala.util.control.NonFatal

/*
 * This service save the list of target (only their id) in a
 * json file in given directory.
 * JSON format:
 * {
 *   "unsupervised": [
 *     "group:xxxxxx",
 *     "special:allnodes",
 *     ...
 *   ]
 * }
 *
 * Because of https://issues.rudder.io/issues/14330 we need to
 * save *un*supervised target so that when a new group is created,
 * it is automatically supervised.
 *
 */
object UnsupervisedTargetsRepository {
  // invert non supervised target to find the ones supervised
  def invertTargets(unsupervised: Set[SimpleTarget], groupLib: FullNodeGroupCategory): Set[SimpleTarget] = {
    groupLib.allTargets.values.map(_.target.target).collect { case t: SimpleTarget if(!unsupervised.contains(t)) => t }.toSet
  }
}

class UnsupervisedTargetsRepository(
    directory: Path
  , filename : String
) {
  implicit val formats = net.liftweb.json.Serialization.formats(NoTypeHints)
  private[this] val path = new File(directory.toFile, filename)

  /*
   * Check that the directory path exists and is writable.
   * If it doesn't exists, try to create it.
   */
  def checkPathAndInitRepos(): Unit = {
    val f = directory.toFile
    if(f.exists()) {
      if(f.canWrite) {
        ChangeValidationLogger.debug(s"Directory '${directory.toString()}' exists and is writable: ok")
      } else {
        ChangeValidationLogger.error(s"Directory '${directory.toString()}' exists but is not writable. Please correct rights on that directory.")
      }
    } else {
      // try to create it
      val msg = s"Error when creating directory '${directory.toString()}'. Please correct the problem."
      try {
        f.mkdirs()
      } catch {
        case NonFatal(ex) => ChangeValidationLogger.error(msg + " Error was: " + ex.getMessage)
      }
    }
    //also check the file
    if(path.exists()) {
      // ok
      ChangeValidationLogger.debug(s"Supervised target repository file '${path.toString()}' exists: ok")
    } else { //create it
      ChangeValidationLogger.debug(s"Initializing supervised target repository file '${path.toString()}'.")
      save(Set())
    }
  }


  def save(groups: Set[SimpleTarget]): Box[Unit] = {

    // Always save by replacing the whole file.
    // Sort by name.
    val targets = UnsupervisedTargetIds(groups.toList.map( _.target ).sorted) // natural sort on string
    val jsonString = Serialization.writePretty[UnsupervisedTargetIds](targets)

    //write file
    try {
      FileUtils.writeStringToFile(path, jsonString, StandardCharsets.UTF_8)
      Full(())
    } catch {
      case NonFatal(ex) =>
        val msg = s"Error when saving list of group which trigger a change validation request from '${path.getAbsolutePath}'"
        ChangeValidationLogger.error(msg)
        Failure(msg, Full(ex), Empty)
    }
  }

  def load(): Box[Set[SimpleTarget]] = {

    def read(): Box[String] = {
      try {
        Full(FileUtils.readFileToString(path, StandardCharsets.UTF_8))
      } catch {
        case NonFatal(ex) =>
          val msg = s"Error when reading list of group which trigger a change validation request from '${path.getAbsolutePath}'"
          ChangeValidationLogger.error(msg)
          Failure(msg, Full(ex), Empty)
      }
    }

    for {
      content <- read()
      targets <- Ser.parseTargetIds(content)
    } yield {
      targets
    }
  }
}
