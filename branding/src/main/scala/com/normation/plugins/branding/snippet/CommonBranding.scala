/*
*************************************************************************************
* Copyright 2018 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/


package com.normation.plugins.branding.snippet

import com.normation.plugins.SnippetExtensionPoint
import com.normation.rudder.web.snippet.CommonLayout
import net.liftweb.common.{Full, Loggable}
import net.liftweb.util._
import Helpers._
import bootstrap.rudder.plugin.BrandingPluginConf

import scala.reflect.ClassTag
import scala.xml.NodeSeq



class CommonBranding(implicit val ttag: ClassTag[CommonLayout])  extends SnippetExtensionPoint[CommonLayout] with Loggable {

  def compose(snippet:CommonLayout) : Map[String, NodeSeq => NodeSeq] = Map(
    "display" -> display _
  )

  private [this] val confRepo = BrandingPluginConf.brandingConfService

  def display(xml:NodeSeq) = {
    val data = confRepo.getConf
    val bar = data match {
      case Full(data) if (data.displayBar) =>
      <div id="headerBar">
        <span>{if (data.displayLabel) data.labelText}</span>
        <style>
          .main-header {{top:30px }}
          .content-wrapper, .main-sidebar {{padding-top:80px}}
          #headerBar {{height: 30px; padding-top:5px; font-size:20px; position: fixed; z-index:1050;background-color: {data.barColor.toRgba}; color: {data.labelColor.toRgba }; font-weight: 700; width:100%; text-align:center;}}
        </style>
      </div>
      case _ => NodeSeq.Empty
    }
    ("* -*" #> bar).apply(xml)

  }

}
