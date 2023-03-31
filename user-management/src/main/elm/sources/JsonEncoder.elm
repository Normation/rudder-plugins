module JsonEncoder exposing (..)

import DataTypes exposing (AddUserForm, Authorization, User)
import Json.Encode exposing (Value, bool, list, object, string)
import String

encodeAuthorization: Authorization -> Value
encodeAuthorization authorizations =
    object
    [ ("action", string "rolesCoverageOnRights")
    , ("permissions", list (\s -> string s) authorizations.permissions)
    , ("authz", list (\s -> string s) authorizations.permissions)
    ]

encodeUser: (User, String) -> Value
encodeUser (user, password) =
    object
    [ ("username", string user.login)
    , ("password", string password)
    , ("permissions", list (\s -> string s) (user.authz ++  user.permissions))
    ]

encodeAddUser: AddUserForm -> Value
encodeAddUser userForm =
    object
    [ ("username", string userForm.user.login)
    , ("password", string userForm.password)
    , ("permissions", list (\s -> string s) (userForm.user.authz ++  userForm.user.permissions))
    , ("isPreHashed", bool userForm.isPreHashed)
    ]
