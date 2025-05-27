module DataTypes exposing (..)

import Http exposing (Error)
import List exposing (map)
import Result exposing (Result)


type alias UserList =
    List User


type alias Username =
    String


type alias ApiMsg =
    String


type EditMod
    = On
    | Off



{--
  Left  : validated users
  Right : unvalidated users
--}


type ColPos
    = Left
    | Right


type alias User =
    { username : Username
    , isValidated : Bool
    , isInFile : Bool
    }


type alias Model =
    { contextPath : String
    , users : UserList
    , validatedUsers : UserList
    , unvalidatedUsers : UserList
    , rightChecked : List User
    , leftChecked : List User
    , hasMoved : List User -- To track updates
    , editMod : EditMod
    , hasWriteRights : Bool
    , viewState : ViewState
    }


type ViewState
    = NoView
    | Form { initValues : FormState, formValues : FormState }


type alias FormState =
    { validateAll : Bool -- "enable_validate_all" setting
    }


getUsernames : UserList -> List Username
getUsernames users =
    map .username users


type Msg
    = {--Messages for the "Workflow Users" table --}
      {--API CALLS --}
      GetUsers (Result Error UserList)
    | RemoveUser (Result Error Username)
    | SaveWorkflow (Result Error UserList)
    | CallApi (Model -> Cmd Msg)
      {--TABLE MANAGE CONTENT --}
    | LeftToRight
    | RightToLeft
    | AddLeftChecked User Bool
    | AddRightChecked User Bool
    | CheckAll ColPos Bool
      {--MOD MANAGEMENT --}
    | SwitchMode
    | ExitEditMod
      {--Messages for the "Validate all changes" checkbox and button --}
      {--API CALLS--}
    | GetValidateAllSetting (Result Error Bool)
    | SaveValidateAllSetting (Result Error Bool)
      {--VIEW UPDATE--}
    | ChangeValidateAllSetting Bool
