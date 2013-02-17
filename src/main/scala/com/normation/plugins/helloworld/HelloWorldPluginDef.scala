package com.normation.plugins.helloworld

import scala.xml.NodeSeq

import com.normation.plugins.{ PluginName, PluginVersion, RudderPluginDef }
import com.normation.plugins.helloworld.service.LogAccessInDb

import bootstrap.liftweb.ClassPathResource
import net.liftweb.common.Loggable
import net.liftweb.http.ClasspathTemplates
import net.liftweb.sitemap.{ Menu }
import net.liftweb.sitemap.Loc.{ LocGroup, Template, Title }
import net.liftweb.sitemap.LocPath.stringToLocPath

class HelloWorldPluginDef(dbService:LogAccessInDb) extends RudderPluginDef with Loggable {

  val name = PluginName("hello world")
  val basePackage = "com.normation.plugins.helloworld"
  val version = PluginVersion(0,0,1)
  val description : NodeSeq  =
    <div>
    An <b>Hello World !</b> template plugin"
    </div>

  val configFiles = Seq(ClassPathResource("demo-config-1.properties"), ClassPathResource("demo-config-2.properties"))


  def init = {
    logger.info("loading helloWorl plugin")
    dbService.init()
  }

  def oneTimeInit : Unit = {}

  override def updateSiteMap(menus:List[Menu]) : List[Menu] = {
    val helloLoc =
      Menu("HelloPluginConfig") / "secure" / "administration" / "helloplugin" >>
        Title( x => <span>HelloPlugin</span>) >>
        LocGroup("administrationGroup") >>
        Template(() =>
          ClasspathTemplates( "helloPlugin" :: Nil ) openOr
          <div>Template not found</div>)

    menus.map {
      case m@Menu(l, _* ) if(l.name == "AdministrationHome") =>
        Menu(l , m.kids.toSeq :+ helloLoc:_* )
      case m => m
    }
  }

}
