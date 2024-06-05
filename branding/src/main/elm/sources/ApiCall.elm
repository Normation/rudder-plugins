module ApiCall exposing (getSettings, saveSettings)

import DataTypes exposing (..)
import Http exposing (..)
import JsonDecoder exposing (..)
import JsonEncoder exposing (..)


getSettings : Model -> Cmd Msg
getSettings model =
    let
        url =
            model.contextPath ++ "/secure/api/branding"

        req =
            request
                { method = "GET"
                , headers = [header "X-Requested-With" "XMLHttpRequest"]
                , url = url
                , body = emptyBody
                , expect = expectJson GetSettings decodeApiSettings
                , timeout = Nothing
                , tracker = Nothing
                }
    in
      req


saveSettings : Model -> Cmd Msg
saveSettings model =
    let
        req =
            request
                { method = "POST"
                , headers = [header "X-Requested-With" "XMLHttpRequest"]
                , url = model.contextPath ++ "/secure/api/branding"
                , body = jsonBody (encodeSettings model.settings)
                , expect = expectJson SaveSettings decodeApiSettings
                , timeout = Nothing
                , tracker = Nothing
                }
    in
      req
