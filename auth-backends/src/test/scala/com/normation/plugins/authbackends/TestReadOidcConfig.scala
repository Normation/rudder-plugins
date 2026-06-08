/*
 *************************************************************************************
 * Copyright 2024 Normation SAS
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
package com.normation.plugins.authbackends

import com.normation.rudder.tenants.TenantAccess

import better.files.*
import bootstrap.rudder.plugin.RudderTokenMapping
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.RSAKey
import com.normation.rudder.tenants.TenantAccessGrant
import com.normation.rudder.tenants.TenantId

import com.normation.zio.*
import com.typesafe.config.ConfigFactory

import java.security.Security
import org.apache.commons.io.IOUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner

import scala.annotation.nowarn
import scala.concurrent.duration.*

import zio.Chunk

@RunWith(classOf[JUnitRunner])
class TestReadOidcConfig extends Specification {

  // WARNING: HOCON doesn't behave the same if you read from a file or from a string, so for the test to be relevant,
  // we need to load from files.

  "reading an OIDC configuration" should {
    val registration = RudderPropertyBasedOAuth2RegistrationDefinition.make().runNow

    "read the correct name" in {

      val config = ConfigFactory.parseResources("oidc/oidc_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp")
    }

    "have two entitlements mapping" in {

      val config = ConfigFactory.parseResources("oidc/oidc_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"    -> "administrator",
        "rudder_readonly" -> "readonly"
      )) and (regs("someidp").tenants.mapping === Map(
        "rudder_TA" -> "TA",
        "rudder_TB" -> "TB"
      ))
    }

    "be able to use complex roles names with the reverse mapping" in {
      val config = ConfigFactory.parseResources("oidc/oidc_reverse_role_mapping.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"                                                                       -> "administrator",
        "rudder_readonly"                                                                    -> "readonlyOVERRIDDEN",
        "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "administrator"
      )) and (
        regs("someidp").tenants.mapping.toList must containTheSameElementsAs(
          List(
            "rudder_all"                                                                         -> "*",
            "rudder_none"                                                                        -> "-",
            "rudder_TA"                                                                          -> "TA",
            "rudder_TB"                                                                          -> "TB_OVERRIDDEN",
            "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "TA",
            "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=Admin"                                   -> "*",
            "CN=CCCC-DDDDD,OU=Groups,OU=_IT,OU=BB-DD,OU=Nobody"                                  -> "-"
          )
        )
      )
    }

    "be able to use JWT keys for signed/encrypted JWT configuration" in {
      Security.addProvider(new BouncyCastleProvider())

      def withKey(dir: File)(path: String) = for {
        f  <- new Dispose(dir.createChild(File(path).name))(using Disposable.fileDisposer)
        os <- f.fileOutputStream()
        is <- new Dispose(Resource.getAsStream(path))
      } yield {
        IOUtils.copy(is, os)
        f
      }

      // we have keys1 and key2 both private/public files in resources

      @nowarn
      val expectedAlg  = Set(JWEAlgorithm.RSA_OAEP, JWEAlgorithm.ECDH_ES)
      val expectedEnc  = Set(EncryptionMethod.A128CBC_HS256)
      val expectedKey1 = RSAKey.parse(
        """
          {
            "kid": "key1",
            "kty": "RSA",
            "e": "AQAB",
            "n": "v-qimwkTIZGlGTt-fu5jmORLh6f-akbtS_DIxGfM6AWav_MtyB9DdrUVraCREeNZ41BxyfXNE5l2xQJMge49S_YpJr38Sjv_lnjRqfeNtBUsOX7a2xnGVvxA1x4zWHt8qSDwsXQSxcTd3--5RMApw_jYKWKBVacPv-o7sf3je-X2epLFw-36eg1iLLt26PftTnWm686cXekcNTiFXOmZjFBMs9Zu-dk05rlkIXne0ksj5YJa3izRh-2Pk3IIsxYPuirvSNXCWIG2lvY3FK1HefSHNJhPxCys2dtSUHRmiPuJIVZJyv-eQd-gMjt3ewHGMXxNf32XFouSEZM7DffC8Q",
            "p": "7xI09aZtmrAJ03KrVVgMc5ZjpJ-8qBx-n7Ta5S2DG5JeKhrU7gBlojdtX9_bfQDrxwFVUelvMRDPPzP3Zo3sj7MgW1I82c5dpOGCUenQg6znQUYjF_pCfSMiDdS4pFFdUXFOe-Ku5pigqE1AqIiRiwd7dFO0P3A5i2C6v8yxDOs",
            "q": "zYGgpS49owxtbGfAaGc0Tx05Blan6GyMUc4YEDLd5U7rg2XnUz0LqZNA15pC103YNFpsQFn8a0wihNLl4f--_wEdmldkDWJL52IGW6KeJoUhykS1nbBaMv-sFja1ETz6N6c7Ng70KwYIyqPjAoplLEtSsXozAz4o7CXoZeIyCJM",
            "d": "C1UxsPICPTM6ipjmyVwOaZLhmusirOfvT1KyqFZw1SqvjrIve-fMtg_PbedTabkBL9kqPwDS0Vt4lf2iwvFskTXCDFWftkqAt0P_LYg-x-mQKP0Cs3l8KtrOgWV5Jrp0DBCz5eayzRbo-zZOvG6UiMwDB76XYJVy6qRMTiBt9Hzvpl55UJOiTZe2AyJODtaQH4C8sCtI85MC9nABJqpySXcko4HiPYPtUgie5GsVbNroW-oUbn8GQrmHtY97ITp-z_jEbNBUOLAdH7wSKQd5hiuzq7xyvxuvkqemmdDhVoukrOBX_y_l5iVm2rcrDOHKSQADvArihRwYQygWOv0fmQ",
            "qi": "V2OSgsxSMoayg6wjZH0qawyHxJ0Krz-ywUu6tZkCOIGn8WFS-zbvtV9NYbdlekpSkuGvwcrzpyoE9l-c6Sk4AEjjgx4oa-iXsFohgnrgrAb3fAayyqu4dwde0Gf3JZ4qiHantHny-N92Ei2CfpaJ_9LGv5NnOz9DdzvQvXyPc4Y",
            "dp": "cKHHSaRrJuGg_3mats6QrzQ_JaQMIberAFsYdbiHeEnxCy0w_CA7wb0TToQMyEvSySMbq0erFxawTTqSaEKdHOZrbBrGiGbtP2zvFOBvWFnxaZM9nWJOSN5wgMujYebjbCdRrpQRipqFtkUPHVeaGkIgK6Hz3Z9lvQCJeytYjpk",
            "dq": "ybZNvzRnDY5aLUidJB0AzBLL8TvHbax2Aqm_Fs9G3BgdtQimCR7nPpgp0jY5G2nuKF0E2hk5WPwO-b6kI7NKfrRSoTbcHwL3q_KceP9iKj8MzqOofFIoBtzLxbYG_heTJmNADCybX0t5_6TjYpADBHoefdOLFMjDlB8VQEPIhn8"
          }"""
      )
      val expectedKey2 = ECKey.parse(
        """
          {
            "kid": "key2",
            "kty": "EC",
            "crv": "P-256",
            "x": "WCXUATVIshHyu2GzUtuV2k2DEun3wU4BVSSaDsTt7WI",
            "y": "fEKQwosQMPiGpFe6fRcWOKI8vm7TWc4h5PvzOxNAmF4",
            "d":"JKeO1Nr2LYWfzcreSMJjUQLMx8BuKb6FGK0qtzh-16w"
          }"""
      )

      "with both public and private keys" in {
        (for {
          tmp      <- File.temporaryDirectory("rudder-test-oidc-config-")
          key1Pub  <- withKey(tmp)("keys/key1.cert")
          key1Priv <- withKey(tmp)("keys/key1.pem")
          key2Pub  <- withKey(tmp)("keys/key2.pub.pem")
          key2Priv <- withKey(tmp)("keys/key2.pem")
        } yield {
          val config = ConfigFactory
            .parseString(
              s"""
                 |rudder.auth.oauth2.provider.someidp.jwt.keys.key1.publicFile=${key1Pub.pathAsString}
                 |rudder.auth.oauth2.provider.someidp.jwt.keys.key1.privateFile=${key1Priv.pathAsString}
                 |rudder.auth.oauth2.provider.someidp.jwt.keys.key2.publicFile=${key2Pub.pathAsString}
                 |rudder.auth.oauth2.provider.someidp.jwt.keys.key2.privateFile=${key2Priv.pathAsString}
                 |""".stripMargin
            )
            .withFallback(
              ConfigFactory.parseResources("oidc/oidc_jwt.properties")
            )

          val regs  = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap
          val keys1 = JwtConfig.PublicPrivateFile(key1Pub, key1Priv)
          val keys2 = JwtConfig.PublicPrivateFile(key2Pub, key2Priv)

          (regs("someidp").jwtConfig.jwkSetUri === regs("someidp").registration.getProviderDetails.getJwkSetUri) and (
            regs("someidp").jwtConfig.discoverJwsAlgorithms === Some(false)
          ) and (
            regs("someidp").jwtConfig.jweAlgorithms === expectedAlg
          ) and (
            regs("someidp").jwtConfig.jweEncryptionMethods === expectedEnc
          ) and (
            regs("someidp").jwtConfig.keys === Map(
              "key1" -> JwkConfig(
                "key1",
                KeyType.RSA,
                use = None,
                config = Some(JwtConfig.RSA(keys1)(expectedKey1))
              ),
              "key2" -> JwkConfig(
                "key2",
                KeyType.EC,
                use = Some(JwtConfig.Use.Enc),
                config = Some(JwtConfig.EC(keys2)(expectedKey2))
              )
            )
          ) and (
            regs("someidp").jwtConfig.keys("key1").config.map(_.jwk) === Some(expectedKey1)
          ) and (
            regs("someidp").jwtConfig.keys("key2").config.map(_.jwk) === Some(expectedKey2)
          )
        }).get()
      }

      "with only private keys" in {
        (for {
          tmp      <- File.temporaryDirectory("rudder-test-oidc-config-")
          key1Priv <- withKey(tmp)("keys/key1.pem")
          key2Priv <- withKey(tmp)("keys/key2.pem")
        } yield {
          val config = ConfigFactory
            .parseString(
              s"""
                 |rudder.auth.oauth2.provider.someidp.jwt.keys.key1.privateFile=${key1Priv.pathAsString}
                 |rudder.auth.oauth2.provider.someidp.jwt.keys.key2.privateFile=${key2Priv.pathAsString}
                 |""".stripMargin
            )
            .withFallback(
              ConfigFactory.parseResources("oidc/oidc_jwt_private.properties")
            )

          val regs  = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap
          val keys1 = JwtConfig.PrivateFile(key1Priv)
          val keys2 = JwtConfig.PrivateFile(key2Priv)

          (regs("someidp").jwtConfig.jwkSetUri === regs("someidp").registration.getProviderDetails.getJwkSetUri) and (
            regs("someidp").jwtConfig.discoverJwsAlgorithms === Some(false)
          ) and (
            regs("someidp").jwtConfig.jweAlgorithms === expectedAlg
          ) and (
            regs("someidp").jwtConfig.jweEncryptionMethods === expectedEnc
          ) and (
            regs("someidp").jwtConfig.keys === Map(
              "key1" -> JwkConfig(
                "key1",
                KeyType.RSA,
                use = None,
                config = Some(JwtConfig.RSA(keys1)(expectedKey1))
              ),
              "key2" -> JwkConfig(
                "key2",
                KeyType.EC,
                use = Some(JwtConfig.Use.Enc),
                config = Some(JwtConfig.EC(keys2)(expectedKey2))
              )
            )
          ) and (
            regs("someidp").jwtConfig.keys("key1").config.map(_.jwk) === Some(expectedKey1)
          ) and (
            regs("someidp").jwtConfig.keys("key2").config.map(_.jwk) === Some(expectedKey2)
          )
        }).get()
      }

    }
  }

  "tenants mapping" should {
    val config       = ConfigFactory.parseResources("oidc/oidc_tenants.properties")
    val registration = RudderPropertyBasedOAuth2RegistrationDefinition.make().runNow
    val regs         = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

    "work for simple tenants" in {
      val tokenValues = Set("rudder_TA", "rudder_TB")
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", TenantAccessGrant.None)(_ => Some(tokenValues))

      tenants === TenantAccessGrant.ByTenants(Chunk(TenantAccess(TenantId("TA")), TenantAccess(TenantId("TB"))))
    }

    "work for no tenants" in {
      val tokenValues = Set.empty[String]
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", TenantAccessGrant.None)(_ => Some(tokenValues))

      tenants === TenantAccessGrant.None
    }

    "work for none" in {
      val tokenValues = Set("rudder_none", "rudder_TA")
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", TenantAccessGrant.None)(_ => Some(tokenValues))

      tenants === TenantAccessGrant.None
    }

    "work for all" in {
      val tokenValues = Set("rudder_all", "rudder_TA")
      val tenants     =
        RudderTokenMapping.getTenants(regs("someidp"), "user", "jwt", TenantAccessGrant.None)(_ => Some(tokenValues))

      tenants === TenantAccessGrant.All
    }
  }

  "reading a JWT configuration" should {
    val registration = RudderPropertyBasedJwtRegistrationDefinition.make().runNow

    "read the correct name" in {

      val config = ConfigFactory.parseResources("jwt/jwt_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp")
    }

    "have two entitlements mapping" in {

      val config = ConfigFactory.parseResources("jwt/jwt_simple.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"    -> "administrator",
        "rudder_readonly" -> "readonly"
      )) and (regs("someidp").tenants.mapping === Map(
        "rudder_TA" -> "TA",
        "rudder_TB" -> "TB"
      ))
    }

    "be able to use complex roles names with the reverse mapping" in {
      val config = ConfigFactory.parseResources("jwt/jwt_reverse_role_mapping.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      (regs("someidp").roles.mapping === Map(
        "rudder_admin"                                                                       -> "administrator",
        "rudder_readonly"                                                                    -> "readonlyOVERRIDDEN",
        "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "administrator"
      )) and (
        regs("someidp").tenants.mapping === Map(
          "rudder_TA"                                                                          -> "TA",
          "rudder_TB"                                                                          -> "TB_OVERRIDDEN",
          "CN=AAAA-BBBBB,OU=Groups,OU=_IT,OU=BB-DD,OU=UUU-XXXX-YY,DC=ee,DC=if,DC=ttttt,DC=uuu" -> "TA"
        )
      )
    }
  }

  "reading an opaque configuration" should {
    val reg = RudderOpaqueTokenRegistration(
      "someidp",
      "xxxClientIdxxx",
      "xxxClientPassxxx",
      "https://someidp/oauth2/v1/introspect",
      "email",
      Some(6.minutes)
    )

    val registration = RudderPropertyBasedOpaqueTokenRegistrationDefinition.make().runNow

    "read the correct configuration" in {

      val config = ConfigFactory.parseResources("opaque/opaque.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp") and regs("someidp") === reg
    }

    "read the correct configuration with default" in {

      val config = ConfigFactory.parseResources("opaque/opaque_default.properties")
      val regs   = registration.readAllRegistrations(config, registration.readOneRegistration).runNow.toMap

      regs.keySet === Set("someidp") and regs("someidp") === reg.copy(pivotAttributeName = "sub", cacheRequestDuration = None)
    }

  }

}
