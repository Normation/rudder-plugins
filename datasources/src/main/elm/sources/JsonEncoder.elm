module JsonEncoder exposing (..)

import Model exposing (..)
import Json.Encode exposing (..)
import Utils exposing (scheduleString)

encodeDataSource: DataSource -> Value
encodeDataSource datasource =
  object [
    ("id"  , string datasource.id)
  , ("name",  string datasource.name)
  , ("description",  string datasource.description)
  , ("enabled",  bool datasource.enabled)
  , ("updateTimeout",  int datasource.updateTimeout)
  , ("type", encodeDataSourceType datasource.type_)
  , ("runParameters", encodeRunParameters datasource.runParameters)
  ]

encodeSchedule: Schedule -> Value
encodeSchedule schedule =
  object [
    ("type",  string (scheduleString schedule.type_) )
  , ("duration",  int schedule.duration)
  ]

encodeRunParameters: RunParameters -> Value
encodeRunParameters runParameters =
  object [
    ("onGeneration"  , bool runParameters.onGeneration)
  , ("onNewNode",  bool runParameters.onNewNode)
  , ("schedule",  encodeSchedule runParameters.schedule)
  ]

encodeHeader : Header -> Value
encodeHeader header =
  object [
    ("name",  string header.name)
  , ("value",  string header.value)
  ] 

encodeParameter : Parameter -> Value
encodeParameter parameter =
  object [
    ("name",  string parameter.name)
  , ("value",  string parameter.value)
  ] 

encodeRequestMode: RequestMode -> Value
encodeRequestMode mode =
  case mode of
    ByNode ->
      object [ ("name",  string "byNode") ]
    AllNodes path attribute ->
      object [
        ("name",  string "allNodes")
      , ("path",  string path)
      , ("attribute",  string attribute)
      ]

encodeOnMissing: OnMissing -> Value
encodeOnMissing onmissing =
  case onmissing of
    Delete ->
      object [ ("name",  string "delete") ]
    NoChange ->
      object [ ("name",  string "noChange") ]
    Default value ->
      object [
        ("name",  string "defaultValue")
      , ("value",  string value)
      ]

encodeDataSourceType: Type -> Value
encodeDataSourceType type_ = 
    case type_ of 
      HTTP data ->
          object [
            ("name" , string "HTTP")
          , ("parameters", object [  
              ("url",  string data.url)
            , ("path",  string data.path)
            , ("checkSsl",  bool data.checkSsl)
            , ("maxParallelReq",  int data.maxParallelRequest)
            , ("requestTimeout",  int data.requestTimeout)
            , ("requestMethod",  string (case data.method of
                                          GET -> "GET"
                                          POST -> "POST"
                                        ) )
            , ("headers", list encodeHeader data.headers )
            , ("params", list encodeParameter data.parameters )
            , ("requestMode", encodeRequestMode data.requestMode)
            , ("onMissing", encodeOnMissing data.onMissing)
            ])
          ]  