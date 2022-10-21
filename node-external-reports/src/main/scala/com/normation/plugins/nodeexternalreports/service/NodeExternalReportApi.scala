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

import java.io.FileInputStream
import net.liftweb.common._
import net.liftweb.http.LiftResponse
import net.liftweb.http.Req
import net.liftweb.http.StreamingResponse
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Helpers.urlDecode

/**
 * Class in charge of generating the JSON from the
 * iTop compliance status and serving it to the API URL.
 */
class NodeExternalReportApi(
    readReport: ReadExternalReports
) extends RestHelper with Loggable {

  val requestDispatch: PartialFunction[Req, () => Box[LiftResponse]] = {

    case Get(reportType :: fileName :: "raw" :: Nil, req) => {
      // capture values
      val tpe  = urlDecode(reportType)
      val name = urlDecode(fileName)

      () =>
        for {
          (file, contentType) <- readReport.getFileContent(tpe, name)
          stream              <- tryo(new FileInputStream(file))
          if null ne stream
        } yield {
          StreamingResponse(
            stream,
            () => stream.close,
            stream.available.toLong,
            List("Content-Type" -> contentType),
            Nil,
            200
          )
        }
    }
  }

  serve("secure" / "nodeManager" / "externalInformation" prefix requestDispatch)

}
