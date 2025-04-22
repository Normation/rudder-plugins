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
import com.normation.eventlog.EventLog
import com.normation.rudder.AuthorizationType
import com.normation.rudder.domain.workflows.*
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.users.CurrentUser
import com.normation.rudder.web.services.JsTableData
import com.normation.rudder.web.services.JsTableLine
import com.normation.utils.DateFormaterService
import com.normation.zio.UnsafeRun
import net.liftweb.common.*
import net.liftweb.http.*
import net.liftweb.http.DispatchSnippet
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.SelectableOption
import net.liftweb.http.js.JE.*
import net.liftweb.http.js.JsCmds.*
import net.liftweb.http.js.JsObj
import net.liftweb.util.Helpers.*
import org.apache.commons.text.StringEscapeUtils
import scala.xml.Elem
import scala.xml.NodeSeq
import scala.xml.Text
import zio.UIO
import zio.syntax.*

class ChangeRequestManagement extends DispatchSnippet with Loggable {

  private[this] val roCrRepo                     = ChangeValidationConf.roChangeRequestRepository
  private[this] val workflowService              = RudderConfig.workflowLevelService.getWorkflowService()
  private[this] val changeRequestEventLogService = RudderConfig.changeRequestEventLogService
  private[this] val workflowLoggerService        = RudderConfig.workflowEventLogService
  private[this] val changeRequestTableId         = "changeRequestTable"
  private[this] val currentUser                  =
    CurrentUser.checkRights(AuthorizationType.Validator.Read) || CurrentUser.checkRights(AuthorizationType.Deployer.Read)

  private[this] val initFilter: Box[String] = S.param("filter").map(_.replace("_", " "))

