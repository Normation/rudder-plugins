package com.normation.plugins.helloworld.snippet

import net.liftweb.common._
import net.liftweb.http.DispatchSnippet
import net.liftweb.util._
import net.liftweb.util.Helpers._
import scala.xml.NodeSeq
import bootstrap.liftweb.LiftSpringApplicationContext.inject
import com.normation.plugins.helloworld.HelloWorldPluginDef

class HelloWorld extends DispatchSnippet {

  val moduleConfig = inject[HelloWorldPluginDef]

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