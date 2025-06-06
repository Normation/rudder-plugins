module JsonUtils exposing (..)

import DataTypes exposing (ApiMsg, Settings, User, UserList, Username)
import Json.Decode exposing (Decoder, at, bool, field, map4)
import Json.Encode exposing (Value, object)


decodeSetting : String -> Decoder Bool
decodeSetting fieldName =
    let
        decSetting =
            field "settings" (field fieldName bool)
    in
    at [ "data" ] decSetting


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


encodeSetting : Bool -> Value
encodeSetting value =
    object [ ( "value", Json.Encode.bool value ) ]
