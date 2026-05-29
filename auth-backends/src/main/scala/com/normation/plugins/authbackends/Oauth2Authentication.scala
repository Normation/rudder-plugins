/*
 *************************************************************************************
 * Copyright 2021 Normation SAS
 *************************************************************************************
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * In accordance with the terms of section 7 (7. Additional Terms.) of
 * the GNU Affero GPL v3, the copyright holders add the following
 * Additional permissions:
 * Notwithstanding to the terms of section 5 (5. Conveying Modified Source
 * Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
 * licence, when you create a Related Module, this Related Module is
 * not considered as a part of the work and may be distributed under the
 * license agreement of your choice.
 * A "Related Module" means a set of sources files including their
 * documentation that, without modification of the Source Code, enables
 * supplementary functions or services in addition to those offered by
 * the Software.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
 *
 *************************************************************************************
 */

package com.normation.plugins.authbackends

import better.files.File
import bootstrap.liftweb.LogoutPostAction
import bootstrap.liftweb.UserLogout
import bootstrap.rudder.plugin.BuildLogout
import cats.Monoid
import cats.Show
import cats.data.NonEmptyList
import cats.syntax.either.*
import cats.syntax.semigroup.*
import cats.syntax.show.*
import com.nimbusds.jose.{Option as _, *}
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.*
import com.normation.errors.*
import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.readProviders
import com.typesafe.config.Config
import enumeratum.Enum
import enumeratum.EnumEntry
import enumeratum.EnumEntry.Lowercase
import java.io.FileReader
import java.security.*
import java.security.interfaces.{ECKey as _, RSAKey as _, *}
import java.security.spec.*
import java.time.format.DateTimeParseException
import java.util
import java.util.concurrent.TimeUnit
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*
import zio.{Duration as _, *}
import zio.syntax.*

/**
 * This file contain logic related to Oauth2/OpenID Connect authentication and
 * user data sharing.
 *
 * In spring security, Oauth2 is implemented in a dedicated filter that
 * intercept request before other authentication methods (the exact order is
 * defined in: https://docs.spring.io/spring-security/site/docs/5.3.9.RELEASE/reference/html5/#servlet-security-filters)
 *
 * It requires registration to oauth2 services as a client, and several oauth2
 * services can be configured: this is contractualized by implementing the `InMemoryOAuth2AuthorizedClientService` trait
 * In our case, the configuration is done in the rudder property configuration file, and
 * we just parse the different client and register them in an in memory immutable map.
 *
 * We then have two broad cases:
 * - delegating authentication for *users* to an IdP through an interactive process which is called `authorization_code`
 *   workflow.
 *   One of the important aspect of that workflow is the identifier used to match the user between the IdP and Rudder.
 *   For now, that identifier must be used as Rudder login.
 * - delegating authentication for *API tokens* to an IdP through a JWT bearer token created by an IdP and for which we
 *   check the authenticity through a process which is called `client_credentials` workflow.
 *
 */

sealed trait ProvidedList {
  def debugName:         String
  def enabled:           Boolean
  def attributeName:     String
  def overrides:         Boolean
  def restrictToMapping: Boolean
  def mapping:           Map[String, String]

  override def toString: String = if (enabled) s"enabled, list obtained from attribute: ${attributeName} (override: ${overrides})"
  else "disabled"
}

final case class ProvidedRoles(
    enabled:           Boolean,
    attributeName:     String,
    overrides:         Boolean,
    restrictToMapping: Boolean,
    mapping:           Map[String, String]
) extends ProvidedList {
  val debugName = "roles"
}

final case class ProvidedTenants(
    enabled:           Boolean,
    attributeName:     String,
    overrides:         Boolean,
    restrictToMapping: Boolean,
    mapping:           Map[String, String]
) extends ProvidedList {
  val debugName = "tenants"
}

final case class JwtAudience(
    check: Boolean,
    value: String
)

/**
 * Validated config for JWT parsing (JWS or/and JWE) using algorithms (JWA) and keys (JWK) that we support.
 * This will ultimately allow to configure a [[org.springframework.security.oauth2.jwt.NimbusJwtDecoder]].
 *
 * @param jwkSetUri always enabled since it is mandatory in ClientRegistration
 * @param discoverJwsAlgorithms whether to make an initial query to the JWKS URI
 *                              to support other signature algorithms than RS256 (Nimbus JWT decoder behavior).
 * @param jweAlgorithms algorithms to attempt JWE decoding with
 * @param jweEncryptionMethods encryption to attempt JWE decoding, along with [[jweAlgorithms]]
 * @param keys all keys configuration that would be used for JWS/JWE
 */
final case class JwtConfig(
    jwkSetUri:             String,
    discoverJwsAlgorithms: Option[Boolean],
    jweAlgorithms:         Set[JWEAlgorithm],
    jweEncryptionMethods:  Set[EncryptionMethod],
    keys:                  Map[String, JwkConfig]
) {
  import JwtConfig.given

  val jwkSet: ImmutableJWKSet[SecurityContext] = new ImmutableJWKSet[SecurityContext](
    JWKSet(keys.map((_, config) => config.jwk).toList.asJava)
  )

  /**
   * By default, use all algorithms and encryption keys at "recommended" level in Nimbus,
   * plus RSA ones because it is very common.
   *
   * Deprecated ones from Nimbus are not included.
   *
   * See [[com.nimbusds.jose.crypto.MultiDecrypter]] for more details
   */
  private def defaultJweKeySelectors: NonEmptyList[JWEKeySelector[SecurityContext]] = {
    for {
      enc <- NonEmptyList.of(
               EncryptionMethod.A128CBC_HS256,
               EncryptionMethod.A256CBC_HS512,
               //
               EncryptionMethod.A128GCM,
               EncryptionMethod.A256GCM
             )
      alg <- NonEmptyList.of(
               JWEAlgorithm.ECDH_ES,
               JWEAlgorithm.ECDH_ES_A128KW,
               JWEAlgorithm.ECDH_ES_A256KW,
               //
               JWEAlgorithm.RSA_OAEP_256,
               JWEAlgorithm.RSA_OAEP_384,
               JWEAlgorithm.RSA_OAEP_512
             )
    } yield {
      JWEDecryptionKeySelector[SecurityContext](alg, enc, jwkSet)
    }
  }

  val jweKeySelector: JWEKeySelector[SecurityContext] = {
    val selectors = for {
      alg <- jweAlgorithms
      enc <- jweEncryptionMethods
    } yield {
      JWEDecryptionKeySelector[SecurityContext](alg, enc, jwkSet): JWEKeySelector[SecurityContext]
    }
    selectors
      .reduceOption(_ |+| _)
      .getOrElse(defaultJweKeySelectors.reduce)
  }

  val decoder: NimbusJwtDecoder = {
    NimbusJwtDecoder
      .withJwkSetUri(jwkSetUri)
      // default to false for compatibility
      .pipe(if (discoverJwsAlgorithms.getOrElse(false)) _.discoverJwsAlgorithms() else identity)
      // caching of JWKS should be configurable
      // .cache(...)
      .jwtProcessorCustomizer(_.setJWEKeySelector(jweKeySelector))
      .build
  }

  def debugConfiguration: String = {
    s"keys: ${keys.toList.map(_.show).mkString("[", ",", "]")}, supported algorithms: ${jweAlgorithms.mkString("[", ",", "]")}, encryption methods: ${jweEncryptionMethods
        .mkString("[", ",", "]")}"
  }
}

