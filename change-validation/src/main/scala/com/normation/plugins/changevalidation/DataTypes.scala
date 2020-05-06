/*
*************************************************************************************
* Copyright 2018 Normation SAS
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

package com.normation.plugins.changevalidation

import com.normation.NamedZioLogger
import com.normation.rudder.domain.policies.FullRuleTargetInfo
import com.normation.rudder.domain.policies.RuleTarget
import com.normation.rudder.domain.policies.SimpleTarget
import com.normation.rudder.repository.FullNodeGroupCategory
import com.normation.utils.Control
import net.liftweb.common.Box
import net.liftweb.common.Empty
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.common.Logger
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.JArray
import net.liftweb.json.NoTypeHints
import net.liftweb.json.parse
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

/**
 * Applicative log of interest for Rudder ops.
 */
object ChangeValidationLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("change-validation")

  object Metrics extends Logger {
    override protected def _logger = LoggerFactory.getLogger("change-validation.metrics")
  }
}

object ChangeValidationLoggerPure extends NamedZioLogger {

  override val loggerName: String = "change-validation"

  object Metrics extends NamedZioLogger {
    override def loggerName: String = "change-validation.metrics"
  }
}


/*
 * What is a group in the API ?
 */
final case class JsonTarget(
    id         : String // this a target id, so either group:groupid or special:specialname
  , name       : String
  , description: String
  , supervised : Boolean
)

/*
 * The JSON class saved in /var/rudder/plugins/...
 * with the list of targets
 */
final case class SupervisedTargetIds(
  supervised: List[String]
)

/*
 * The JSON sent to client side
 */
final case class JsonCategory(
    name      : String
  , categories: List[JsonCategory]
  , targets   : List[JsonTarget]
)

/*
 * Mapping between Rudder category/group and json one
 */
object RudderJsonMapping {


  implicit class TargetToJson(target: FullRuleTargetInfo) {
    /**
     * We only know how to map SimpleTarget, so just map that.
     */
    def toJson(supervisedSet: Set[SimpleTarget]): Option[JsonTarget] = {

      target.target.target match {
        case st: SimpleTarget =>
          Some(JsonTarget(
              st.target
            , target.name
            , target.description
            , supervisedSet.contains(st)
          ))
        case _ => None
      }
    }
  }

  implicit class CatToJson(cat: FullNodeGroupCategory) {
    def toJson(supervisedSet: Set[SimpleTarget]): JsonCategory = JsonCategory(
        cat.name
      , cat.subCategories.map( _.toJson(supervisedSet)).sortBy(_.name)
      , cat.targetInfos.flatMap(_.toJson(supervisedSet)).sortBy(_.name)
    )
  }

}

/*
 * Ser utils
 */
object Ser {
  implicit val formats = net.liftweb.json.Serialization.formats(NoTypeHints)

  /*
   * Parse a string as a simple target ID
   */
  def parseTargetId(s: String): Box[SimpleTarget] = {
    RuleTarget.unser(s) match {
      case Some(x: SimpleTarget) => Full(x)
      case _                     =>
        val msg = s"Error: the string '${s}' can not parsed as a valid rule target"
        ChangeValidationLogger.error(msg)
        Failure(msg, Empty, Empty)
    }
  }

  /*
   * Transform a JSON value
   */
  def parseJsonTargets(json: JValue): Box[Set[SimpleTarget]] = {
    /*
     * Here, for some reason, JNothing.extractOpt[SupervisedTargetIds] returns Some(SupervisedTargetIds(Nil)).
     * Which is not what we want, obviously. So the workaround.
     */
    for {
      list    <- parseSupervised(json)
      targets <- Control.sequence(list)(parseTargetId)
    } yield {
      targets.toSet
    }
  }

  def parseSupervised(json: JValue): Box[List[String]] = {
    (json \ "supervised") match {
      case JArray(list) => Control.sequence(list)( s => Box(s.extractOpt[String])).map( _.toList)
      case _            =>
        val msg = s"Error when trying to parse JSON content ${json.toString} as a set of rule target."
        ChangeValidationLogger.error(msg)
        Failure("Error when trying to parse JSON content as a set of rule target.", Empty, Empty)
    }
  }

  /*
   * Parse a string, expecting to be the JSON representation
   * of SupervisedTargetIds
   */
  def parseTargetIds(source: String): Box[Set[SimpleTarget]] = {
    for {
      json    <- try {
                   Full(parse(source))
                 } catch {
                   case NonFatal(ex) =>
                     val msg = s"Error when trying to parse source document as JSON ${source}"
                     ChangeValidationLogger.error(msg)
                     Failure(s"Error when trying to parse source document as JSON.", Full(ex), Empty)
                 }
      targets <- parseJsonTargets(json)
    } yield {
      targets
    }
  }
}
