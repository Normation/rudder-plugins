/*
*************************************************************************************
 * Copyright 2023 Normation SAS
*************************************************************************************
 *
 * This file is part of Rudder.
 *
 * Rudder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * In accordance with the terms of section 7 (7. Additional Terms.) of
 * the GNU General Public License version 3, the copyright holders add
 * the following Additional permissions:
 * Notwithstanding to the terms of section 5 (5. Conveying Modified Source
 * Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
 * Public License version 3, when you create a Related Module, this
 * Related Module is not considered as a part of the work and may be
 * distributed under the license agreement of your choice.
 * A "Related Module" means a set of sources files including their
 * documentation that, without modification of the Source Code, enables
 * supplementary functions or services in addition to those offered by
 * the Software.
 *
 * Rudder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

 *
*************************************************************************************
 */

package bootstrap.rudder.plugin

import better.files.File
import com.normation.errors._
import com.normation.plugins.changevalidation.ChangeValidationLoggerPure
import com.normation.plugins.changevalidation.OldFileFormat
import com.normation.plugins.changevalidation.SupervisedSimpleTargets
import com.normation.plugins.changevalidation.UnsupervisedTargetsRepository
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.repository.RoNodeGroupRepository
import io.scalaland.chimney.syntax._
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import zio.ZIO
import zio.json._

/*
 * The validation workflow level
 */
class MigrateSupervisedGroups(
    groupRepository:  RoNodeGroupRepository,
    unsupervisedRepo: UnsupervisedTargetsRepository,
    directory:        Path,
    oldFilename:      String
) {
  private[this] val old = File(directory) / oldFilename

  def migrate(): IOResult[Unit] = {
    (for {
      exists <- IOResult.attempt(old.exists)

      _ <- if (!exists) { // ok, plugin installed in new version
             ChangeValidationLoggerPure.debug("No migration needed from supervised to unsupervised groups")
           } else { // migration needed
             (for {
               _ <-
                 ChangeValidationLoggerPure.info(s"Old file format for supervised target found: '${old}': migrating")

               oldTargetStrings <- old.contentAsString(StandardCharsets.UTF_8).fromJson[OldFileFormat].toIO
               targets           = oldTargetStrings.transformInto[SupervisedSimpleTargets].supervised
               unsupervised     <-
                 groupRepository
                   .getFullGroupLibrary()
                   .map(groups => UnsupervisedTargetsRepository.invertTargets(targets, groups))
                   .catchAll[Any, RudderError, Set[SimpleTarget]](_ => {
                     ChangeValidationLoggerPure
                       .warn("Error when retrieving group library for migration: all groups will be supervised")
                       .as(Set.empty)
                   })

               _ <-
                 (unsupervisedRepo.save(unsupervised) *>
                 IOResult.attempt(old.renameTo(oldFilename + "_migrated")) *>
                 ChangeValidationLoggerPure.info(s"Migration of old supervised group file format done"))
                   .chainError("Error when saving supervised group. Please check you configuration")

             } yield ())
           }
    } yield ())
      .chainError("Error when migrating supervised group. Please check you configuration")
      .onError(err => ZIO.foreach(err.failureOption.map(_.fullMsg))(ChangeValidationLoggerPure.error(_)))
  }
}
