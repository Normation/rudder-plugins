package com.normation.plugins.changesvalidation

import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.web.snippet.CommonLayout
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers._

import scala.reflect.ClassTag
import scala.xml.NodeSeq

class TopBarExtension(val status: PluginStatus)(implicit val ttag: ClassTag[CommonLayout]) extends PluginExtensionPoint[CommonLayout] with Loggable {

  def pluginCompose(snippet: CommonLayout) : Map[String, NodeSeq => NodeSeq] = Map(
      "display" -> render _
  )

  def render(xml:NodeSeq) = {
    (
     "#rudder-navbar -*" #> <li class="lift:comet?type=WorkflowInformation" name="workflowInfo" ></li>
    ).apply(xml)
  }

}
