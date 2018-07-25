package com.normation.plugins.helloworld.snippet

import net.liftweb.http.DispatchSnippet
import net.liftweb.util.Helpers._
import bootstrap.rudder.plugin.HelloWorldConf

class HelloWorld extends DispatchSnippet {

  val moduleConfig =  HelloWorldConf.pluginDef

  def dispatch = {
    case "hello" => sayHello
  }


  def sayHello = {
    val props = Seq("plugin.hello.world", "override.value", "rudder.community.port")

    "#snippetHello *" #> "Hello from the HelloWorld Snippet" &
    props.map( prop =>
      s"#${prop} *" #> s"${prop} value: ${moduleConfig.config.getString(prop)}"
    ).reduceLeft( _ & _)
  }
}
