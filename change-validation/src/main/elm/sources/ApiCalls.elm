module ApiCalls exposing (..)

import DataTypes exposing (..)
import Http exposing (emptyBody, expectJson, header, jsonBody, request)
import JsonDecoders exposing (decodeApiDeleteUsername, decodeUserList)
import JsonEncoders exposing (encodeUsernames)


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
                , expect = expectJson GetUsers decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


removeValidatedUser : Username -> Model -> Cmd Msg
removeValidatedUser username model =
    let
        req =
            request
                { method = "DELETE"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("validatedUsers/" ++ username)
                , body = emptyBody
                , expect = expectJson RemoveUser decodeApiDeleteUsername
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
                , expect = expectJson SaveWorkflow decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req
