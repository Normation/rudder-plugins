module ApiCalls exposing (..)

------------------------------
-- API --
------------------------------
-- API call to get the category tree


import DataTypes exposing (AddUserForm, Model, Msg(..), Username)
import Http exposing (emptyBody, expectJson, jsonBody, request, send)
import JsonDecoder exposing (decodeApiAddUserResult, decodeApiCurrentUsersConf, decodeApiDeleteUserResult, decodeApiReloadResult, decodeApiUpdateUserResult, decodeGetRoleApiResult)
import JsonEncoder exposing (encodeAddUser)
import JsonDecoder exposing (decodeApiStatusResult)
import Json.Decode as Decode

getUrl: DataTypes.Model -> String -> String
getUrl m url =
  m.contextPath ++ "/secure/api" ++ url

getUsersConf : Model -> Cmd Msg
getUsersConf model =
    let
        req =
            request
                { method          = "GET"
                , headers         = []
                , url             = getUrl model "/usermanagement/users"
                , body            = emptyBody
                , expect          = expectJson decodeApiCurrentUsersConf
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send GetUserInfo req


postReloadConf : Model -> Cmd Msg
postReloadConf model =
    let
        req =
            request
                { method          = "POST"
                , headers         = []
                , url             = getUrl model "/usermanagement/users/reload"
                , body            = emptyBody
                , expect          = expectJson decodeApiReloadResult
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send PostReloadUserInfo req

addUser : Model -> AddUserForm -> Cmd Msg
addUser model userForm =
    let
        req =
            request
                { method          = "POST"
                , headers         = []
                , url             = getUrl model "/usermanagement"
                , body            = jsonBody (encodeAddUser userForm)
                , expect          = expectJson decodeApiAddUserResult
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send AddUser req

deleteUser : String -> Model -> Cmd Msg
deleteUser  username model =
    let
        req =
            request
                { method          = "DELETE"
                , headers         = []
                , url             = getUrl model ("/usermanagement/" ++ username)
                , body            = emptyBody
                , expect          = expectJson decodeApiDeleteUserResult
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send DeleteUser req

updateUser : Model -> String -> AddUserForm -> Cmd Msg
updateUser model toUpdate userForm =
    let
        req =
            request
                { method          = "POST"
                , headers         = []
                , url             = getUrl model ("/usermanagement/update/" ++ toUpdate)
                , body            = jsonBody (encodeAddUser userForm)
                , expect          = expectJson decodeApiUpdateUserResult
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send UpdateUser req

activateUser : Model -> Username -> Cmd Msg
activateUser model username =
    let
        req =
            request
                { method          = "PUT"
                , headers         = []
                , url             = getUrl model ("/usermanagement/status/activate/" ++ username)
                , body            = emptyBody
                , expect          = expectJson (Decode.map (\_ -> username) decodeApiStatusResult)
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send UpdateUserStatus req

disableUser : Model -> Username -> Cmd Msg
disableUser model username =
    let
        req =
            request
                { method          = "PUT"
                , headers         = []
                , url             = getUrl model ("/usermanagement/status/disable/" ++ username)
                , body            = emptyBody
                , expect          = expectJson (Decode.map (\_ -> username) decodeApiStatusResult)
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send UpdateUserStatus req

getRoleConf : Model -> Cmd Msg
getRoleConf model =
    let
        req =
            request
                { method          = "GET"
                , headers         = []
                , url             = getUrl model "/usermanagement/roles"
                , body            = emptyBody
                , expect          = expectJson decodeGetRoleApiResult
                , timeout         = Nothing
                , withCredentials = False
                }
    in
    send GetRoleConf req