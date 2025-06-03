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
import com.normation.rudder.web.snippet.WithNonce
import com.normation.zio.UnsafeRun
import net.liftweb.http.*
import net.liftweb.http.js.JsCmds.Run
import net.liftweb.http.js.JsCmds.Script
import net.liftweb.util.Helpers.*
import scala.xml.NodeSeq
import zio.syntax.*

class ChangeValidationSettings extends DispatchSnippet {

  private[this] val configService: ReadConfigService with UpdateConfigService = RudderConfig.configService
  private[this] val workflowLevelService = RudderConfig.workflowLevelService

  def dispatch = {
    case "activation" => workflowConfiguration
    case "validation" => validationConfiguration
  }

  def workflowConfiguration: NodeSeq => NodeSeq = { (xml: NodeSeq) =>
    //  initial values, updated on successful submit
    var initEnabled = configService.rudder_workflow_enabled()
    var initSelfVal = configService.rudder_workflow_self_validation()
    var initSelfDep = configService.rudder_workflow_self_deployment()

    // form values
    var enabled = initEnabled.orElseSucceed(false).runNow
    var selfVal = initSelfVal.orElseSucceed(false).runNow
    var selfDep = initSelfDep.orElseSucceed(false).runNow

    def submit = {
      configService.set_rudder_workflow_enabled(enabled).either.runNow match {
        case Right(_) => initEnabled = enabled.succeed
        case _        => ()
      }

      configService.set_rudder_workflow_self_validation(selfVal).either.runNow match {
        case Right(_) => initSelfVal = selfVal.succeed
        case _        => ()
      }

      configService.set_rudder_workflow_self_deployment(selfDep).either.runNow match {
        case Right(_) => initSelfDep = selfDep.succeed
        case _        => ()
      }

      S.notice("updateWorkflow", "Change Requests (validation workflow) configuration correctly updated")
      check()
    }

    def noModif = (initEnabled.map(_ == enabled).orElseSucceed(false).runNow
      && initSelfVal.map(_ == selfVal).orElseSucceed(false).runNow
      && initSelfDep.map(_ == selfDep).orElseSucceed(false).runNow)

    def check()                    = {
      if (!noModif) {
        S.notice("updateWorkflow", "")
      }
      Run(s"""$$("#workflowSubmit").attr("disabled",${noModif});""")
    }
    def initJs(newStatus: Boolean) = {
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
    val finalXml = if (workflowLevelService.workflowLevelAllowsEnable) {
      xml ++ WithNonce.scriptWithNonce(Script(initJs(enabled)))
    } else {
      NodeSeq.Empty
    }

    ("#workflowEnabled" #> {
      initEnabled
        .chainError("there was an error while fetching value of property: 'Enable Change Requests' ")
        .either
        .runNow match {
        case Right(value) =>
          SHtml.ajaxCheckbox(
            value,
            initJs _,
            ("id", "workflowEnabled"),
            ("class", "twoCol")
          )
        case Left(err)    =>
          <div class="error">{err.msg}</div>
      }
    } &

    "#selfVal" #> {
      initSelfVal
        .chainError("there was an error while fetching value of property: 'Allow self validation' ")
        .either
        .runNow match {
        case Right(value) =>
          SHtml.ajaxCheckbox(
            value,
            (b: Boolean) => { selfVal = b; check() },
            ("id", "selfVal"),
            ("class", "twoCol")
          )
        case Left(err)    =>
          <div class="error">{err.msg}</div>
      }
    } &

    "#selfDep " #> {
      initSelfDep
        .chainError("there was an error while fetching value of property: 'Allow self deployment' ")
        .either
        .runNow match {
        case Right(value) =>
          SHtml.ajaxCheckbox(
            value,
            (b: Boolean) => { selfDep = b; check() },
            ("id", "selfDep"),
            ("class", "twoCol")
          )
        case Left(err)    =>
          <div class="error">{err.msg}</div>
      }
    } &

    "#selfValTooltip *" #> {

      initSelfVal.either.runNow match {
        case Right(_) =>
          val tooltipMsg =
            """Allow users to validate Change Requests they created themselves? Validating is moving a Change Request to the "<b>Pending deployment</b>" status"""
          <span class="fa fa-info-circle icon-info" data-bs-toggle="tooltip" data-bs-placement="bottom" title={tooltipMsg}></span>
        case Left(_)  => NodeSeq.Empty
      }
    } &

    "#selfDepTooltip *" #> {

      initSelfDep.either.runNow match {
        case Right(_) =>
          val tooltipMsg =
            """Allow users to deploy Change Requests they created themselves? Deploying is effectively applying a Change Request in the "<b>Pending deployment</b>" status."""
          <span class="fa fa-info-circle icon-info" data-bs-toggle="tooltip" data-bs-placement="bottom" title={tooltipMsg}></span>
        case Left(_)  => NodeSeq.Empty
      }
    } &

    "#workflowSubmit " #> {
      SHtml.ajaxSubmit("Save change", () => submit, ("class", "btn btn-default"))
    }) apply (finalXml)
  }

  // same as workflowConfiguration but with 1 single checkbox, and val autoValidatedUsers = configService.rudder_workflow_validation_auto_validated_users().toBox
  def validationConfiguration: NodeSeq => NodeSeq = { (xml: NodeSeq) =>
    // initial value, updated on successful submit
    var initAutoValidatedUsers = configService.rudder_workflow_validate_all()

    // form value
    var autoValidatedUsers = initAutoValidatedUsers.orElseSucceed(false).runNow

    def submit = {
      configService
        .set_rudder_workflow_validate_all(autoValidatedUsers)
        .either
        .runNow match {
        case Right(_) => initAutoValidatedUsers = autoValidatedUsers.succeed
        case _        => ()
      }
      S.notice("updateValidation", "Validation configuration correctly updated")
      check()
    }

    def noModif = initAutoValidatedUsers.map(_ == autoValidatedUsers).orElseSucceed(false).runNow

    def check() = {
      if (!noModif) {
        S.notice("updateValidation", "")
      }
      Run(s"""$$("#validationSubmit").attr("disabled",${noModif});""")
    }

    ("#validationAutoValidatedUser" #> {
      initAutoValidatedUsers
        .chainError("there was an error while fetching value of property: 'Auto validated users' ")
        .either
        .runNow match {
        case Right(value) =>
          SHtml.ajaxCheckbox(
            value,
            (b: Boolean) => { autoValidatedUsers = b; check() },
            ("id", "validationAutoValidatedUser"),
            ("class", "twoCol")
          )
        case Left(err)    =>
          <div class="error">{err.msg}</div>
      }
    } &
    "#validationAutoSubmit " #> {
      SHtml.ajaxSubmit("Save change", () => submit, ("class", "btn btn-default"))
    })(xml)
  }
}