/**
 * Key configuration that allows converting it to a [[com.nimbusds.jose.jwk.JWK]]
 */
final case class JwkConfig(
    kid:    String,
    `type`: JwtConfig.KeyType,
    use:    Option[
      JwtConfig.Use
    ], // it could be for both (so it's optional in Nimbus), it could be also be inferred from certificate (KeyUse.from)
    config: Option[JwtConfig.ValidatedJwk]
) {
  def jwk: JWK = config
    .map(_.jwk)
    .getOrElse(
      JWK.parse(s"""{"kid":"${kid}","kty":"${`type`}","use":${use.fold("null")(e => "\"" + e.entryName + "\"")}}""")
    )
}
object JwkConfig {
  given Show[JwkConfig] = Show.fromToString
}

/**
 * Key configuration need specific mandatory/optional parameters depending on the algorithms.
 * For example: private (+ public) key for asymmetric algorithms.
 *
 * We only define what we support in this object.
 */
object JwtConfig {
  import com.nimbusds.jose.jwk.KeyType as KT
  // no support yet for symmetric algorithms
  type KeyType = KT.RSA.type | KT.EC.type

  sealed trait Use extends EnumEntry with Lowercase
  object Use       extends Enum[Use] {
    case object Sig extends Use
    case object Enc extends Use
    override def values: IndexedSeq[Use] = findValues
  }

  sealed trait ValidatedJwk {
    def jwk: JWK
  }

  type KeyConfig = PrivateFile | PublicPrivateFile
  object KeyConfig {
    import PrivateFile.given
    import PublicPrivateFile.given
    given Show[KeyConfig] = Show.show {
      case f: PrivateFile       => f.show
      case f: PublicPrivateFile => f.show
    }
  }

  opaque type PrivateFile = File
  object PrivateFile       {
    def apply(f: File): PrivateFile = f
    given Show[PrivateFile] = Show.show(f => s"PrivateFile(privateFile = ${f})")
  }
  final case class PublicPrivateFile(publicFile: File, privateFile: File)
  object PublicPrivateFile {
    given Show[PublicPrivateFile] =
      Show.show(f => s"PublicPrivateFile(publicFile = ${f.publicFile}, privateFile = ${f.privateFile}")
  }

  // We always expect a private key, and should be able to derive the public key
  sealed trait KeyFileConfig[T] {
    extension (t: T) {
      def load(): IOResult[(PublicKey, PrivateKey)]
    }
  }
  object KeyFileConfig          {
    given KeyFileConfig[PublicPrivateFile] = new KeyFileConfig[PublicPrivateFile] {
      extension (publicPrivateFile: PublicPrivateFile) {
        def load(): ZIO[Any, RudderError, (PublicKey, PrivateKey)] = {
          ValidatedJwk.loadPublicKey(publicPrivateFile.publicFile) zipPar ValidatedJwk.loadPrivateKey(
            publicPrivateFile.privateFile
          )
        }
      }
    }

    given (using keyType: KeyType): KeyFileConfig[PrivateFile] = new KeyFileConfig[PrivateFile] {
      extension (privateFile: PrivateFile) {
        def load(): ZIO[Any, RudderError, (PublicKey, PrivateKey)] = {
          ValidatedJwk
            .loadPrivateKey(privateFile)
            .flatMap(priv => {
              IOResult.attempt(s"Error generating public key from private key file ${privateFile.pathAsString}") {
                keyType match {
                  case KT.RSA =>
                    val privRSA       = priv.asInstanceOf[RSAPrivateCrtKey]
                    val publicKeySpec = new RSAPublicKeySpec(privRSA.getModulus, privRSA.getPublicExponent)
                    val keyFactory    = KeyFactory.getInstance("RSA")
                    val pub           = keyFactory.generatePublic(publicKeySpec)
                    (pub, priv)
                  case KT.EC  =>
                    // https://stackoverflow.com/questions/42639620/generate-ecpublickey-from-ecprivatekey
                    // with bouncycastle utils
                    // Nimbus always require a public key and deriving one may not always succeed, since getParams could be null
                    val privEC     = priv.asInstanceOf[ECPrivateKey]
                    val params     = privEC.getParams
                    val curve      = EC5Util.convertCurve(params.getCurve)
                    val g          = EC5Util.convertPoint(curve, params.getGenerator)
                    val q          = g.multiply(privEC.getS).normalize
                    val point      = new ECPoint(q.getAffineXCoord.toBigInteger, q.getAffineYCoord.toBigInteger)
                    val pubSpec    = new ECPublicKeySpec(point, params)
                    val keyFactory = KeyFactory.getInstance("EC")
                    val pub        = keyFactory.generatePublic(pubSpec)
                    (pub, priv)
                }
              }
            })
        }
      }
    }
  }

  // curried jwk avoids using full "jwk" in toString, equals: since it has secret data, it should not be output
  final case class EC(keys: KeyConfig)(override val jwk: ECKey)   extends ValidatedJwk
  final case class RSA(keys: KeyConfig)(override val jwk: RSAKey) extends ValidatedJwk

  object ValidatedJwk { // validate using BouncyCastle
    private val converter: JcaPEMKeyConverter = new JcaPEMKeyConverter().setProvider("BC")

    def from(
        kid:         String,
        keyType:     KeyType,
        publicFile:  Option[File],
        privateFile: Option[File]
    ): IOResult[Option[ValidatedJwk]] = {
      given keyId: String = kid
      given KeyType = keyType
      (publicFile, privateFile) match {
        case (None, None)                          => ZIO.none
        case (Some(_), None)                       => Inconsistency(s"Private key file configuration is missing for JWT key ${kid}").fail
        case (None, Some(privateFile))             =>
          val config = PrivateFile(privateFile)
          load(config).flatMap(from(_, _)(using config = config)).asSome
        case (Some(publicFile), Some(privateFile)) =>
          val config = PublicPrivateFile(publicFile, privateFile)
          load(config).flatMap(from(_, _)(using config = config)).asSome
      }
    }

