module View exposing (..)

------------------------------
-- VIEW --
------------------------------


import ApiCalls exposing (deleteUser)

import DataTypes exposing (PanelMode(..), Model, Msg(..), RoleConf, Roles, StateInput(..), User, Username, Users, UsersConf)
import Dict exposing (keys)
import Html exposing (Html, a, br, button, div, h2, h3, h4, h5, i, input, p, span, text)
import Html.Attributes exposing (attribute, class, disabled, href, id, placeholder, required, type_, value)
import Html.Events exposing (onClick, onInput)
import Init exposing (defaultConfig)
import List exposing (all, any, filter, map)
import String exposing (isEmpty)
import Toasty
import Toasty.Defaults

view : Model -> Html Msg
view model =
    let
        content =
           if (isEmpty model.digest) || (List.isEmpty (keys model.users)) then
               text "Waiting for data from server..."

           else
               displayUsersConf model model.users
    in
    div []
        [ content
        , div [ class "toasties" ] [ Toasty.view defaultConfig Toasty.Defaults.view ToastyMsg model.toasties ]
        ]

hashPasswordMenu : Model -> Html Msg
hashPasswordMenu model =
    let
        hashPasswdIsActivate =
            if (model.hashedPasswd == True) then
                "active"
            else
                ""
        clearPasswdIsActivate =
             if (model.hashedPasswd == False) then
                 "active"
             else
                 ""
    in
        div [class "btn-group", attribute "role" "group"]
        [
              a [class ("btn btn-default " ++ hashPasswdIsActivate), onClick PreHashedPasswd][text "Enter pre-hashed value"]
            , a [class ("btn btn-default " ++ clearPasswdIsActivate), onClick PreHashedPasswd][text "Password to hash"]
        ]

displayRightPanelAddUser : Model -> Html Msg
displayRightPanelAddUser model =
   let
       phMsg = if model.hashedPasswd then "New hashed password" else "This password will be hashed and then stored"
       isHidden = if model.hashedPasswd then "text" else "password"
       emptyUsername =
           case model.panelMode of
               AddMode ->
                   if (model.isValidInput == InvalidUsername || model.isValidInput == InvalidInputs) then "invalid-username" else "valid-input"
               _       ->
                   "valid-input"

       emptyPassword =
           case model.panelMode of
               AddMode ->
                   if (model.isValidInput == InvalidPassword || model.isValidInput == InvalidInputs) then "invalid-password" else "valid-input"
               _       ->
                   "valid-input"
   in
   div [class "panel-wrap"]
   [
       div [class "panel"]
       [
             button [class "btn btn-sm btn-outline-secondary", onClick DeactivatePanel][text "Close"]
           , div[class "card-header"][h2 [class "title-username"] [text "Create User"]]
           , div []
           [
                 input [id emptyUsername, class "form-control username-input", type_ "text", placeholder "Username", onInput Login, required True] []
               , hashPasswordMenu model
               , input [id emptyPassword, class "form-control", type_ isHidden, placeholder phMsg, onInput Password, value model.password , attribute "autocomplete" "new-password", required True] []
               , button [class "btn btn-sm btn-success btn-save", onClick (SubmitNewUser (User model.login [] []))] [text "Save"]
           ]
       ]
   ]

getDiffRoles : Roles -> List String -> List String
getDiffRoles total sample =
    let
        t =  keys total
    in
        (filter (\r -> (all (\r2 -> r /= r2) sample)) t)

displayDropdownRoleList : List String -> Html Msg
displayDropdownRoleList roles =
    let
        tokens = List.map (\r -> a [href "#", onClick (AddRole r)][text r]) roles
    in
    div [class "dropdown-content"] tokens


