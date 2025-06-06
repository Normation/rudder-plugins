module ApiCalls exposing (..)

import DataTypes exposing (..)
import Http exposing (emptyBody, expectJson, header, jsonBody, request)
import JsonDecoders exposing (decodeApiDeleteUsername, decodeSetting, decodeUserList)
import JsonEncoders exposing (encodeSetting, encodeUsernames)


getUrl : DataTypes.Model -> String -> String
getUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


getUsers : DataTypes.Model -> Cmd Msg
getUsers model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "users"
                , body = emptyBody
                , expect = expectJson (WorkflowUsersMsg << GetUsers) decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveWorkflow : List Username -> Model -> Cmd Msg
saveWorkflow usernames model =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "validatedUsers"
                , body = jsonBody (encodeUsernames usernames)
                , expect = expectJson (WorkflowUsersMsg << SaveWorkflow) decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getSetting : Model -> String -> (Result Http.Error Bool -> WorkflowUsersMsg) -> Cmd Msg
getSetting model settingId msg =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("settings/" ++ settingId)
                , body = emptyBody
                , expect = expectJson (WorkflowUsersMsg << msg) (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getValidateAllSetting : Model -> Cmd Msg
getValidateAllSetting model =
    getSetting model "enable_validate_all" GetValidateAllSetting


setSetting : Model -> String -> (Result Http.Error Bool -> WorkflowUsersMsg) -> Bool -> Cmd Msg
setSetting model settingId msg newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson (WorkflowUsersMsg << msg) (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveValidateAllSetting : Bool -> Model -> Cmd Msg
saveValidateAllSetting newValue model =
    setSetting model "enable_validate_all" SaveValidateAllSetting newValue
