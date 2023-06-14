module ApiCalls exposing (..)

------------------------------
-- API --
------------------------------
-- API call to get the category tree


import DataTypes exposing (AddUserForm, Authorization, Model, Msg(..), User)
import Http exposing (emptyBody, expectJson, jsonBody, request, get, post)
import JsonDecoder exposing (decodeApiAddUserResult, decodeApiCurrentUsersConf, decodeApiDeleteUserResult, decodeApiReloadResult, decodeApiRoleCoverage, decodeApiUpdateUserResult, decodeGetRoleApiResult)
import JsonEncoder exposing (encodeAddUser, encodeAuthorization)

getUrl: DataTypes.Model -> String -> String
getUrl m url =
  m.contextPath ++ "/secure/api" ++ url

getUsersConf : Model -> Cmd Msg
getUsersConf model =
    let
        req =
            get
                { url    = getUrl model "/usermanagement/users"
                , expect = expectJson GetUserInfo decodeApiCurrentUsersConf
                }
    in
      req


postReloadConf : Model -> Cmd Msg
postReloadConf model =
    let
        req =
            post
                {  url   = getUrl model "/usermanagement/users/reload"
                , body   = emptyBody
                , expect = expectJson PostReloadUserInfo decodeApiReloadResult
                }
    in
      req

computeRoleCoverage : Model -> Authorization -> Cmd Msg
computeRoleCoverage model authorizations =
    let
        req =
            post
                { url    = getUrl model "/usermanagement/coverage"
                , body   = jsonBody (encodeAuthorization authorizations)
                , expect = expectJson ComputeRoleCoverage decodeApiRoleCoverage
                }
    in
      req

addUser : Model -> AddUserForm -> Cmd Msg
addUser model userForm =
    let
        req =
            post
                { url    = getUrl model "/usermanagement"
                , body   = jsonBody (encodeAddUser userForm)
                , expect = expectJson AddUser decodeApiAddUserResult
                }
    in
     req

deleteUser : String -> Model -> Cmd Msg
deleteUser  username model =
    let
        req =
            request
                { method  = "DELETE"
                , headers = []
                , url     = getUrl model ("/usermanagement/" ++ username)
                , body    = emptyBody
                , expect  = expectJson  DeleteUser decodeApiDeleteUserResult
                , timeout = Nothing
                , tracker = Nothing
                }
    in
      req

updateUser : Model -> String -> AddUserForm -> Cmd Msg
updateUser model toUpdate userForm =
    let
        req =
            post
                { url    = getUrl model ("/usermanagement/update/" ++ toUpdate)
                , body   = jsonBody (encodeAddUser userForm)
                , expect = expectJson UpdateUser decodeApiUpdateUserResult
                }
    in
      req

getRoleConf : Model -> Cmd Msg
getRoleConf model =
    let
        req =
            get
                { url    = getUrl model "/usermanagement/roles"
                , expect = expectJson GetRoleConf decodeGetRoleApiResult
                }
    in
      req