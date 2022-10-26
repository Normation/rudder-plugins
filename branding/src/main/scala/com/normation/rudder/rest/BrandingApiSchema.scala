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

package com.normation.rudder.rest

import com.normation.rudder.api.HttpAction._
import sourcecode.Line

/*
 * these files need to be in that package because they need access to the
 * package-protected `z` methods.
 */

sealed trait BrandingApiSchema extends EndpointSchema with GeneralApi with SortIndex

object BrandingApiEndpoints extends ApiModuleProvider[BrandingApiSchema] {
  import EndpointSchema.syntax._
  final case object GetBrandingConf extends BrandingApiSchema with ZeroParam with StartsAtVersion10 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Get branding plugin configuration"
    val (action, path) = GET / "branding"
    val dataContainer: Option[String] = None

  }

  final case object UpdateBrandingConf extends BrandingApiSchema with ZeroParam with StartsAtVersion10 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Update branding plugin configuration"
    val (action, path) = POST / "branding"
    val dataContainer: Option[String] = None
  }

  final case object ReloadBrandingConf extends BrandingApiSchema with ZeroParam with StartsAtVersion10 with SortIndex {
    val z              = implicitly[Line].value
    val description    = "Reload branding plugin configuration from config file"
    val (action, path) = POST / "branding" / "reload"
    val dataContainer: Option[String] = None
  }

  def endpoints = ca.mrvisser.sealerate.values[BrandingApiSchema].toList.sortBy(_.z)
}
