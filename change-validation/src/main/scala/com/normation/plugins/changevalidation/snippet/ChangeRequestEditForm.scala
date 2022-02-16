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
import com.normation.rudder.ActionType
import com.normation.rudder.domain.workflows._
import com.normation.rudder.web.ChooseTemplate
import com.normation.rudder.web.model._
import com.normation.rudder.web.services.CurrentUser

import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js._
import net.liftweb.util.Helpers._

import scala.xml._

object ChangeRequestEditForm {
  def form = ChooseTemplate(
      "toserve" :: "changevalidation" :: "ComponentChangeRequest" :: Nil
    , "component-details"
  )
}

class ChangeRequestEditForm (
    var info        : ChangeRequestInfo
  , creator         : String
  , step            : Box[WorkflowNodeId]
  , crId            : ChangeRequestId
  , SuccessCallback : ChangeRequestInfo => JsCmd
) extends DispatchSnippet with Loggable {

  import ChangeRequestEditForm._

  private[this] val containerId = "changeRequestDetails"
  private[this] val workflowService = RudderConfig.workflowLevelService.getWorkflowService()

  def dispatch = { case "details" => { _ => display } }

  private[this] val changeRequestName =new WBTextField("Change request title", info.name) {
    override def setFilter = notNull _ :: trim _ :: Nil
    override def className = "form-control"
    override def labelClassName = "col-xs-12"
    override def subContainerClassName = "col-xs-12"
    override def validations =
      valMinLen(1, "Name must not be empty") _ :: Nil
  }

  private[this] val changeRequestDescription= new WBTextAreaField("Description", info.description) {
    override def className = "form-control"
    override def labelClassName = "col-xs-12"
    override def subContainerClassName = "col-xs-12"
    override def setFilter = notNull _ :: trim _ :: Nil
    override val maxLen = 255
    override def validations = Nil
  }

  private[this] val formTracker = new FormTracker( changeRequestName )
  private[this] def onNothingToDo : JsCmd = {
    formTracker.addFormError(error("There are no modifications to save."))
    updateFomClientSide
  }
  private[this] val isEditable = {
    val authz = CurrentUser.getRights.authorizationTypes.toSeq.collect{case right:ActionType.Edit => right.authzKind}
    val isOwner = creator == CurrentUser.actor.name
    step.map(workflowService.isEditable(authz,_,isOwner))
    }.openOr(false)

  private[this] def actionButton = {
    if (isEditable)
      SHtml.ajaxSubmit("Update", () =>  submit, ("class","btn btn-default"))
    else
      NodeSeq.Empty
  }
  private[this] def updateAndDisplayNotifications() : NodeSeq = {
      val notifications = formTracker.formErrors
      formTracker.cleanErrors
      if(notifications.isEmpty)
        NodeSeq.Empty
      else
        <div id="notifications" class="alert alert-danger"><ul>{notifications.map( n => <li>{n}</li>) }</ul></div>
    }
  private[this]  def error(msg:String) = <span>{msg}</span>
  private[this] def crName = {
    if (isEditable) changeRequestName.toForm_! else changeRequestName.readOnlyValue
  }

  private[this] def CRDescription = {
    if (isEditable) changeRequestDescription.toForm_! else changeRequestDescription.readOnlyValue
  }

  def updateFomClientSide = SetHtml(containerId,display)

  def display: NodeSeq =
    ( "#detailsForm *" #> { (n:NodeSeq) => SHtml.ajaxForm(n) } andThen
      "#formError *" #> updateAndDisplayNotifications() &
      "#CRName *" #> crName &
      "#CRId *"   #> crId.value &
      "#CRStatusDetails *"   #>  step.map(wfId => Text(wfId.toString)).openOr(<div class="error">Cannot find the status of this change request</div>) &
      "#CRDescription *" #> CRDescription &
      "#CRSave *" #> actionButton
    ) (form)

  def submit = {
    if (formTracker.hasErrors) {
      formTracker.addFormError(error("There was problem with your request"))
      updateFomClientSide
    }
    else {
      val newInfo = ChangeRequestInfo(changeRequestName.get,changeRequestDescription.get)
      if (info == newInfo)
        onNothingToDo
      else {
        info = newInfo
        SuccessCallback(info) & SetHtml("changeRequestDetails",display)
      }
    }
  }
}

