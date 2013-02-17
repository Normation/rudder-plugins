package bootstrap.rudder.plugin

import scala.xml.{ NodeSeq, Text }

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.{ ApplicationContext, ApplicationContextAware }
import org.springframework.context.annotation.{ Bean, Configuration }

import com.normation.plugins.{ PluginName, PluginVersion, RudderPluginDef, SnippetExtensionRegister }
import com.normation.plugins.helloworld.HelloWorldPluginDef
import com.normation.plugins.helloworld.extension.{ CreateRuleEditFormExtension, CreateRuleExtension }
import com.normation.plugins.helloworld.service.LogAccessInDb

import bootstrap.liftweb.RudderConfig
import net.liftweb.common.Loggable

/**
 * Definition of services for the HelloWorld plugin.
 */
@Configuration
class HelloWorldConf extends Loggable with  ApplicationContextAware with InitializingBean {

  var appContext : ApplicationContext = null

  override def afterPropertiesSet() : Unit = {
    val ext = appContext.getBean(classOf[SnippetExtensionRegister])
    ext.register(ext1)
    ext.register(ext2)
  }

  override def setApplicationContext(applicationContext:ApplicationContext) = {
    appContext = applicationContext
  }

  @Bean def moduleDef2 = new HelloWorldPluginDef(dbService)

  @Bean def ext1 = new CreateRuleExtension()

  @Bean def ext2 = new CreateRuleEditFormExtension(RudderConfig.ruleTargetService, dbService)

  @Bean def dbService = new LogAccessInDb(
      RudderConfig.RUDDER_JDBC_URL
    , RudderConfig.RUDDER_JDBC_USERNAME
    , RudderConfig.RUDDER_JDBC_PASSWORD
  )
}

/**
 * An other plugin for testing purpose, to verify that
 * two plugins don't override each other one another.
 */
@Configuration
class Module1 extends Loggable {

  /**
   * This class contains the metadatas about the plugins : it's version,
   * name, description, etc.
   */
  @Bean def moduleDef1 = new RudderPluginDef {
    val name = PluginName("module1")
    val version = PluginVersion(0,0,1)
    val basePackage = "com.normation.plugins.helloworld"
    val description : NodeSeq  = Text {
      "A template plugin for testing purpose"
    }

    val configFiles = Seq()


    def init = {
      logger.info("loading module 1")
    }

    def oneTimeInit : Unit = {}
  }
}

