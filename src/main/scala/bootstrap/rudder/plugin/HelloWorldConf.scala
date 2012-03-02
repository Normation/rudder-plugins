package bootstrap.rudder.plugin

import org.springframework.context.annotation._
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.{ApplicationContext,ApplicationContextAware}
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.io.ClassPathResource
import org.joda.time.DateTime
import scala.collection.JavaConversions._
import com.normation.plugins._
import net.liftweb.common.Loggable
import scala.xml._
import com.normation.plugins.helloworld.HelloWorldPluginDef
import org.springframework.beans.factory.InitializingBean
import com.normation.plugins.helloworld.extension._
import com.normation.plugins._
import bootstrap.liftweb.AppConfig
import com.normation.rudder.services.policies.RuleTargetService
import com.normation.plugins.helloworld.service.LogAccessInDb
import bootstrap.liftweb.PropertyPlaceholderConfig

/**
 * Definition of services for the HelloWorld plugin.
 */
@Configuration
@Import(Array(classOf[PropertyPlaceholderConfig]))
class HelloWorldConf extends Loggable with  ApplicationContextAware with InitializingBean {
  
  var appContext : ApplicationContext = null

  
  //only work with Postgres for now
  @Value("${rudder.jdbc.url}")
  var jdbcUrl = ""
  @Value("${rudder.jdbc.username}")
  var jdbcUsername = ""
  @Value("${rudder.jdbc.password}")
  var jdbcPassword = ""
    
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
  
  @Bean def ext2 = new CreateRuleEditFormExtension(appContext.getBean(classOf[RuleTargetService]),dbService)

  @Bean def dbService = new LogAccessInDb(jdbcUrl, jdbcUsername, jdbcPassword)
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
      
      
    def init = {
      logger.info("loading module 1")
    }
 
    def oneTimeInit : Unit = {}
  }
}

