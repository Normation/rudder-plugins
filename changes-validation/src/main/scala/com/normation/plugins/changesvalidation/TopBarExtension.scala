package com.normation.plugins.changesvalidation

import com.normation.plugins.SnippetExtensionPoint
import com.normation.rudder.web.snippet.CommonLayout
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers._

import scala.reflect.ClassTag
import scala.xml.NodeSeq

class TopBarExtension(implicit val ttag: ClassTag[CommonLayout]) extends SnippetExtensionPoint[CommonLayout] with Loggable {

  def compose(snippet: CommonLayout) : Map[String, NodeSeq => NodeSeq] = Map(
      "display" -> render _
  )

  def render(xml:NodeSeq) = {

    println("**** I'm extending! ****")

    (
     "#rudder-navbar -*" #> <li class="lift:comet?type=WorkflowInformation" name="workflowInfo" ></li>
    ).apply(xml)
  }

}
