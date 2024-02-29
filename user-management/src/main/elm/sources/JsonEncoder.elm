module JsonEncoder exposing (..)

import DataTypes exposing (AddUserForm, NewUser, User)
import Json.Encode exposing (Value, bool, list, object, string)
import Dict

encodeUser: (User, String) -> Value
encodeUser (user, password) =
    object
    [ ("username", string user.login)
    , ("password", string password)
    , ("permissions", list (\s -> string s) (user.authz ++  user.roles))
    ]

encodeAddUser: AddUserForm -> Value
encodeAddUser userForm =
    object
    [ ("username", string userForm.user.login)
    , ("password", string userForm.password)
    , ("permissions", list (\s -> string s) (userForm.user.authz ++  userForm.user.roles))
    , ("isPreHashed", bool userForm.isPreHashed)
    , ("name", string userForm.user.name)
    , ("email", string userForm.user.email)
    , ("otherInfo", object (List.map (\(k, v) -> (k, string v)) (Dict.toList userForm.user.otherInfo)))
    ]
