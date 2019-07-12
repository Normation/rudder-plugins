module JsonEncoder exposing (..)

import DataTypes exposing (Authorization, User)
import Json.Encode exposing (Value, list, object, string)
import String exposing (split)

encodeAuthorization: Authorization -> Value
encodeAuthorization authorizations =
    object
    [ ("action", string "rolesCoverageOnRights")
    , ("role", list (\s -> string s) authorizations.roles)
    , ("authz", list (\s -> string s) authorizations.roles)
    ]

encodeUser: (User, String) -> Value
encodeUser (user, password) =
    object
    [ ("username", string user.login)
    , ("password", string password)
    , ("role", list (\s -> string s) (user.authz ++  user.role))
    ]