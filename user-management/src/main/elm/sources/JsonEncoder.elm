module JsonEncoder exposing (..)

import DataTypes exposing (AddUserForm, Authorization, User)
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

encodeAddUser: AddUserForm -> Value
encodeAddUser userForm =
    object
    [ ("username", string userForm.user.login)
    , ("password", string userForm.password)
    , ("role", list (\s -> string s) (userForm.user.authz ++  userForm.user.role))
    , ("isPreHashed", bool userForm.isPreHashed)
    ]
