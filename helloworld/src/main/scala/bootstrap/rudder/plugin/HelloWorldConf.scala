package bootstrap.rudder.plugin

import bootstrap.liftweb.RudderConfig
import com.normation.plugins._
import com.normation.plugins.helloworld.CheckRudderPluginEnableImpl
import com.normation.plugins.helloworld.HelloWorldPluginDef
import com.normation.plugins.helloworld.extension.CreateRuleEditFormExtension
import com.normation.plugins.helloworld.extension.CreateRuleExtension
import com.normation.plugins.helloworld.service.LogAccessInDb
import com.normation.utils.ParseVersion
import net.liftweb.common.Loggable
import scala.xml.NodeSeq
import scala.xml.Text

/**
 * Definition of services for the HelloWorld plugin.
 */
object HelloWorldConf extends Loggable with RudderPluginModule {

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService = new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val pluginDef = new HelloWorldPluginDef(dbService, pluginStatusService)

  RudderConfig.snippetExtensionRegister.register(new CreateRuleExtension())
  RudderConfig.snippetExtensionRegister.register(new CreateRuleEditFormExtension(dbService))

  lazy val dbService = new LogAccessInDb(
    RudderConfig.RUDDER_JDBC_URL,
    RudderConfig.RUDDER_JDBC_USERNAME,
    RudderConfig.RUDDER_JDBC_PASSWORD
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
    val status      = new PluginEnableImpl() {}
    val name        = PluginName("rudder-plugin-module1")
    val shortName   = "module1"
    // version parsing error are not handled
    val version     = PluginVersion(ParseVersion.parse("7.0.0").getOrElse(???), ParseVersion.parse("0.0.1").getOrElse(???))
    val versionInfo = None
    val basePackage = "com.normation.plugins.helloworld"
    val description: NodeSeq = Text {
      "A template plugin for testing purpose"
    }

    val configFiles = Seq()

    def init = {
      logger.info("loading module 1")
    }
  }
}
