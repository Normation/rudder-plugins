/*
*************************************************************************************
* Copyright 2014 Normation SAS
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

  @Bean def nodeExternalReportDef = new NodeExternalReportPluginDef()

  @Bean def tabExtension = new CreateNodeDetailsExtension(readReport)

  @Bean def readReport = new ReadExternalReports(
      RudderConfig.nodeInfoService
    , nodeExternalReportDef.config.getString("plugin.externalReport.configFile")
  )

}
