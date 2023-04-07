module UserManagement exposing (processApiError, update)

import ApiCalls exposing (addUser, computeRoleCoverage, getRoleConf, getUsersConf, postReloadConf, updateUser)
import Browser
import DataTypes exposing (Authorization, Model, Msg(..), PanelMode(..), Provider(..), StateInput(..), User, Username, Users, toProvider)
import Dict exposing (fromList)
import Http exposing (..)
import Init exposing (createErrorNotification, createSuccessNotification, defaultConfig, init, subscriptions)
import String exposing (isEmpty)
import Toasty
import View exposing (view)
import List

main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }

getUser: Username -> Users -> Maybe User
getUser username users =
    case (Dict.get username users) of
        Just a ->
           Just (User username a.custom a.permissions)
        Nothing ->
            Nothing

update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        CallApi call ->
            (model, call model)
        {--Api Calls message --}
        GetUserInfo result ->
            case result of
                Ok u ->
                    let
                        knownProviders = List.filter (\p -> p /= Unknown) (List.map toProvider u.authenticationBackends)
                        recordUser =
                            List.map (\x -> (x.login, (Authorization x.authz  x.permissions))) u.users
                        newModel =
                            { model | users = fromList recordUser, digest = u.digest, providers = knownProviders}

                    in
                    ( newModel, getRoleConf model )

                Err err ->
                    processApiError err model
        GetRoleConf result ->
            case result of
                Ok roles ->
                    let
                        recordRole =
                            List.map (\x -> (x.id, x.rights)) roles
                        newModel =
                            { model | rolesConf = roles , roles = fromList recordRole}
                    in
                    ( newModel, Cmd.none )

                Err err ->
                    processApiError err model

        PostReloadUserInfo result ->
            case result of
                Ok _ ->
                    (model, getUsersConf model)

                Err err ->
                    processApiError err model

        SendReload ->
            (model, postReloadConf model)
                |> createSuccessNotification "User configuration's file have been reloaded"

        ToastyMsg subMsg ->
            Toasty.update defaultConfig ToastyMsg subMsg model

        ActivePanelAddUser ->
            if model.panelMode == AddMode then
                ({model | panelMode = Closed, authzToAddOnSave = [], userForcePasswdInput = False}, Cmd.none)
            else
                ({model | panelMode = AddMode, authzToAddOnSave = [], userForcePasswdInput = False}, Cmd.none)
        ActivePanelSettings user ->
            case model.panelMode of
                EditMode u ->
                    if u.login == user.login then
                        ({model | panelMode = Closed, authzToAddOnSave = [], userForcePasswdInput = False}, Cmd.none)
                    else
                        ({model |authzToAddOnSave = [], password = "", panelMode = EditMode user, userForcePasswdInput = False }, Cmd.none)
                _          ->
                    ({model | panelMode = EditMode user}, Cmd.none)

        DeactivatePanel ->
            ({model | isValidInput = ValidInputs, panelMode = Closed, authzToAddOnSave = [], password = "", userForcePasswdInput = False}, Cmd.none)

        ComputeRoleCoverage result ->
            case result of
                Ok _ ->
                    (model, computeRoleCoverage model model.authorizations)
                Err err ->
                    processApiError err model

        AddUser result ->
             case result of
                 Ok username ->
                     (model, getUsersConf model )
                         |> createSuccessNotification (username ++ " have been added")
                 Err err ->
                     processApiError err model

        DeleteUser result ->
             case result of
                  Ok deletedUser ->
                       ({model | panelMode = Closed, login = "", openDeleteModal = False}, getUsersConf model)
                           |> createSuccessNotification (deletedUser ++ " have been deleted")
                  Err err ->
                       processApiError err model

        UpdateUser result ->
             case result of
                  Ok username ->
                      (model, getUsersConf model)
                        |> createSuccessNotification (username ++ " have been modified")

                  Err err ->
                       processApiError err model

        AddRole r ->
            ({model | authzToAddOnSave = r :: model.authzToAddOnSave}, Cmd.none)
        RemoveRole user r ->
            let
                newRoles = List.filter (\x -> r /= x) user.permissions
                newAuthz = List.filter (\x -> r /= x) user.authz
                newUser = {user | permissions = newRoles, authz = newAuthz}
            in
            ({model | panelMode = EditMode newUser}, updateUser model user.login (DataTypes.AddUserForm newUser "" model.isHashedPasswd))
        Notification subMsg ->
            Toasty.update defaultConfig Notification subMsg model

        Password newPassword ->
            ({model | isValidInput = ValidInputs, password = newPassword}, Cmd.none)
        Login newLogin ->
            ({model | isValidInput = ValidInputs, login = newLogin}, Cmd.none)
        SubmitUpdatedInfos u ->
            ({model | authzToAddOnSave = [], password = "", userForcePasswdInput = False, login = "", panelMode = Closed}, updateUser model u.login (DataTypes.AddUserForm { u | login = model.login} model.password model.isHashedPasswd))
        SubmitNewUser u  ->
            if(isEmpty u.login) then
              ({model | isValidInput = InvalidUsername}, Cmd.none)
            else
              ({ model |
                 panelMode            = Closed
               , login                = ""
               , password             = ""
               , userForcePasswdInput = False
               , isHashedPasswd         = True
               , isValidInput         = ValidInputs
               , authzToAddOnSave     = []
               }
               , addUser model (DataTypes.AddUserForm u model.password model.isHashedPasswd)
               )

        PreHashedPasswd bool ->
            ({model | password = "",isHashedPasswd = bool}, Cmd.none)
        AddPasswdAnyway ->
            if (model.userForcePasswdInput) then
                ({model | userForcePasswdInput = False, password = ""}, Cmd.none)
            else
                ({model | userForcePasswdInput = True}, Cmd.none)
        OpenDeleteModal username ->
            ({model | openDeleteModal = True, login = username}, Cmd.none)
        CloseDeleteModal ->
            ({model | openDeleteModal = False, login = ""}, Cmd.none)

processApiError : Error -> Model -> ( Model, Cmd Msg )
processApiError err model =
    let
        newModel =
            { model | digest = "", users = fromList []}
    in
    ( newModel, Cmd.none ) |> createErrorNotification "Error while trying to fetch settings." err
