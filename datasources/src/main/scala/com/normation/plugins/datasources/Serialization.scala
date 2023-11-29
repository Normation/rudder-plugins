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

import DataSourceJsonCodec._
import cats.implicits._
import com.normation.errors._
import com.normation.rudder.domain.properties.GenericProperty._
import com.normation.rudder.repository.json.JsonExtractorUtils
import com.normation.rudder.rest.RudderJsonRequest._
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import io.scalaland.chimney.Patcher
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.PatcherConfiguration
import io.scalaland.chimney.syntax._
import java.util.concurrent.TimeUnit
import net.liftweb.common.Failure
import net.liftweb.common.Full
import net.liftweb.http.Req
import scala.concurrent.duration.FiniteDuration
import scala.math.BigDecimal
import scala.util.chaining._
import zio._
import zio.json._
import zio.json.ast.Json

object Translate {
  implicit class DurationToScala(d: Duration) {
    def toScala = FiniteDuration(d.toMillis, TimeUnit.MILLISECONDS)
  }
}
import Translate._

/**
 * All the content of the datasource without it's id
 * WARNING: this is used to read the datasource representation in the database as json, so its format and the database source must be kept retrocompatible
 */
final case class DataSourceProperties(
    name:                                      DataSourceName,
    description:                               String,
    @jsonField("type") sourceType:             FullDataSourceType,
    @jsonField("runParameters") runParam:      FullDataSourceRunParameters,
    @jsonField("updateTimeout") updateTimeOut: Duration,
    enabled:                                   Boolean
) {
  def toDataSource(id: DataSourceId): DataSource = {
    DataSource(
      id,
      name,
      sourceType.transformInto[DataSourceType],
      runParam.transformInto[DataSourceRunParameters],
      description,
      enabled,
      updateTimeOut
    )
  }
}

/**
 * Data representation for a new datasource with all property fields optional. It can be transformed using known default values.
 * WARNING: any change in this class must be kept retrocompatible with the API request definitions, as it is used to read the request body
 */
final case class NewDataSource(
    id:                                        DataSourceId,
    name:                                      Option[DataSourceName],
    description:                               Option[String],
    @jsonField("type") sourceType:             DataSourceExtractor.PatchDataSourceType,
    @jsonField("runParameters") runParam:      DataSourceExtractor.PatchDataSourceRunParam,
    @jsonField("updateTimeout") updateTimeOut: Option[Duration],
    enabled:                                   Option[Boolean]
) {
  def toDataSource: DataSource = {
    val base = NewDataSource.baseDataSource(id)
    this.transformInto[DataSourceExtractor.PatchDataSource].withBase(base)
  }
}

object NewDataSource {
  implicit val transformToPatchDataSource: Transformer[NewDataSource, DataSourceExtractor.PatchDataSource] =
    Transformer.derive[NewDataSource, DataSourceExtractor.PatchDataSource]

  // default values

  val defaultDuration = DataSource.defaultDuration
  val baseSourceType  = DataSourceType.HTTP(
    "",
    Map(),
    HttpMethod.GET,
    Map(),
    false,
    "",
    DataSourceType.HTTP.defaultMaxParallelRequest,
    HttpRequestMode.OneRequestByNode,
    defaultDuration,
    MissingNodeBehavior.Delete
  )
  val baseRunParam    = DataSourceRunParameters.apply(DataSourceSchedule.NoSchedule(defaultDuration), false, false)

  def baseDataSource(id: DataSourceId) =
    DataSource.apply(id, DataSourceName(""), baseSourceType, baseRunParam, "", false, defaultDuration)
}

/**
 * All the content of the datasource that can be serialized to json.
 * WARNING: this is used to write the datasource representation in the database as json, so its format and the database read format must be kept retrocompatible
 */
final case class FullDataSource(
    name:                                      DataSourceName,
    id:                                        DataSourceId,
    description:                               String,
    @jsonField("type") sourceType:             FullDataSourceType,
    @jsonField("runParameters") runParam:      FullDataSourceRunParameters,
    @jsonField("updateTimeout") updateTimeOut: Duration,
    enabled:                                   Boolean
)

object FullDataSource {
  implicit val transformerFrom: Transformer[FullDataSource, DataSource] =
    Transformer.derive[FullDataSource, DataSource]
  implicit val transformerTo:   Transformer[DataSource, FullDataSource] =
    Transformer.derive[DataSource, FullDataSource]
}

