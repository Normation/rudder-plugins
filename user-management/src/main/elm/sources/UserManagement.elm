port module UserManagement exposing (processApiError, update)

import ApiCalls exposing (addUser, computeRoleCoverage, getRoleConf, getUsersConf, postReloadConf, updateUser)
import Browser
import DataTypes exposing (Authorization, EditMod(..), Model, Msg(..), User, Username, Users)
import Dict exposing (fromList)
import Http exposing (..)
import Init exposing (createErrorNotification, defaultConfig, init, subscriptions)
import List exposing (filter)
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
                    ( { model | editMod = Off}
                    , getUsersConf model
                    )

                Err err ->
                    processApiError err model

        SendReload ->
            ( model
            , postReloadConf model
            )

        ToastyMsg subMsg ->
            Toasty.update defaultConfig ToastyMsg subMsg model

        ActivePanelAddUser ->
            if model.addMod == On then
                ({model | addMod = Off}, Cmd.none)
            else
                ({model | addMod = On, editMod = Off}, Cmd.none)
        ActivePanelSettings username ->
            ({model | addMod = Off, editMod = On, userFocusOn = username}, Cmd.none)

        DeactivatePanel ->
            ({model | addMod = Off, editMod = Off, userFocusOn = { login = "", authz = [], role = []}}, Cmd.none)


        ComputeRoleCoverage result ->
            case result of
                Ok _ ->
                    ( model
                    , computeRoleCoverage model model.authorizations
                    )
                Err err ->
                    processApiError err model

        AddUser result ->
             case result of
                 Ok _ ->
                     ( model, postReloadConf model )
                 Err err ->
                     processApiError err model

        DeleteUser result ->
             case result of
                  Ok deletedUser ->
                       (model, postReloadConf model)
                  Err err ->
                       processApiError err model

        UpdateUser result ->
             case result of
                  Ok _ ->
                      (model, postReloadConf model)

                  Err err ->
                       processApiError err model

        AddRole user r ->
                (model, updateUser model user.login "" {user | role = r :: user.role })
        RemoveRole user r ->
                (model, updateUser model user.login "" {user | role = filter (\x -> r /= x) user.role, authz = filter (\x -> r /= x) user.authz })
        Notification subMsg ->
               Toasty.update defaultConfig Notification subMsg model

        ChangeFocusOn userToFocus ->
            ({model | userFocusOn = userToFocus}, Cmd.none)
        Password newPassword ->
            ({model | password = newPassword}, Cmd.none)
        Login newLogin ->
            ({model | login = newLogin}, Cmd.none)
        SubmitUpdatedInfos u ->
            let
                newLogin =
                    if isEmpty u.login then
                        model.userFocusOn.login
                    else
                        u.login
            in
            ({model | password = "", login = "", userFocusOn = {login = newLogin, authz =[], role = []}}, updateUser model u.login model.password { u | login = model.login })
        SubmitNewUser u password ->
            ({model | addMod = Off}, addUser model u)
        PreHashedPasswd ->
            ({model | hashedPasswd = True, clearPasswd = False}, Cmd.none)
        ClearPasswd ->
            ({model | clearPasswd = True, hashedPasswd = False}, Cmd.none)


processApiError : Error -> Model -> ( Model, Cmd Msg )
processApiError err model =
    let
        newModel =
            { model | digest = "", users = fromList []}
    in
    ( newModel, Cmd.none ) |> createErrorNotification "Error while trying to fetch settings." err
