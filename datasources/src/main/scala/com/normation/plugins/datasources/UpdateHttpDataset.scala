/*
*************************************************************************************
* Copyright 2016 Normation SAS
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

package com.normation.plugins.datasources

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.normation.inventory.domain.NodeId
import com.normation.rudder.domain.nodes.NodeInfo
import com.normation.rudder.domain.nodes.NodeProperty
import com.normation.rudder.domain.policies.GlobalPolicyMode
import com.normation.rudder.services.policies.InterpolatedValueCompiler
import net.minidev.json.JSONArray
import net.minidev.json.JSONAware
import net.minidev.json.JSONValue

import scala.util.control.NonFatal
import scalaj.http.Http
import scalaj.http.HttpOptions
import zio._
import zio.syntax._
import com.normation.errors._
import com.normation.rudder.domain.nodes.GenericProperty
import com.normation.rudder.domain.nodes.GenericProperty._
import com.normation.rudder.domain.parameters.GlobalParameter
import com.normation.rudder.services.policies.ParamInterpolationContext
import com.typesafe.config.ConfigValue
import zio.duration._
import com.softwaremill.quicklens._

/*
 * This file contain the logic to update dataset from an
 * HTTP Datasource.
 * More specifically, it allows to:
 * - query a node endpoint using node specific properties,
 * - select a sub-json,
 * - save it in the node properties.
 */

/*
 * The whole service:
 * - from a datasource and node context,
 * - get the node datasource URL,
 * - query it,
 * - parse the json result,
 * - return a rudder property with the content.
 */
class GetDataset(valueCompiler: InterpolatedValueCompiler) {

  val compiler = new InterpolateNode(valueCompiler)


  /**
   * Get the node property for the configured datasource.
   * Return an Option[NodeProperty], where None mean "don't change
   * existing state", and Some(nodeProperty) means "change existing
   * state for node property name to node property value".
   */
  def getNode(
      datasourceName   : DataSourceId
    , datasource       : DataSourceType.HTTP
    , node             : NodeInfo
    , policyServer     : NodeInfo
    , globalPolicyMode : GlobalPolicyMode
    , parameters       : Set[GlobalParameter]
    , connectionTimeout: Duration
    , readTimeOut      : Duration
  ) : IOResult[Option[NodeProperty]] = {
    //utility to expand both key and values of a map
    def expandMap(expand: String => PureResult[String], map: Map[String, String]): IOResult[Map[String, String]] = {
      (ZIO.foreach(map.toList) { case (key, value) =>
        (for {
          newKey   <- expand(key)
          newValue <- expand(value)
        } yield {
          (newKey, newValue)
        }).toIO
      }).map( _.toMap )
    }

    //actual logic

    for {
      parameters <- ZIO.foreach(parameters)(compiler.compileParameters(_).toIO).chainError("Error when transforming Rudder Parameter for variable interpolation")
      expand     =  compiler.compileInput(node, policyServer, globalPolicyMode, parameters.toMap) _
      url        <- expand(datasource.url).chainError(s"Error when trying to parse URL ${datasource.url}").toIO
      path       <- expand(datasource.path).chainError(s"Error when trying to compile JSON path ${datasource.path}").toIO
      headers    <- expandMap(expand, datasource.headers)
      httpParams <- expandMap(expand, datasource.params)
      time_0     <- UIO.effectTotal(System.currentTimeMillis)
      body       <- QueryHttp.QUERY(datasource.httpMethod, url, headers, httpParams, datasource.sslCheck, connectionTimeout, readTimeOut).chainError(s"Error when fetching data from ${url}")
      _          <- DataSourceLoggerPure.Timing.trace(s"[${System.currentTimeMillis - time_0} ms] node '${node.id.value}': GET ${headers.map{case(k,v) => s"$k=$v"}.mkString("[","|","]")} ${url} // ${path}")
      optJson    <- body match {
                      case Some(body) => JsonSelect.fromPath(path, body).map(x => Some(x)).chainError(s"Error when extracting sub-json at path ${path} from ${body}")
                      // this mean we got a 404 => choose behavior based on onMissing value
                      case None => datasource.missingNodeBehavior match {
                        case MissingNodeBehavior.Delete              => Some(Nil).succeed
                        case MissingNodeBehavior.DefaultValue(value) => Some(value :: Nil).succeed
                        case MissingNodeBehavior.NoChange            => None.succeed
                      }
                    }
    } yield {
      // optJson is an Option[value :: tails] (None meaning: don't change the existing value, that case is processed elsewhere).
      // We only get the first element from the path, ignoring if there is several.
      // And if list is empty, returns "" (remove property).
      optJson.map {
        case Nil        => DataSource.nodeProperty(datasourceName.value, "".toConfigValue)
        case value :: _ => DataSource.nodeProperty(datasourceName.value, value)
      }
    }
  }

  /**
   * Get information for many nodes.
   * Policy servers for each node must be in the map.
   */
  def getMany(datasource: DataSource, nodes: Seq[NodeId], policyServers: Map[NodeId, NodeInfo], parameters: Set[GlobalParameter]): Seq[IOResult[NodeProperty]] = {
    ???
  }

}

/*
 * Timeout are given in Milleseconds
 */
object QueryHttp {

