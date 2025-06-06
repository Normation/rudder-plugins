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
import com.normation.errors.IOResult
import com.normation.rudder.ActionType
import com.normation.rudder.domain.workflows.*
import com.normation.rudder.users.CurrentUser
import com.normation.rudder.web.ChooseTemplate
import com.normation.rudder.web.model.*
import com.normation.zio.UnsafeRun
import net.liftweb.common.Loggable
import net.liftweb.http.*
import net.liftweb.http.js.*
import net.liftweb.http.js.JsCmds.*
import net.liftweb.util.FieldError
import net.liftweb.util.Helpers.*
import scala.xml.*

object ChangeRequestEditForm {
  def form = ChooseTemplate(
    "toserve" :: "changevalidation" :: "ComponentChangeRequest" :: Nil,
    "component-details"
  )
}

class ChangeRequestEditForm(
    var info:        ChangeRequestInfo,
    creator:         String,
    step:            IOResult[WorkflowNodeId],
    crId:            ChangeRequestId,
    SuccessCallback: ChangeRequestInfo => JsCmd
) extends DispatchSnippet with Loggable {

  import ChangeRequestEditForm.*

  private[this] val containerId     = "changeRequestDetails"
  private[this] val workflowService = RudderConfig.workflowLevelService.getWorkflowService()

  def dispatch = { case "details" => { _ => display } }

  private[this] val changeRequestName = new WBTextField("Change request title", info.name) {
    override def setFilter             = notNull _ :: trim _ :: Nil
    override def className             = "form-control"
    override def labelClassName        = "col-xs-12"
    override def subContainerClassName = "col-xs-12"
    override def validations           =
      valMinLen(1, "Name must not be empty") _ :: Nil
  }

  private[this] val changeRequestDescription = new WBTextAreaField("Description", info.description) {
    override def className             = "form-control"
    override def labelClassName        = "col-xs-12"
    override def subContainerClassName = "col-xs-12"
    override def setFilter             = notNull _ :: trim _ :: Nil
    override val maxLen                = 255
    override def validations: List[String => List[FieldError]] = Nil
  }

  private[this] val formTracker: FormTracker = new FormTracker(changeRequestName)
  def onNothingToDo:             JsCmd       = {
    formTracker.addFormError(error("There are no modifications to save."))
    updateFromClientSide
  }
  private[this] val isEditable = {
    val authz   = CurrentUser.getRights.authorizationTypes.toSeq.collect { case right: ActionType.Edit => right.authzKind }
    val isOwner = creator == CurrentUser.actor.name
    step.map(workflowService.isEditable(authz, _, isOwner))
  }.orElseSucceed(false).runNow

  private[this] def actionButton = {
    if (isEditable)
      SHtml.ajaxSubmit("Update", () => submit, ("class", "btn btn-default"))
    else
      NodeSeq.Empty
  }
  private[this] def updateAndDisplayNotifications(): NodeSeq = {
    val notifications = formTracker.formErrors
    formTracker.cleanErrors
    if (notifications.isEmpty)
      NodeSeq.Empty
    else
      <div id="notifications" class="alert alert-danger"><ul>{notifications.map(n => <li>{n}</li>)}</ul></div>
  }
  private[this] def error(msg: String) = <span>{msg}</span>
  private[this] def crName = {
    if (isEditable) changeRequestName.toForm_! else changeRequestName.readOnlyValue
  }

  private[this] def CRDescription = {
    if (isEditable) changeRequestDescription.toForm_! else changeRequestDescription.readOnlyValue
  }

  def updateFromClientSide = SetHtml(containerId, display)

  def display: NodeSeq = {
    ("#detailsForm *" #> { (n: NodeSeq) => SHtml.ajaxForm(n) } andThen
    "#formError *" #> updateAndDisplayNotifications() &
    "#CRName *" #> crName &
    "#CRId *" #> crId.value &
    "#CRStatusDetails *" #> step
      .map(wfId => Text(wfId.toString))
      .orElseSucceed(<div class="error">Cannot find the status of this change request</div>)
      .runNow &
    "#CRDescription *" #> CRDescription &
    "#CRSave *" #> actionButton)(form)
  }

  def submit = {
    if (formTracker.hasErrors) {
      formTracker.addFormError(error("There was problem with your request"))
      updateFromClientSide
    } else {
      val newInfo = ChangeRequestInfo(changeRequestName.get, changeRequestDescription.get)
      if (info == newInfo) {
        onNothingToDo
      } else {
        info = newInfo
        SuccessCallback(info) & SetHtml("changeRequestDetails", display)
      }
    }
  }
}
