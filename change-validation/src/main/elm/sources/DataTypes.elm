module DataTypes exposing (..)

import Http exposing (Error)
import Result exposing (Result)



------------------------------
-- WORKFLOW USERS --
------------------------------


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


type alias WorkflowUsersModel =
    { contextPath : String
    , editMod : EditMod
    , hasWriteRights : Bool
    , workflowUsersView : WorkflowUsersView
    , validateAllView : ValidateAllView
    }


type alias WorkflowUsersForm =
    { users : UserList
    , validatedUsers : UserList
    , unvalidatedUsers : UserList
    , rightChecked : UserList
    , leftChecked : UserList
    , hasMoved : UserList -- To track updates
    }


type WorkflowUsersView
    = WorkflowUsersInitView
    | WorkflowUsers WorkflowUsersForm


type UserListField
    = Users
    | ValidatedUsers
    | UnvalidatedUsers
    | RightChecked
    | LeftChecked
    | HasMoved


type ValidateAllView
    = ValidateAllInitView
    | ValidateAll { initValues : FormState, formValues : FormState }


type alias FormState =
    { validateAll : Bool -- "enable_validate_all" setting
    }



------------------------------
-- WORKFLOW SETTINGS --
------------------------------


type alias WorkflowSettingsModel =
    { contextPath : String
    , hasWriteRights : Bool
    , viewState : ViewState
    }


type ViewState
    = InitWorkflowSettingsView
    | WorkflowSettingsView WorkflowSettingsForm


type alias WorkflowSettingsForm =
    { initSettings : WorkflowSettings
    , formSettings : WorkflowSettings
    }


type alias WorkflowSettings =
    { workflowEnabled : Bool
    , selfValidation : Bool
    , selfDeployment : Bool
    }


type WorkflowSetting
    = WorkflowEnabled
    | SelfValidation
    | SelfDeployment



------------------------------
-- SUPERVISED TARGETS --
------------------------------


type alias Target =
    { id : String -- id
    , name : String -- display name of the rule target
    , description : String -- description
    , supervised : Bool -- do you want to validate CR targeting that rule target
    }


type alias Category =
    { name : String -- name of the category
    , categories : Subcategories -- sub-categories
    , targets : List Target -- targets in category
    }


type Subcategories
    = Subcategories (List Category) -- needed because no recursive type alias support


type alias SupervisedTargetsModel =
    { contextPath : String
    , allTargets : Category -- from API
    }


type alias Settings =
    { workflowEnabled : Bool
    , selfValidation : Bool
    , selfDeployment : Bool
    , workflowValidateAll : Bool
    }



--------------------------------
-- CHANGE VALIDATION SETTINGS --
--------------------------------


type alias Model =
    { contextPath : String
    , workflowUsersModel : WorkflowUsersModel
    , supervisedTargetsModel : SupervisedTargetsModel
    , workflowSettingsModel : WorkflowSettingsModel
    }


type Msg
    = WorkflowUsersMsg WorkflowUsersMsg
    | SupervisedTargetsMsg SupervisedTargetsMsg
    | WorkflowSettingsMsg WorkflowSettingsMsg
      -- GET all workflow settings
    | GetAllWorkflowSettings (Result Error Settings)
    | CopyToClipboard String


type WorkflowUsersMsg
    = {--Messages for the "Workflow Users" table --}
      {--API CALLS --}
      GetUsers (Result Error UserList)
    | RemoveUser (Result Error Username)
    | SaveWorkflowUsers (Result Error UserList)
    | CallApi (WorkflowUsersModel -> Cmd Msg)
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
    | SaveValidateAllSetting (Result Error Bool)
      {--VIEW UPDATE--}
    | ChangeValidateAllSetting Bool


type SupervisedTargetsMsg
    = GetTargets (Result Error Category)
    | SaveTargets (Result Error String) -- here the string is just the status message
    | SendSave
    | UpdateTarget Target


type WorkflowSettingsMsg
    = {--Messages for the change-validation settings list--}
      -- SET workflow settings API call
      SaveWorkflowSetting WorkflowSetting (Result Error Bool)
      -- Edit form in view
    | ToggleSetting WorkflowSetting
    | SaveSettings
