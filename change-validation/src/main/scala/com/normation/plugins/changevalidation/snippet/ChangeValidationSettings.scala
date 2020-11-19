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

package com.normation.plugins.changevalidation.snippet

import bootstrap.liftweb.RudderConfig
import com.normation.appconfig.ReadConfigService
import com.normation.appconfig.UpdateConfigService
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.js.JsCmds.Run
import net.liftweb.http.js.JsCmds.Script
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._

import com.normation.box._

import scala.xml.NodeSeq


class ChangeValidationSettings extends DispatchSnippet {

  private[this] val configService : ReadConfigService with UpdateConfigService = RudderConfig.configService
  private[this] val workflowLevelService = RudderConfig.workflowLevelService

  def dispatch = {
    case "activation" => workflowConfiguration
  }

  def workflowConfiguration: NodeSeq => NodeSeq = { xml : NodeSeq =>

    //  initial values, updated on successfull submit
    var initEnabled = configService.rudder_workflow_enabled().toBox
    var initSelfVal = configService.rudder_workflow_self_validation().toBox
    var initSelfDep = configService.rudder_workflow_self_deployment().toBox

    // form values
    var enabled = initEnabled.getOrElse(false)
    var selfVal = initSelfVal.getOrElse(false)
    var selfDep = initSelfDep.getOrElse(false)

    def submit = {
      configService.set_rudder_workflow_enabled(enabled).toBox.foreach(updateOk => initEnabled = Full(enabled))
      configService.set_rudder_workflow_self_validation(selfVal).toBox.foreach(updateOk => initSelfVal = Full(selfVal))
      configService.set_rudder_workflow_self_deployment(selfDep).toBox.foreach(updateOk => initSelfDep = Full(selfDep))
        S.notice("updateWorkflow","Change Requests (validation workflow) configuration correctly updated")
      check()
    }

    def noModif = (    initEnabled.map(_ == enabled).getOrElse(false)
                    && initSelfVal.map(_ == selfVal).getOrElse(false)
                    && initSelfDep.map(_ == selfDep).getOrElse(false)
                  )

    def check() = {
      if(!noModif){
        S.notice("updateWorkflow","")
      }
      Run(s"""$$("#workflowSubmit").attr("disabled",${noModif});""")
    }
    def initJs(newStatus :Boolean) = {
      enabled = newStatus
      check() &
      Run(
        s"""
            $$("#selfVal").attr("disabled",${!newStatus});
            $$("#selfDep").attr("disabled",${!newStatus});
            if(${!newStatus}){
              $$("#selfDep").parent().parent().addClass('disabled');
              $$("#selfVal").parent().parent().addClass('disabled');
            }else{
              $$("#selfDep").parent().parent().removeClass('disabled');
              $$("#selfVal").parent().parent().removeClass('disabled');
            }
        """
      )
    }

    // if the workflow plugin is not loaded, just removed the correstponding config
    val finalXml = if(workflowLevelService.workflowLevelAllowsEnable) {
      xml ++ Script(initJs(enabled))
    } else {
      NodeSeq.Empty
    }

    ( "#workflowEnabled" #> {
      initEnabled match {
        case Full(value) =>
          SHtml.ajaxCheckbox(
              value
            , initJs _
            , ("id","workflowEnabled")
            , ("class","twoCol")
          )
          case eb: EmptyBox =>
            val fail = eb ?~ "there was an error, while fetching value of property: 'Enable Change Requests' "
            <div class="error">{fail.msg}</div>
        }
      } &

      "#selfVal" #> {
        initSelfVal match {
          case Full(value) =>
            SHtml.ajaxCheckbox(
                value
              , (b : Boolean) => { selfVal = b; check() }
              , ("id","selfVal")
              , ("class","twoCol")
            )
          case eb: EmptyBox =>
            val fail = eb ?~ "there was an error, while fetching value of property: 'Allow self validation' "
            <div class="error">{fail.msg}</div>
        }

      } &

      "#selfDep " #> {
        initSelfDep match {
          case Full(value) =>
            SHtml.ajaxCheckbox(
                value
              , (b : Boolean) => { selfDep = b; check() }
              , ("id","selfDep")
              , ("class","twoCol")
            )
          case eb: EmptyBox =>
            val fail = eb ?~ "there was an error, while fetching value of property: 'Allow self deployment' "
            <div class="error">{fail.msg}</div>
        }
      } &

      "#selfValTooltip *" #> {

        initSelfVal match {
          case Full(_) =>
            val tooltipid = Helpers.nextFuncName
            <span class="tooltipable" tooltipid={tooltipid} title="">
              <span class="glyphicon glyphicon-info-sign info"></span>
            </span>
            <div class="tooltipContent" id={tooltipid}>
              Allow users to validate Change Requests they created themselves? Validating is moving a Change Request to the "<b>Pending deployment</b>" status
            </div>
          case _ => NodeSeq.Empty
        }
      } &

      "#selfDepTooltip *" #> {
        initSelfDep match {
          case Full(_) =>
            val tooltipid = Helpers.nextFuncName
            <span class="tooltipable" tooltipid={tooltipid} title="">
              <span class="glyphicon glyphicon-info-sign info"></span>
            </span>
            <div class="tooltipContent" id={tooltipid}>
              Allow users to deploy Change Requests they created themselves? Deploying is effectively applying a Change Request in the "<b>Pending deployment</b>" status.
            </div>
          case _ => NodeSeq.Empty
        }
      } &

      "#workflowSubmit " #> {
         SHtml.ajaxSubmit("Save change", () => submit, ("class","btn btn-default"))
      }
    ) apply (finalXml)
  }
}
