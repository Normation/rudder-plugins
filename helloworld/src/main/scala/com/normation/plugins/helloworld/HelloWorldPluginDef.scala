package com.normation.plugins.helloworld


import com.normation.plugins._
import com.normation.plugins.helloworld.service.LogAccessInDb

import bootstrap.liftweb.ClassPathResource
import net.liftweb.common.Loggable
import net.liftweb.http.ClasspathTemplates
import net.liftweb.sitemap.{ Menu }
import net.liftweb.sitemap.Loc.{ LocGroup, Template, Title }
import net.liftweb.sitemap.LocPath.stringToLocPath

class HelloWorldPluginDef(dbService:LogAccessInDb, override val status: PluginStatus ) extends DefaultPluginDef with Loggable {

  override val basePackage = "com.normation.plugins.helloworld"

  val configFiles = Seq(ClassPathResource("demo-config-1.properties"), ClassPathResource("demo-config-2.properties"))


  def init = {
    logger.info("loading helloWorld plugin")
    dbService.init()
  }

  def oneTimeInit : Unit = {}

  override def pluginMenuEntry: List[(Menu, Option[String])] = {
    (( Menu("HelloPluginConfig") / "secure" / "plugins" / "helloplugin" >>
      Title( x => <span>HelloPlugin</span>) >>
      LocGroup("administrationGroup") >>
      Template(() =>
        ClasspathTemplates( "helloPlugin" :: Nil ) openOr
        <div>Template not found</div>)
    ).toMenu, None) :: Nil
  }
}
