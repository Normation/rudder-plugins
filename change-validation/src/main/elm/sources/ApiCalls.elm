module ApiCalls exposing (..)

import DataTypes exposing (..)
import Http exposing (emptyBody, expectJson, header, request)
import JsonUtils exposing (decodeWorkflowSettings)


getUrl : Model -> String -> String
getUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


getAllWorkflowSettings : Model -> Cmd Msg
getAllWorkflowSettings model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "settings"
                , body = emptyBody
                , expect = expectJson GetAllWorkflowSettings decodeWorkflowSettings
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req
