/*
*************************************************************************************
* Copyright 2017 Normation SAS
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

import com.normation.plugins.datasources.DataSourceSchedule._
import com.normation.plugins.datasources.HttpRequestMode._
import com.normation.rudder.repository.json.JsonExtractorUtils

import java.util.concurrent.TimeUnit
import net.liftweb.common.Full
import net.liftweb.common.Failure
import net.liftweb.common.Box
import net.liftweb.json._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration.FiniteDuration
import cats._
import cats.implicits._
import com.normation.rudder.domain.properties.GenericProperty._
import com.typesafe.config.ConfigRenderOptions
import zio.duration.Duration

object Translate {
  implicit class DurationToScala(d: Duration) {
    def toScala = FiniteDuration(d.toMillis, TimeUnit.MILLISECONDS)
  }
}
import Translate._

object DataSourceJsonSerializer{

  def serialize(source : DataSource) : JValue = {
    import net.liftweb.json.JsonDSL._
    ( ( "name"        -> source.name.value  )
    ~ ( "id"        -> source.id.value  )
    ~ ( "description" -> source.description )
    ~ ( "type" ->
        ( ( "name" -> source.sourceType.name )
        ~ ( "parameters" -> {
            source.sourceType match {
              case DataSourceType.HTTP(url, headers, method, params, checkSsl, path, maxReq, mode, timeOut, missing) =>
                ( ( "url"            -> url     )
                ~ ( "headers"        -> headers.map{
                  case (name,value) => ("name" -> name) ~ ("value" -> value)
                } )
                ~ ( "params"         -> params.map{
                  case (name,value) => ("name" -> name) ~ ("value" -> value)
                } )
                ~ ( "path"           -> path    )
                ~ ( "checkSsl"       -> checkSsl)
                ~ ( "maxParallelReq" -> maxReq )
                ~ ( "requestTimeout" -> timeOut.toScala.toSeconds )
                ~ ( "requestMethod"  -> method.name )
                ~ ( "requestMode"    ->
                    ( ( "name" -> mode.name )
                    ~ { mode match {
                        case OneRequestByNode =>
                          JObject(Nil)
                        case OneRequestAllNodes(subPath,nodeAttribute) =>
                          ( ( "path" -> subPath)
                          ~ ( "attribute" -> nodeAttribute)
                          )
                    } } )
                  )
                ~ ( "onMissing" ->  (missing match {
                      case MissingNodeBehavior.Delete              =>  ("name"  -> MissingNodeBehavior.Delete.name):JObject
                      case MissingNodeBehavior.NoChange            =>  ("name"  -> MissingNodeBehavior.NoChange.name):JObject
                      case MissingNodeBehavior.DefaultValue(value) => (("name"  -> MissingNodeBehavior.DefaultValue.name) ~
                                                                       ("value" -> parse(value.render(ConfigRenderOptions.concise())))):JObject
                    })
                  )
                )
            }
        } ) )
      )
    ~ ( "runParameters" -> (
        ( "onGeneration" -> source.runParam.onGeneration )
      ~ ( "onNewNode"    -> source.runParam.onNewNode )
      ~ ( "schedule"     -> (
          ( "type" -> (source.runParam.schedule match {
                          case _:Scheduled => "scheduled"
                          case _:NoSchedule => "notscheduled"
                        } ) )
        ~ ( "duration" -> source.runParam.schedule.duration.toScala.toSeconds)
        ) )
      ) )
    ~ ( "updateTimeout" -> source.updateTimeOut.toScala.toSeconds )
    ~ ( "enabled"    -> source.enabled )
    )
  }
}

trait DataSourceExtractor[M[_]] extends JsonExtractorUtils[M] {

  case class DataSourceRunParamWrapper(
      schedule     : M[DataSourceSchedule]
    , onGeneration : M[Boolean]
    , onNewNode    : M[Boolean]
  ) {
    def to = {
      monad.map3(schedule, onGeneration, onNewNode){
        case (a,b,c) => DataSourceRunParameters(a,b,c)
      }
    }

    def withBase (base : DataSourceRunParameters) = {
      base.copy(
          getOrElse(schedule, base.schedule)
        , getOrElse(onGeneration,base.onGeneration)
        , getOrElse(onNewNode,base.onNewNode)
      )
    }
  }

  case class DataSourceTypeWrapper(
    url            : M[String]
  , headers        : M[Map[String,String]]
  , httpMethod     : M[HttpMethod]
  , params         : M[Map[String,String]]
  , sslCheck       : M[Boolean]
  , path           : M[String]
  , maxParallelReq : M[Int]
  , requestMode    : M[HttpRequestMode]
  , requestTimeout : M[FiniteDuration]
  , onMissing      : M[MissingNodeBehavior]
  ) {
    def to = {
      monad.map10(url, headers, httpMethod, params, sslCheck, path, maxParallelReq, requestMode, requestTimeout, onMissing){
        case (a,b,c,d,e,f,g,h,i,j) => DataSourceType.HTTP(a,b,c,d,e,f,g,h,Duration.fromScala(i),j)
      }
    }
    def withBase (base : DataSourceType) : DataSourceType = {
      base match {
        case httpBase : DataSourceType.HTTP =>
          httpBase.copy(
              getOrElse(url           , httpBase.url)
            , getOrElse(headers       , httpBase.headers)
            , getOrElse(httpMethod    , httpBase.httpMethod)
            , getOrElse(params        , httpBase.params)
            , getOrElse(sslCheck      , httpBase.sslCheck)
            , getOrElse(path          , httpBase.path)
            , getOrElse(maxParallelReq, httpBase.maxParallelRequest)
            , getOrElse(requestMode   , httpBase.requestMode)
            , Duration.fromScala(getOrElse(requestTimeout, httpBase.requestTimeOut.toScala))
            , getOrElse(onMissing     , httpBase.missingNodeBehavior)
          )
      }
    }
  }

  case class DataSourceWrapper(
    id            : DataSourceId
  , name          : M[DataSourceName]
  , sourceType    : M[DataSourceTypeWrapper]
  , runParam      : M[DataSourceRunParamWrapper]
  , description   : M[String]
  , enabled       : M[Boolean]
  , updateTimeout : M[FiniteDuration]
  ) {
    def to = {
      val unwrapRunParam = monad.flatMap(runParam)(_.to)
      val unwrapType = monad.flatMap(sourceType)(_.to)
        monad.map6(name, unwrapType, unwrapRunParam, description, enabled, updateTimeout){
          case (a,b,c,d,e,f) => DataSource(id,a,b,c,d,e,Duration.fromScala(f))
        }
    }
    def withBase (base : DataSource) = {
      val unwrapRunParam = monad.map(runParam)(_.withBase(base.runParam))
      val unwrapType = monad.map(sourceType)(_.withBase(base.sourceType))
      base.copy(
          base.id
        , getOrElse(name          , base.name)
        , getOrElse(unwrapType    , base.sourceType)
        , getOrElse(unwrapRunParam, base.runParam)
        , getOrElse(description   , base.description)
        , getOrElse(enabled       , base.enabled)
        , Duration.fromScala(getOrElse(updateTimeout , base.updateTimeOut.toScala))
      )
    }
  }

  def extractDuration (value : BigInt) = tryo { FiniteDuration(value.toLong, TimeUnit.SECONDS) }

  def extractDataSource(id : DataSourceId, json : JValue) = {
    for {
      params <- extractDataSourceWrapper(id,json)
    } yield {
      params.to
    }
  }

  def extractDataSourceType(json : JObject) = {
    for {
      params <- extractDataSourceTypeWrapper(json)
    } yield {
      params.to
    }
  }

  def extractDataSourceRunParam(json : JObject) = {
    for {
      params <- extractDataSourceRunParameterWrapper(json)
    } yield {
      params.to
    }
  }

  def extractDataSourceWrapper(id : DataSourceId, json : JValue) : Box[DataSourceWrapper] = {
    for {
      name         <- extractJsonString( json, "name"         ,  x => Full(DataSourceName(x)))
      description  <- extractJsonString( json, "description"  )
      sourceType   <- extractJsonObj(    json, "type"         , extractDataSourceTypeWrapper(_))
      runParam     <- extractJsonObj(    json, "runParameters", extractDataSourceRunParameterWrapper(_))
      timeOut      <- extractJsonBigInt( json, "updateTimeout", extractDuration)
      enabled      <- extractJsonBoolean(json, "enabled"      )
    } yield {
      DataSourceWrapper(id, name, sourceType, runParam, description, enabled, timeOut)
    }
  }

  def extractDataSourceRunParameterWrapper(obj : JObject) = {
    //the subpart in charge of the Schedule
    def extractSchedule(obj : JObject) = {
        for {
            duration <- extractJsonBigInt(obj, "duration", extractDuration)
            scheduleBase  <- {

              val t: Box[M[M[DataSourceSchedule]]] = extractJsonString(obj, "type",  _ match {
                case "scheduled" => Full(monad.map(duration)(d => Scheduled(Duration.fromScala(d))))
                case "notscheduled" => Full(monad.map(duration)(d => NoSchedule(Duration.fromScala(d))))
                case _ => Failure("not a valid value for datasource schedule")
              })
              t.map( monad.flatten(_))
            }
        } yield {
          scheduleBase
      }
    }

    for {
      onGeneration <- extractJsonBoolean(obj, "onGeneration")
      onNewNode    <- extractJsonBoolean(obj, "onNewNode")
      schedule     <- extractJsonObj(    obj, "schedule", extractSchedule(_))
    } yield {
      DataSourceRunParamWrapper(monad.flatten(schedule), onGeneration, onNewNode)
    }
  }

  // A source type is composed of two fields : "name" and "parameters"
  // name allow to determine which kind of datasource we are managing and how to extract parameters
  def extractDataSourceTypeWrapper(obj : JObject) : Box[DataSourceTypeWrapper] = {

    obj \ "name" match {
      case JString(DataSourceType.HTTP.name) =>

        def extractHttpRequestMode(obj: JObject) : Box[M[HttpRequestMode]] = {
          obj \ "name" match {
            case JString(OneRequestByNode.name) =>
              Full(monad.pure(OneRequestByNode))
            case JString(OneRequestAllNodes.name) =>
              for {
                attribute <- extractJsonString(obj, "attribute")
                path      <- extractJsonString(obj, "path")
              } yield {
                monad.map2(path, attribute){case (a,b) => OneRequestAllNodes(a,b)}
              }
            case x => Failure(s"Cannot extract request type from: ${x}")
          }
        }

        def extractNameValueObjectArray(json: JValue, key: String) : Box[M[List[(String,String)]]] = {
          import com.normation.utils.Control.sequence
          json \ key match {
            case JArray(values) =>
              for {
                converted <- sequence(values) { v =>
                                for {
                                  name  <- extractJsonString(v, "name" , boxedIdentity)
                                  value <- extractJsonString(v, "value", boxedIdentity)
                                } yield {
                                  monad.tuple2(name,value)
                                }
                              }
              } yield {
                Traverse[List].sequence(converted.toList)
              }

            case JNothing   => emptyValue(key)
            case x          => Failure(s"Invalid json to extract a json array, current value is: ${compactRender(x)}")
          }
        }

        //we need to special case JNothing to take care of previous version of
        //the plugin that could be missing the attribute
        def extractMissingNodeBehavior(json: JValue) : Box[M[MissingNodeBehavior]] = {
          val onMissing: Box[MissingNodeBehavior] = (json \ "onMissing" match {
            case JNothing => Full(MissingNodeBehavior.Delete)
            case obj: JObject => (obj \ "name") match {
              case JString(MissingNodeBehavior.Delete.name)       =>
                Full(MissingNodeBehavior.Delete)
              case JString(MissingNodeBehavior.NoChange.name)     =>
                Full(MissingNodeBehavior.NoChange)
              case JString(MissingNodeBehavior.DefaultValue.name) =>
                (obj \ "value") match {
                  case JNothing => Failure("Missing 'value' field for default value in data source")
                  case value    => Full(MissingNodeBehavior.DefaultValue(fromJsonValue(value)))
                }
              case _                                                                    =>
                Failure(s"Can not extract onMissingNode behavior from fields ${compactRender(obj)}")
            }
            case x => Failure(s"Can not extract onMissingNode behavior from ${compactRender(x)}")
          })
          onMissing.map(x => monad.pure(x))
        }

        def HttpDataSourceParameters (obj : JObject)  = {
          for {
            url         <- extractJsonString(obj, "url")
            path        <- extractJsonString(obj, "path")
            method      <- extractJsonString(obj, "requestMethod", {s => Box(HttpMethod.values.find( _.name == s))})
            checkSsl    <- extractJsonBoolean(obj, "checkSsl")
            maxParReq   <- Full(monad.point(obj \ "maxParallelReq" match {
                             case JInt(n) if(n > BigInt(0) || n < BigInt(Int.MaxValue)) => n.intValue
                             case _                                                     => DataSourceType.HTTP.defaultMaxParallelRequest // if not int or key absent => default value
                           }))
            timeout     <- extractJsonBigInt(obj, "requestTimeout", extractDuration)
            params      <- extractNameValueObjectArray(obj, "params")
            headers     <- extractNameValueObjectArray(obj, "headers")
            requestMode <- extractJsonObj(obj, "requestMode", extractHttpRequestMode(_))
            onMissing   <- extractMissingNodeBehavior(obj)
          } yield {

            val headersM   = monad.map(headers)(_.toMap)
            val paramsM    = monad.map(params)(_.toMap)
            val unwrapMode = monad.flatten(requestMode)

            (
                url
              , headersM
              , method
              , paramsM
              , checkSsl
              , path
              , maxParReq
              , unwrapMode
              , timeout
              , onMissing
            )
          }
        }

        for {
          parameters <- extractJsonObj(obj, "parameters", HttpDataSourceParameters)
          url       = monad.flatMap(parameters)(_. _1)
          headers   = monad.flatMap(parameters)(_. _2)
          method    = monad.flatMap(parameters)(_. _3)
          params    = monad.flatMap(parameters)(_. _4)
          checkSsl  = monad.flatMap(parameters)(_. _5)
          path      = monad.flatMap(parameters)(_. _6)
          maxParReq = monad.flatMap(parameters)(_. _7)
          mode      = monad.flatMap(parameters)(_. _8)
          timeout   = monad.flatMap(parameters)(_. _9)
          onMissing = monad.flatMap(parameters)(_._10)
        } yield {
          DataSourceTypeWrapper(
              url
            , headers
            , method
            , params
            , checkSsl
            , path
            , maxParReq
            , mode
            , timeout
            , onMissing
          )
        }

      case x => Failure(s"Cannot extract a data source type from: ${x}")
    }
  }

}

object DataSourceExtractor {
  object OptionalJson extends DataSourceExtractor[Option] {
    def monad = implicitly
    def emptyValue[T](id : String) = Full(None)
    def getOrElse[T](value : Option[T], default : T) = value.getOrElse(default)
  }

  type Id[X] = X

  object CompleteJson extends DataSourceExtractor[Id] {
    def monad = implicitly
    def emptyValue[T](id : String) = Failure(s"parameter '${id}' cannot be empty")
    def getOrElse[T](value : T, default : T) = value
  }
}
