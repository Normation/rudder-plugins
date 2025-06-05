module JsonDecoders exposing (..)

import DataTypes exposing (ApiMsg, Settings, User, UserList, Username)
import Json.Decode exposing (Decoder, andThen, at, bool, fail, field, index, list, map, map2, map4, string, succeed)
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



------------------------------
-- WorkflowSettings         --
------------------------------


decodeWorkflowSettings : Decoder Settings
decodeWorkflowSettings =
    at [ "data" ]
        (field "settings"
            (map4 Settings
                (field "enable_change_request" bool)
                (field "enable_self_validation" bool)
                (field "enable_self_deployment" bool)
                (field "enable_validate_all" bool)
            )
        )
