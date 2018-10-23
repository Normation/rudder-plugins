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

package com.normation.plugins.authbackends

import bootstrap.liftweb.AuthBackendProvidersManager
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueType

/*
 * This class handle the translation between configuration files
 * for authentication and information that can be presented to the
 * user.
 */
class AuthBackendsRepository(
    authService     : AuthBackendProvidersManager
  , configParameters: Config
) {



  /**
   * Get all information about the currently configured back-ends in a
   * format that can be understood by client side.
   */
  def getConfigOption(): Either[Exception, JsonAuthConfiguration] = {

    // utility method which look in "config" for the key and
    // return an ConfigOption for the value, with an empty value
    // if the key was missing
    def param(desc: String, key: String) = {
      try {
        val confValue = configParameters.getValue(key)
        val value = confValue.valueType() match { // arg java API :/
          case ConfigValueType.STRING
             | ConfigValueType.BOOLEAN
             | ConfigValueType.NUMBER => confValue.unwrapped().toString
          case ConfigValueType.NULL => "" // treat it like a missing, which it is likely
          case ConfigValueType.LIST
             | ConfigValueType.OBJECT => confValue.render(ConfigRenderOptions.concise())
        }
        ConfigOption(desc, key, value)
      } catch {
        case ex: ConfigException.Missing =>
          ConfigOption(desc, key, "")
      }
    }


    // hide a passward with stars
    implicit class HideValue(conf: ConfigOption) {
      def hideValue() = conf.copy(value = "****")
    }

    //fill-in all information for the known backend.


    val rootAdmin = {
      val login = param("Login for root admin"
          , "rudder.auth.admin.login"
      )
      val password = param("Password for the root admin"
          ,   "rudder.auth.admin.password"
      ).hideValue()


      JsonAdminConfig(
        """Rudder has a root admin account, with full rights on the
            # application, and whose authentication is independant from
            # the authentication provider chosen (file, LDAP, etc).
            # By default, the accound is disabled (either by letting the
            # the login or the password empty, or by commenting it)."""
        , login
        , password
        , login.value.nonEmpty && password.value.nonEmpty
      )
    }

    val file = JsonFileConfig(
        "file"
      , """By default, Rudder authentication is collocated with user authorisatoin
          |in 'rudder-user.xml' file. The file is located:""".stripMargin
      , "/opt/rudder/etc/rudder-users.xml"
    )

    val ldap = JsonLdapConfig(
        "ldap"
      , """# The following parameters allow to configure the LDAP authentication provider.
           # The LDAP authentication procedure is a typical bind/search/rebind, in which
           # an application connection (bind) is used to search (search) for an user entry
           # given some base and filter parameters, and then, a bind (rebind) is tried on
           # that entry with the credential provided by the user.
           # That allows to seperate the user DN (especially RDN) from the search criteria.
           #
           # Be careful, the authorization is still done in the rudder-user.xml, what means
           # that each user should have access to Rudder MUST have a line in that file.
           # Without that line, the user can have a successful LDAP authentication, but
           # won't be able to do or see anything in Rudder (safe logout).""".stripMargin('#')
      , param("""# Connection URL to the LDAP server, in the form:
                 # ldap://hostname:port/base_dn""".stripMargin('#')
          , "rudder.auth.ldap.connection.url"
        )
      , param("""# Bind DN used by Rudder to do the search
                 # LDAP dn, no default value.""".stripMargin('#')
          , "rudder.auth.ldap.connection.bind.dn"
        )
      , param("""# Bind password used by Rudder to do the search.
                 # String, no default value.""".stripMargin('#')
          , "rudder.auth.ldap.connection.bind.password"
        ).hideValue()
      , param("""# Search base and filter to use to find the user.
                 # The search base can be left empty. In that
                 # case, the root of directory is used.""".stripMargin('#')
          , "rudder.auth.ldap.searchbase"
        )
      , param("""# In the filter, {0} denotes the value provided as
                 # login by the user.
                 # The filter must lead to at most one result, which
                 # will be used to try the bind request.""".stripMargin('#')
          , "rudder.auth.ldap.filter"
        )
    )

    val radius = JsonRadiusConfig(
        "radius"
      , """# The following parameters allow to configure authentication with a
           # Radius server.""".stripMargin('#')
      , param("""# IP or hostname of the Radius server. Both work, but it
                 # is prefered to use an IP.""".stripMargin('#')
          , "rudder.auth.radius.host.name"
        )
      , param("""# Authentication port for the Radius server""".stripMargin('#')
          , "rudder.auth.radius.host.authPort"
        )
      , param("""# The shared secret as configured in your Radius server
                 # for Rudder application / host.""".stripMargin('#')
          , "rudder.auth.radius.host.sharedSecret"
        ).hideValue()
      , param("""# Time to wait in seconds when trying to connect to
                 # the server before giving up.""".stripMargin('#')
        , "rudder.auth.radius.auth.timeout")
      , param("""# Number of retries to attempt in case of timeout before
                 # giving up.""".stripMargin('#')
        , "rudder.auth.radius.auth.retries")
      , param("""# Authentication protocol to use to connect to the Radius server.
                 # The default one is 'pap' (PAP).
                 # Available protocols are: pap, chap, eap-md5, eap-ttls.
                 #
                 # For `eap-ttls`, you can append `key=value` parameters, separated by `:`
                 # to the protocol name to specify protocol option, for example:
                 # `eap-tls:keyFile=keystore:keyPassword=mypass`""".stripMargin('#')
          , "rudder.auth.radius.auth.protocol"
        )
    )

   // get configured backend order - it shall not fail.
    val configuredOrder = try {
      configParameters.getString("rudder.auth.provider")
    } catch {
      case ex: ConfigException.Missing => "" // if the key is absent, default config will be used
    }

    val usedOrder = authService.getConfiguredProviders().map( _.name )


    Right(JsonAuthConfiguration(
        configuredOrder
      , usedOrder
      , rootAdmin
      , file
      , ldap
      , radius
    ))
  }
}
