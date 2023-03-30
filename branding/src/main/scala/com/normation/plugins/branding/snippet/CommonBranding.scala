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

import bootstrap.rudder.plugin.BrandingPluginConf
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.web.snippet.CommonLayout
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers._
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class CommonBranding(val status: PluginStatus)(implicit val ttag: ClassTag[CommonLayout])
    extends PluginExtensionPoint[CommonLayout] with Loggable {

  def pluginCompose(snippet: CommonLayout): Map[String, NodeSeq => NodeSeq] = Map(
    "display" -> display _
  )

  private[this] val confRepo = BrandingPluginConf.brandingConfService

  def display(xml: NodeSeq) = {
    val data                                          = confRepo.getConf
    val bar                                           = data match {
      case Full(d) if (d.displayBar) =>
        <div id="headerBar">
        <div class="background">
          <span>{if (d.displayLabel) d.labelText}</span>
        </div>
        <style>
          .main-header {{top:30px }}
          .content-wrapper, .main-sidebar {{padding-top:80px}}
          #headerBar {{height: 30px; position: fixed; z-index:1050; background-color: #fff; width:100%;}}
          #headerBar > .background {{background-color: {d.barColor.toRgba}; color: {
          d.labelColor.toRgba
        }; font-size:20px; font-weight: 700; text-align:center; position: absolute;top: 0;bottom: 0;left: 0;right: 0;}}
          #headerBar + .wrapper aside.main-sidebar.fixed {{height: calc(100% - 80px); min-height: calc(100% - 80px); margin-top: 80px;}}
          #headerBar ~ .content-wrapper .rudder-template .template-main .main-container,
          #headerBar ~ .content-wrapper .rudder-template .template-main > .main-table{{height: calc(100vh - 80px);}}
        </style>
      </div>
      case _                         => NodeSeq.Empty
    }
    var (customWideLogo, customSmallLogo, rudderLogo) = data match {
      case Full(d) =>
        (
          d.wideLogo.commonWideLogo,
          d.smallLogo.commonSmallLogo, {
            if ((d.wideLogo.enable && d.wideLogo.data.isDefined) || (d.smallLogo.enable && d.smallLogo.data.isDefined)) {
              <a target="_blank" href="https://www.rudder.io/" class="rudder-branding-footer">
              {
                if (d.wideLogo.enable && d.wideLogo.data.isDefined)
                  <img alt="Rudder" data-lift="with-cached-resource" src="/images/logo-rudder-white.svg" class="rudder-branding-logo-lg"/>
                else NodeSeq.Empty
              }
              {
                if (d.smallLogo.enable && d.smallLogo.data.isDefined)
                  <img alt="Rudder" data-lift="with-cached-resource" src="/images/logo-rudder-sm.svg"    class="rudder-branding-logo-sm"/>
                else NodeSeq.Empty
              }
            </a>
            } else {
              NodeSeq.Empty
            }
          }
        )
      case _       =>
        (
          <img alt="Rudder" data-lift="with-cached-resource" src="/images/logo-rudder-nologo.svg" class="rudder-branding-logo-lg"/>,
          <img alt="Rudder" data-lift="with-cached-resource" src="/images/logo-rudder-sm.svg"     class="rudder-branding-logo-sm"/>,
          NodeSeq.Empty
        )
    }
    val style                                         = {
      <style>
        .custom-branding-logo {{
        background-size: contain;
        background-repeat: no-repeat;
        background-position: center;
        flex: 1;
        }}
        .sidebar-menu > li.treeview.footer{{
        margin-top: 0;
        }}
        .sidebar-menu > li.plugin-info{{
        display: block !important;
        margin-top: auto;
        }}
        .sidebar-menu>li.plugin-info > a{{
        border: none !important;
        padding: 8px;
        }}
        a.rudder-branding-footer{{
        opacity: .6;
        transition-duration: .2s;
        }}
        a.rudder-branding-footer:hover{{
        opacity: 1;
        }}
        .sidebar-collapse a.rudder-branding-footer .rudder-branding-logo-lg,
        a.rudder-branding-footer .rudder-branding-logo-sm{{
        display: none;
        }}
        a.rudder-branding-footer .rudder-branding-logo-lg{{
          display: block;
          width: 80%;
          margin: auto;
        }}
        .sidebar-collapse a.rudder-branding-footer .rudder-branding-logo-sm{{
        display: block;
        width: 100%;
        }}
        .sidebar-collapse a.rudder-branding-footer .rudder-branding-logo-sm{{
        max-width: 35px;
        }}
      </style>
    }
    val commonBrandingCss: NodeSeq = data match {
      case Full(d) =>
        (d.wideLogo.enable, d.wideLogo.data, d.smallLogo.enable, d.smallLogo.data) match {
          case (true, Some(wideLogoData), _, _)  => style
          case (_, _, true, Some(smallLogoData)) => style
          case _                                 => NodeSeq.Empty
        }
      case _       => NodeSeq.Empty
    }

    (".logo-lg *" #> customWideLogo & ".logo-mini *" #> customSmallLogo & ".plugin-info -*" #> rudderLogo & ".plugin-info -*" #> commonBrandingCss)
      .apply(("* -*" #> bar & ".treeview-footer -*" #> rudderLogo).apply(xml))
  }

}