displayRightPanel : Model -> Html Msg
displayRightPanel model =
    let
        user = case model.panelMode of
            EditMode u -> u
            _ -> User "" [] []
        availableRoles = getDiffRoles model.roles (user.authz ++ model.authzToAddOnSave)
        addBtn =
            if List.isEmpty availableRoles then
                button [id "addBtn-disabled", class "addBtn", disabled True][i [class "fa fa-plus"][]]
            else
                button [class "addBtn"][i [class "fa fa-plus"][]]
        isHidden = if model.hashedPasswd then "text" else "password"
    in
    div [class "panel-wrap"]
    [
        div [class "panel"]
        [
           div[class "panel-header"]
           [
                h2 [class "title-username"] [text user.login]
              , button [class "btn btn-sm btn-outline-secondary", onClick DeactivatePanel][text "Close"]
           ]
           , input [class "form-control username-input", type_ "text", placeholder "New Username", onInput Login] []
           , hashPasswordMenu model
           , input [class "form-control", type_ isHidden, placeholder "New Password", onInput Password, attribute "autocomplete" "new-password", value model.password ] []
           , h4 [class "role-title"][text "Roles"]
           , div [class "role-management-wrapper"]
           [
                  div [id "input-role"][(displayAddAuth model user)]
                , div [class "dropdown"]
                [
                      addBtn
                    , (displayDropdownRoleList availableRoles)
                ]
           ]
           , button [class "btn btn-sm btn-danger btn-delete", onClick (CallApi ( deleteUser user.login))] [text "Delete"]
           , button [class "btn btn-sm btn-success btn-save", onClick (SubmitUpdatedInfos {user | role = user.role ++ model.authzToAddOnSave})] [text "Save"]
        ]
    ]

displayUsersConf : Model -> Users -> Html Msg
displayUsersConf model u =
    let
        users =
            (map (\(name, rights) -> (User name rights.custom rights.roles)) (Dict.toList u)) |> List.map (\user -> displayUser user)
        newUserMenu =
            if model.panelMode == AddMode then
                displayRightPanelAddUser model
            else
                div [] []
        panel =
            case model.panelMode of
                EditMode user -> displayRightPanel model
                _             -> div [][]
    in
    div [ class "row" ]
        [
            div [class "header-plugin"]
            [
                  button [class "btn btn-sm btn-success new-icon btn-add", onClick ActivePanelAddUser][text "Create"]
                , button [class "btn btn-box-tool btn-blue btn-sm btn-reload", onClick SendReload]
                [
                      text "Reload"
                    , span [id "reloadBtn", class "fa fa-refresh"][]
                ]
                , newUserMenu
                , panel
            ]
            , div [ class "col-xs-4" ]
            [
                p [ class "callout-fade callout-info" ]
                [
                      div [ class "marker marker" ] [ span [ class "glyphicon glyphicon-info-sign" ] [] ]
                    , text ("Password encoder: " ++ model.digest)
                ]
            ]
            , div [ class "col-xs-12 user-list" ][ div [ class "row " ] users ]
        ]

displayUser : User -> Html Msg
displayUser user =
    let
        starAdmin = if (any (\a -> a == "administrator") user.authz ) then  i [class "fa fa-star admin-star"][] else div[][]
    in
    div [class "user-card-wrapper", onClick (ActivePanelSettings user)]
    [
        div [class "user-card"]
        [
            div [class "user-card-inner"]
            [
                  starAdmin
                , h3 [id "name"][text user.login]
            ]
            , displayAuth  user
        ]
    ]

displayAddAuth : Model -> User -> Html Msg
displayAddAuth model user  =
    let
        newAddedRole =
         List.map (
             \x ->
                 span [ class "auth-added" ][ text x ]
         ) (model.authzToAddOnSave)

        userRoles =
            List.map (
                \x ->
                    span [ class "auth" ]
                    [
                          text x
                        , div [id "remove-role",class "fa fa-times", onClick (RemoveRole user x)] []
                    ]

            ) (user.role ++ user.authz)
        roles = userRoles ++ newAddedRole
    in
    if (List.isEmpty roles) then
        span[class "list-auths"][]
    else
        span[class "list-auths"](userRoles ++ newAddedRole)

displayAuth : User -> Html Msg
displayAuth user  =
    let
        userRoles =
            List.map (
                \x ->
                    span [ class "auth" ][text x]
            ) (user.role ++ user.authz)
    in
    if (List.isEmpty userRoles) then
        span[class "list-auths-empty"]
        [
            i[class "fa fa-lock fa-4x"][]
            , p [][text "No rights found"]
        ]
    else
        span[class "list-auths"](userRoles)