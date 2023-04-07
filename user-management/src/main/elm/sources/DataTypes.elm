module DataTypes exposing (..)

import Dict exposing (Dict)
import Http exposing (Error)
import String exposing (toLower)
import Toasty
import Toasty.Defaults

type alias Roles = Dict String (List String)
type alias Users = Dict Username Authorization
type alias Username = String
type alias Password = String
type alias RoleConf = List Role

type alias AddUserForm =
    { user : User
    , password : String
    , isPreHashed : Bool
    }

type alias Role =
    { id: String
    , rights: List String
    }

type alias Authorization =
    { permissions : List String
    , custom: List String
    }

type alias User =
    { login : String
    , authz : List String
    , permissions : List String
    }

type Provider
  = File
  | Ldap
  | Radius
  | Unknown

toProvider: String -> Provider
toProvider str =
  case toLower str of
    "file"   -> File
    "ldap"   -> Ldap
    "radius" -> Radius
    _        -> Unknown

providerToString: Provider -> String
providerToString provider =
  case provider of
    File   -> "file"
    Ldap   -> "ldap"
    Radius -> "radius"
    _      -> "unknown provider"

type alias UsersConf =
    { digest : String
    , authenticationBackends: List String
    , users : List User
    }

type PanelMode
  = AddMode
  | EditMode User
  | Closed

type StateInput
    = InvalidUsername
    | ValidInputs

type alias Model =
    { contextPath : String
    , digest: String
    , users: Users
    , roles: Roles
    , rolesConf : RoleConf  -- from API
    , authorizations : Authorization
    , toasties : Toasty.Stack Toasty.Defaults.Toast
    , panelMode : PanelMode
    , password : String
    , login : String
    , isHashedPasswd : Bool
    , isValidInput : StateInput
    , authzToAddOnSave : List String
    , providers : List Provider
    , userForcePasswdInput : Bool
    , openDeleteModal : Bool
    }

type Msg
    = GetUserInfo (Result Error UsersConf)
    | GetRoleConf (Result Error RoleConf)
    | PostReloadUserInfo (Result Error String) -- also returns the updated list
    | SendReload -- ask for API call to reload user list
    | ComputeRoleCoverage (Result Error Authorization)
    | AddUser (Result Error String)
    | DeleteUser (Result Error String)
    | UpdateUser (Result Error String)
    | CallApi (Model -> Cmd Msg)
    | ActivePanelSettings User
    | ActivePanelAddUser
    | DeactivatePanel
    | Password String
    | Login String
    | AddRole String
    | RemoveRole User String
    | SubmitUpdatedInfos User
    | SubmitNewUser User
    | PreHashedPasswd Bool
    | AddPasswdAnyway
    | OpenDeleteModal String
    | CloseDeleteModal


      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)
    | Notification (Toasty.Msg Toasty.Defaults.Toast)