    private def load[T <: KeyConfig: KeyFileConfig](config: T): IOResult[(PublicKey, PrivateKey)] = {
      config.load()
    }

    private[authbackends] def from(pub: PublicKey, priv: PrivateKey)(using
        kid:     String,
        keyType: KeyType,
        config:  KeyConfig
    ): IOResult[ValidatedJwk] = {
      IOResult.attempt(s"Invalid key matching for ${keyType} key '${kid}', please raise the issue to Rudder developer team") {
        keyType match {
          case KT.RSA =>
            RSA(config)(
              new RSAKey.Builder(pub.asInstanceOf[RSAPublicKey])
                .privateKey(priv.asInstanceOf[RSAPrivateKey])
                .keyID(kid)
                .build()
            )
          case KT.EC  =>
            val ecPub = pub.asInstanceOf[ECPublicKey]
            val curve = Curve.forECParameterSpec(ecPub.getParams)
            EC(config)(
              new ECKey.Builder(curve, ecPub)
                .privateKey(priv.asInstanceOf[ECPrivateKey])
                .keyID(kid)
                .build()
            )
        }
      }
    }

    private[JwtConfig] def loadPublicKey(file: File): IOResult[PublicKey] = {
      parseKeyFile(file).flatMap {
        case spki: org.bouncycastle.asn1.x509.SubjectPublicKeyInfo =>
          IOResult.attempt(converter.getPublicKey(spki))
        case cert: org.bouncycastle.cert.X509CertificateHolder     =>
          IOResult.attempt(converter.getPublicKey(cert.getSubjectPublicKeyInfo))
        case other =>
          Inconsistency(s"Unsupported public key format: ${other.getClass.getName}").fail
      }.chainError(
        s"Could not read public key file at ${file.pathAsString}, please ensure correct file access permissions and file content (PKCS#8 or X509 certificate)"
      )
    }

    private[JwtConfig] def loadPrivateKey(file: File): IOResult[PrivateKey] = {
      parseKeyFile(file).flatMap {
        case pair:   org.bouncycastle.openssl.PEMKeyPair       =>
          IOResult.attempt(converter.getKeyPair(pair).getPrivate)
        case pkInfo: org.bouncycastle.asn1.pkcs.PrivateKeyInfo =>
          IOResult.attempt(converter.getPrivateKey(pkInfo))
        case _ =>
          Inconsistency("Unsupported private key format").fail
      }.chainError(
        s"Could not read private key file at ${file.pathAsString}, please ensure correct file access permissions and file content (PKCS#8)"
      )
    }

    private def parseKeyFile(file: File): IOResult[Object] = {
      IOResult.attempt(s"Could not read file ${file.pathAsString}") {
        val parser = new PEMParser(new FileReader(file.pathAsString))
        val obj    = parser.readObject()
        parser.close()
        obj
      }
    }
  }

  final private class CombinedJWEKeySelector(
      first:  JWEKeySelector[SecurityContext],
      second: JWEKeySelector[SecurityContext]
  ) extends JWEKeySelector[SecurityContext] {
    override def selectJWEKeys(jweHeader: JWEHeader, context: SecurityContext): util.List[? <: Key] = {
      // no thrown error because of ImmutableJWKSet
      (first.selectJWEKeys(jweHeader, context).asScala ++ second.selectJWEKeys(jweHeader, context).asScala).asJava
    }
  }

  private object EmptyJWEKeySelector extends JWEKeySelector[SecurityContext] {
    override def selectJWEKeys(header: JWEHeader, context: SecurityContext): util.List[? <: Key] = util.List.of()
  }

  given Monoid[JWEKeySelector[SecurityContext]] = Monoid.instance(EmptyJWEKeySelector, CombinedJWEKeySelector(_, _))

  // With only JWS support from a JWKS URI
  def default(jwkSetUri: String): JwtConfig = JwtConfig(jwkSetUri, None, Set.empty, Set.empty, Map.empty)
}

/*
 * We have to data structures to model the configuration parameters needed for a workflow:
 * - the first, `RudderJWTRegistration` is simple and is only used for API protection with Bearer token. It only needs an
 *   URL to get a public key for signature checking, and some role mapping
 * - the second, `RudderClientRegistration`, is complicated because in it, Rudder takes active part in the authentication
 *   and act for the users to make them log on IdP and then into Rudder. We need it to have a client ID and secret in
 *   the IdP, and lots of other parameters.
 */

// properties common to both JWT and OAuth2/OIDC registration
sealed trait RudderOAuth2Registration {
  def registrationId: String
}

trait RegistrationWithPivotAttribute {
  def pivotAttributeName: String
}

trait RegistrationWithRoles {
  def roles:   ProvidedRoles
  def tenants: ProvidedTenants
}

/*
 * API access with JWT (bearer token) - client_credentials workflow
 */
final case class RudderJwtRegistration(
    registrationId:     String,
    jwkSetUri:          String,
    pivotAttributeName: String,
    audience:           JwtAudience,
    roles:              ProvidedRoles,
    tenants:            ProvidedTenants
) extends RudderOAuth2Registration with RegistrationWithRoles
    with RegistrationWithPivotAttribute // there is nothing secret here, no need to override `toString()`.

object RudderJwtRegistration {
  val defaultPivotAttribute: String = "cid"
}

/*
 * API access with JWT (bearer token) - client_credentials workflow
 */
final case class RudderOpaqueTokenRegistration(
    registrationId:       String,
    clientId:             String,
    clientSecret:         String,
    introspectUri:        String,
    pivotAttributeName:   String,
    cacheRequestDuration: Option[Duration]
) extends RudderOAuth2Registration with RegistrationWithPivotAttribute {
  // be careful to not leak secret in "toString"
  override def toString: String = {
    s"""{${registrationId}}, clientId: '${clientId}' introspect URL: '${introspectUri}', token mapping attribute: '${pivotAttributeName}', use validation cache: ${cacheRequestDuration
        .fold("no")(d => s"yes, ${d.toString}")}"""
  }
}

object RudderOpaqueTokenRegistration {
  // by default, the user/service ID that gained authentication with that token is store in "sub", but it can
  // be implementation specific.
  val defaultPivotAttribute: String = "sub"
}

/*
 * User login - authorization_code workflow.
 * Data container class to add our properties to spring ClientRegistration ones
 * We never want to print the secret in logs, so override it.
 */
