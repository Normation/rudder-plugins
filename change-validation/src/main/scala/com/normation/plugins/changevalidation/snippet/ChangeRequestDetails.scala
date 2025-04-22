/*
 *************************************************************************************
 * Copyright 2011-2013 Normation SAS
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
import bootstrap.rudder.plugin.ChangeValidationConf
import com.normation.box.*
import com.normation.errors.IOResult
import com.normation.eventlog.EventActor
import com.normation.plugins.changevalidation.ChangeValidationLogger
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl
import com.normation.rudder.AuthorizationType
import com.normation.rudder.domain.eventlog.AddChangeRequest
import com.normation.rudder.domain.eventlog.DeleteChangeRequest
import com.normation.rudder.domain.eventlog.ModifyChangeRequest
import com.normation.rudder.domain.workflows.*
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.services.workflows.NoWorkflowAction
import com.normation.rudder.services.workflows.WorkflowAction
import com.normation.rudder.users.CurrentUser
import com.normation.rudder.web.ChooseTemplate
import com.normation.rudder.web.model.*
import com.normation.utils.DateFormaterService
import com.normation.zio.UnsafeRun
import net.liftweb.common.*
import net.liftweb.http.*
import net.liftweb.http.js.*
import net.liftweb.http.js.JE.*
import net.liftweb.http.js.JsCmds.*
import net.liftweb.util.Helpers.*
import scala.xml.NodeSeq
import scala.xml.Text

object ChangeRequestDetails {

  private[this] val templatePath = "toserve" :: "changevalidation" :: "ComponentChangeRequest" :: Nil

  val header             = ChooseTemplate(templatePath, "component-header")
  val popup              = ChooseTemplate(templatePath, "component-popup")
  val popupContent       = ChooseTemplate(templatePath, "component-popupcontent")
  val actionButtons      = ChooseTemplate(templatePath, "component-actionbuttons")
  def unmergeableWarning = ChooseTemplate(templatePath, "component-warnunmergeable")
}

class ChangeRequestDetails extends DispatchSnippet with Loggable {
  import ChangeRequestDetails.*

  private[this] val userPropertyService          = RudderConfig.userPropertyService
  private[this] val workFlowEventLogService      = RudderConfig.workflowEventLogService
  private[this] val changeRequestEventLogService = RudderConfig.changeRequestEventLogService
  private[this] val roChangeRequestRepo          = ChangeValidationConf.roChangeRequestRepository
  private[this] val workflowService              = RudderConfig.workflowLevelService.getWorkflowService()
  private[this] val eventlogDetailsService       = RudderConfig.eventLogDetailsService
  private[this] val commitAndDeployChangeRequest = RudderConfig.commitAndDeployChangeRequest

  private[this] def checkAccess(cr: ChangeRequest) = {
    val check = CurrentUser.checkRights(AuthorizationType.Validator.Read) || CurrentUser.checkRights(
      AuthorizationType.Deployer.Read
    ) || cr.owner == CurrentUser.actor.name
    ChangeValidationLogger.trace(s"check user '${CurrentUser.actor.name}' access to change request '${cr.id}': ${check}")
    check
  }
  private[this] val CrId: Box[Int] = { S.param("crId").map(x => x.toInt) }
  private[this] var changeRequest: Box[ChangeRequest] = {
    CrId match {
      case Full(id) =>
        roChangeRequestRepo.get(ChangeRequestId(id)).toBox match {
          case Full(Some(cr)) =>
            if (checkAccess(cr))
              Full(cr)
            else Failure("You are not allowed to see this change request")
          case Full(None)     => Failure(s"There is no Cr with id :${id}")
          case eb: EmptyBox =>
            val fail = eb ?~ "no id selected"
            Failure(s"Error in the cr id asked: ${fail.msg}")
        }
      case eb: EmptyBox =>
        val fail = eb ?~ "no id selected"
        Failure(s"Error in the cr id asked: ${fail.msg}")
    }
  }
  private[this] def step = changeRequest.flatMap(cr => workflowService.findStep(cr.id).toBox)

  implicit private val qc: QueryContext = CurrentUser.queryContext // bug https://issues.rudder.io/issues/26605

  def dispatch = {
    // Display Change request Header
    case "header"  =>
      (xml => {
        changeRequest match {
          case eb: EmptyBox => NodeSeq.Empty
          case Full(cr) => displayHeader(cr)
        }
      })

    // Display change request details
    case "details" =>
      (xml => {
        changeRequest match {
          case eb: EmptyBox =>
            val error = eb ?~ "Error"
            <div style="padding :40px;text-align:center">
              <h2>{error.msg}</h2>
              <h3>You will be redirected to the change requests page</h3>
            </div> ++
            Script(
              JsRaw(
                s"""setTimeout("location.href = '${S.contextPath}/secure/configurationManager/changes/changeRequests';",5000);"""
              ) // JsRaw ok, const
            )
          case Full(cr) =>
            new ChangeRequestEditForm(
              cr.info,
              cr.owner,
              step,
              cr.id,
              changeDetailsCallback(cr)(_)
            ).display
        }
      })

    // Display change request content
    case "changes" =>
      (xml => {
        changeRequest match {
          case eb: EmptyBox => NodeSeq.Empty
          case Full(id) =>
            val form = new ChangeRequestChangesForm(id).dispatch("changes")(xml)
            <div id="changeRequestChanges">{form}</div>
        }
      })

    case "warnUnmergeable" => (
      _ => {
        changeRequest match {
          case eb: EmptyBox => NodeSeq.Empty
          case Full(cr) => displayWarnUnmergeable(cr)
        }
      }
    )
  }

  def displayActionButton(cr: ChangeRequest, step: WorkflowNodeId)(implicit qc: QueryContext): NodeSeq = {
    val authz   = Nil // we are sideStepping it, see: https://issues.rudder.io/issues/22595
    val isOwner = cr.owner == CurrentUser.actor.name

    ("#backStep" #> {
      workflowService.findBackSteps(authz, step, isOwner) match {
        case Nil   =>
          ChangeValidationLogger.trace(
            s"- no back step found for user '${CurrentUser.actor.name}' for CR #${cr.id.value} for step '${step}' (user is owner: ${isOwner})"
          )
          NodeSeq.Empty
        case steps =>
          ChangeValidationLogger.trace(
            s"- back steps '${steps.map(_._1.value).mkString(", ")}' found for user '${CurrentUser.actor.name}' for CR #${cr.id.value} for step '${step}' (user is owner: ${isOwner})"
          )
          SHtml.ajaxButton(
            "Decline",
            () => ChangeStepPopup("Decline", steps, cr),
            ("class", "btn btn-danger")
          )
      }
    } &
    "#nextStep" #> {
      workflowService.findNextSteps(authz, step, isOwner) match {
        case NoWorkflowAction                                             =>
          ChangeValidationLogger.trace(
            s"- no next step found for user '${CurrentUser.actor.name}' for CR #${cr.id.value} for step '${step}' (user is owner: ${isOwner})"
          )
          NodeSeq.Empty
        case WorkflowAction(actionName, emptyList) if emptyList.size == 0 =>
          ChangeValidationLogger.trace(
            s"- no next step found for user '${CurrentUser.actor.name}' for CR #${cr.id.value} for step '${step}' (user is owner: ${isOwner})"
          )
          NodeSeq.Empty
        case WorkflowAction(actionName, steps)                            =>
          ChangeValidationLogger.trace(
            s"- next steps '${steps.map(_._1.value).mkString(", ")}' found for user '${CurrentUser.actor.name}' for CR #${cr.id.value} for step '${step}' (user is owner: ${isOwner})"
          )
          SHtml.ajaxButton(
            actionName,
            () => ChangeStepPopup(actionName, steps, cr),
            ("class", "btn btn-success")
          )
      }
    })(actionButtons)
  }

  private[this] def changeDetailsCallback(cr: ChangeRequest)(statusUpdate: ChangeRequestInfo)(implicit qc: QueryContext) = {
    workflowService match {
      case ws: TwoValidationStepsWorkflowServiceImpl =>
        val newCR = ws.updateChangeRequestInfo(cr, statusUpdate, CurrentUser.actor, None).toBox
        changeRequest = newCR
        SetHtml("changeRequestHeader", displayHeader(newCR.openOr(cr))) &
        SetHtml("changeRequestChanges", new ChangeRequestChangesForm(newCR.openOr(cr)).dispatch("changes")(NodeSeq.Empty))

      case _ =>
        // not sure about what we want to do if there an other workflows.
        Alert("Current workflow kind does not support that option. Perhaps the workflows plugin is not enable?")
    }
  }

  def displayHeader(cr: ChangeRequest)(implicit qc: QueryContext) = {
    val (findStep, last) = (for {
      // last action on the change Request (name/description changed):
      lastCRLog      <- changeRequestEventLogService.getLastLog(cr.id).either
      (crAct, crDate) = lastCRLog match {
                          case Left(err)      => (s"Error when retrieving the last change request action : ${err.fullMsg}", None)
                          case Right(None)    => (s"Error: no action was recorded for change request with id ${cr.id.value}", None)
                          case Right(Some(e)) =>
                            val actionName = e match {
                              case _: ModifyChangeRequest => "Modified"
                              case _: AddChangeRequest    => "Created"
                              case _: DeleteChangeRequest => "Deleted"
                            }
                            (
                              s"${actionName} on ${DateFormaterService.getDisplayDate(e.creationDate)} by ${e.principal.name}",
                              Some(e.creationDate)
                            )
                        }

      // Last workflow change on that change Request
      lastWFLog      <- workFlowEventLogService.getLastLog(cr.id).either
      (wfAct, wfDate) = lastWFLog match {
                          case Left(err)      => (s"Error when retrieving the last workflow change : ${err.fullMsg}", None)
                          case Right(None)    => (s"Error: no action was recorded for change request with id ${cr.id.value}", None)
                          case Right(Some(e)) =>
                            val changeStep = eventlogDetailsService
                              .getWorkflotStepChange(e.details)
                              .map(step => s"State changed from ${step.from} to ${step.to}")
                              .getOrElse("Step changed")

                            (
                              s"${changeStep} on ${DateFormaterService.getDisplayDate(e.creationDate)} by ${e.principal.name}",
                              Some(e.creationDate)
                            )
                        }

      last      = (crDate, wfDate) match {
                    case (Some(crd), Some(wfd)) => if (crd.isAfter(wfd)) crAct else wfAct
                    case (None, Some(_))        => wfAct
                    case (_, None)              => crAct
                  }
      findStep <- workflowService
                    .findStep(cr.id)
                    .either
    } yield {
      (findStep, last)
    }).runNow

    val (crStatus, actionBtns) = findStep match {
      case Left(err)   =>
        (<div class="error">Cannot find the status of this change request</div>, NodeSeq.Empty)
      case Right(step) =>
        (Text(step.value), displayActionButton(cr, step))
    }

    ("#backButton [href]" #> "/secure/configurationManager/changes/changeRequests" &
    "#nameTitle *" #> s"CR #${cr.id}: ${cr.info.name}" &
    "#CRStatus *" #> crStatus &
    "#CRLastAction *" #> s"${last}" &
    "#actionBtns *" #> actionBtns)(header)

  }

  def displayWarnUnmergeable(cr: ChangeRequest)(implicit qc: QueryContext): NodeSeq = {
    step.map { wfId =>
      if (!workflowService.isPending(wfId) || commitAndDeployChangeRequest.isMergeable(cr)) {
        NodeSeq.Empty
      } else {
        unmergeableWarning
      }
    }.openOr(NodeSeq.Empty)
  }

  def ChangeStepPopup(
      action:    String,
      nextSteps: Seq[(WorkflowNodeId, (ChangeRequestId, EventActor, Option[String]) => IOResult[WorkflowNodeId])],
      cr:        ChangeRequest
  )(implicit qc: QueryContext) = {
    type stepChangeFunction = (ChangeRequestId, EventActor, Option[String]) => IOResult[WorkflowNodeId]

    def closePopup: JsCmd = {
      SetHtml("changeRequestHeader", displayHeader(cr)) &
      SetHtml(
        "CRStatusDetails",
        workflowService
          .findStep(cr.id)
          .map(x => Text(x.value))
          .orElseSucceed(<div class="error">Cannot find the status of this change request</div>)
          .runNow
      ) &
      SetHtml("changeRequestChanges", new ChangeRequestChangesForm(cr).dispatch("changes")(NodeSeq.Empty)) &
      JsRaw("""hideBsModal('popupContent');""") // JsRaw ok, const
    }

    var nextChosen                                                = nextSteps.head
    def nextSelect(default: (WorkflowNodeId, stepChangeFunction)) = {
      SHtml.selectObj(
        nextSteps.map(v => (v, v._1.value)),
        Full(nextChosen),
        (t: (WorkflowNodeId, stepChangeFunction)) => nextChosen = t
      ) % ("class" -> "form-select mb-3")
    }

    def buildReasonField(mandatory: Boolean, containerClass: String) = {
      new WBTextAreaField("Change audit message", "") {
        override def setFilter  = notNull _ :: trim _ :: Nil
        override def inputField =
          super.inputField % ("style" -> "height:8em;") % ("placeholder" -> { userPropertyService.reasonsFieldExplanation })
        override def validations = {
          if (mandatory) {
            valMinLen(5, "The reason must have at least 5 characters.") _ :: Nil
          } else {
            Nil
          }
        }
      }
    }

    val changeMessage = {
      import com.normation.rudder.config.ReasonBehavior.*
      userPropertyService.reasonsFieldBehavior match {
        case Disabled  => None
        case Mandatory => Some(buildReasonField(true, "subContainerReasonField"))
        case Optional  => Some(buildReasonField(false, "subContainerReasonField"))
        // for non-exhaustiveness God - yes, enum were not very well designed before scala 3
        case _         => throw new IllegalArgumentException(s"This case should not happen, please report to developers")
      }
    }

    def changeMessageDisplay = {
      changeMessage.map { f =>
        <div>
          <h4 class="col-lg-12 col-sm-12 col-xs-12 audit-title">Change Audit Log</h4>
          {f.toForm_!}
        </div>
      }
    }

    def next(default: (WorkflowNodeId, stepChangeFunction)) = {
      nextSteps match {
        case Nil              => <span class="well" id="CRStatus">Error</span>
        case (head, _) :: Nil => <span class="well" id="CRStatus"> {head.value} </span>
        case _                => nextSelect(default)
      }
    }

    val formTracker = new FormTracker(changeMessage.toList)

    def updateAndDisplayNotifications(): NodeSeq = {
      val notifications = formTracker.formErrors
      formTracker.cleanErrors
      if (notifications.isEmpty)
        NodeSeq.Empty
      else
        <div id="notifications" class="notify"><ul>{notifications.map(n => <li>{n}</li>)}</ul></div>
    }

    val introMessage                                           = {
      <h5 class="text-center">
        {
        nextSteps match {
          case Nil              => "You can't confirm"
          case (next, _) :: Nil => s"The change request will be sent to the '${next}' status"
          case list             => s"Please, choose the next state for this Change request"
        }
      }
     </h5>
    }
    def content(default: (WorkflowNodeId, stepChangeFunction)) = {
      val classForButton = action match {
        case "Decline" => "btn-danger"
        case _         => "btn-success"
      }
      ("#header" #> s"${action} CR #${cr.id.value}: ${cr.info.name}" &
      "#form -*" #>
      SHtml.ajaxForm(
        ("#reason" #> changeMessageDisplay &
        "#next" #> next(default) &
        "#cancel" #> SHtml.ajaxButton("Cancel", () => closePopup) &
        "#confirm" #> SHtml.ajaxSubmit(s"${action}", () => confirm(), ("class", classForButton)) &
        "#intro *+" #> introMessage andThen
        "#formError *" #> updateAndDisplayNotifications())(popupContent)
      ))(popup)
    }

    def updateForm(default: (WorkflowNodeId, stepChangeFunction)) = SetHtml("popupContent", content(default))

    def error(msg: String) = <div class="alert alert-danger">{msg}</div>

    def confirm(): JsCmd = {
      val user = CurrentUser.actor

      if (formTracker.hasErrors) {
        formTracker.addFormError(error("There was problem with your request"))
        updateForm(nextChosen)
      } else {
        val (_, evalNextStep) = nextChosen
        evalNextStep(cr.id, user, changeMessage.map(_.get))
          .chainError("could not change Change request step")
          .either
          .runNow match {
          case Left(err)   =>
            formTracker.addFormError(error(err.fullMsg))
            logger.error(s"Error when saving change request '${cr.id.value}': ${err.fullMsg}")
            updateForm(nextChosen)
          case Right(next) =>
            SetHtml("workflowActionButtons", displayActionButton(cr, next)) &
            SetHtml("newStatus", Text(next.value)) &
            closePopup & JsRaw(""" initBsModal("successWorkflow"); """) // JsRaw ok, const
        }
      }
    }

    SetHtml("popupContent", content(nextChosen)) &
    JsRaw("initBsModal('popupContent')")

  }
}
