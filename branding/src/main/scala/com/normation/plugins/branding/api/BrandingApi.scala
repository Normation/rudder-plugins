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

import com.normation.box._
import com.normation.eventlog.EventActor
import com.normation.plugins.branding.BrandingConf
import com.normation.plugins.branding.BrandingConfService
import com.normation.rudder.rest._
import com.normation.rudder.rest.lift.DefaultParams
import com.normation.rudder.rest.lift.LiftApiModule
import com.normation.rudder.rest.lift.LiftApiModule0
import com.normation.rudder.rest.lift.LiftApiModuleProvider
import com.normation.utils.StringUuidGenerator
import net.liftweb.common.Box
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json.JsonAST.JValue


class BrandingApi (
    brandingApiService: BrandingApiService
  , restExtractorService: RestExtractorService
  , uuidGen: StringUuidGenerator
) extends LiftApiModuleProvider[BrandingApiSchema] {

  val dataName = "branding"

  def schemas = BrandingApiEndpoints

  def response(function: Box[JValue], req: Req, errorMessage: String, id: Option[String])(implicit action: String): LiftResponse = {
    RestUtils.response(restExtractorService, dataName, id)(function, req, errorMessage)
  }

  type ActionType = RestUtils.ActionType

  def actionResponse(function: Box[ActionType], req: Req, errorMessage: String, id: Option[String], actor: EventActor)(implicit action: String): LiftResponse = {
    RestUtils.actionResponse2(restExtractorService, dataName, uuidGen, id)(function, req, errorMessage)(action, actor)
  }


  def getLiftEndpoints(): List[LiftApiModule] = {
    modules
  }

  private[this] lazy val modules = {
    GetBrandingConf    ::
    UpdateBrandingConf ::
    ReloadBrandingConf ::
    Nil
  }

  object GetBrandingConf extends LiftApiModule0 {
    val schema = BrandingApiEndpoints.GetBrandingConf
    val restExtractor = restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "getBrandingConf"
      response(brandingApiService.getConf(), req, "Could not fetch branding plugin configuration", None)
    }
  }

  object ReloadBrandingConf extends LiftApiModule0 {
    val schema = BrandingApiEndpoints.ReloadBrandingConf
    val restExtractor = restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "getBrandingConf"
      response(brandingApiService.reloadConf(), req, "Could not reload branding plugin configuration", None)
    }
  }

  object UpdateBrandingConf extends LiftApiModule0 {
    val schema = BrandingApiEndpoints.UpdateBrandingConf
    val restExtractor = restExtractorService

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      implicit val action = "updateBRandingConf"
      val result = for {
        json    <- req.json
        newConf <- BrandingConf.parse(json)
        result  <- brandingApiService.update(newConf).toBox
      } yield {
        result
      }

      response(result, req, s"Could not update Branding plugin configuration", None)
    }
  }

}

class BrandingApiService (
    brandingConfService: BrandingConfService
) {
  def getConf() = {
    brandingConfService.getConf.map(BrandingConf.serialize)
  }
  def reloadConf() = {
    brandingConfService.reloadCache.map(BrandingConf.serialize).toBox
  }
  def update(newConf : BrandingConf) = {
    brandingConfService.updateConf(newConf).map(BrandingConf.serialize)
  }
}