  def dispatch = {
    case "filter"  =>
      xml => ("#actualFilter *" #> statusFilter).apply(xml)
    case "display" =>
      xml => {
        xml ++
        Script(OnLoad(JsRaw(dataTableInit))) // JsRaw ok, escaped
      }
  }

  /*
   *  { "name" : Change request name [String]
   *   , "id" : Change request id [String]
   *   , "step" : Change request validation step [String]
   *   , "creator" : Name of the user that has created the change Request [String]
   *   , "lastModification" : date of last modification [ String ]
   *   }
   */
  case class ChangeRequestLine(
      changeRequest:    ChangeRequest,
      workflowStateMap: Map[ChangeRequestId, WorkflowNodeId],
      eventsMap:        Map[ChangeRequestId, EventLog]
  ) extends JsTableLine {
    val date =
      eventsMap.get(changeRequest.id).map(event => DateFormaterService.serialize(event.creationDate)).getOrElse("Unknown")

    override def json(freshName: () => String): JsObj = toJson

    val toJson = {
      JsObj(
        "id"               -> changeRequest.id.value,
        "name"             -> changeRequest.info.name,
        "creator"          -> changeRequest.owner,
        "step"             -> (workflowStateMap.get(changeRequest.id).map(_.value).getOrElse("Unknown"): String),
        "lastModification" -> date
      )
    }
  }

  def getLines(): UIO[JsTableData[ChangeRequestLine]] = {
    val changeRequests = if (currentUser) roCrRepo.getAll() else roCrRepo.getByContributor(CurrentUser.actor)

    for {
      crs              <- changeRequests
                            .chainError("Could not get change requests")
                            .catchAll(err => {
                              logger.error(err.fullMsg)
                              Vector().succeed
                            })
      workflowStateMap <- workflowService
                            .getAllChangeRequestsStep()
                            .chainError("Could not find change requests state")
                            .catchAll(err => {
                              logger.error(err.fullMsg)
                              Map.empty[ChangeRequestId, WorkflowNodeId].succeed
                            })
      lastEventsMap    <- getLastEventsMap
    } yield {
      JsTableData(crs.map(ChangeRequestLine(_, workflowStateMap, lastEventsMap)).toList)
    }
  }

  def dataTableInit = {
    val refresh = AnonFunc(
      SHtml.ajaxInvoke(() => JsRaw(s"refreshTable('${changeRequestTableId}',${getLines().runNow.toJson.toJsCmd})"))
    ) // JsRaw ok, from json

    val filter = initFilter match {
      case Full(filter) =>
        s"$$('#${changeRequestTableId}').dataTable().fnFilter('${StringEscapeUtils.escapeEcmaScript(filter)}',1,true,false,true);"
      case eb: EmptyBox => s"$$('#${changeRequestTableId}').dataTable().fnFilter('pending',1,true,false,true);"
    }
    s"""
      var refreshCR = ${refresh.toJsCmd};
      createChangeRequestTable('${changeRequestTableId}',[], '${S.contextPath}', refreshCR)
      ${filter};
      refreshCR();
    """

  }

  /**
   * Get all events, merge them via a mutMap (we only want to keep the most recent event)
   */
  private[this] def getLastEventsMap: UIO[Map[ChangeRequestId, EventLog]] = {

    for {
      crEventsMap       <- changeRequestEventLogService.getLastCREvents
                             .chainError("Could not find last Change requests events requests state")
                             .catchAll(err => {
                               logger.error(err.fullMsg)
                               Map.empty[ChangeRequestId, EventLog].succeed
                             })
      workflowEventsMap <- workflowLoggerService
                             .getLastWorkflowEvents()
                             .chainError("Could not find last Change requests events requests state")
                             .catchAll(err => {
                               logger.error(err.fullMsg)
                               Map.empty[ChangeRequestId, EventLog].succeed
                             })
    } yield {
      import scala.collection.mutable.Map as MutMap

      val eventMap = MutMap() ++ crEventsMap

      for {
        (crId, event) <- workflowEventsMap
      } {
        eventMap.get(crId) match {
          case Some(currentEvent) if (currentEvent.creationDate isAfter event.creationDate) =>
          case _                                                                            => eventMap.update(crId, event)
        }
      }

      eventMap.toMap
    }
  }

  def statusFilter = {

    val values       = workflowService.stepsValue.map(_.value)
    val selectValues = values.map(x => (x, x))
    var value        = ""

    val filterFunction = {
      s"""var filter = [];
          var selected = $$(this).find(":selected")
          if (selected.length > 0) {
            selected.each(function () {
              filter.push($$(this).attr("value"));
            } );
            $$('#${changeRequestTableId}').dataTable().fnFilter(filter.join("|"),1,true,false,true);
          }
          else {
            // No filter, display nothing
            $$('#${changeRequestTableId}').dataTable().fnFilter(".",1);
          }"""
    }
    val onChange       = ("onchange" -> JsRaw(filterFunction)) // JsRaw ok, const

    def filterForm(select: Elem, btnClass: String, transform: String => NodeSeq) = {
      val submit = {
        SHtml.a(
          Text(""),
          JsRaw(s"$$('.expand').click();"), // JsRaw ok, const
          ("class", s"btn btn-default btn-expand btn-${btnClass}")
        ) ++
        SHtml.ajaxSubmit(
          "",
          () => SetHtml("actualFilter", transform(value)),
          ("class", "expand visually-hidden")
        )
      }

      SHtml.ajaxForm(
        <label for="select-status">Status:</label> ++
        select % onChange ++ submit
      )
    }
    def unexpandedFilter(default: String): NodeSeq = {
      val multipleValues = ("", "All") :: ("Pending", "Open") :: ("^(?!Pending)", "Closed") :: Nil
      val select: Elem = SHtml.select(
        (multipleValues ::: selectValues).map { case (a, b) => SelectableOption(a, b) },
        Full(default),
        list => value = list,
        ("class", "form-select w-auto mb-3 ms-2 me-2"),
        ("id", "select-status")
      )
      (s"value='${default}' [selected]" #> "selected").apply(("select *" #> {
        <optgroup label="Multiple" style="margin-bottom:10px" value="" >
                  {multipleValues.map { case (value, label) => <option value={value} style="margin-left:10px">{label}</option> }}
                </optgroup> ++
        <optgroup label="Single">
                  {
          selectValues.map { case (value, label) => <option value={value} style="margin-left:10px">{label}</option> }
        }
                </optgroup>
      }).apply(filterForm(select, "more", expandedFilter)))
    }

    def expandedFilter(default: String) = {
      val extendedDefault = {
        default match {
          case ""                                       => values
          case "Pending"                                => values.filter(_.contains("Pending"))
          case "^(?!Pending)"                           => values.filterNot(_.contains("Pending"))
          case default if (values.exists(_ == default)) => List(default)
          case _                                        => Nil
        }
      }

      def computeDefault(selectedValues: List[String]) = selectedValues match {
        case allValues if allValues.size == 4                            => ""
        case value :: Nil                                                => value
        case openValues if openValues.forall(_.contains("Pending"))      => "Pending"
        case closedValues if closedValues.forall(!_.contains("Pending")) => "^(?!Pending)"
        case _                                                           => selectedValues.head
      }

      val multiSelect = SHtml.multiSelect(
        selectValues,
        extendedDefault,
        list => value = computeDefault(list),
        ("class", "form-control")
      )
      filterForm(multiSelect, "less", unexpandedFilter)
    }

    unexpandedFilter(initFilter.getOrElse("Pending"))
  }
}
