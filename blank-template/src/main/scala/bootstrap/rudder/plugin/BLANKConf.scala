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

package bootstrap.rudder.plugin

import com.normation.plugins.BLANK.BLANKPluginDef
import com.normation.plugins.BLANK.CheckRudderPluginEnableImpl
import net.liftweb.common.Loggable
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{Bean, Configuration}


/*
 * Actual configuration of the plugin logic
 */
object BLANKConf {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl()

  // other service instanciation / initialization

}

/**
 * Init module
 */
@Configuration
class BLANKPluginConf extends Loggable with  ApplicationContextAware with InitializingBean {
  @Bean def bLANKModuleDef = new BLANKPluginDef(BLANKConf.pluginStatusService)


  // spring thingies
  var appContext : ApplicationContext = null

  override def afterPropertiesSet() : Unit = {}

  override def setApplicationContext(applicationContext:ApplicationContext) = {
    appContext = applicationContext
  }
}

