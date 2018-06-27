module ApiCall exposing (..)

import DataTypes exposing (..)
import Http exposing (..)
import JsonDecoder exposing (..)
import JsonEncoder exposing(..)

getSettings : Model -> Cmd Msg
getSettings model =
  let
    url     = (model.contextPath ++ "/secure/api/branding")
    headers = []
    req = request {
        method          = "GET"
      , headers         = []
      , url             = url
      , body            = emptyBody
      , expect          = expectJson decodeApiSettings
      , timeout         = Nothing
      , withCredentials = False
      }
  in
    send GetSettings req

saveSettings : Model -> Cmd Msg
saveSettings model =
 let
   req = request {
             method = "POST"
           , headers = []
           , url = model.contextPath ++ "/secure/api/branding"
           , body = jsonBody (encodeSettings model.settings)
           , expect = expectJson decodeApiSettings
           , timeout = Nothing
           , withCredentials = False
         }
 in
    send SaveSettings req