final case class RudderClientRegistration(
    registration:      ClientRegistration,
    logoutUrl:         Option[String],
    logoutRedirectUrl: Option[String],
    infoMsg:           String,
    roles:             ProvidedRoles,
    provisioning:      Boolean,
    tenants:           ProvidedTenants,
    jwtConfig:         JwtConfig
) extends RudderOAuth2Registration with RegistrationWithRoles with RegistrationWithPivotAttribute {
  override def registrationId: String = registration.getRegistrationId

  override def pivotAttributeName: String = registration.getProviderDetails.getUserInfoEndpoint.getUserNameAttributeName

  // we don't have access to "registration.toString", and it leaks "clientSecret", so we need to hide it
  override def toString: String = {
    toDebugStringWithSecret.replaceAll("""clientSecret='([^']+?)'""", "clientSecret='*****'")
  }

  // avoid that in logs etc, use only for interactive debugging sessions
  def toDebugStringWithSecret: String =
    s"""{${registration.toString}}, '${infoMsg}', roles: ${roles.toString}, user provisioning enabled: ${provisioning}"""
}

/*
 * We want to have same attribute name for same things in both JWT and OAuth2/OIDC configuration files.
 */
object RudderRegistrationPropertyCommon {
  val A_NAME                    = "name"
  val A_CLIENT_ID               = "client.id"
  val A_CLIENT_SECRET           = "client.secret"
  val A_CLIENT_REDIRECT         = "client.redirect"
  val A_AUTH_METHOD             = "authMethod"
  val A_GRANT_TYPE              = "grantType"
  val A_INFO_MESSAGE            = "ui.infoMessage"
  val A_SCOPE                   = "scope"
  val A_AUDIENCE_CHECK          = "audience.check"
  val A_AUDIENCE_VALUE          = "audience.value"
  val A_TENANTS_ENABLED         = "tenants.enabled"
  val A_TENANTS_ATTRIBUTE       = "tenants.attribute"
  val A_TENANTS_OVERRIDE        = "tenants.override"
  val A_ENFORCE_TENANTS_MAPPING = "tenants.mapping.restricted"
  val A_TENANTS_MAPPING         = "tenants.mapping.entitlements"        // ie: OIDC tenant = Rudder tenant
  val A_TENANTS_REVERSE_MAPPING = "tenants.mapping.reverseEntitlements" // ie: Rudder tenants = OIDC tenants (overrides mapping)
  val A_URI_AUTH                = "uri.auth"
  val A_URI_TOKEN               = "uri.token"
  val A_URI_USER_INFO           = "uri.userInfo"
  val A_URI_JWK_SET             = "uri.jwkSet"
  val A_URI_LOGOUT              = "uri.logout"
  val A_URI_LOGOUT_REDIRECT     = "uri.logoutRedirect"
  val A_URI_INTROSPECT          = "uri.introspect"
  val A_PIVOT_ATTRIBUTE         = "userNameAttributeName"
  val A_CACHE_VALIDATION        = "validationCacheDuration"
  val A_ROLES_ENABLED           = "roles.enabled"
  val A_ROLES_ATTRIBUTE         = "roles.attribute"
  val A_ROLES_OVERRIDE          = "roles.override"
  val A_ENFORCE_ROLES_MAPPING   = "roles.mapping.restricted"
  val A_ROLES_MAPPING           = "roles.mapping.entitlements"          // ie: OIDC role = Rudder role
  val A_ROLES_REVERSE_MAPPING   = "roles.mapping.reverseEntitlements"   // ie: Rudder role = OIDC role (overrides mapping)
  val A_PROVISIONING            = "enableProvisioning"
  val A_JWT_KEYS                = "jwt.keys"
  val A_JWT_JWS_DISCOVER        = "jwt.jws.discoverAlgorithms"
  val A_JWT_JWE_ALG             = "jwt.jwe.algorithms"
  val A_JWT_JWE_ENC             = "jwt.jwe.encryptionMethods"
  val A_JWT_KEY_TYPE            = "type"
  val A_JWT_KEY_USE             = "use"
  val A_JWT_KEY_PUBLIC_FILE     = "publicFile"
  val A_JWT_KEY_PRIVATE_FILE    = "privateFile"

  val authMethods = {
    import ClientAuthenticationMethod.*
    List(CLIENT_SECRET_BASIC, CLIENT_SECRET_POST, CLIENT_SECRET_JWT, NONE)
  }

  val grantTypes = {
    import AuthorizationGrantType.*
    List(AUTHORIZATION_CODE, REFRESH_TOKEN, CLIENT_CREDENTIALS) // PASSWORD and IMPLICIT are deprecated for security reasons
  }

