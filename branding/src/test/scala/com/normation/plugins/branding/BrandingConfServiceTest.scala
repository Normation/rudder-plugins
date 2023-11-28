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
package com.normation.plugins.branding

import better.files._
import com.normation.plugins.branding.BrandingConf
import com.normation.plugins.branding.BrandingConfService
import com.normation.zio.UnsafeRun
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.AfterAll
import zio.json._

@RunWith(classOf[JUnitRunner])
class BrandingConfServiceTest extends Specification with AfterAll {
  sequential

  val fakeBrandingConf: BrandingConf = BrandingConf(
    displayBar = true,
    barColor = JsonColor(0.5, 0.5, 0.5, 1.0),
    displayLabel = true,
    labelText = "Test Label",
    labelColor = JsonColor(0.2, 0.8, 0.4, 1.0),
    wideLogo = Logo(enable = true, name = Some("wide_logo.png"), data = None),
    smallLogo = Logo(enable = true, name = Some("small_logo.png"), data = None),
    displayBarLogin = true,
    displayMotd = true,
    motd = "Test MOTD"
  )
  val fakeBrandingConfJson = s"""
    {
      "displayBar": true,
      "barColor": {
          "red": 0.5,
          "green": 0.5,
          "blue": 0.5,
          "alpha": 1.0
      },
      "displayLabel": true,
      "labelText": "Test Label",
      "labelColor": {
          "red": 0.2,
          "green": 0.8,
          "blue": 0.4,
          "alpha": 1.0
      },
      "wideLogo": {
          "enable": true,
          "name": "wide_logo.png",
          "data": null
      },
      "smallLogo": {
          "enable": true,
          "name": "small_logo.png",
          "data": null
      },
      "displayBarLogin": true,
      "displayMotd": true,
      "motd": "Test MOTD"
    }"""
  val tempFile: File                = File.newTemporaryFile("rudder", "branding-config.json").overwrite(fakeBrandingConfJson)
  val service:  BrandingConfService = new BrandingConfService(tempFile.pathAsString)
  service.reloadCache.runNow

  "BrandingConfService" should {

    "get the branding configuration" in {
      service.getConf.runNow must beEqualTo(fakeBrandingConf)
    }

    "update the branding configuration" in {
      val updatedConf = BrandingConf(
        displayBar = false,
        barColor = JsonColor(0.1, 0.1, 0.1, 1.0),
        displayLabel = false,
        labelText = "Updated Label",
        labelColor = JsonColor(0.9, 0.9, 0.9, 1.0),
        wideLogo = Logo(enable = false, name = Some("updated_wide_logo.png"), data = None),
        smallLogo = Logo(enable = false, name = Some("updated_small_logo.png"), data = None),
        displayBarLogin = false,
        displayMotd = false,
        motd = "Updated MOTD"
      )
      service.updateConf(updatedConf).runNow must beEqualTo(updatedConf)

      val updatedConfFromFile = tempFile.contentAsString.fromJson[BrandingConf]
      updatedConfFromFile must beEqualTo(Right(updatedConf))

      service.getConf.runNow must beEqualTo(updatedConf)
    }

  }

  override def afterAll(): Unit = {
    tempFile.delete()
  }
}
