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

package com.normation.plugins.branding

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import net.liftweb.common._

import scala.util.{Success, Try, Failure => CatchException}


class BrandingConfService extends Loggable {

  private[this] val initialValue =
    BrandingConf(
        true
      , JsonColor(204,0,0,1)
      , true
      , "Production"
      , JsonColor(255,255,255,1)
      , true
      , true
      , true
      , true
      , true
      , true
      , true
      , "Welcome, please sign in:"
    )


  val configFilePath = "/var/rudder/plugins/branding/configuration.json"


  private[this] var cache : Box[BrandingConf] = reloadCache(true)

  def reloadCache(init : Boolean) : Box[BrandingConf] = {
    Try {
      val path = Paths.get(configFilePath)
      if (Files.exists(path)) {
        import net.liftweb.json.parseOpt
        import scala.io.Source.fromFile
        val content = fromFile(configFilePath).mkString("")
        for {
          json <- Box(parseOpt(content)) ?~! "Could nor parse correctly Branding plugin configuration file"
          conf <- BrandingConf.parse(json)
        }  yield {
          cache = Full(conf)
          conf
        }
      } else {
        if (init) {
          updateConf(initialValue)
        } else {
          // Should we update cache to that value ??
          Failure("Could not read plugin configuration from cache")
        }
      }
    } match {
      case Success(_) =>
        cache
      case CatchException(e) =>
        logger.debug(e.getMessage)
        Failure("Could not read configuration for branding plugin", Full(e), Empty)
    }
  }

  def getConf : Box[BrandingConf] = cache

  def updateConf(newConf : BrandingConf) : Box[BrandingConf] = {
    import net.liftweb.json.prettyRender
    val content = prettyRender(BrandingConf.serialize(newConf))
    Try{
      val path = Paths.get(configFilePath)
      if (!Files.exists(path)) {
        Files.createDirectories(path.getParent)
        Files.createFile(path)
      }
      Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    } match {
      case Success(_) =>
        cache = Full(newConf)
        cache
      case CatchException(e) =>
        Failure("Could not write new configuration for branding plugin", Full(e), Empty)
    }
  }
}