  val registrationAttributes = Map(
    A_NAME                    -> "human readable name to use in the button 'login with XXXX'",
    A_CLIENT_ID               -> "id generated in the Oauth2 service provider to identify rudder as a client app",
    A_CLIENT_SECRET           -> "the corresponding secret key",
    A_CLIENT_REDIRECT         -> "rudder URL to redirect to once authentication is done on the provider (must be resolvable from user browser)",
    A_AUTH_METHOD             -> s"authentication method to use (${authMethods.map(_.getValue).mkString(",")})",
    A_GRANT_TYPE              -> s"authorization grant type to use (${grantTypes.map(_.getValue).mkString(",")}",
    A_INFO_MESSAGE            -> "message displayed in the login form, for example to tell the user what login he must use",
    A_SCOPE                   -> "data scope to request access to",
    A_AUDIENCE_CHECK          -> "(default 'true') in the case of JWT, does Rudder need to check the 'aud' claim of the token?",
    A_AUDIENCE_VALUE          -> "(default 'io.rudder.api') in the case of JWT, the audience value that the token must have to be given access to an API",
    A_TENANTS_ENABLED         -> "(default false) if enable, restrict the authentication to the provided list of tenants",
    A_TENANTS_ATTRIBUTE       -> "(default '') the name of the attribute containing the list of authorized tenants",
    A_TENANTS_OVERRIDE        -> "(default false) keep user configured tenants in rudder-user.xml or override them with the one provided in the token",
    A_TENANTS_MAPPING         -> s"(optional) provides a map of alias `IdP tenant name` -> `Rudder tenant name`, where each IdP tenant name is a sub-key of '${A_TENANTS_MAPPING}'",
    A_TENANTS_REVERSE_MAPPING -> s"(optional) provides a map of alias `Rudder tenant name` -> `IdP tenant name`, where each IdP tenant name is a sub-key of '${A_TENANTS_MAPPING}', useful when the IdP tenant name contains '='",
    A_ENFORCE_TENANTS_MAPPING -> "(default true) if true, restricts roles available by the IdP to the role defined in mapping entitlement. Else the map provides alias for Rudder internal role names.",
    A_URI_AUTH                -> "provider URL to contact for main authentication (see provider documentation)",
    A_URI_TOKEN               -> "provider URL to contact for token verification (see provider documentation)",
    A_URI_USER_INFO           -> "provider URL to contact to get user information (see provider documentation)",
    A_URI_JWK_SET             -> "provider URL to check signature of JWT token (see provider documentation)",
    A_URI_LOGOUT              -> "(optional) provider URL to logout and end session (see provider documentation).",
    A_URI_LOGOUT_REDIRECT     -> "(optional) the redirect URL to provide to the IdP after logout",
    A_URI_INTROSPECT          -> "in case of opaque access bearer token, the introspect URL on which the token must be validated",
    A_PIVOT_ATTRIBUTE         -> "the attribute used to find local app user (OIDC user authentication) or the local API token (opaque bearer token)",
    A_CACHE_VALIDATION        -> "(optional) the duration (number followed by a time unit) for which a validated opaque token should be cached",
    A_ROLES_ENABLED           -> "(default false) enable custom role extension by OIDC",
    A_ROLES_ATTRIBUTE         -> "the attribute to use for list of custom role name. It's content in token must be a array of strings.",
    A_ROLES_OVERRIDE          -> "(default false) keep user configured roles in rudder-user.xml or override them with the one provided in the token",
    A_PROVISIONING            -> "(default false) allows the automatic creation of users in Rudder in they successfully authenticate with OIDC",
    A_ROLES_MAPPING           -> s"(optional) provides a map of alias `IdP role name` -> `Rudder role name`, where each IdP role name is a sub-key of '${A_ROLES_MAPPING}'",
    A_ROLES_REVERSE_MAPPING   -> s"(optional) provides a map of alias `Rudder role name` -> `IdP role name`, where each IdP role name is a sub-key of '${A_ROLES_MAPPING}', useful when the IdP role name contains '='",
    A_ENFORCE_ROLES_MAPPING   -> "(default true) if true, restricts roles available by the IdP to the role defined in mapping entitlement. Else the map provides alias for Rudder internal role names.",
    A_JWT_KEYS                -> "(optional) base property for JWT keys definition. Key ID (\"kid\") are direct sub-properties, and require a key `type`",
    A_JWT_JWS_DISCOVER        -> "(default false) whether an initial query to the JWKS URI should be made to support other signature algorithms than RS256",
    A_JWT_JWE_ALG             -> "(optional) algorithms for JWE to attempt to match for JWK defined in configuration and JWKS URL. It could be the ones you known the keys of, or the ones matching the `userinfo_encryption_alg_values_supported` from the /.well-known/openid-configuration of your OIDC provider. If no algorithm or no encryption method is provided, then the fallback algorithms are RSA-OEAP with SHA2 hash functions (RSA-OAEP-256, RSA-OAEP-384, RSA-OAEP-512), and ECDH-ES with wrapped CEK variants (ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A256KW).",
    A_JWT_JWE_ENC             -> "(optional) encryption methods to attempt to match for JWK defined in configuration and JWKS URL. It could be the ones matching the `userinfo_encryption_enc_values_supported` from the /.well-known/openid-configuration of your OIDC provider. If no algorithm or no encryption method is provided, then the fallback encryption methods are AES_CBC_HMAC_SHA2 required variants (A128CBC-HS256, A256CBC-HS512)  and AES GCM recommended variants (A128GCM, A256GCM).",
    A_JWT_KEY_TYPE            -> "type of supported key, available from \"kty\" (e.g. RSA, EC)",
    A_JWT_KEY_USE             -> "(optional) use of key: `sig` for signature, `enc` for encryption, leave empty for either",
    A_JWT_KEY_PUBLIC_FILE     -> "(optional) path of a file containing the public key to use in case the key type requires one",
    A_JWT_KEY_PRIVATE_FILE    -> "(optional) path of a file containing the private key to use in case the key type requires one"
  )

