package com.normation.plugins.changevalidation

import better.files.File
import com.normation.errors._
import com.normation.plugins.changevalidation.ChangeValidationLoggerPure
import com.normation.plugins.changevalidation.RudderJsonMapping._
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.repository.FullNodeGroupCategory
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.collection.immutable.SortedSet
import zio.ZIO
import zio.json._

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
    groupLib.allTargets.values.map(_.target.target).collect { case t: SimpleTarget if (!unsupervised.contains(t)) => t }.toSet
  }
}

class UnsupervisedTargetsRepository(
    directory: Path,
    filename:  String
) {
  private[this] val path = File(directory) / filename

  /*
   * Check that the directory path exists and is writable.
   * If it doesn't exists, try to create it.
   */
  def checkPathAndInitRepos(): IOResult[Unit] = {
    val f = File(directory)
    for {
      exists     <- IOResult.attempt(f.exists)
      isWritable <- IOResult.attempt(f.isWritable)
      _          <- if (exists) {
                      if (isWritable) ChangeValidationLoggerPure.debug(s"Directory '$directory' exists and is writable: ok")
                      else {
                        ChangeValidationLoggerPure.error(
                          s"Directory '$directory' exists but is not writable. Please correct rights on that directory."
                        )
                      }
                    } else {
                      // try to create it
                      IOResult
                        .attempt(s"Error when creating directory '$directory'. Please correct the problem.")(
                          f.createDirectory()
                        )
                        .onError(err => ZIO.foreach(err.failureOption.map(_.fullMsg))(ChangeValidationLoggerPure.error(_)))
                    }

      fileExists <- IOResult.attempt(path.exists())
      _          <- if (fileExists) { // ok
                      ChangeValidationLoggerPure.debug(s"Supervised target repository file '$path' exists: ok")
                    } else { // create it
                      ChangeValidationLoggerPure.debug(s"Initializing supervised target repository file '$path'.") *>
                      save(Set())
                    }
    } yield ()
  }

  def save(groups: Set[SimpleTarget]): IOResult[Unit] = {

    // Always save by replacing the whole file.
    // Sort by name.
    val targets = UnsupervisedTargetIds(SortedSet.from(groups)) // natural sort on string

    // write file
    IOResult
      .attempt(
        path.writeByteArray(StandardCharsets.UTF_8.encode(targets.toJson).array())
      )
      .unit
      .chainError("Error when saving list of group which trigger a change validation request")
  }

  def load(): IOResult[Set[SimpleTarget]] = {

    def read(): IOResult[String] = {
      IOResult
        .attempt(s"Error when reading list of group which trigger a change validation request from '${path}'")(
          path.contentAsString(StandardCharsets.UTF_8)
        )
    }

    for {
      content <- read()
      targets <- content.fromJson[UnsupervisedTargetIds].toIO
    } yield {
      targets.unsupervised
    }
  }
}
