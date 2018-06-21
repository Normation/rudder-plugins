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

package com.normation.plugins.apiauthorizations.snippet

import java.nio.file.Paths

import net.liftweb.common.Loggable
import net.liftweb.http.{DispatchSnippet, LiftRules, S}
import net.liftweb.util.Helpers._

import scala.xml.NodeSeq
import bootstrap.liftweb.LiftSpringApplicationContext.inject
import com.normation.plugins.PluginVersion
import com.normation.plugins.apiauthorizations.ApiAuthorizationsPluginDef


/*
 * Use that snippet to resolve resources in the "toserve" resources directory.
 * <link css="http://blabla/styles.css" data-lift="toserve?path=some/resource/name.css"/>
 * A resource with path ending with ".js" will add/replace the "src" attribute and
 * a resource with a path ending with a ".css" will replace the "rel" tag.
 * Path is mandatory. If missing, nothing (NodeSeq.Empty) is rendered.
 */
class ServeResource extends DispatchSnippet with Loggable {


  private[this] val moduleDef = inject[ApiAuthorizationsPluginDef]

  private[this] def link(s: String) = s"/${LiftRules.resourceServerPath}/${s}"
  private[this] val css = """(.+)\.css""".r
  private[this] val js  = """(.+)\.js""".r


  override def dispatch = {
    case "remove" => remove
    case _        => render _
  }

  val remove = (_:NodeSeq) => NodeSeq.Empty

  //the plugin version in format 4.1-2.1
  def pluginVersion(version: PluginVersion) = {
    s"${version.prefix}${version.major}.${version.minor}"
  }

  def render(xml: NodeSeq) = {

    S.attr("path").toOption match {
      case Some(path) =>
        try {
          val p = Paths.get("/" + path) // check path is actually a path (always add '/' at beging)
          p.toString match {
            case css(x) => ("link [href]"  #> link(x+s".css?version=${pluginVersion(moduleDef.version)}")).apply(xml)
            case js (x) => ("script [src]" #> link(x+s".js?version=${pluginVersion(moduleDef.version)}" )).apply(xml)
            case _      => NodeSeq.Empty
          }
        } catch {
          case ex: Exception =>
            logger.debug(s"Resources path '${path}' is not a valid path: ${ex.getMessage}")
            NodeSeq.Empty
        }
      case None => NodeSeq.Empty
    }
  }
}