  /*
   * Simple synchronous http get/post, return the response
   * body as a string.
   */
  def QUERY(method: HttpMethod, url: String, headers: Map[String, String], params: Map[String, String], checkSsl: Boolean, connectionTimeout: Duration, readTimeOut: Duration): IOResult[Option[String]] = {
    val options = (
        HttpOptions.connTimeout(connectionTimeout.toMillis.toInt)
     :: HttpOptions.readTimeout(readTimeOut.toMillis.toInt)
     :: (if(checkSsl) {
          Nil
        } else {
          HttpOptions.allowUnsafeSSL :: Nil
        })
    )

    val client = {
      val c = Http(url).headers(headers).options(options).params(params)
      method match {
        case HttpMethod.GET  => c
        case HttpMethod.POST => c.postForm
      }
    }

    for {
      response <- IOResult.effect(client.asString)
      result   <- if(response.isSuccess) {
                    Some(response.body).succeed
                  } else {
                    // If we have a 404 response, we need to remove the property from datasource by setting an empty string here
                    if (response.code == 404) {
                      None.succeed
                    } else {
                      Unexpected(s"Failure updating datasource with URL '${url}': code ${response.code}: ${response.body}").fail
                    }
                  }
    } yield {
      result
    }
  }
}

/**
 * A little service that allows the interpolation of a
 * string with node properties given all the relevant context:
 * - the node and its policy server infos,
 * - rudder global parameters
 */
class InterpolateNode(compiler: InterpolatedValueCompiler) {

  def compileParameters(parameter: GlobalParameter): PureResult[(String, ParamInterpolationContext => PureResult[String])] = {
    compiler.compileParam(serializeToHocon(parameter.value)).map(v => (parameter.name, v))
  }

  def compileInput(node: NodeInfo, policyServer: NodeInfo, globalPolicyMode: GlobalPolicyMode,  parameters: Map[String, ParamInterpolationContext => PureResult[String]])(input: String): PureResult[String] = {

    // we inject some props that are usefull as identity pivot (like short name)
    // accessible with for ex: ${node.properties[datasources-injected][short-hostname]}
    val injectedPropsJson = s"""{"short-hostname": "${node.hostname.split("\\.")(0)}"}"""

    for {
      compiled <- compiler.compileParam(input)
      injected <- GenericProperty.parseValue(injectedPropsJson)
      //build interpolation context from node:
      enhanced =  node.modify(_.node.properties).using(props =>  NodeProperty("datasources-injected", injected, None) :: props )
      context  =  ParamInterpolationContext(enhanced, policyServer, globalPolicyMode, parameters, 5)
      bounded  <- compiled(context)
    } yield {
      bounded
    }
  }
}

/**
 * Service that allows to find sub-part of JSON matching a JSON
 * path as defined in: http://goessner.net/articles/JsonPath/
 */
object JsonSelect {

  /*
   * Configuration for json path:
   * - always return list,
   * - We don't want "SUPPRESS_EXCEPTIONS" because null are returned
   *   in place => better to IOResult it.
   * - We don't want ALWAYS_RETURN_LIST, because it blindly add an array
   *   around the value, even if the value is already an array.
   */
  val config = Configuration.builder.build()

  /*
   * Return the selection corresponding to path from the string.
   * Fails on bad json or bad path.
   *
   * Always return a list with normalized outputs regarding
   * arrays and string quotation, see JsonPathTest for details.
   *
   * The list may be empty if 0 node matches the results.
   */
  def fromPath(path: String, json: String): IOResult[List[ConfigValue]] = {
    for {
      p <- compilePath(path)
      j <- parse(json)
      r <- select(p, j)
      h <- r.accumulatePure(GenericProperty.parseValue(_)).toIO
    } yield {
      h
    }
  }

  ///                                                       ///
  /// implementation logic - protected visibility for tests ///
  ///                                                       ///

  protected[datasources] def parse(json: String): IOResult[DocumentContext] = {
    IOResult.effect(JsonPath.using(config).parse(json))
  }

  /*
   * Some remarks:
   * - just a name "foo" is interpreted as "$.foo"
   * - If path is empty, replace it by "$" or the path compilation fails,
   *   an empty path means accepting the whole json
   */
  protected[datasources] def compilePath(path: String): IOResult[JsonPath] = {
    val effectivePath = if (path.isEmpty()) "$" else path
    IOResult.effect(JsonPath.compile(effectivePath))
  }

  /*
   * not exposed to user due to risk to not use the correct config
   */
  protected[datasources] def select(path: JsonPath, json: DocumentContext): IOResult[List[String]] = {

    // so, this lib seems to be a whole can of unconsistancies on String quoting.
    // we would like to NEVER have quoted string if they are not in a JSON object
    // but to have them quoted in json object.
    def toJsonString(a: Any): String = {
      a match {
        case s: String => s
        case x         => JSONValue.toJSONString(x)
      }
    }

    // I didn't find any other way to do that:
    // - trying to parse as Any, then find back the correct JSON type
    //   lead to a mess of quoted strings
    // - just parsing as JSONAware fails on string, int, etc.

    import scala.jdk.CollectionConverters._

    for {
      jsonValue <- IOResult.effectM(try {
                     json.read[JSONAware](path).succeed
                   } catch {
                     case _: ClassCastException =>
                       try {
                         json.read[Any](path).toString.succeed
                       } catch {
                         case NonFatal(ex) => SystemError(s"Error when trying to get path '${path.getPath}': ${ex.getMessage}", ex).fail
                       }
                     case NonFatal(ex) => SystemError(s"Error when trying to get path '${path.getPath}': ${ex.getMessage}", ex).fail
                   })
    } yield {
      jsonValue match {
        case x:JSONArray  => x.asScala.toList.map(toJsonString)
        case x            => toJsonString(x) :: Nil
      }
    }
  }

}
