package bootstrap.rudder.plugin

import com.normation.plugins.datasources.DataSourcesPluginDef

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.{ ApplicationContext, ApplicationContextAware }
import org.springframework.context.annotation.{ Bean, Configuration }
import com.normation.plugins.{ PluginName, PluginVersion, RudderPluginDef, SnippetExtensionRegister }

import net.liftweb.common.Loggable
import com.normation.inventory.domain.NodeId
import com.normation.rudder.web.rest.datasource.DataSourceApi9
import com.normation.rudder.services.servers.NewNodeManagerHooks
import com.normation.rudder.datasources.DataSourceJdbcRepository
import com.normation.rudder.datasources.DataSourceRepoImpl
import com.normation.rudder.services.policies.PromiseGenerationHooks
import com.normation.rudder.datasources.HttpQueryDataSourceService
import com.normation.rudder.web.rest.datasource.DataSourceApiService
import net.liftweb.common.Box
import org.joda.time.DateTime
import net.liftweb.common.Full
import bootstrap.liftweb.RudderConfig
import com.normation.rudder.web.rest.ApiVersion

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

  override def afterPropertiesSet() : Unit = {
      val ext = appContext.getBean(classOf[SnippetExtensionRegister])
    }

    override def setApplicationContext(applicationContext:ApplicationContext) = {
      appContext = applicationContext
    }
}