sealed trait FullDataSourceHttpMethod { def name: String }
object FullDataSourceHttpMethod       {
  final case object GET  extends FullDataSourceHttpMethod { val name = "GET"  }
  final case object POST extends FullDataSourceHttpMethod { val name = "POST" }

  implicit val transformerFrom: Transformer[FullDataSourceHttpMethod, HttpMethod] =
    Transformer.derive[FullDataSourceHttpMethod, HttpMethod]
  implicit val transformerTo:   Transformer[HttpMethod, FullDataSourceHttpMethod] =
    Transformer.derive[HttpMethod, FullDataSourceHttpMethod]

  def byName(name: String): Either[String, FullDataSourceHttpMethod] = {
    name match {
      case GET.name  => Right(GET)
      case POST.name => Right(POST)
      case _         => Left(s"Unknown FullDataSourceHttpMethod: ${name}")
    }
  }
}

@jsonDiscriminator("name") sealed trait FullDataSourceType

// the data source type is wrapped in "parameters", we need a wrapper for each data source type to create codecs by simple derivation
@jsonHint("HTTP") final case class FullDataSourceParameterHttp(
    parameters: FullDataSourceHttpType
) extends FullDataSourceType

final case class FullDataSourceHttpType(
    url:                                             String,
    headers:                                         List[FullDataSourceNameValue],
    params:                                          List[FullDataSourceNameValue],
    path:                                            String,
    @jsonField("checkSsl") sslCheck:                 Boolean,
    @jsonField("maxParallelReq") maxParallelRequest: FullDataSourceHttpType.MaxParallelRequest,
    @jsonField("requestTimeout") requestTimeOut:     Duration,
    @jsonField("requestMethod") httpMethod:          FullDataSourceHttpMethod,
    requestMode:                                     FullDataSourceMode,
    @jsonField("onMissing") missingNodeBehavior:     Option[FullDataSourceMissing]
)

object FullDataSourceType {
  implicit val transformerFrom: Transformer[FullDataSourceType, DataSourceType] = {
    Transformer
      .define[FullDataSourceType, DataSourceType]
      .withCoproductInstance[FullDataSourceType] {
        case http: FullDataSourceParameterHttp => http.parameters.transformInto[DataSourceType.HTTP]
      }
      .buildTransformer
  }

  implicit val transformerTo: Transformer[DataSourceType, FullDataSourceType] = {
    Transformer
      .define[DataSourceType, FullDataSourceType]
      .withCoproductInstance[DataSourceType] {
        case http: DataSourceType.HTTP => FullDataSourceParameterHttp(http.transformInto[FullDataSourceHttpType])
      }
      .buildTransformer
  }

}

object FullDataSourceHttpType {
  final case class MaxParallelRequest(value: Int) extends AnyVal
  object MaxParallelRequest {
    val default = MaxParallelRequest(DataSourceType.HTTP.defaultMaxParallelRequest)
  }

  implicit val transformerFrom: Transformer[FullDataSourceHttpType, DataSourceType.HTTP] = {
    Transformer
      .define[FullDataSourceHttpType, DataSourceType.HTTP]
      .withFieldComputed(_.headers, _.headers.map(x => (x.name, x.value)).toMap)
      .withFieldComputed(_.params, _.params.map(x => (x.name, x.value)).toMap)
      .withFieldComputed(
        _.missingNodeBehavior,
        // default value is Delete
        _.missingNodeBehavior.getOrElse(FullDataSourceMissing.Delete).transformInto[MissingNodeBehavior]
      )
      .buildTransformer
  }
  implicit val transformerTo:   Transformer[DataSourceType.HTTP, FullDataSourceHttpType] = {
    Transformer
      .define[DataSourceType.HTTP, FullDataSourceHttpType]
      .withFieldComputed(_.headers, _.headers.toList.map(x => FullDataSourceNameValue(x._1, x._2)))
      .withFieldComputed(_.params, _.params.toList.map(x => FullDataSourceNameValue(x._1, x._2)))
      .buildTransformer
  }
}

final case class FullDataSourceNameValue(
    name:  String,
    value: String
)

@jsonDiscriminator("name") sealed trait FullDataSourceMode

