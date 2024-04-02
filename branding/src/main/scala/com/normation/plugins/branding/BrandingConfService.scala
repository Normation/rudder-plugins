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

import better.files.*
import com.normation.box.*
import com.normation.errors.*
import com.normation.zio.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import net.liftweb.common.*
import net.liftweb.json.parseOpt
import zio.*
import zio.syntax.*

object BrandingConfService {

  def initialValue = {
    BrandingConf(
      true
      // #da291c, from 7.0 color palette
      ,
      JsonColor(0.85, 0.16, 0.11, 1),
      true,
      "Production",
      JsonColor(2.55, 2.55, 2.55, 1),
      Logo(true, None, None),
      Logo(true, None, None),
      true,
      true,
      "Welcome, please sign in:"
    )
  }

  val defaultConfigFilePath = "/var/rudder/plugins/branding/configuration.json"

}

class BrandingConfService(configFilePath: String) {

  private[this] val cache: Ref[Either[RudderError, BrandingConf]] = (for {
    v <- Ref.make[Either[RudderError, BrandingConf]](Left(Unexpected("Cache is not yet initialized")))
    c <- reloadCacheInternal(true, v).either
  } yield v).runNow

  private def reloadCacheInternal(init: Boolean, ref: Ref[Either[RudderError, BrandingConf]]): IOResult[BrandingConf] = {
    (for {
      path   <- IOResult.attempt(Paths.get(configFilePath))
      exists <- IOResult.attempt(Files.exists(path))
      res    <- if (exists) {
                  for {
                    content <- IOResult.attempt(s"Error when trying to read file: ${configFilePath}")(
                                 File(configFilePath).contentAsString(StandardCharsets.UTF_8)
                               )
                    json    <- parseOpt(content).notOptional("Could nor parse correctly Branding plugin configuration file")
                    conf    <- BrandingConf.parse(json).toIO
                    ref     <- ref.set(Right(conf))
                  } yield {
                    conf
                  }
                } else {
                  if (init) {
                    updateConf(BrandingConfService.initialValue, Some(ref))
                  } else {
                    // Should we update cache to that value ??
                    Inconsistency("Could not read plugin configuration from cache").fail
                  }
                }
    } yield {
      res
    }).chainError("Could not read configuration for branding plugin")
  }

  def reloadCache: IOResult[BrandingConf] = reloadCacheInternal(false, cache)

  def getConf: Box[BrandingConf] = {
    (for {
      c <- cache.get
      r <- c.toIO
    } yield {
      r
    }).toBox
  }

  // During init, cache doesn't exist yet, so we need the ref to init the cache
  def updateConf(newConf: BrandingConf, ref: Option[Ref[Either[RudderError, BrandingConf]]] = None): IOResult[BrandingConf] = {
    import net.liftweb.json.prettyRender
    val content = prettyRender(BrandingConf.serialize(newConf))
    (for {
      _ <- IOResult.attempt {
             val path = Paths.get(configFilePath)
             if (!Files.exists(path)) {
               Files.createDirectories(path.getParent)
               Files.createFile(path)
             }
             Files.write(path, content.getBytes(StandardCharsets.UTF_8))
           }
      _ <- ref match {
             case None                  => cache.set(Right(newConf))
             case Some(cacheDuringInit) => cacheDuringInit.set(Right(newConf))
           }
    } yield {
      newConf
    }).chainError("Could not write new configuration for branding plugin")
  }
}
