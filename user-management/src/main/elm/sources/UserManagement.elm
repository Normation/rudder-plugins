module UserManagement exposing (processApiError, update)

import ApiCalls exposing (addUser, getRoleConf, getUsersConf, postReloadConf, updateUser)
import Browser
import DataTypes exposing (Model, Msg(..), PanelMode(..), StateInput(..), newUserToUser, userProviders)
import Dict exposing (fromList)
import Http exposing (..)
import Init exposing (createErrorNotification, createSuccessNotification, defaultConfig, init, subscriptions)
import String exposing (isEmpty)
import Toasty
import View exposing (view)
import List
import List.FlatMap

main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }

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
                            List.map (\x -> (x.login, x)) u.users
                        users = fromList recordUser
                        newPanelMode =
                            case model.panelMode of
                                EditMode user ->
                                    case Dict.get user.login users of
                                        Just u_ ->
                                            EditMode u_
                                        Nothing ->
                                            Closed
                                _ ->
                                    Closed
                        newModel =
                            { model | roleListOverride = u.roleListOverride, users = users, panelMode = newPanelMode, digest = u.digest, providers = (userProviders u.authenticationBackends)}

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
                ({model | panelMode = Closed, rolesToAddOnSave = [], userForcePasswdInput = False}, Cmd.none)
            else
                ({model | panelMode = AddMode, rolesToAddOnSave = [], userForcePasswdInput = False}, Cmd.none)
        ActivePanelSettings user ->
            case model.panelMode of
                EditMode u ->
                    if u.login == user.login then
                        ({model | panelMode = Closed, rolesToAddOnSave = [], userForcePasswdInput = False}, Cmd.none)
                    else
                        ({model |rolesToAddOnSave = [], password = "", panelMode = EditMode user, userForcePasswdInput = False }, Cmd.none)
                _          ->
                    ({model | panelMode = EditMode user}, Cmd.none)

        DeactivatePanel ->
            ({model | isValidInput = ValidInputs, panelMode = Closed, rolesToAddOnSave = [], password = "", userForcePasswdInput = False}, Cmd.none)

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
            ({model | rolesToAddOnSave = r :: model.rolesToAddOnSave}, Cmd.none)
        RemoveRole user r ->
            let
                -- remove role, and also authz that are associated to the role but not associated with any other remaining role
                newRoles = user.roles |> List.filter (\x -> r /= x) 
                newAuthz = 
                    -- keep authz if it is found in any authz of newRoles
                    -- keep authz if it's in custom authz of the user
                    let
                        allAuthz = Dict.toList model.roles |> List.FlatMap.flatMap (\(role, authz) -> if List.member role newRoles then authz else [])
                    in 
                        user.customRights ++ List.filter (\x ->
                            case model.roles |> Dict.get x of
                                Just _ -> True
                                Nothing -> 
                                    newRoles
                                    |> List.any (\y -> List.member y allAuthz)
                        ) user.authz
                newUser = {user | roles = newRoles, authz = newAuthz}
            in
            ({model | panelMode = EditMode newUser}, updateUser model user.login (DataTypes.AddUserForm newUser "" model.isHashedPasswd))
        Notification subMsg ->
            Toasty.update defaultConfig Notification subMsg model

        Password newPassword ->
            ({model | isValidInput = ValidInputs, password = newPassword}, Cmd.none)
        Login newLogin ->
            ({model | isValidInput = ValidInputs, login = newLogin}, Cmd.none)
        SubmitUpdatedInfos u ->
            ({model | rolesToAddOnSave = [], password = "", userForcePasswdInput = False, login = ""}, updateUser model u.login (DataTypes.AddUserForm { u | login = u.login} model.password model.isHashedPasswd))
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
               , rolesToAddOnSave     = []
               }
               , addUser model (DataTypes.AddUserForm (newUserToUser u) model.password model.isHashedPasswd)
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
