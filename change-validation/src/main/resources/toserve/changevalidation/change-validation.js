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


/*
 *  Table of changes requests
 *
 *   Javascript object containing all data to create a line in the DataTable
 *   { "name" : Change request name [String]
 *   , "id" : Change request id [String]
 *   , "step" : Change request validation step [String]
 *   , "creator" : Name of the user that has created the change Request [String]
 *   , "lastModification" : date of last modification [ String ]
 *   }
 */
function createChangeRequestTable(gridId, data, contextPath, refresh) {

  var columns = [ {
      "sWidth": "5%"
    , "mDataProp": "id"
    , "sTitle": "#"
    , "fnCreatedCell" : function (nTd, sData, oData, iRow, iCol) {
        $(nTd).empty();
        $(nTd).addClass("link");
        var editLink = $("<a/>");
        editLink.attr("href",contextPath +'/secure/plugins/changes/changeRequest/'+sData);
        editLink.text(sData);
        $(nTd).append(editLink);
      }
  } , {
      "sWidth": "10%"
    , "mDataProp": "step"
     , "sTitle": "Status"
  } , {
      "sWidth": "65%"
    , "mDataProp": "name"
    , "sTitle": "Name"
    , "fnCreatedCell" : function (nTd, sData, oData, iRow, iCol) {
        $(nTd).empty();
        $(nTd).addClass("link");
        var editLink = $("<a/>");
        editLink.attr("href",contextPath +'/secure/plugins/changes/changeRequest/'+oData.id);
        editLink.text(sData);
        $(nTd).append(editLink);
      }
  } , {
      "sWidth": "10%"
    , "mDataProp": "creator"
    , "sTitle": "Creator"
  } , {
      "sWidth": "10%"
    , "mDataProp": "lastModification"
    , "sTitle": "Last Modification"
  } ];

  var params = {
      "bFilter" : true
    , "bPaginate" : true
    , "bLengthChange": true
    , "sPaginationType": "full_numbers"
    , "oLanguage": {
        "sSearch": ""
    }
    , "aaSorting": [[ 0, "asc" ]]
    , "sDom": '<"dataTables_wrapper_top newFilter"f<"dataTables_refresh">>rt<"dataTables_wrapper_bottom"lip>'
  };

  createTable(gridId,data, columns, params, contextPath, refresh, "change_requests");
}
