module JsonDecoders exposing (..)

import DataTypes exposing (ApiMsg, PluginInfo, Settings, User, UserList, Username)
import Json.Decode exposing (Decoder, at, bool, field, index, list, map, map2, map4, string, succeed)
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


changeValidationPluginId : String
changeValidationPluginId =
    "com.normation.plugins.changevalidation.ChangeValidationPluginDef"


decodePluginInfo : Decoder PluginInfo
decodePluginInfo =
    map2
        PluginInfo
        (field "id" string)
        (field "status" bool)


findChangeValidationStatus : List PluginInfo -> Bool
findChangeValidationStatus ls =
    case List.head (List.filter (\elt -> elt.pluginId == changeValidationPluginId) ls) of
        Just elt ->
            elt.pluginStatus

        Nothing ->
            False


decodePluginStatus : Decoder Bool
decodePluginStatus =
    at [ "data" ]
        (field "plugins"
            (index 0
                (map findChangeValidationStatus (field "details" (list decodePluginInfo)))
            )
        )


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
