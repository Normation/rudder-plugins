/*
*************************************************************************************
* Copyright 2014 Normation SAS
*************************************************************************************
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU Affero GPL v3, the copyright holders add the following
* Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
* licence, when you create a Related Module, this Related Module is
* not considered as a part of the work and may be distributed under the
* license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
*
*************************************************************************************
*/

package com.normation.plugins.nodeexternalreport.service

import java.io.FileInputStream

import com.normation.rudder.web.rest.RestUtils._

import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.http.StreamingResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers.{tryo, urlDecode}

/**
 * Class in charge of generating the JSON from the
 * iTop compliance status and serving it to the API URL.
 */
class NodeExternalReportApi(
    readReport   : ReadExternalReports
) extends RestHelper with Loggable {

  import net.liftweb.json.JsonDSL._

  val requestDispatch : PartialFunction[Req, () => Box[LiftResponse]] = {

    case Get(reportType :: fileName :: "raw" :: Nil, req) => {
      //capture values
      val tpe =  urlDecode(reportType)
      val name = urlDecode(fileName)

      () =>
      for {
        (file, contentType) <- readReport.getFileContent(tpe, name)
        stream              <- tryo(new FileInputStream(file))
        if null ne stream
      } yield {
        StreamingResponse(
            stream
          , () => stream.close
          , stream.available
          , List("Content-Type" -> contentType)
          , Nil
          , 200
        )
      }
    }
  }

  serve( "secure" / "nodeManager" / "externalInformation" prefix requestDispatch)

}
