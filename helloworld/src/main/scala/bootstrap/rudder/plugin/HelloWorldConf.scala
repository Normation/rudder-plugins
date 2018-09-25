package bootstrap.rudder.plugin

import scala.xml.{NodeSeq, Text}
import com.normation.plugins._
import com.normation.plugins.helloworld.HelloWorldPluginDef
import com.normation.plugins.helloworld.CheckRudderPluginEnableImpl
import com.normation.plugins.helloworld.extension.{CreateRuleEditFormExtension, CreateRuleExtension}
import com.normation.plugins.helloworld.service.LogAccessInDb
import bootstrap.liftweb.RudderConfig
import net.liftweb.common.Loggable

/**
 * Definition of services for the HelloWorld plugin.
 */
object HelloWorldConf extends Loggable with RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl()


  lazy val pluginDef = new HelloWorldPluginDef(dbService, pluginStatusService)

  RudderConfig.snippetExtensionRegister.register(new CreateRuleExtension())
  RudderConfig.snippetExtensionRegister.register(new CreateRuleEditFormExtension(dbService))

  lazy val dbService = new LogAccessInDb(
      RudderConfig.RUDDER_JDBC_URL
    , RudderConfig.RUDDER_JDBC_USERNAME
    , RudderConfig.RUDDER_JDBC_PASSWORD
  )
}

/**
 * An other plugin for testing purpose, to verify that
 * two plugins don't override each other one another.
 */
object Module1 extends Loggable with RudderPluginModule {

  /**
   * This class contains the metadatas about the plugins : it's version,
   * name, description, etc.
   */
  lazy val pluginDef = new RudderPluginDef {
    val status = new PluginEnableImpl(){}
    val name = PluginName("rudder-plugin-module1")
    val shortName = "module1"
    val version = PluginVersion(0,0,1)
    val basePackage = "com.normation.plugins.helloworld"
    val description : NodeSeq  = Text {
      "A template plugin for testing purpose"
    }

    val configFiles = Seq()


    def init = {
      logger.info("loading module 1")
    }
  }
}

