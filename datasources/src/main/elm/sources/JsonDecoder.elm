module JsonDecoder exposing (..)

import Model exposing (..)
import Json.Decode exposing (..)
import Json.Decode.Pipeline exposing (required)


headList : Decoder (List a) -> Decoder a
headList decoder =
  andThen ( \l ->
    case l of
      [] -> fail ""
      x :: _ -> succeed x
    ) decoder

decodeDataList: Decoder a ->  List String -> Decoder (List a)
decodeDataList decoder path =
  decodeData (list decoder) path

decodeData: Decoder a -> List String -> Decoder a
decodeData decoder path =
  at ("data" :: path)  decoder

decodeScheduleType : Decoder ScheduleType
decodeScheduleType =
  string |> andThen (\s ->
    case s of
      "notscheduled" -> succeed NotScheduled
      "scheduled" -> succeed Scheduled
      _ -> fail "Data Source schedule must be 'scheduled/notscheduled'"
    )

decodeSchedule : Decoder Schedule
decodeSchedule =
  succeed Schedule
    |> required "type" decodeScheduleType
    |> required "duration" int

decodeRunParameters: Decoder RunParameters
decodeRunParameters =
  succeed RunParameters
    |> required "onGeneration" bool
    |> required "onNewNode" bool
    |> required "schedule" decodeSchedule

decodeHeader : Decoder Header
decodeHeader =
  succeed Header
    |> required "name" string
    |> required "value" string

decodeParameter : Decoder Parameter
decodeParameter =
  succeed Parameter
    |> required "name" string
    |> required "value" string

decodeMethod : Decoder HTTPMethod
decodeMethod =
  string |> andThen (\s ->
                        case s of
                         "GET" -> succeed GET
                         "POST" -> succeed POST
                         _ -> fail "Data Source http method must be 'get/post'"
                      )

decodeRequestMode : Decoder RequestMode
decodeRequestMode =
  field "name" string |> andThen (\s ->
    case s of
     "byNode" -> succeed ByNode
     "allNodes" -> succeed AllNodes
                     |> required "path" string
                     |> required "attribute" string
     _ -> fail "Data Source http request mode must be 'byNode/allNodes'"
  )


decodeOnMissing : Decoder OnMissing
decodeOnMissing =
  field "name" string |> andThen (\s ->
    case s of
     "delete" -> succeed Delete
     "noChange" -> succeed NoChange
     "defaultValue" -> succeed Default
                         |> required "value" string
     _ -> fail "Data Source on missing data behavior must be 'delete/noChange/defaultValue'"
  )

decodeHttp : Decoder HTTPType
decodeHttp =
  succeed HTTPType
    |> required "url" string
    |> required "requestMethod" decodeMethod
    |> required "path" string
    |> required "checkSsl" bool
    |> required "params" (list decodeParameter)
    |> required "requestTimeout" int
    |> required "headers" (list decodeHeader)
    |> required "requestMode" decodeRequestMode
    |> required "maxParallelReq" int
    |> required "onMissing" decodeOnMissing


decodeType : Decoder Type
decodeType =
  field "name" string |> andThen (\s ->
    case s of
     "HTTP" -> succeed HTTP
                 |> required "parameters" decodeHttp
     _ -> fail "Data Source type must be 'http'"
  )

decodeDataSource : Decoder DataSource
decodeDataSource =
  succeed DataSource
    |> required "name" string
    |> required "id" string
    |> required "description" string
    |> required "enabled" bool
    |> required "updateTimeout" int
    |> required "runParameters" decodeRunParameters
    |> required "type" decodeType

