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

import net.liftweb.common._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{NoTypeHints, Serialization}

// This case class is serialized with it's parameters directly in json
// Changing a parameter name impacts Rest api and parsing in Elm app
final case class BrandingConf (
    displayBar       : Boolean
  , barColor         : JsonColor
  , displayLabel     : Boolean
  , labelText        : String
  , labelColor       : JsonColor
  , enableLogo       : Boolean
  , displayFavIcon   : Boolean
  , displaySmallLogo : Boolean
  , displayBigLogo   : Boolean
  , displayBarLogin  : Boolean
  , displayLoginLogo : Boolean
  , displayMotd      : Boolean
  , motd             : String
)

final case class JsonColor (
    red   : Double
  , green : Double
  , blue  : Double
  , alpha : Double
) {
  def toRgba= s"rgba(${red*100}%, ${green*100}%, ${blue*100}%, ${alpha})"
}


object BrandingConf {
  implicit val formats = Serialization.formats(NoTypeHints)
  def serialize(conf : BrandingConf) : JValue = {
    import net.liftweb.json.Extraction.decompose
    decompose(conf)
  }
  def parse(jValue: JValue) : Box[BrandingConf] = {
    import net.liftweb.json.Extraction.extractOpt
    Box(extractOpt[BrandingConf](jValue)) ?~! "Could not extract Branding plugin configuration from json"
  }
}

