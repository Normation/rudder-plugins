/*
 *************************************************************************************
 * Copyright 2014 Normation SAS
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

package com.normation.plugins.nodeexternalreports.service

import com.normation.box.*
import com.normation.inventory.domain.NodeId
import com.normation.rudder.AuthorizationType
import com.normation.rudder.facts.nodes.QueryContext
import com.normation.rudder.users.CurrentUser
import java.io.FileInputStream
import net.liftweb.common.*
import net.liftweb.http.ForbiddenResponse
import net.liftweb.http.LiftResponse
import net.liftweb.http.NotFoundResponse
import net.liftweb.http.Req
import net.liftweb.http.StreamingResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Helpers.urlDecode

/**
 * Class in charge of serving external report files for a node.
 *
 * The endpoint is scoped to a `NodeId` and a report type; it requires `Node.Read` and resolves the
 * file server-side under the caller's `QueryContext` (so node/tenant ACLs are enforced). It never
 * accepts a client-supplied file name.
 */
class NodeExternalReportApi(
    readReport: ReadExternalReports
) extends RestHelper with Loggable {

  val requestDispatch: PartialFunction[Req, () => Box[LiftResponse]] = {

    case Get(nodeId :: reportType :: "raw" :: Nil, req) => {
      // capture values
      val id  = urlDecode(nodeId)
      val tpe = urlDecode(reportType)

      () => {
        // fail closed: any authenticated user without Node.Read (incl. no authenticated context) is denied
        if (!CurrentUser.checkRights(AuthorizationType.Node.Read)) {
          Full(ForbiddenResponse("You don't have sufficient rights to access node external reports"))
        } else {
          implicit val qc: QueryContext = CurrentUser.queryContext
          readReport.getReportFile(NodeId(id), tpe).toBox match {
            case Full(Some((file, contentType))) =>
              tryo(new FileInputStream(file)).map { stream =>
                StreamingResponse(
                  stream,
                  () => stream.close,
                  stream.available.toLong,
                  List("Content-Type" -> contentType),
                  Nil,
                  200
                )
              }
            case Full(None)                      =>
              Full(NotFoundResponse(s"No external report of type '${tpe}' is available for node '${id}'"))
            case eb: EmptyBox =>
              val e = eb ?~! s"Error when accessing external report '${tpe}' for node '${id}'"
              logger.warn(e.messageChain)
              Full(ForbiddenResponse("Could not access the requested external report"))
          }
        }
      }
    }
  }

  serve("secure" / "nodeManager" / "externalInformation" prefix requestDispatch)

}
