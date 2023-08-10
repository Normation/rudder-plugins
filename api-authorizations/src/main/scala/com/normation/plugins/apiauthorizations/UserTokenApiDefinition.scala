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

package com.normation.plugins.apiauthorizations

import com.normation.box._
import com.normation.eventlog.ModificationId
import com.normation.rudder.api._
import com.normation.rudder.apidata.ApiAccountSerialisation._
import com.normation.rudder.rest._
import com.normation.rudder.rest.{UserApi => API}
import com.normation.rudder.rest.lift._
import com.normation.utils.StringUuidGenerator
import net.liftweb.common.EmptyBox
import net.liftweb.common.Full
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.json._
import net.liftweb.json.JsonAST.JArray
import net.liftweb.json.JsonDSL._
import org.joda.time.DateTime

class UserApi(
    restExtractor:  RestExtractorService,
    readApi:        RoApiAccountRepository,
    writeApi:       WoApiAccountRepository,
    tokenGenerator: TokenGenerator,
    uuidGen:        StringUuidGenerator
) extends LiftApiModuleProvider[API] {
  api =>

  def schemas = API

  def getLiftEndpoints(): List[LiftApiModule] = {
    API.endpoints
      .map(e => {
        e match {
          case API.GetApiToken    => GetApiToken
          case API.CreateApiToken => CreateApiToken
          case API.DeleteApiToken => DeleteApiToken
          case API.UpdateApiToken => UpdateApiToken
        }
      })
      .toList
  }

  /*
   * By convention, an USER API token has the user login for identifier and name
   * (so that we enforce only one token by user - that could be change in the future
   * by only enforcing the name)
   */

  object GetApiToken extends LiftApiModule0 {
    val schema        = API.GetApiToken
    val restExtractor = api.restExtractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      readApi.getById(ApiAccountId(authzToken.actor.name)).toBox match {
        case Full(Some(account)) =>
          val filtered = account.copy(token = if (account.token.isHashed) {
            // Don't send hashes
            ApiToken("")
          } else {
            account.token
          })
          val accounts: JValue = ("accounts" -> JArray(List(filtered.toJson)))
          RestUtils.toJsonResponse(None, accounts)(schema.name, true)

        case Full(None) =>
          val accounts: JValue = ("accounts" -> JArray(Nil))
          RestUtils.toJsonResponse(None, accounts)(schema.name, true)

        case eb: EmptyBox =>
          val e = eb ?~! s"Error when trying to get user '${authzToken.actor.name}' API token"
          RestUtils.toJsonError(None, e.messageChain)(schema.name, true)
      }
    }
  }

  object CreateApiToken extends LiftApiModule0 {
    val schema        = API.CreateApiToken
    val restExtractor = api.restExtractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val now     = DateTime.now
      val secret  = ApiToken.generate_secret(tokenGenerator)
      val hash    = ApiToken.hash(secret)
      val account = ApiAccount(
        ApiAccountId(authzToken.actor.name),
        ApiAccountKind.User,
        ApiAccountName(authzToken.actor.name),
        ApiToken(hash),
        s"API token for user '${authzToken.actor.name}'",
        isEnabled = true,
        now,
        now
      )

      writeApi.save(account, ModificationId(uuidGen.newUuid), authzToken.actor).toBox match {
        case Full(account) =>
          val accounts: JValue = ("accounts" -> JArray(
            List(
              account
                .copy(
                  // Send clear text secret
                  token = ApiToken(secret)
                )
                .toJson
            )
          ))
          RestUtils.toJsonResponse(None, accounts)(schema.name, true)

        case eb: EmptyBox =>
          val e = eb ?~! s"Error when trying to save user '${authzToken.actor.name}' API token"
          RestUtils.toJsonError(None, e.messageChain)(schema.name, true)
      }
    }
  }

  object DeleteApiToken extends LiftApiModule0 {
    val schema        = API.DeleteApiToken
    val restExtractor = api.restExtractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      writeApi.delete(ApiAccountId(authzToken.actor.name), ModificationId(uuidGen.newUuid), authzToken.actor).toBox match {
        case Full(account) =>
          val accounts: JValue = ("accounts" -> ("id" -> account.value))
          RestUtils.toJsonResponse(None, accounts)(schema.name, true)

        case eb: EmptyBox =>
          val e = eb ?~! s"Error when trying to delete user '${authzToken.actor.name}' API token"
          RestUtils.toJsonError(None, e.messageChain)(schema.name, true)
      }
    }
  }

  object UpdateApiToken extends LiftApiModule0 {
    val schema        = API.UpdateApiToken
    val restExtractor = api.restExtractor
    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      readApi.getById(ApiAccountId(authzToken.actor.name)).toBox match {
        case Full(Some(account)) =>
          val accounts: JValue = ("accounts" -> JArray(List(account.toJson)))
          RestUtils.toJsonResponse(None, accounts)(schema.name, true)

        case Full(None) =>
          val accounts: JValue = ("accounts" -> JArray(Nil))
          RestUtils.toJsonResponse(None, accounts)(schema.name, true)

        case eb: EmptyBox =>
          val e = eb ?~! s"Error when trying to get user '${authzToken.actor.name}' API token"
          RestUtils.toJsonError(None, e.messageChain)(schema.name, true)
      }
    }
  }

}
