package com.normation.plugins.datasources

import scala.xml.NodeSeq

import com.normation.plugins.{ PluginName, PluginVersion, RudderPluginDef }

import bootstrap.liftweb.ClassPathResource
import net.liftweb.common.Loggable
import net.liftweb.http.ClasspathTemplates
import net.liftweb.sitemap.{ Menu }
import net.liftweb.sitemap.Loc.{ LocGroup, Template, Title }
import net.liftweb.sitemap.LocPath.stringToLocPath
import net.liftweb.sitemap.Loc.TestAccess
import com.normation.rudder.authorization.Read
import bootstrap.liftweb.Boot
import com.normation.rudder.domain.logger.PluginLogger
import bootstrap.rudder.plugin.DataSourcesPluginConf
import net.liftweb.http.LiftRules
import bootstrap.rudder.plugin.DatasourcesConf
import net.liftweb.http.ResourceServer

class DataSourcesPluginDef() extends RudderPluginDef with Loggable {

  val name = PluginName("data sources")
  val basePackage = "com.normation.plugins.datasources"
  val version = PluginVersion(1,0,0)
  val description : NodeSeq  =
    <div>
     Data source plugin allows to get node properties from third parties provider accessible via a REST API.
     Configuration can be done in <a href="/secure/administration/dataSourceManagement">the dedicated management page</a>
    </div>

  def init = {
    PluginLogger.info("loading 'datasources' plugin")
    LiftRules.statelessDispatch.append(DatasourcesConf.dataSourceApi9)
    // resources in src/main/resources/toserve must be allowed:
    ResourceServer.allow{
      case "datasources" :: _ => true
    }
  }

  def oneTimeInit : Unit = {}

  val configFiles = Seq()

  override def updateSiteMap(menus:List[Menu]) : List[Menu] = {
    val datasourceMenu = (
      Menu("dataSourceManagement", <span>Data sources</span>) /
        "secure" / "administration" / "dataSourceManagement"
        >> LocGroup("administrationGroup")
        >> TestAccess ( () => Boot.userIsAllowed("/secure/administration/policyServerManagement",Read("administration")) )
        >> Template(() => ClasspathTemplates( "template" :: "dataSourceManagement" :: Nil ) openOr <div>Template not found</div>)
    )

    menus.map {
      case m@Menu(l, _* ) if(l.name == "AdministrationHome") =>
        Menu(l , m.kids.toSeq :+ datasourceMenu:_* )
      case m => m
    }
  }

}
