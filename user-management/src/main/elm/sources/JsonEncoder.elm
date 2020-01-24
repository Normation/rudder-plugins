module JsonEncoder exposing (..)

import DataTypes exposing (Authorization, User)
import Json.Encode exposing (Value, bool, list, object, string)
import String

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

encodeAddUser: (User, String, Bool) -> Value
encodeAddUser (user, password, isPreHashed) =
    object
   [ ("username", string user.login)
    , ("password", string password)
    , ("role", list (\s -> string s) (user.authz ++  user.role))
    , ("isPreHashed", bool isPreHashed)
   ]
