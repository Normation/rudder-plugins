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

import zio.json.*

// This case class is serialized with it's parameters directly in json
// Changing a parameter name impacts Rest api and parsing in Elm app and file format.
// Migration must be done at least for config file format.
final case class BrandingConf(
    displayBar:      Boolean,
    barColor:        JsonColor,
    displayLabel:    Boolean,
    labelText:       String,
    labelColor:      JsonColor,
    wideLogo:        Logo,
    smallLogo:       Logo,
    displayBarLogin: Boolean,
    displayMotd:     Boolean,
    motd:            String
)

// for compat with previous version of plugin
final private case class BrandingConfV5_0(
    displayBar:       Boolean,
    barColor:         JsonColor,
    displayLabel:     Boolean,
    labelText:        String,
    labelColor:       JsonColor,
    enableLogo:       Boolean,
    displayFavIcon:   Boolean,
    displaySmallLogo: Boolean,
    displayBigLogo:   Boolean,
    displayBarLogin:  Boolean,
    displayLoginLogo: Boolean,
    displayMotd:      Boolean,
    motd:             String
) {
  def toCurrent = BrandingConf(
    displayBar,
    barColor,
    displayLabel,
    labelText,
    labelColor,
    Logo(displayBigLogo, None, None),
    Logo(displaySmallLogo, None, None),
    displayBarLogin,
    displayMotd,
    motd
  )
}

final case class JsonColor(
    red:   Double,
    green: Double,
    blue:  Double,
    alpha: Double
) {
  def toRgba = s"rgba(${red * 100}%, ${green * 100}%, ${blue * 100}%, ${alpha})"
}

object JsonColor {
  implicit val encoder: JsonEncoder[JsonColor] = DeriveJsonEncoder.gen[JsonColor]
  implicit val decoder: JsonDecoder[JsonColor] = DeriveJsonDecoder.gen[JsonColor]
}

// This case class is serialized with it's parameters directly in json
// Changing a parameter name impacts Rest api and parsing in Elm app and file format.
// Migration must be done at least for config file format.
final case class Logo(enable: Boolean, name: Option[String], data: Option[String]) {
  def loginLogo = (enable, data) match {
    case (true, Some(d)) => (<div class="custom-branding-logo" style={"background-image: url(" ++ d ++ ");"}></div>)
    case (_, _)          => <img src="/images/logo-rudder.svg" data-lift="with-cached-resource" alt="Rudder"/>
  }

  def commonWideLogo  = (enable, data) match {
    case (true, Some(d)) =>
      <span class="custom-branding-logo" style={"background-image: url(" ++ d ++ ");"}>
        <style>
          .logo-lg{{display: flex !important; height: 50px; padding: 5px;}}
          .sidebar-collapse .logo-lg{{display: none !important;}}
        </style>
      </span>
    case (_, _)          => <img src="/images/logo-rudder-white.svg" data-lift="with-cached-resource" alt="Rudder"/>
  }
  def commonSmallLogo = (enable, data) match {
    case (true, Some(d)) =>
      <span class="custom-branding-logo" style={"background-image: url(" ++ d ++ ");"}>
        <style>
          /* LOGO SM */
          .sidebar-collapse .treeview.footer{{
            display: none !important;
          }}
          @media (min-width: 768px){{
            .sidebar-mini.sidebar-collapse .main-header .logo>.logo-mini {{
              display: flex; height: 50px; padding: 5px;
            }}
          }}
        </style>
      </span>
    case (_, _)          => <img src="/images/logo-rudder-sm.svg" data-lift="with-cached-resource" alt="Rudder"/>
  }
}

object Logo {
  implicit val encoder: JsonEncoder[Logo] = DeriveJsonEncoder.gen[Logo]
  implicit val decoder: JsonDecoder[Logo] = DeriveJsonDecoder.gen[Logo]
}

object BrandingConf {
  implicit val encoder: JsonEncoder[BrandingConf] = DeriveJsonEncoder.gen[BrandingConf]

  // decoding needs compatibility with previous version of plugin when decoding
  implicit val decoder: JsonDecoder[BrandingConf] = {
    val brandingConfV5_0Decoder = DeriveJsonDecoder.gen[BrandingConfV5_0]
    val brandingConfDecoder     = DeriveJsonDecoder.gen[BrandingConf]
    brandingConfV5_0Decoder.map(_.toCurrent) <> brandingConfDecoder
  }
}
