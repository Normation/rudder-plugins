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

package com.normation.plugins.branding.api

import com.normation.plugins.branding.BrandingConf
import com.normation.plugins.branding.BrandingConfService
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.rest.*
import com.normation.rudder.rest.RudderJsonRequest.*
import com.normation.rudder.rest.implicits.*
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.utils.StringUuidGenerator
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import zio.json.*

class BrandingApi(
    brandingApiService: BrandingApiService,
    uuidGen:            StringUuidGenerator
) extends LiftApiModuleProvider[BrandingApiSchema] {

  val dataName = "branding"

  override def schemas: ApiModuleProvider[BrandingApiSchema] = BrandingApiEndpoints

  def getLiftEndpoints(): List[LiftApiModule] = {
    modules
  }

  private lazy val modules = {
    GetBrandingConf ::
    UpdateBrandingConf ::
    ReloadBrandingConf ::
    Nil
  }

  object GetBrandingConf extends LiftApiModule0 {
    val schema: BrandingApiEndpoints.GetBrandingConf.type = BrandingApiEndpoints.GetBrandingConf

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      brandingApiService
        .getConf()
        .chainError("Could not fetch branding plugin configuration")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object ReloadBrandingConf extends LiftApiModule0 {
    val schema: BrandingApiEndpoints.ReloadBrandingConf.type = BrandingApiEndpoints.ReloadBrandingConf

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      brandingApiService
        .reloadConf()
        .chainError("Could not reload branding plugin configuration")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object UpdateBrandingConf extends LiftApiModule0 {
    val schema: BrandingApiEndpoints.UpdateBrandingConf.type = BrandingApiEndpoints.UpdateBrandingConf

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      (for {
        conf   <- req.fromJson[BrandingConf].toIO
        result <- brandingApiService.update(conf)
      } yield {
        result
      }).chainError("Could not update Branding plugin configuration").toLiftResponseOne(params, schema, None)
    }
  }

}

object BrandingApiService {
  final case class RestBrandingResponse(
      branding: BrandingConf
  )

  implicit val encoder: JsonEncoder[RestBrandingResponse] = DeriveJsonEncoder.gen[RestBrandingResponse]
}

class BrandingApiService(
    brandingConfService: BrandingConfService
) {
  import BrandingApiService.*

  def getConf()                     = {
    brandingConfService.getConf.map(RestBrandingResponse(_))
  }
  def reloadConf()                  = {
    brandingConfService.reloadCache.map(RestBrandingResponse(_))
  }
  def update(newConf: BrandingConf) = {
    brandingConfService.updateConf(newConf).map(RestBrandingResponse(_))
  }
}