  def parseAuthenticationMethod(method: String): PureResult[ClientAuthenticationMethod] = {
    authMethods.find(_.getValue.equalsIgnoreCase(method)) match {
      case None    =>
        // spring change the name between the version we use in 6.2 and 7.0. We want to provide compatibility
        // and only document the most recent ones.
        method.toLowerCase match {
          case "post" | "client_secret_post"   => Right(ClientAuthenticationMethod.CLIENT_SECRET_POST)
          case "basic" | "client_secret_basic" => Right(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          case _                               =>
            Left(
              Inconsistency(
                s"Requested OAUTH2 authentication methods '${method}' is not recognized, please use one of: ${authMethods.map(_.getValue).mkString("'", "','", "'")}"
              )
            )
        }
      case Some(m) => Right(m)
    }
  }

  def parseAuthorizationGrantType(grant: String): PureResult[AuthorizationGrantType] = {
    grantTypes.find(_.getValue.equalsIgnoreCase(grant)) match {
      case None    =>
        Left(
          Inconsistency(
            s"Requested OAUTH2 authorization grant type '${grant}' is not recognized, please use one of: ${grantTypes.map(_.getValue).mkString("'", "','", "'")}"
          )
        )
      case Some(m) => Right(m)
    }
  }

  /*
   * In a first release of the plugin, scope were comma separated, which is inconsistent with OIDC spec
   * https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
   * `Multiple scope values MAY be used by creating a space delimited, case sensitive list of ASCII scope values.`
   * It is not documented anymore and will only support an handful of early adopters.
   * Compatibility can be removed at some point in the future (timeline: rudder 7.2)
   */
  def parseScope(scopes: String): IOResult[List[String]] = {
    // it seems that only the given list of email, phone etc is supported, even if the speak of "case sensitive", let be
    // a bit more broad, the protocol lib will check more thoroughly.
    val ascii                 = """\w""".r
    def checkScope(s: String) = {
      if (ascii.matches(s))
        Inconsistency(s"Only ascii [a-zA-Z0-9_] is authorized in scope definition but '${s}' doesn't matches it").fail
      else s.succeed
    }
    val s                     = (
      if (scopes.contains(",")) scopes.split(",")
      else scopes.split("""\s+""")
    ).toList.map(_.trim)

    ZIO.partition(s)(checkScope).flatMap {
      case (errs, oks) =>
        errs.toList match {
          case Nil       => oks.toList.succeed
          case h :: tail => Accumulated(NonEmptyList.of(h, tail*)).fail
        }
    }
  }

  // utility method that read key in config and fails with an error message if
  // not found.
  protected[authbackends] def read(key: String)(implicit base: BasePath, config: Config): IOResult[String] = {
    val path = base.path(key)
    IOResult.attempt(s"Missing key '${path}' for registration '${base.id}' (${registrationAttributes(key)})")(
      config.getString(path)
    )
  }

  protected[authbackends] def toBool(s: String) = s.toLowerCase match {
    case "true" => true
    case _      => false
  }

  // Return a map with the key entries as Map key, and object for each key
  protected[authbackends] def readMapObject[A](
      key:        String
  )(
      readObject: (String, BasePath) => IOResult[A]
  )(implicit
      base:       BasePath,
      config:     Config
  ): IOResult[Map[String, A]] = {
    val path = base.path(key)
    import scala.jdk.CollectionConverters.*
    for {
      keySet <- IOResult
                  .attempt(s"Missing key '${path}' for OAUTH2 registration '${base.id}' (${registrationAttributes(key)})")(
                    config.getObject(path).keySet().asScala.toList
                  )
                  .catchAll(_ =>
                    List().succeed
                  ) // in that case, we suppose that the key is just missing so we return a default empty value for mapping
      values <- ZIO.foreach(keySet) { k =>
                  val newBasePath = BasePath(base.base, base.id + "." + key + "." + k)
                  readObject(k, newBasePath)
                    .map((k, _))
                }
    } yield values.toMap
  }

  protected[authbackends] def readMap(
      key: String
  )(implicit base: BasePath, config: Config): IOResult[Map[String, String]] = {
    val path = base.path(key)
    import scala.jdk.CollectionConverters.*
    for {
      keySet <- IOResult
                  .attempt(s"Missing key '${path}' for OAUTH2 registration '${base.id}' (${registrationAttributes(key)})")(
                    config.getObject(path).keySet().asScala.toList
                  )
                  // in that case, we suppose that the key is just missing so we return a default empty value for mapping
                  .catchAll(_ => List().succeed)
      values <- ZIO.foreach(keySet) { key =>
                  val wholeKey = path + "." + key
                  IOResult.attempt(s"Error when reading role entitlement mapping '${wholeKey}'") {
                    val (k, v) = {
                      // see https://issues.rudder.io/issues/28890:
                      // tenants can use special config for 'reverseEntitlements."*"' (resp. '-') key, but typesafe config would keep the double quotes
                      key match {
                        case "\"*\"" => ("*", config.getObject(path).get("\"*\"").unwrapped().asInstanceOf[String])
                        case "\"-\"" => ("-", config.getObject(path).get("\"-\"").unwrapped().asInstanceOf[String])
                        case _       => (key, config.getString(wholeKey))
                      }
                    }
                    (k, v)
                  }
                }
    } yield values.toMap
  }

  protected[authbackends] def readRoles()(implicit base: BasePath, config: Config): IOResult[ProvidedRoles] = {
    for {
      rolesEnabled       <- read(A_ROLES_ENABLED).catchAll(_ => "false".succeed)
      rolesAttr          <- read(A_ROLES_ATTRIBUTE).catchAll(_ => "".succeed)
      rolesOverride      <- read(A_ROLES_OVERRIDE).catchAll(_ => "false".succeed)
      enforceRoleMapping <- read(A_ENFORCE_ROLES_MAPPING).catchAll(_ => "false".succeed)
      mapping            <- readMap(A_ROLES_MAPPING)
      reverseMapping     <- readMap(A_ROLES_REVERSE_MAPPING)
    } yield {
      ProvidedRoles(
        toBool(rolesEnabled),
        rolesAttr,
        toBool(rolesOverride),
        toBool(enforceRoleMapping),
        mapping ++ reverseMapping.map { case (a, b) => (b, a) }
      )
    }
  }

  protected[authbackends] def readTenants()(implicit base: BasePath, config: Config): IOResult[ProvidedTenants] = {
    for {
      tenantsEnabled     <- read(A_TENANTS_ENABLED).catchAll(_ => "false".succeed)
      tenantsAttr        <- read(A_TENANTS_ATTRIBUTE).catchAll(_ => "".succeed)
      tenantsOverride    <- read(A_TENANTS_OVERRIDE).catchAll(_ => "false".succeed)
      enforceRoleMapping <- read(A_ENFORCE_TENANTS_MAPPING).catchAll(_ => "false".succeed)
      mapping            <- readMap(A_TENANTS_MAPPING)
      reverseMapping     <- readMap(A_TENANTS_REVERSE_MAPPING)
    } yield {
      ProvidedTenants(
        toBool(tenantsEnabled),
        tenantsAttr,
        toBool(tenantsOverride),
        toBool(enforceRoleMapping),
        mapping ++ reverseMapping.map { case (a, b) => (b, a) }
      )
    }
  }

  protected[authbackends] def readJwt(jwkSetUri: String)(implicit base: BasePath, config: Config): IOResult[JwtConfig] = {
    import JwtConfig.*
    import com.nimbusds.jose.jwk.KeyType as KT
    def readJwk(kid: String, basePath: BasePath): IOResult[JwkConfig] = {
      given base: BasePath = basePath // override base path, since it is within the "kid"
      for {
        `type` <- read(A_JWT_KEY_TYPE)
        typ    <- IOResult.attempt("Could not parse key type")(KT.parse(`type`)).flatMap[Any, RudderError, KeyType] {
                    case k: KT.RSA.type => k.succeed
                    case k: KT.EC.type  => k.succeed
                    case k => Inconsistency(s"Key type ${k} unsupported").fail
                  }
        use    <- read(A_JWT_KEY_USE).foldZIO(_ => ZIO.none, Use.withNameInsensitiveEither(_).bimap(_.getMessage, Some(_)).toIO)
        pub    <- read(A_JWT_KEY_PUBLIC_FILE).fold(_ => None, f => Some(File(f)))
        priv   <- read(A_JWT_KEY_PRIVATE_FILE).fold(_ => None, f => Some(File(f)))
        k      <- ValidatedJwk.from(kid, typ, pub, priv)
      } yield {
        JwkConfig(kid, typ, use, k)
      }
    }

    for {
      discoverJwsAlg <- read(A_JWT_JWS_DISCOVER).fold(_ => None, s => Some(toBool(s)))
      alg            <- read(A_JWT_JWE_ALG).fold(_ => Set.empty, _.split(",").toSet.map(JWEAlgorithm.parse))
      enc            <- read(A_JWT_JWE_ENC).fold(_ => Set.empty, _.split(",").toSet.map(EncryptionMethod.parse))
      keys           <- readMapObject(A_JWT_KEYS)(readJwk)
    } yield {
      JwtConfig(
        jwkSetUri,
        discoverJwsAlg,
        alg,
        enc,
        keys
      )
    }
  }

  def readProviders(config: Config, baseProperty: String): IOResult[List[String]] = {
    val path = baseProperty + ".registrations"
    IOResult.attempt(
      s"Missing property '${path}' which define the comma separated list of provider registration to use for OAUTH2."
    )(
      config.getString(path).split(",").map(_.trim).toList
    )
  }
}

/*
 * Client registration definition based on rudder property file:
 * - read property `rudder.auth.{oauth2,jwt}.client.registrations` which give a comma-separated
 *   list of client registration to oauth2 providers
 * - for each registration, read the needed properties based on template
 *   `rudder.auth.{oauth2,jwt}.client.${registration}.${propertyName}`
 */

// a base trait for common methods
trait RudderPropertyBasedRegistration[A <: RudderOAuth2Registration] {

