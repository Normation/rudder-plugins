module DataTypes exposing (..)

------------------------------
-- MODEL --
------------------------------
-- An user: Login, List of rights,


import Dict exposing (Dict)
import Http exposing (Error)
import Toasty
import Toasty.Defaults

type alias Roles = Dict String (List String)
type alias Users = Dict Username Authorization
type alias Username = String
type alias Password = String
type alias RoleConf = List Role

type alias Role =
    { id: String
    , rights: List String
    }

type alias Authorization =
    { roles : List String
    , custom: List String
    }

type alias User =
    { login : String
    , authz : List String
    , role : List String
    }

type alias UsersConf =
    { digest : String
    , users : List User
    }

type PanelMode
  = AddMode
  | EditMode User
  | Closed

type StateInput
    = InvalidPassword
    | InvalidUsername
    | InvalidInputs
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
    , hashedPasswd : Bool
    , isValidInput : StateInput
    , authzToAddOnSave : List String
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
    | PreHashedPasswd

      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)
    | Notification (Toasty.Msg Toasty.Defaults.Toast)