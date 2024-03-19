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
import com.normation.box.*
import com.normation.plugins.changevalidation.ChangeValidationLogger
import com.normation.plugins.changevalidation.UnsupervisedTargetsRepository
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.repository.RoNodeGroupRepository
import java.nio.charset.StandardCharsets
import net.liftweb.common.*
import net.liftweb.json.NoTypeHints

final case class OldfileFormat(supervised: List[String])

/*
 * The validation workflow level
 */
class MigrateSupervisedGroups(
    groupRepository:  RoNodeGroupRepository,
    unsupervisedRepo: UnsupervisedTargetsRepository
) {
  implicit val formats: net.liftweb.json.Formats = net.liftweb.json.Serialization.formats(NoTypeHints)
  val directory   = "/var/rudder/plugin-resources/change-validation"
  val oldFilename = "supervised-targets.json"

  def migrate(): Unit = {
    Box.tryo {
      val path = directory + "/" + oldFilename
      val old  = File(path)
      if (!old.exists) { // ok, plugin installed in new version
        ChangeValidationLogger.debug("No migration needed from supervised to unsupervised groups")
      } else { // migration needed
        ChangeValidationLogger.info(s"Old file format for supervised target found: '${path}': migrating")
        val oldTargetStrings = net.liftweb.json.Serialization.read[OldfileFormat](old.contentAsString(StandardCharsets.UTF_8))
        val targets          = oldTargetStrings.supervised
          .flatMap(t => {
            RuleTarget.unser(t).flatMap {
              case t: SimpleTarget => Some(t)
              case _ => None
            }
          })
          .toSet
        val unsupervised: Set[SimpleTarget] = (
          for {
            groups <- groupRepository.getFullGroupLibrary().toBox
          } yield {
            UnsupervisedTargetsRepository.invertTargets(targets, groups)
          }
        ) match {
          case Full(t) => t
          case e: EmptyBox =>
            val msg = (e ?~! s"Error when retrieving group library for migration: all groups will be supervised").messageChain
            ChangeValidationLogger.warn(msg)
            Set()
        }
        unsupervisedRepo.save(unsupervised) match {
          case Full(_) =>
            old.renameTo(oldFilename + "_migrated")
            ChangeValidationLogger.info(s"Migration of old supervised group file format done")
          case e: EmptyBox =>
            val msg = (e ?~! s"Error when saving supervised group. Please check you configuration")
            ChangeValidationLogger.warn(msg)
        }
      }
    } match {
      case Full(_) => () // done
      case e: EmptyBox =>
        val msg = (e ?~! s"Error when migrating supervised group. Please check you configuration")
        ChangeValidationLogger.warn(msg)
    }
  }
}