  /*
   * Name of the authentication kind to use in logs
   */
  def registrationLogName: String
  /*
   * Name of the root path used for the base property.
   */
  def baseProperty:        String

  /*
   * The list of registration for that kind of JWT/OAuth2 authentication
   */
  def registrations: Ref[List[(String, A)]]

  /*
   * How to decode one authentication
   */
  def readOneRegistration(id: String, config: Config): IOResult[A]

  /*
   * Read the whole set of providers with their registrations.
   * We return a list to keep the order provided in the config file.
   */
  def readAllRegistrations(
      config:              Config,
      readOneRegistration: (String, Config) => IOResult[A]
  ): IOResult[List[(String, A)]] = {
    for {
      // we don't want to fail if the list of provider is missing, just log it as a warning
      providers     <- readProviders(config, baseProperty)
      // we don't want to fail if one of the registration is not ok, just log it
      _             <- AuthBackendsLoggerPure.info(s"List of configured providers for ${registrationLogName}: ${providers.mkString(", ")}")
      registrations <- ZIO.foreach(providers) { p =>
                         readOneRegistration(p, config).foldZIO(
                           err =>
                             AuthBackendsLoggerPure.error(
                               s"Error when reading ${registrationLogName} configuration for registration to '${p}' provider: ${err.fullMsg}'"
                             ) *>
                             None.succeed,
                           res => {
                             AuthBackendsLoggerPure.debug(s"New registration for provider '${p}': ${res} ") *>
                             Some((p, res)).succeed
                           }
                         )
                       }
    } yield registrations.flatten
  }

  /*
   * read information from config and update internal cache
   */
  def updateRegistration(config: Config): IOResult[Unit]

}

object RudderPropertyBasedJwtRegistrationDefinition {

  private val baseProperty        = "rudder.auth.oauth2.jwt.provider"
  private val registrationLogName = "OAuth2 JWT"

  def make(): IOResult[RudderPropertyBasedJwtRegistrationDefinition] = {
    for {
      ref <- Ref.make(List.empty[(String, RudderJwtRegistration)])
    } yield {
      new RudderPropertyBasedJwtRegistrationDefinition(ref)
    }
  }

}

class RudderPropertyBasedJwtRegistrationDefinition(val registrations: Ref[List[(String, RudderJwtRegistration)]])
    extends RudderPropertyBasedRegistration[RudderJwtRegistration] {

  import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.*

  override def baseProperty:        String = RudderPropertyBasedJwtRegistrationDefinition.baseProperty
  override def registrationLogName: String = RudderPropertyBasedJwtRegistrationDefinition.registrationLogName

  def updateRegistration(config: Config): IOResult[Unit] = {
    for {
      newOnes <- readAllRegistrations(config, readOneRegistration)
      _       <- registrations.set(newOnes)
    } yield ()
  }

  def readOneRegistration(id: String, config: Config): IOResult[RudderJwtRegistration] = {
    implicit val base = BasePath(baseProperty, id)
    implicit val c    = config

    for {
      jwkSetUri      <- read(A_URI_JWK_SET)
      checkAudience  <- read(A_AUDIENCE_CHECK).catchAll(_ => "true".succeed)
      audienceValue  <- read(A_AUDIENCE_VALUE).catchAll(_ => "io.rudder.api".succeed)
      pivotAttribute <- read(A_PIVOT_ATTRIBUTE).catchAll(_ => RudderJwtRegistration.defaultPivotAttribute.succeed)
      roles          <- readRoles()
      tenants        <- readTenants()
    } yield {
      RudderJwtRegistration(
        id,
        jwkSetUri,
        pivotAttribute,
        JwtAudience(toBool(checkAudience), audienceValue),
        roles,
        tenants
      )
    }
  }
}

/*
 * Registration for opaque access tokens
 */
object RudderPropertyBasedOpaqueTokenRegistrationDefinition {

  private val baseProperty        = "rudder.auth.oauth2.opaque.provider"
  private val registrationLogName = "OAuth2 Opaque Access Token"

  def make(): IOResult[RudderPropertyBasedOpaqueTokenRegistrationDefinition] = {
    for {
      ref <- Ref.make(List.empty[(String, RudderOpaqueTokenRegistration)])
    } yield {
      new RudderPropertyBasedOpaqueTokenRegistrationDefinition(ref)
    }
  }

}

class RudderPropertyBasedOpaqueTokenRegistrationDefinition(val registrations: Ref[List[(String, RudderOpaqueTokenRegistration)]])
    extends RudderPropertyBasedRegistration[RudderOpaqueTokenRegistration] {

  import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.*

  // the maximum duration for request cache above which we log a warning
  private val MAX_CACHE_DURATION_WARN: Duration = Duration(5, TimeUnit.MINUTES)

  override def baseProperty:        String = RudderPropertyBasedOpaqueTokenRegistrationDefinition.baseProperty
  override def registrationLogName: String = RudderPropertyBasedOpaqueTokenRegistrationDefinition.registrationLogName

  def updateRegistration(config: Config): IOResult[Unit] = {
    for {
      newOnes <- readAllRegistrations(config, readOneRegistration)
      _       <- registrations.set(newOnes)
    } yield ()
  }

  private def parseRequestCache()(implicit base: BasePath, config: Config): IOResult[Option[Duration]] = {
    read(A_CACHE_VALIDATION).option.flatMap {
      case None =>
        AuthBackendsLoggerPure.debug(s"Cache for bearer token validation request is disabled") *> None.succeed

      case Some(x) =>
        try {
          val d = Duration(x)
          // we impose that the cache is at least 1ms
          if (d.toMillis < 1) {
            AuthBackendsLoggerPure.info(
              s"Cache for bearer token validation request has an invalid value '${x}': disabling it"
            ) *> None.succeed
          } else {
            AuthBackendsLoggerPure.info(
              s"Cache for bearer token validation request is configured with a duration of '${d.toString}'"
            ) *>
            ZIO.when(d > MAX_CACHE_DURATION_WARN) {
              AuthBackendsLoggerPure.warn(
                s"The cache has a retention duration of '${d.toString}' which is too long: it increases " +
                s"risks of stolen token replay without identity provider validation"
              )
            } *> Some(d).succeed
          }
        } catch {
          case ex: DateTimeParseException => Inconsistency(ex.getMessage).fail
        }
    }
  }

  def readOneRegistration(id: String, config: Config): IOResult[RudderOpaqueTokenRegistration] = {
    implicit val base = BasePath(baseProperty, id)
    implicit val c    = config

    for {
      clientId       <- read(A_CLIENT_ID)
      clientSecret   <- read(A_CLIENT_SECRET)
      introspectUri  <- read(A_URI_INTROSPECT)
      pivotAttribute <- read(A_PIVOT_ATTRIBUTE).catchAll(_ => RudderOpaqueTokenRegistration.defaultPivotAttribute.succeed)
      cacheDuration  <- parseRequestCache()
    } yield {
      RudderOpaqueTokenRegistration(
        id,
        clientId,
        clientSecret,
        introspectUri,
        pivotAttribute,
        cacheDuration
      )
    }
  }
}

/*
 * Client registration definition based on rudder property file:
 * - read property `rudder.auth.oauth2.client.registrations` which give a comma-separated
 *   list of client registration to oauth2 providers
 * - for each registration, read the needed properties based on template
 *   `rudder.auth.oauth2.client.${registration}.${propertyName}`
 */
object RudderPropertyBasedOAuth2RegistrationDefinition {
  private val baseProperty        = "rudder.auth.oauth2.provider"
  private val registrationLogName = "OAuth2/OIDC"

