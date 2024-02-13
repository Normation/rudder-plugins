module DataTypes exposing (..)

import Dict exposing (Dict)
import Http exposing (Error)
import String exposing (toLower)
import Toasty
import Toasty.Defaults

type alias Roles = Dict String (List String)
type alias Users = Dict Username User
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

type alias User =
    { login : String
    , authz : List String
    , roles : List String
    , rolesCoverage : List String
    , customRights : List String
    }

type alias NewUser = 
    { login : String
    , authz : List String
    , roles : List String
    }

-- does the configured list of provider allows to change the list of user roles, and how (extend only, or override?)
-- Note: until rudder core is updated to give us the info, we can only guess based on provider list
type RoleListOverride
  = None
  | Extend
  | Override

-- we want to filter out the "rootadmin" account (and perhaps other in the future). All back-end provided
-- provider list should be filtered by that method

userProviders: List String -> List String
userProviders providers =
  List.filter (\p -> p /= "rootAdmin") providers

takeFirstExtProvider: List String -> Maybe String
takeFirstExtProvider providers =
    List.head (List.filter (\p -> p /= "file" && p /= "rootAdmin") providers)

newUserToUser : NewUser -> User
newUserToUser {login, authz, roles} = {login = login, authz = authz, roles = roles, rolesCoverage = [], customRights = []}

type alias UsersConf =
    { digest : String
    , roleListOverride: RoleListOverride
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
    , roleListOverride: RoleListOverride
    , toasties : Toasty.Stack Toasty.Defaults.Toast
    , panelMode : PanelMode
    , password : String
    , login : String
    , isHashedPasswd : Bool
    , isValidInput : StateInput
    , rolesToAddOnSave : List String --TODO: it's roles and not "authz" here
    , providers : List String
    , userForcePasswdInput : Bool
    , openDeleteModal : Bool
    }

type Msg
    = GetUserInfo (Result Error UsersConf)
    | GetRoleConf (Result Error RoleConf)
    | PostReloadUserInfo (Result Error String) -- also returns the updated list
    | SendReload -- ask for API call to reload user list
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
    | SubmitNewUser NewUser
    | PreHashedPasswd Bool
    | AddPasswdAnyway
    | OpenDeleteModal String
    | CloseDeleteModal


      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)
    | Notification (Toasty.Msg Toasty.Defaults.Toast)
