/*
*************************************************************************************
* Copyright 2016 Normation SAS
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

import bootstrap.liftweb.RudderConfig

import com.normation.inventory.domain.NodeId
import com.normation.plugins.datasources.DataSourceJdbcRepository
import com.normation.plugins.datasources.DataSourceRepoImpl
import com.normation.plugins.datasources.DataSourcesPluginDef
import com.normation.plugins.datasources.HttpQueryDataSourceService
import com.normation.plugins.datasources.api.DataSourceApi9
import com.normation.plugins.datasources.api.DataSourceApiService
import com.normation.rudder.services.policies.PromiseGenerationHooks
import com.normation.rudder.services.servers.NewNodeManagerHooks
import com.normation.rudder.web.rest.ApiVersion

import org.joda.time.DateTime
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.{ ApplicationContext, ApplicationContextAware }
import org.springframework.context.annotation.{ Bean, Configuration }

import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.common.Loggable

object DatasourcesConf {

  import bootstrap.liftweb.{ RudderConfig => Cfg }

  lazy val dataSourceRepository = new DataSourceRepoImpl(
      new DataSourceJdbcRepository(Cfg.doobie)
    , new HttpQueryDataSourceService(Cfg.nodeInfoService, Cfg.roLDAPParameterRepository, Cfg.woNodeRepository, Cfg.interpolationCompiler)
    , Cfg.stringUuidGenerator
  )

  // add data source pre-deployment update hook
  Cfg.deploymentService.appendPreGenCodeHook(new PromiseGenerationHooks() {
    def beforeDeploymentSync(generationTime: DateTime): Box[Unit] = {
      //that doesn't actually wait for the return. Not sure how to do it.
      Full(dataSourceRepository.onGenerationStarted(generationTime))
    }
  })

  Cfg.newNodeManager.appendPostAcceptCodeHook(new NewNodeManagerHooks() {
    def afterNodeAcceptedAsync(nodeId: NodeId): Unit = {
      dataSourceRepository.onNewNode(nodeId)
    }
  })

  // initialize datasources to start schedule
  dataSourceRepository.initialize()


  val dataSourceApiService = new DataSourceApiService(dataSourceRepository, Cfg.restDataSerializer, Cfg.restExtractorService)
  val dataSourceApi9 = new DataSourceApi9(Cfg.restExtractorService, dataSourceApiService, Cfg.stringUuidGenerator)

  // data source started with Rudder 4.1 / Api version 9.
  RudderConfig.apiDispatcher.addEndpoints(Map( ApiVersion(9, false) -> (dataSourceApi9 :: Nil)))

}

/**
 * Init module
 */
@Configuration
class DataSourcesPluginConf extends Loggable with  ApplicationContextAware with InitializingBean {
  @Bean def datasourceModuleDef = new DataSourcesPluginDef()


  // spring thingies
  var appContext : ApplicationContext = null

  override def afterPropertiesSet() : Unit = {}

  override def setApplicationContext(applicationContext:ApplicationContext) = {
    appContext = applicationContext
  }
}

