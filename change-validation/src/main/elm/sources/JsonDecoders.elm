module JsonDecoders exposing (..)

import DataTypes exposing (ApiMsg, User, UserList, Username)
import Json.Decode exposing (Decoder, at, bool, field, list, string, succeed)
import Json.Decode.Pipeline exposing (required)


decodeUser : Decoder User
decodeUser =
    succeed User
        |> required "username" string
        |> required "isValidated" bool
        |> required "userExists" bool


decodeUserList : Decoder UserList
decodeUserList =
    at [ "data" ] (list decodeUser)


decodeApiDeleteUsername : Decoder Username
decodeApiDeleteUsername =
    at [ "data" ] string


decodeSetting : String -> Decoder Bool
decodeSetting fieldName =
    let
        decSetting =
            field "settings" (field fieldName bool)
    in
    at [ "data" ] decSetting
