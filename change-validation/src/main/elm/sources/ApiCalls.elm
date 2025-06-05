module ApiCalls exposing (..)

import DataTypes exposing (..)
import Http exposing (emptyBody, expectJson, header, jsonBody, request)
import JsonDecoders exposing (decodeSetting, decodeUserList, decodeWorkflowSettings)
import JsonEncoders exposing (encodeSetting, encodeUsernames)


getUrlWU : WorkflowUsersModel -> String -> String
getUrlWU m url =
    m.contextPath ++ "/secure/api/" ++ url


getUsers : WorkflowUsersModel -> Cmd Msg
getUsers model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrlWU model "users"
                , body = emptyBody
                , expect = expectJson (WorkflowUsersMsg << GetUsers) decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveWorkflow : List Username -> WorkflowUsersModel -> Cmd Msg
saveWorkflow usernames model =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrlWU model "validatedUsers"
                , body = jsonBody (encodeUsernames usernames)
                , expect = expectJson (WorkflowUsersMsg << SaveWorkflow) decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


setSetting : WorkflowUsersModel -> String -> (Result Http.Error Bool -> WorkflowUsersMsg) -> Bool -> Cmd Msg
setSetting model settingId msg newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrlWU model ("settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson (WorkflowUsersMsg << msg) (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveValidateAllSetting : Bool -> WorkflowUsersModel -> Cmd Msg
saveValidateAllSetting newValue model =
    setSetting model "enable_validate_all" SaveValidateAllSetting newValue


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
