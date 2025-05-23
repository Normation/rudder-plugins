module JsonEncoders exposing (..)

import DataTypes exposing (User, Username)
import Json.Encode exposing (Value, bool, list, object, string)


username : Username -> ( String, Value )
username value =
    ( "username", string value )


isValidated : Bool -> ( String, Value )
isValidated value =
    ( "isValidated", bool value )


isInFile : Bool -> ( String, Value )
isInFile value =
    ( "userExists", bool value )


encodeUser : User -> Value
encodeUser userInfo =
    object
        [ username userInfo.username
        , isValidated userInfo.isValidated
        , isInFile userInfo.isInFile
        ]


encodeUsernames : List Username -> Value
encodeUsernames usernames =
    object
        [ ( "action", string "addValidatedUsersList" )
        , ( "validatedUsers", list (\s -> string s) usernames )
        ]


encodeSetting : Bool -> Value
encodeSetting value =
    object [ ( "value", bool value ) ]