object FullDataSourceMode {
  @jsonHint(HttpRequestMode.OneRequestByNode.name) case object OneRequestByNode extends FullDataSourceMode
  @jsonHint(HttpRequestMode.OneRequestAllNodes.name) case class OneRequestAllNodes(
      @jsonField("path") matchingPath:       String,
      @jsonField("attribute") nodeAttribute: String
  ) extends FullDataSourceMode

  implicit val transformerFrom: Transformer[FullDataSourceMode, HttpRequestMode] =
    Transformer.derive[FullDataSourceMode, HttpRequestMode]
  implicit val transformerTo:   Transformer[HttpRequestMode, FullDataSourceMode] =
    Transformer.derive[HttpRequestMode, FullDataSourceMode]
}

@jsonDiscriminator("name") sealed trait FullDataSourceMissing

object FullDataSourceMissing {
  @jsonHint(MissingNodeBehavior.Delete.name) final case object Delete                                extends FullDataSourceMissing
  @jsonHint(MissingNodeBehavior.NoChange.name) final case object NoChange                            extends FullDataSourceMissing
  @jsonHint(MissingNodeBehavior.DefaultValue.name) final case class DefaultValue(value: ConfigValue) extends FullDataSourceMissing

  implicit val transformerFrom: Transformer[FullDataSourceMissing, MissingNodeBehavior] =
    Transformer.derive[FullDataSourceMissing, MissingNodeBehavior]
  implicit val transformerTo:   Transformer[MissingNodeBehavior, FullDataSourceMissing] =
    Transformer.derive[MissingNodeBehavior, FullDataSourceMissing]
}

final case class FullDataSourceRunParameters(
    onGeneration: Boolean,
    onNewNode:    Boolean,
    schedule:     FullDataSourceSchedule
)

object FullDataSourceRunParameters {
  implicit val transformerFrom: Transformer[FullDataSourceRunParameters, DataSourceRunParameters] =
    Transformer.derive[FullDataSourceRunParameters, DataSourceRunParameters]
  implicit val transformerTo:   Transformer[DataSourceRunParameters, FullDataSourceRunParameters] =
    Transformer.derive[DataSourceRunParameters, FullDataSourceRunParameters]
}

@jsonDiscriminator("type") sealed trait FullDataSourceSchedule

object FullDataSourceSchedule {
  @jsonHint("scheduled") final case class Scheduled(
      duration: Duration
  ) extends FullDataSourceSchedule
  @jsonHint("notscheduled") final case class NoSchedule(
      @jsonField("duration") savedDuration: Duration
  ) extends FullDataSourceSchedule

  implicit val transformerFrom: Transformer[FullDataSourceSchedule, DataSourceSchedule] =
    Transformer.derive[FullDataSourceSchedule, DataSourceSchedule]

  implicit val transformerTo: Transformer[DataSourceSchedule, FullDataSourceSchedule] =
    Transformer.derive[DataSourceSchedule, FullDataSourceSchedule]
}

object DataSourceJsonCodec {
  // encode seconds as Long, decode seconds as BigInt (kept retrocompatibility from lift-json migration)
  implicit val durationEncoder: JsonEncoder[Duration] = JsonEncoder[Long].contramap(_.toScala.toSeconds)
  implicit val durationDecoder: JsonDecoder[Duration] =
    JsonDecoder[BigInt].map(v => Duration.fromScala(FiniteDuration(v.toLong, TimeUnit.SECONDS)))

  implicit val maxParallelRequestEncoder: JsonEncoder[FullDataSourceHttpType.MaxParallelRequest] =
    JsonEncoder[Int].contramap(_.value)
  implicit val maxParallelRequestDecoder: JsonDecoder[FullDataSourceHttpType.MaxParallelRequest] = JsonDecoder[Option[Json]].map {
    case Some(Json.Num(n)) if (BigDecimal(n).pipe(i => i > 0 && i.isValidInt)) =>
      FullDataSourceHttpType.MaxParallelRequest(BigDecimal(n).toIntExact)
    case _                                                                     =>
      FullDataSourceHttpType.MaxParallelRequest.default // if not int or key absent => default value
  }