  def make(): IOResult[RudderPropertyBasedOAuth2RegistrationDefinition] = {
    for {
      ref <- Ref.make(List.empty[(String, RudderClientRegistration)])
    } yield {
      new RudderPropertyBasedOAuth2RegistrationDefinition(ref)
    }
  }
}

class RudderPropertyBasedOAuth2RegistrationDefinition(val registrations: Ref[List[(String, RudderClientRegistration)]])
    extends RudderPropertyBasedRegistration[RudderClientRegistration] {

  import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.*

  override def baseProperty:        String = RudderPropertyBasedOAuth2RegistrationDefinition.baseProperty
  override def registrationLogName: String = RudderPropertyBasedOAuth2RegistrationDefinition.registrationLogName

  /*
   * read information from config and update internal cache
   */
  override def updateRegistration(config: Config): IOResult[Unit] = {
    for {
      newOnes <- readAllRegistrations(config, readOneRegistration)
      _       <- registrations.set(newOnes)
      // for each oauthRegistration, register a logout action
      _       <- ZIO.foreach(newOnes) {
                   case (id, r) =>
                     r.logoutUrl match {
                       case Some(url) =>
                         val logoutAction =
                           LogoutPostAction(s"oauth2_end_session:${id}", BuildLogout.build(id, url, r.logoutRedirectUrl))
                         AuthBackendsLoggerPure.debug(s"Adding remote logout URL GET for '${id}'") *>
                         UserLogout.logoutActions.update(_.appended(logoutAction))
                       case None      =>
                         AuthBackendsLoggerPure.debug(s"No remote logout URL configured for '${id}'")
                     }
                 }
    } yield ()
  }

  override def readOneRegistration(id: String, config: Config): IOResult[RudderClientRegistration] = {
    implicit val base = BasePath(baseProperty, id)
    implicit val c    = config

    for {
      name                <- read(A_NAME)
      clientId            <- read(A_CLIENT_ID)
      clientSecret        <- read(A_CLIENT_SECRET)
      clientRedirect      <- read(A_CLIENT_REDIRECT)
      authMethod          <- read(A_AUTH_METHOD).flatMap(parseAuthenticationMethod(_).toIO)
      grantTypes          <- read(A_GRANT_TYPE).flatMap(parseAuthorizationGrantType(_).toIO)
      infoMessage         <- read(A_INFO_MESSAGE)
      scopes              <- read(A_SCOPE).flatMap(parseScope)
      uriAuth             <- read(A_URI_AUTH)
      uriToken            <- read(A_URI_TOKEN)
      uriUserInfo         <- read(A_URI_USER_INFO)
      pivotAttr           <- read(A_PIVOT_ATTRIBUTE)
      jwkSetUri           <- read(A_URI_JWK_SET)
      logoutUri           <- read(A_URI_LOGOUT).fold(_ => Option.empty[String], Some(_))
      logoutUriRedirect   <- read(A_URI_LOGOUT_REDIRECT).fold(_ => Option.empty[String], Some(_))
      roles               <- readRoles()
      tenants             <- readTenants()
      provisioningAllowed <- read(A_PROVISIONING).catchAll(_ => "false".succeed)
      jwtConfig           <- readJwt(jwkSetUri)
    } yield {
      RudderClientRegistration(
        ClientRegistration
          .withRegistrationId(id)
          .clientId(clientId)
          .clientSecret(clientSecret)
          .clientAuthenticationMethod(authMethod)
          .authorizationGrantType(grantTypes)
          .redirectUri(clientRedirect)
          .scope(scopes*)
          .authorizationUri(uriAuth)
          .tokenUri(uriToken)
          .userInfoUri(uriUserInfo)
          .userNameAttributeName(pivotAttr)
          .clientName(name)
          .jwkSetUri(jwkSetUri)
          .build(),
        logoutUri,
        logoutUriRedirect,
        infoMessage,
        roles,
        toBool(provisioningAllowed),
        tenants,
        jwtConfig
      )
    }
  }

}

def decodeJwt(jwtString: String)(using jwtConfig: JwtConfig): Either[JwtException, Jwt] = {
  val res = Either.catchOnly[JwtException](jwtConfig.decoder.decode(jwtString))
  res match {
    case Left(err) =>
      Option(err.getCause) match {
        case Some(ex: BadJOSEException) =>
          AuthBackendsLoggerPure.logEffect.warn(
            s"Error when attempting to decode a JWT response, please ensure your JWT keys configuration are right, ${jwtConfig.debugConfiguration}.\nCause: ${ex.getMessage}"
          )
        case _                          =>
          AuthBackendsLoggerPure.logEffect.debug(
            s"Error when attempting to decode a JWT response: ${err.getMessage}"
          )
      }
    case Right(e)  =>
      AuthBackendsLoggerPure.logEffect.trace(s"Successfully decoded JWT with headers : ${e.getHeaders}")
  }
  res
}
