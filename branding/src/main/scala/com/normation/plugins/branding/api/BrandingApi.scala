package com.normation.rudder.rest

import com.normation.eventlog.EventActor
import com.normation.plugins.branding.{BrandingConf, BrandingConfService}
import com.normation.rudder.api.HttpAction._
import com.normation.rudder.rest.lift.{DefaultParams, LiftApiModule, LiftApiModule0, LiftApiModuleProvider}
import com.normation.utils.StringUuidGenerator
import net.liftweb.common.Box
import net.liftweb.json.JsonAST.JValue
import net.liftweb.http.{LiftResponse, Req}
import sourcecode.Line
import com.normation.box._

sealed trait BrandingApiSchema extends EndpointSchema with GeneralApi with SortIndex
object BrandingApiEndpoints extends ApiModuleProvider[BrandingApiSchema] {
  import EndpointSchema.syntax._
  final case object GetBrandingConf extends BrandingApiSchema with ZeroParam with StartsAtVersion10 with SortIndex {
    val z = implicitly[Line].value
    val description = "Get branding plugin configuration"
    val (action, path)  = GET / "branding"
  }

  final case object UpdateBrandingConf extends BrandingApiSchema with ZeroParam with StartsAtVersion10 with SortIndex {
    val z = implicitly[Line].value
    val description = "Update branding plugin configuration"
    val (action, path)  = POST / "branding"
  }

  final case object ReloadBrandingConf extends BrandingApiSchema with ZeroParam with StartsAtVersion10 with SortIndex {
    val z = implicitly[Line].value
    val description = "Reload branding plugin configuration from config file"
    val (action, path)  = POST / "branding" / "reload"
  }

  def endpoints = ca.mrvisser.sealerate.values[BrandingApiSchema].toList.sortBy( _.z )
}


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
