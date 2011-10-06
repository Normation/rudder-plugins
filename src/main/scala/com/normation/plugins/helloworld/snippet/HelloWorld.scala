package com.normation.plugins.helloworld.snippet

import net.liftweb.common._
import net.liftweb.http.DispatchSnippet
import net.liftweb.util._
import net.liftweb.util.Helpers._
import scala.xml.NodeSeq


class HelloWorld extends DispatchSnippet {

  
  def dispatch = {
    case "hello" => sayHello
  }
  
  
  def sayHello = "#snippetHello" #> "Hello from the HelloWorld Snippet"
}