  implicit val dataSourceNameEncoder:              JsonEncoder[DataSourceName]              = JsonEncoder[String].contramap(_.value)
  implicit val dataSourceIdEncoder:                JsonEncoder[DataSourceId]                = JsonEncoder[String].contramap(_.value)
  implicit val fullDataSourceNameValueEncoder:     JsonEncoder[FullDataSourceNameValue]     =
    DeriveJsonEncoder.gen[FullDataSourceNameValue]
  implicit val fullDataSourceModeEncoder:          JsonEncoder[FullDataSourceMode]          = DeriveJsonEncoder.gen[FullDataSourceMode]
  implicit val configValueEncoder:                 JsonEncoder[ConfigValue]                 = {
    JsonEncoder[Json].contramap(
      _.render(ConfigRenderOptions.concise()).fromJson[Json].toOption.get
    ) // such rending always return valid json
  }
  implicit val fullDataSourceMissingEncoder:       JsonEncoder[FullDataSourceMissing]       = DeriveJsonEncoder.gen[FullDataSourceMissing]
  implicit val fullDataSourceScheduleEncoder:      JsonEncoder[FullDataSourceSchedule]      = DeriveJsonEncoder.gen[FullDataSourceSchedule]
  implicit val fullDataSourceRunParametersEncoder: JsonEncoder[FullDataSourceRunParameters] =
    DeriveJsonEncoder.gen[FullDataSourceRunParameters]
  implicit val fullDataSourceHttpMethodEncoder:    JsonEncoder[FullDataSourceHttpMethod]    = JsonEncoder[String].contramap(_.name)
  implicit val fullDataSourceHttpTypeEncoder:      JsonEncoder[FullDataSourceHttpType]      =
    DeriveJsonEncoder.gen[FullDataSourceHttpType]
  implicit val fullDataSourceParameterHttpEncoder: JsonEncoder[FullDataSourceParameterHttp] =
    DeriveJsonEncoder.gen[FullDataSourceParameterHttp]
  implicit val fullDataSourceTypeEncoder:          JsonEncoder[FullDataSourceType]          = DeriveJsonEncoder.gen[FullDataSourceType]
  implicit val fullDataSourceEncoder:              JsonEncoder[FullDataSource]              = DeriveJsonEncoder.gen[FullDataSource]

  // Decoders
  implicit val dataSourceNameDecoder:              JsonDecoder[DataSourceName]              = JsonDecoder[String].map(DataSourceName(_))
  implicit val dataSourceIdDecoder:                JsonDecoder[DataSourceId]                = JsonDecoder[String].map(DataSourceId(_))
  implicit val fullDataSourceNameValueDecoder:     JsonDecoder[FullDataSourceNameValue]     =
    DeriveJsonDecoder.gen[FullDataSourceNameValue]
  implicit val fullDataSourceModeDecoder:          JsonDecoder[FullDataSourceMode]          = DeriveJsonDecoder.gen[FullDataSourceMode]
  implicit val configValueDecoder:                 JsonDecoder[ConfigValue]                 = JsonDecoder[Json].map(fromZioJson(_))
  implicit val fullDataSourceMissingDecoder:       JsonDecoder[FullDataSourceMissing]       = DeriveJsonDecoder.gen[FullDataSourceMissing]
  implicit val fullDataSourceScheduleDecoder:      JsonDecoder[FullDataSourceSchedule]      = DeriveJsonDecoder.gen[FullDataSourceSchedule]
  implicit val fullDataSourceRunParametersDecoder: JsonDecoder[FullDataSourceRunParameters] =
    DeriveJsonDecoder.gen[FullDataSourceRunParameters]
  implicit val fullDataSourceHttpMethodDecoder:    JsonDecoder[FullDataSourceHttpMethod]    =
    JsonDecoder[String].mapOrFail(FullDataSourceHttpMethod.byName(_))
  implicit val fullDataSourceHttpTypeDecoder:      JsonDecoder[FullDataSourceHttpType]      =
    DeriveJsonDecoder.gen[FullDataSourceHttpType]
  implicit val fullDataSourceParameterHttpDecoder: JsonDecoder[FullDataSourceParameterHttp] =
    DeriveJsonDecoder.gen[FullDataSourceParameterHttp]
  implicit val fullDataSourceTypeDecoder:          JsonDecoder[FullDataSourceType]          = DeriveJsonDecoder.gen[FullDataSourceType]
  implicit val fullDataSourceDecoder:              JsonDecoder[FullDataSource]              = DeriveJsonDecoder.gen[FullDataSource]
  implicit val dataSourcePayloadDecoder:           JsonDecoder[DataSourceProperties]        = DeriveJsonDecoder.gen[DataSourceProperties]
  implicit val newDataSourceDecoder:               JsonDecoder[NewDataSource]               = DeriveJsonDecoder.gen[NewDataSource]

}

