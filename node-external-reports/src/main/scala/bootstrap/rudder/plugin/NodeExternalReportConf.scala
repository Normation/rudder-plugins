/*
*************************************************************************************
* Copyright 2014 Normation SAS
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

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.{ ApplicationContext, ApplicationContextAware }
import org.springframework.context.annotation.{ Bean, Configuration }
import com.normation.plugins.SnippetExtensionRegister
import com.normation.plugins.nodeexternalreport.NodeExternalReportPluginDef
import com.normation.plugins.nodeexternalreport.extension.CreateNodeDetailsExtension
import com.normation.plugins.nodeexternalreport.service.ReadExternalReports
import bootstrap.liftweb.RudderConfig
import net.liftweb.common.Loggable
import com.normation.plugins.nodeexternalreport.service.NodeExternalReportApi
import com.normation.rudder.domain.logger.ApplicationLogger

/**
 * Definition of services for the HelloWorld plugin.
 */
@Configuration
class NodeExternalReportConf extends Loggable with ApplicationContextAware with InitializingBean {

  var appContext : ApplicationContext = null

  override def afterPropertiesSet() : Unit = {
    val ext = appContext.getBean(classOf[SnippetExtensionRegister])
    ext.register(tabExtension)
    logger.info("Extension initialized")
  }

  override def setApplicationContext(applicationContext: ApplicationContext) = {
    appContext = applicationContext
  }

  @Bean def readReport = {

    val CONFIG_FILE_KEY = "rudder.plugin.externalNodeInformation.config"
    val defaultPath = "/opt/rudder/share/plugins/node-external-reports/node-external-reports.properties"

    val configPath = System.getProperty(CONFIG_FILE_KEY) match {
      case null | "" => //use default location in standard plugin place
        ApplicationLogger.info(s"JVM property -D${CONFIG_FILE_KEY} is not defined, use configuration file at '${defaultPath}'")
        defaultPath
      case x => //so, it should be a full path, check it
        ApplicationLogger.info(s"Use configuration file defined by JVM property -D${CONFIG_FILE_KEY}: ${x}.")
        x
    }

    new ReadExternalReports(RudderConfig.nodeInfoService, configPath)
  }

  @Bean def externalNodeReportApi = new NodeExternalReportApi(readReport)

  @Bean def nodeExternalReportDef = new NodeExternalReportPluginDef(externalNodeReportApi)

  @Bean def tabExtension = new CreateNodeDetailsExtension(readReport)

}
