module APICalls exposing (..)


import Model exposing (DataSource, Model)
import Http exposing (..)
import Json.Decode
import JsonDecoder exposing (..)
import JsonEncoder exposing (encodeDataSource)
import Messages exposing (..)

getUrl: Model -> String -> String
getUrl m url =
  m.contextPath ++ "/secure/api/" ++ url

getDataSources : Model -> Cmd Msg
getDataSources  model =
  let
    req =
      request
        { method  = "GET"
        , headers = []
        , url     = getUrl model "datasources"
        , body    = emptyBody
        , expect  = expectJson GetDataSources (decodeDataList decodeDataSource [ "datasources"] )
        , timeout = Nothing
        , tracker = Nothing
        }
  in
    req

saveDataSource : Model -> Bool -> DataSource -> Cmd Msg
saveDataSource  model isNew datasource =
  let
    req =
      request
        { method  = if isNew then "PUT" else "POST"
        , headers = []
        , url     = getUrl model "datasources"
        , body    = jsonBody (encodeDataSource datasource)
        , expect  = expectJson SaveDataSource (decodeDataList decodeDataSource [ "datasources"] |> headList )
        , timeout = Nothing
        , tracker = Nothing
        }
  in
    req


deleteDataSource : Model -> DataSource -> Cmd Msg
deleteDataSource  model datasource =
  let
    req =
      request
        { method  = "DELETE"
        , headers = []
        , url     = getUrl model ("datasources/" ++ datasource.id)
        , body    = emptyBody
        , expect  = expectJson DeleteDataSource (decodeData Json.Decode.string [ "datasources", "id" ]  )
        , timeout = Nothing
        , tracker = Nothing
        }
  in
    req