// API response with "id" and "message" json object in data
final case class RestResponseMessage(
    id:      DataSourceId,
    message: String
)

object RestResponseMessage {
  implicit val encoder: JsonEncoder[RestResponseMessage] = DeriveJsonEncoder.gen[RestResponseMessage]
}

/**
 * Set of patch classes and utilities to extract and decode values.
 * For patch classes, fields from the full class are made optional, but and the json representation is kept,
 * hence the similarity in the ADT structures.
 */
object DataSourceExtractor {

  // For patching we have the strategy of not overriding the base value if the patch value is None
  // see https://chimney.readthedocs.io/en/0.8.3/supported-patching/ and "the cookbook" for scala 3 migration
  // (or more verbose solution : define the Patcher with the .ignoreNoneInPatch option instead of deriving)
  implicit protected val patchCfg: PatcherConfiguration[_] = PatcherConfiguration.default.ignoreNoneInPatch

  case class PatchDataSourceRunParam(
      onGeneration: Option[Boolean],
      onNewNode:    Option[Boolean],
      schedule:     Option[FullDataSourceSchedule]
  ) {
    def withBase(base: DataSourceRunParameters): DataSourceRunParameters = base.patchUsing(this)
  }

  object PatchDataSourceRunParam {
    implicit val patcher: Patcher[DataSourceRunParameters, PatchDataSourceRunParam] =
      Patcher.derive[DataSourceRunParameters, PatchDataSourceRunParam]
    implicit val decoder: JsonDecoder[PatchDataSourceRunParam]                      = DeriveJsonDecoder.gen[PatchDataSourceRunParam]

    def empty: PatchDataSourceRunParam = PatchDataSourceRunParam(None, None, None)
  }

  @jsonDiscriminator("name") sealed trait PatchDataSourceType {
    def withBase(base: DataSourceType): DataSourceType = {
      base.patchUsing(this)
    }
  }

  // the data source type is wrapped in "parameters", we need a wrapper to have the same structure as the full data source type
  @jsonHint("HTTP") final case class PatchDataSourceParameterHttp(
      parameters: PatchDataSourceHttpType
  ) extends PatchDataSourceType

  final case class PatchDataSourceHttpType(
      url:                                             Option[String],
      headers:                                         Option[List[FullDataSourceNameValue]],
      params:                                          Option[List[FullDataSourceNameValue]],
      path:                                            Option[String],
      @jsonField("checkSsl") sslCheck:                 Option[Boolean],
      @jsonField("maxParallelReq") maxParallelRequest: Option[FullDataSourceHttpType.MaxParallelRequest],
      @jsonField("requestTimeout") requestTimeOut:     Option[Duration],
      @jsonField("requestMethod") httpMethod:          Option[FullDataSourceHttpMethod],
      requestMode:                                     Option[FullDataSourceMode],
      @jsonField("onMissing") missingNodeBehavior:     Option[FullDataSourceMissing]
  )

  object PatchDataSourceType {
    implicit val httpPatcher: Patcher[DataSourceType.HTTP, PatchDataSourceHttpType] =
      Patcher.derive[DataSourceType.HTTP, PatchDataSourceHttpType]

    implicit val patcher: Patcher[DataSourceType, PatchDataSourceType] = {
      case (obj: DataSourceType.HTTP, patch: PatchDataSourceParameterHttp) => httpPatcher.patch(obj, patch.parameters)
    }

    implicit val httpDecoder: JsonDecoder[PatchDataSourceHttpType] = DeriveJsonDecoder.gen[PatchDataSourceHttpType]
    implicit val decoder:     JsonDecoder[PatchDataSourceType]     = DeriveJsonDecoder.gen[PatchDataSourceType]

    def empty: PatchDataSourceType = PatchDataSourceParameterHttp(
      PatchDataSourceHttpType(
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None
      )
    )

  }

