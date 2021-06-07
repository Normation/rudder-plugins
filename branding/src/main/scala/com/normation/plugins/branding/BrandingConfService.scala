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

import com.normation.errors._
import net.liftweb.common._
import zio._
import zio.syntax._
import com.normation.zio._
import better.files._
import net.liftweb.json.parseOpt
import com.normation.box._

object BrandingConfService {

  def initialValue =
    BrandingConf(
        true
      , JsonColor(204,0,0,1)
      , true
      , "Production"
      , JsonColor(255,255,255,1)
      , Logo
        ( true
        , None
        , None
        )
      , Logo
        ( true
          , None
          , None
        )
      , true
      , true
      , "Welcome, please sign in:"
    )

  val defaultConfigFilePath = "/var/rudder/plugins/branding/configuration.json"

}

class BrandingConfService(configFilePath: String) {

  private[this] val cache : Ref[Either[RudderError, BrandingConf]] = (for {
    v <- Ref.make[Either[RudderError, BrandingConf]](Left(Unexpected("Cache is not yet initialized")))
    c <- reloadCacheInternal(true, v).either
  } yield v).runNow

  private def reloadCacheInternal(init: Boolean, ref: Ref[Either[RudderError, BrandingConf]]) : IOResult[BrandingConf] = {
    (for {
      path   <- IOResult.effect(Paths.get(configFilePath))
      exists <- IOResult.effect(Files.exists(path))
      res    <- if(exists) {
                  for {
                    content <- IOResult.effect(s"Error when trying to read file: ${configFilePath}")(
                                 File(configFilePath).contentAsString(StandardCharsets.UTF_8)
                               )
                    json    <- parseOpt(content).notOptional("Could nor parse correctly Branding plugin configuration file")
                    conf    <- BrandingConf.parse(json).toIO
                    ref     <- ref.set(Right(conf))
                  }  yield {
                    conf
                  }
                } else {
                  if (init) {
                    updateConf(BrandingConfService.initialValue)
                  } else {
                    // Should we update cache to that value ??
                    Inconsistency("Could not read plugin configuration from cache").fail
                  }
                }
    } yield {
      res
    }).chainError("Could not read configuration for branding plugin")
  }

  def reloadCache : IOResult[BrandingConf] = reloadCacheInternal(false, cache)

  def getConf : Box[BrandingConf] = {
    (for {
      c <- cache.get
      r <- c.toIO
    } yield {
      r
    }).toBox
  }


  def updateConf(newConf : BrandingConf) : IOResult[BrandingConf] = {
    import net.liftweb.json.prettyRender
    val content = prettyRender(BrandingConf.serialize(newConf))
    (for {
      _ <- IOResult.effect {
             val path = Paths.get(configFilePath)
             if (!Files.exists(path)) {
               Files.createDirectories(path.getParent)
               Files.createFile(path)
             }
             Files.write(path, content.getBytes(StandardCharsets.UTF_8))
           }
      _ <- cache.set(Right(newConf))
    } yield {
      newConf
    }).chainError("Could not write new configuration for branding plugin")
  }
}
