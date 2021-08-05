module UserManagement exposing (processApiError, update)

import ApiCalls exposing (addUser, computeRoleCoverage, getRoleConf, getUsersConf, postReloadConf, updateUser)
import Browser
import DataTypes exposing (Authorization, Model, Msg(..), PanelMode(..), StateInput(..), User, Username, Users)
import Dict exposing (fromList)
import Http exposing (..)
import Init exposing (createErrorNotification, createSuccessNotification, defaultConfig, init, subscriptions)
import List exposing (all, filter)
import String exposing (isEmpty)
import Toasty
import View exposing (view)

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
           Just (User username a.custom a.roles)
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
                        recordUser =
                            List.map (\x -> (x.login, (Authorization x.authz  x.role))) u.users
                        newModel =
                            { model | users = fromList recordUser, digest = u.digest}

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
                ({model | panelMode = Closed, authzToAddOnSave = []}, Cmd.none)
            else
                ({model | panelMode = AddMode, authzToAddOnSave = []}, Cmd.none)
        ActivePanelSettings user ->
            case model.panelMode of
                EditMode u ->
                    if u.login == user.login then
                        ({model | panelMode = Closed, authzToAddOnSave = []}, Cmd.none)
                    else
                        ({model |authzToAddOnSave = [], panelMode = EditMode user}, Cmd.none)
                _          ->
                    ({model | panelMode = EditMode user}, Cmd.none)

        DeactivatePanel ->
            ({model | isValidInput = ValidInputs, panelMode = Closed, authzToAddOnSave = []}, Cmd.none)

        ComputeRoleCoverage result ->
            case result of
                Ok _ ->
                    (model, computeRoleCoverage model model.authorizations)
                Err err ->
                    processApiError err model

        AddUser result ->
             case result of
                 Ok username ->
                     (model, postReloadConf model )
                         |> createSuccessNotification (username ++ " have been added")
                 Err err ->
                     processApiError err model

        DeleteUser result ->
             case result of
                  Ok deletedUser ->
                       ({model | panelMode = Closed}, postReloadConf model)
                           |> createSuccessNotification (deletedUser ++ " have been deleted")
                  Err err ->
                       processApiError err model

        UpdateUser result ->
             case result of
                  Ok username ->
                      (model, postReloadConf model)
                        |> createSuccessNotification (username ++ " have been modified")

                  Err err ->
                       processApiError err model

        AddRole r ->
            ({model | authzToAddOnSave = r :: model.authzToAddOnSave}, Cmd.none)
        RemoveRole user r ->
            let
                newRoles = filter (\x -> r /= x) user.role
                newAuthz = filter (\x -> r /= x) user.authz
                newUser = {user | role = newRoles, authz = newAuthz}
            in
            ({model | panelMode = EditMode newUser}, updateUser model user.login "" newUser)
        Notification subMsg ->
               Toasty.update defaultConfig Notification subMsg model

        Password newPassword ->
            ({model | isValidInput = ValidInputs, password = newPassword}, Cmd.none)
        Login newLogin ->
            ({model | isValidInput = ValidInputs, login = newLogin}, Cmd.none)
        SubmitUpdatedInfos u ->
            let
                newLogin =
                    if isEmpty model.login then
                        case model.panelMode of
                            EditMode user ->
                                user.login
                            _             ->
                                "" -- This case shouldn't happen since we must be in EditMod, it will invalidate the user in the file.
                    else
                        model.login
            in
            ({model | authzToAddOnSave = [], password = "", login = "", panelMode = Closed}, updateUser model u.login model.password { u | login = model.login})
        SubmitNewUser u  ->
            if (isEmpty u.login && isEmpty model.password) then
               ({model | isValidInput = InvalidInputs}, Cmd.none)
            else if (isEmpty u.login) then
               ({model | isValidInput = InvalidUsername}, Cmd.none)
            else if (isEmpty model.password) then
               ({model | isValidInput = InvalidPassword}, Cmd.none)
            else
               ({model | panelMode = Closed, login = "", password = "", hashedPasswd = True, isValidInput = ValidInputs, authzToAddOnSave = []}, addUser model u)

        PreHashedPasswd ->
            if model.hashedPasswd then
                ({model | password = "",hashedPasswd = False}, Cmd.none)
            else
                ({model | password = "", hashedPasswd = True}, Cmd.none)

processApiError : Error -> Model -> ( Model, Cmd Msg )
processApiError err model =
    let
        newModel =
            { model | digest = "", users = fromList []}
    in
    ( newModel, Cmd.none ) |> createErrorNotification "Error while trying to fetch settings." err