  /**
   * Data representation used for update of datasource, simply a DataSourceProperties with all property fields optional
   * WARNING: any change in this class must be kept retrocompatible with the API request definitions, as it is used to read the request body
   */
  case class PatchDataSource(
      name:                                      Option[DataSourceName],
      description:                               Option[String],
      @jsonField("type") sourceType:             PatchDataSourceType,
      @jsonField("runParameters") runParam:      PatchDataSourceRunParam,
      @jsonField("updateTimeout") updateTimeOut: Option[Duration],
      enabled:                                   Option[Boolean]
  ) {
    def withBase(base: DataSource): DataSource = {
      // chimney patcher was not possible to derive due to nested Patch (requested feature from chimney)
      base.copy(
        name = name.getOrElse(base.name),
        description = description.getOrElse(base.description),
        sourceType = sourceType.withBase(base.sourceType),
        runParam = runParam.withBase(base.runParam),
        updateTimeOut = updateTimeOut.getOrElse(base.updateTimeOut),
        enabled = enabled.getOrElse(base.enabled)
      )
    }
  }

  private object PatchDataSource {
    implicit val decoder: JsonDecoder[PatchDataSource] = DeriveJsonDecoder.gen[PatchDataSource]
  }

  /**
   * Utilities to extract values from params Map
   */
  implicit class Extract(params: Map[String, List[String]]) {
    def optGet(key: String):                               Option[String]        = params.get(key).flatMap(_.headOption)
    def parse[A: JsonDecoder](key: String):                PureResult[Option[A]] = {
      optGet(key) match {
        case None    => Right(None)
        case Some(x) => JsonDecoder[A].decodeJson(x).map(Some(_)).left.map(Unexpected)
      }
    }
    def parseString[A](key: String, decoder: String => A): PureResult[Option[A]] = {
      optGet(key) match {
        case None    => Right(None)
        case Some(x) => Some(decoder(x)).asRight
      }
    }
  }

  /**
    * API of extractor for patched JSON : extract field updates, all fields are thus optional
    * We need extraction when knowning the concrete type param of the data source extractor, because we need derivation
    */
  object OptionalJson extends JsonExtractorUtils[Option] {
    def monad                                      = implicitly
    def emptyValue[T](id: String)                  = Full(None)
    def getOrElse[T](value: Option[T], default: T) = value.getOrElse(default)

    def extractNewDataSource(req: Req): PureResult[DataSource] = {
      if (req.json_?) {
        req
          .fromJson[NewDataSource]
          .map(_.toDataSource)
      } else {
        extractDataSourceFromParams(req.params, None)
      }
    }

    // base : a data source to patch
    def extractDataSource(req: Req, base: DataSource): PureResult[DataSource] = {
      if (req.json_?) {
        req
          .fromJson[PatchDataSource]
          .map(_.withBase(base))
      } else {
        extractDataSourceFromParams(req.params, Some(base))
      }
    }

    // Fails (is a left) if "id" is not present in params but is required to create a new data source
    private def extractDataSourceFromParams(
        params: Map[String, List[String]],
        base:   Option[DataSource]
    ): PureResult[DataSource] = {
      for {
        name          <- params.parseString("name", DataSourceName.apply)
        sourceType    <- params.parse[PatchDataSourceType]("sourceType").map(_.getOrElse(PatchDataSourceType.empty))
        runParam      <- params.parse[PatchDataSourceRunParam]("runParam").map(_.getOrElse(PatchDataSourceRunParam.empty))
        description   <- params.parseString("description", identity)
        enabled       <- params.parse[Boolean]("enabled")
        updateTimeout <- params.parse[Duration]("updateTimeout")

        res <- base match {
                 case Some(toPatch) =>
                   PatchDataSource(name, description, sourceType, runParam, updateTimeout, enabled)
                     .withBase(toPatch)
                     .asRight
                 case None          =>
                   params
                     .parseString("id", DataSourceId.apply)
                     .flatMap(_.toRight(Unexpected("Datasource 'id' parameter is mandatory")))
                     .map(NewDataSource(_, name, description, sourceType, runParam, updateTimeout, enabled).toDataSource)

               }
      } yield res
    }

  }

  type Id[X] = X

  /**
   * API of extractor for complete JSON : extract all fields, all fields are thus mandatory
   */
  object CompleteJson extends JsonExtractorUtils[Id] {
    def monad                              = implicitly
    def emptyValue[T](id: String)          = Failure(s"parameter '${id}' cannot be empty")
    def getOrElse[T](value: T, default: T) = value

    // Extract all fields of a DataSource from the JSON
    def extractDataSource(json: String): Either[String, DataSource] = {
      json.fromJson[FullDataSource].map(_.transformInto[DataSource])
    }
  }

}
