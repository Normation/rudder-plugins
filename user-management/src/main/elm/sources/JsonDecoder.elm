module JsonDecoder exposing (..)

import DataTypes exposing (Authorization, Role, RoleConf, User, UsersConf)
import Json.Decode as D exposing (Decoder)
import Json.Decode.Pipeline exposing (required)

decodeApiReloadResult : Decoder String
decodeApiReloadResult =
    D.at [ "data" ] decodeReload

decodeReload : Decoder String
decodeReload =
    D.at [ "reload" ] (D.at [ "status" ] D.string)

-- decode the JSON answer from a "get" API call - only "data" field content is interesting

decodeApiCurrentUsersConf : Decoder UsersConf
decodeApiCurrentUsersConf =
    D.at [ "data" ] decodeCurrentUsersConf

decodeCurrentUsersConf : Decoder UsersConf
decodeCurrentUsersConf =
    D.succeed UsersConf
        |> required "digest" D.string
        |> required "authenticationBackends" (D.list <| D.string)
        |> required "users" (D.list <| decodeUser)

decodeUser : Decoder User
decodeUser =
    D.succeed User
        |> required "login" D.string
        |> required "authz" (D.list <| D.string)
        |> required "role" (D.list <| D.string)

decodeApiRoleCoverage : Decoder Authorization
decodeApiRoleCoverage =
    D.at [ "data" ] decodeRoleCoverage

decodeRoleCoverage : Decoder Authorization
decodeRoleCoverage =
    let
        response =
            D.succeed Authorization
                |> required "role" (D.list <| D.string)
                |> required "authz" (D.list <| D.string)
    in
        D.at [ "coverage" ] response


decodeApiAddUserResult : Decoder String
decodeApiAddUserResult =
    D.at [ "data" ] decodeAddUser

decodeAddUser : Decoder String
decodeAddUser =
    D.at [ "addedUser" ] (D.at [ "username" ] D.string)

decodeApiUpdateUserResult : Decoder String
decodeApiUpdateUserResult =
    D.at [ "data" ] decodeUpdateUser

decodeUpdateUser : Decoder String
decodeUpdateUser =
    D.at [ "updatedUser" ] (D.at [ "username" ] D.string)

decodeApiDeleteUserResult : Decoder String
decodeApiDeleteUserResult =
    D.at [ "data" ] decodeDeletedUser

decodeDeletedUser : Decoder String
decodeDeletedUser =
    D.at [ "deletedUser" ] (D.at [ "username" ] D.string)

decodeRole : Decoder Role
decodeRole =
    D.succeed Role
        |> required "id" D.string
        |> required "rights" (D.list <| D.string)

decodeGetRoleApiResult : Decoder RoleConf
decodeGetRoleApiResult =
    D.at [ "data" ] (D.list <| decodeRole)


