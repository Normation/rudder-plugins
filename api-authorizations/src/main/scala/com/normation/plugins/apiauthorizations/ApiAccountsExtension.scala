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

package com.normation.plugins.apiauthorizations

import bootstrap.liftweb.PluginsInfo
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.rest.AllApi
import com.normation.rudder.rest.ApiKind
import com.normation.rudder.web.snippet.administration.ApiAccounts
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers._
import scala.reflect.ClassTag
import scala.xml.NodeSeq
import zio.json._

class ApiAccountsExtension(val status: PluginStatus)(implicit val ttag: ClassTag[ApiAccounts])
    extends PluginExtensionPoint[ApiAccounts] with Loggable {

  def pluginCompose(snippet: ApiAccounts): Map[String, NodeSeq => NodeSeq] = Map(
    "render" -> render _,
    "body"   -> body _
  )

  /*
   * Append to the place where "var apiPath = ..." is defined the var with the lists of all {api name, api id, verb}.
   * The list defined groups of API (the one starting with the same name):
   * [
   *   { "category": "name of category",
   *     "apis"    : [
   *       {
   *         "name": "GetRules"
   *       , "description": "List all Rules"
   *       , "path": "/rules"
   *       , "verb": "GET"
   *       },
   *       ...
   *      ]
   *   },
   *   ...
   * ]
   */
  def render(xml: NodeSeq) = {
    // get all apis and for public one, and create the structure
    import net.liftweb.http.js.JsCmds._
    import net.liftweb.http.js.JE._

    val categories = ((AllApi.api ++ PluginsInfo.pluginApisDef)
      .filter(x => x.kind == ApiKind.Public || x.kind == ApiKind.General)
      .groupBy(_.path.parts.head)
      .map {
        case (cat, apis) =>
          JsonCategory(
            cat.value,
            apis.map(a => JsonApi(a.name, a.description, apiPathToAcl(a.path.value), a.action.name)).sortBy(_.path)
          )
      })
      .toList
      .sortBy(_.category)
    val json       = categories.toJson

    // now, add declaration of a JS variable: var rudderApis = [{ ... }]
    xml ++ Script(JsRaw(s"""var rudderApis = $json;"""))
  }

  // change variables in api path {xxx] into *
  // it could (should?) be done at case class level, but for a first version is
  // does the job.
  def apiPathToAcl(path: String): String = {
    path.replaceAll("""\{.*?\}""", "*")
  }

  def body(xml: NodeSeq): NodeSeq = {
    print("ok")
    ("#acl-app" #>
    <div>
        <head_merge>
          <link rel="stylesheet" type="text/css" href="/toserve/apiauthorizations/media.css" media="screen" data-lift="with-cached-resource" />
          <script type="text/javascript" data-lift="with-cached-resource"  src="/toserve/apiauthorizations/rudder-apiauthorizations.js"></script>
        </head_merge>
        <div id="acl-configuration" ng-if="myNewAccount.authorizationType === 'acl'">
          <!-- load elm app -->
          <div id="apiauthorization-app">
            <div id="apiauthorization-content"/>
          </div>
          <script>
          //<![CDATA[
            var appAcl;
            $(document).ready(function(){
              app.ports.initAcl.subscribe(function(){
                // init elm app
                var node = document.getElementById("apiauthorization-app");
                var main = document.getElementById("apiauthorization-content");

                var initValues = {
                    contextPath : contextPath
                  , token: { id: "account.id", acl: []}
                  , rudderApis: rudderApis
                };

                appAcl = Elm.ApiAuthorizations.init({node: main, flags: initValues});

                //set back selected acl to the API accounts app
                appAcl.ports.giveAcl.subscribe(function(acl) {
                  app.ports.getCheckedAcl.send(acl)
                });
              });
              //get the acl list of the selected account
              app.ports.shareAcl.subscribe(function(acl){
                appAcl.ports.getToken.send(acl)
              });
            });
          // ]]>
          </script>
        </div>
      </div>).apply(xml)
  }

}

/*
 * JSON representation of API (grouped in categories).
 * These class must be top-level, else liftweb-json gets mad and capture of outer()...
 */
final private case class JsonApi(name: String, description: String, path: String, verb: String)
final private case class JsonCategory(category: String, apis: List[JsonApi])

private object JsonApi {
  implicit val encoder: JsonEncoder[JsonApi] = DeriveJsonEncoder.gen[JsonApi]
}

private object JsonCategory {
  implicit val encoder: JsonEncoder[JsonCategory] = DeriveJsonEncoder.gen[JsonCategory]
}
