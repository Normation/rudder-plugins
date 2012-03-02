package com.normation.plugins.helloworld

import com.normation.plugins._
import scala.xml._
import net.liftweb.common.Loggable
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._
import net.liftweb.http.Templates
import com.normation.plugins.helloworld.service.LogAccessInDb
import net.liftweb.http.ClasspathTemplates

class HelloWorldPluginDef(dbService:LogAccessInDb) extends RudderPluginDef with Loggable {
  
  val name = PluginName("hello world")
  val basePackage = "com.normation.plugins.helloworld"
  val version = PluginVersion(0,0,1)
  val description : NodeSeq  = 
    <div>
    An <b>Hello World !</b> template plugin"
    </div>
  
    
    
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
