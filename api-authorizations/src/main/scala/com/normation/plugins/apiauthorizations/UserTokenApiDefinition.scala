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

import bootstrap.liftweb.AuthBackendProvidersManager
import com.normation.errors.*
import com.normation.eventlog.ModificationId
import com.normation.rudder.api.*
import com.normation.rudder.facts.nodes.NodeSecurityContext
import com.normation.rudder.repository.ldap.JsonApiAuthz
import com.normation.rudder.rest.*
import com.normation.rudder.rest.data.*
import com.normation.rudder.rest.implicits.ToLiftResponseOne
import com.normation.rudder.rest.lift.*
import com.normation.rudder.users.UserRepository
import com.normation.utils.DateFormaterService
import com.normation.utils.StringUuidGenerator
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.syntax.*
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import org.joda.time.DateTime
import zio.json.*

class UserApiImpl(
    readApi:                     RoApiAccountRepository,
    writeApi:                    WoApiAccountRepository,
    userRepository:              UserRepository,
    authBackendProvidersManager: AuthBackendProvidersManager,
    tokenGenerator:              TokenGenerator,
    uuidGen:                     StringUuidGenerator
) extends LiftApiModuleProvider[UserApi] {
  api =>

  import UserApiImpl.*

  def schemas: ApiModuleProvider[UserApi] = UserApi

  def getLiftEndpoints(): List[LiftApiModule] = {
    UserApi.endpoints.map {
      case UserApi.GetTokenFeatureStatus => GetTokenFeatureStatus
      case UserApi.GetApiToken           => GetApiToken
      case UserApi.CreateApiToken        => CreateApiToken
      case UserApi.DeleteApiToken        => DeleteApiToken
      case UserApi.UpdateApiToken        => UpdateApiToken
    }
  }

  /*
   * By convention, an USER API token has the user login for identifier and name
   * (so that we enforce only one token by user - that could be change in the future
   * by only enforcing the name)
   */

  object GetTokenFeatureStatus extends LiftApiModule0 {
    val schema: UserApi.GetTokenFeatureStatus.type = UserApi.GetTokenFeatureStatus

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val username = authzToken.qc.actor.name
      (for {
        userInfo <- userRepository.get(username).notOptional("Could not get token feature status for unknown user in base")
        provider  = userInfo.managedBy
        status   <- authBackendProvidersManager
                      .getProviderProperties()
                      .get(provider)
                      .map(_.restTokenFeatureSwitch)
                      .notOptional("Could not get token feature status for unknown provider")
      } yield {
        status.name
      })
        .chainError(s"Error when trying to get user '${username}' API token configuration status")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object GetApiToken extends LiftApiModule0 {
    val schema: UserApi.GetApiToken.type = UserApi.GetApiToken

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      readApi
        .getById(ApiAccountId(authzToken.qc.actor.name))
        .map(RestAccountsResponse.fromRedacted(_))
        .chainError(s"Error when trying to get user '${authzToken.qc.actor.name}' API token")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object CreateApiToken extends LiftApiModule0 {
    val schema: UserApi.CreateApiToken.type = UserApi.CreateApiToken

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      val now     = DateTime.now
      val secret  = ApiTokenSecret.generate(tokenGenerator)
      val hash    = ApiTokenHash.fromSecret(secret)
      val account = ApiAccount(
        ApiAccountId(authzToken.qc.actor.name),
        ApiAccountKind.User,
        ApiAccountName(authzToken.qc.actor.name),
        Some(hash),
        s"API token for user '${authzToken.qc.actor.name}'",
        isEnabled = true,
        now,
        now,
        // set "no tenant" - they will be updated dynamically when perms are resolved for that token in AppConfigAuth
        NodeSecurityContext.None
      )

      writeApi
        .save(account, ModificationId(uuidGen.newUuid), authzToken.qc.actor)
        .map(RestAccountsResponse.fromUnredacted(_, secret.exposeSecret()))
        .chainError(s"Error when trying to save user '${authzToken.qc.actor.name}' API token")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object DeleteApiToken extends LiftApiModule0 {
    val schema: UserApi.DeleteApiToken.type = UserApi.DeleteApiToken

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      writeApi
        .delete(ApiAccountId(authzToken.qc.actor.name), ModificationId(uuidGen.newUuid), authzToken.qc.actor)
        .map(RestAccountIdResponse(_))
        .chainError(s"Error when trying to delete user '${authzToken.qc.actor.name}' API token")
        .toLiftResponseOne(params, schema, None)
    }
  }

  object UpdateApiToken extends LiftApiModule0 {
    val schema: UserApi.UpdateApiToken.type = UserApi.UpdateApiToken

    def process0(version: ApiVersion, path: ApiPath, req: Req, params: DefaultParams, authzToken: AuthzToken): LiftResponse = {
      readApi
        .getById(ApiAccountId(authzToken.qc.actor.name))
        .map(RestAccountsResponse.fromRedacted(_))
        .chainError(s"Error when trying to get user '${authzToken.qc.actor.name}' API token")
        .toLiftResponseOne(params, schema, None)
    }
  }

}

object UserApiImpl {

  /**
   * The value that will be displayed in the API response for the token.
   */
  final case class ClearTextToken(value: String) extends AnyVal

  object ClearTextToken {

    implicit val encoder: JsonEncoder[ClearTextToken] = JsonEncoder[String].contramap(_.value)
  }

  final case class RestApiAccount(
      id:                              ApiAccountId,
      name:                            ApiAccountName,
      token:                           ClearTextToken,
      tokenGenerationDate:             DateTime,
      kind:                            ApiAccountType,
      description:                     String,
      creationDate:                    DateTime,
      @jsonField("enabled") isEnabled: Boolean,
      expirationDate:                  Option[String],
      expirationDateDefined:           Boolean,
      authorizationType:               Option[ApiAuthorizationKind],
      acl:                             Option[List[JsonApiAuthz]]
  )

  object RestApiAccount extends ApiAccountCodecs {
    implicit class ApiAccountOps(val account: ApiAccount) extends AnyVal {
      import ApiAccountKind.*
      def expirationDate: Option[String] = {
        account.kind match {
          case PublicApi(_, expirationDate) => expirationDate.map(DateFormaterService.getDisplayDateTimePicker)
          case User | System                => None
        }
      }

      def expirationDateDefined: Boolean = expirationDate.isDefined

      def authzType: Option[ApiAuthorizationKind] = {
        account.kind match {
          case PublicApi(authz, _) => Some(authz.kind)
          case User | System       => None
        }
      }

      def acl: Option[List[JsonApiAuthz]] = {
        import ApiAuthorization.*
        account.kind match {
          case PublicApi(authz, expirationDate) =>
            authz match {
              case None | RO | RW => Option.empty
              case ACL(acls)      => Some(acls.map(x => JsonApiAuthz(x.path.value, x.actions.toList.map(_.name))))
            }
          case User | System                    => Option.empty
        }
      }

      /**
        * Always hides any hashed token, and displays any clear-text token
        */
      def toRest = account.transformInto[RestApiAccount]

      /**
        * Always displays the passed secret token
        */
      def toRestWithSecret(secret: ClearTextToken) = account.transformInto[RestApiAccount].copy(token = secret)
    }

    implicit val transformer: Transformer[ApiAccount, RestApiAccount] = Transformer
      .define[ApiAccount, RestApiAccount]
      .withFieldConst(_.token, ClearTextToken("")) // if the hash need to be exposed, it's done post transformation
      .withFieldComputed(_.kind, _.kind.kind)
      .withFieldComputed(_.acl, _.acl)
      .withFieldComputed(_.expirationDate, _.expirationDate)
      .withFieldComputed(_.expirationDateDefined, _.expirationDateDefined)
      .withFieldComputed(
        _.authorizationType,
        _.authzType
      )
      .buildTransformer

    implicit val encoder: JsonEncoder[RestApiAccount] = DeriveJsonEncoder.gen[RestApiAccount]
  }

  /**
    * The format of the API response is a list of accounts (it usually contains a single element or is empty)
    */
  final case class RestAccountsResponse private (
      accounts: List[RestApiAccount]
  )

  object RestAccountsResponse {
    import RestApiAccount.*

    implicit val encoder: JsonEncoder[RestAccountsResponse] = DeriveJsonEncoder.gen[RestAccountsResponse]

    // The format of the API response is a list of accounts but contains only a single account, the secret is used to replace the token in the account
    private def apply(accounts: List[ApiAccount], secret: Option[ClearTextToken] = None): RestAccountsResponse = {
      new RestAccountsResponse(
        accounts.map(a => secret.map(a.toRestWithSecret(_)).getOrElse(a.toRest))
      )
    }

    def empty: RestAccountsResponse = RestAccountsResponse(Nil)

    /**
      * Displays the provided clear-text or hashed token for the api account
      */
    def fromUnredacted(account: ApiAccount, secret: String): RestAccountsResponse = {
      apply(List(account), Some(ClearTextToken(secret)))
    }

    /**
      * Hides the hashed token and displays the clear-text token
      */
    def fromRedacted(account: Option[ApiAccount]): RestAccountsResponse = {
      // Don't send hashes in response
      apply(account.toList)
    }
  }

  final case class RestAccountId(id: ApiAccountId)
  final case class RestAccountIdResponse private (
      accounts: RestAccountId
  )

  object RestAccountIdResponse extends ApiAccountCodecs {
    implicit val accountIdResponseEncoder: JsonEncoder[RestAccountId]         = DeriveJsonEncoder.gen[RestAccountId]
    implicit val encoder:                  JsonEncoder[RestAccountIdResponse] = DeriveJsonEncoder.gen[RestAccountIdResponse]

    def apply(account: ApiAccountId): RestAccountIdResponse = new RestAccountIdResponse(RestAccountId(account))
  }

}
