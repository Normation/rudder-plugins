module View exposing (..)

import ApiCalls exposing (deleteUser)
import DataTypes exposing (Model, Msg(..), PanelMode(..), RoleConf, RoleListOverride(..), Roles, StateInput(..), User, Username, Users, UsersConf, takeFirstExtProvider)
import Dict exposing (keys)
import Html exposing (..)
import Html.Attributes exposing (attribute, class, disabled, href, id, placeholder, required, style, tabindex, type_)
import Html.Events exposing (onClick, onInput)
import Init exposing (defaultConfig)
import String exposing (isEmpty)
import Toasty
import Toasty.Defaults
import List

view : Model -> Html Msg
view model =
    let
        content =
            if (isEmpty model.digest) || (List.isEmpty (keys model.users)) then
                text "Waiting for data from server..."

            else
                displayUsersConf model model.users
        deleteModal =
            if model.openDeleteModal then
                showDeleteModal model.login
            else
                div[][]
    in
    div []
        [ deleteModal
        , content
        , div [ class "toasties" ] [ Toasty.view defaultConfig Toasty.Defaults.view ToastyMsg model.toasties ]
        ]

showDeleteModal : String -> Html Msg
showDeleteModal username =
  div [ tabindex -1, class "modal fade show", style "z-index" "1050", style "display" "block" ]
  [ div [class "modal-backdrop fade show", onClick CloseDeleteModal][]
  , div [class "modal-dialog"]
    [ div [class "modal-content"]
      [ div [class "modal-header"]
        [ h5 [class "modal-title"][text "Delete a user"]
        , button [type_ "button", class "btn-close", attribute "data-bs-dismiss" "modal", attribute "aria-label" "Close", onClick CloseDeleteModal][]
        ]
      , div [class "modal-body"]
        [ div [class "row"]
          [ h5 [class "text-center"][text "Are you sure you want to delete user " , b[][text username] , text "?"]
          ]
        ]
      , div [class "modal-footer"]
        [ button [class "btn btn-default", onClick CloseDeleteModal][text "Cancel"]
        , button [class "btn btn-danger", onClick (CallApi ( deleteUser username ))][text "Delete"]
        ]
      ]
    ]
  ]

hashPasswordMenu : Bool -> Html Msg
hashPasswordMenu isHashedPasswd =
    let
        hashPasswdIsActivate =
            if (isHashedPasswd == True) then
                "active "
            else
                ""
        clearPasswdIsActivate =
             if (isHashedPasswd == False) then
                 "active "
             else
                 ""
    in
        div [class ("btn-group"), attribute "role" "group"]
        [
              a [class ("btn btn-default " ++ hashPasswdIsActivate) , onClick (PreHashedPasswd True)][text "Enter pre-hashed value"]
            , a [class ("btn btn-default " ++ clearPasswdIsActivate), onClick (PreHashedPasswd False)][text "Password to hash"]
        ]


displayPasswordBlock : Model -> Html Msg
displayPasswordBlock model =
    let
        mainProvider =
            case (takeFirstExtProvider model.providers) of
              Just p  -> p
              Nothing -> "unknown_provider"
        classDeactivatePasswdInput = if isPasswordMandatory then "" else " deactivate-auth-backend  ask-passwd-deactivate "
        isPasswordMandatory =
           case (List.head model.providers) of
             Just p -> p == "file"
             Nothing -> True
        isHidden =
            if model.isHashedPasswd then
                "text"
            else "password"
        phMsg = if model.isHashedPasswd then "New hashed password" else "This password will be hashed and then stored"
        passwdRequiredId =
            if isPasswordMandatory then
                case model.panelMode of
                    AddMode ->
                        if (model.isValidInput == InvalidUsername) then "invalid-password" else "valid-input"
                    _       ->
                        "valid-input"
            else
                ""
        passwordInput =
            [
                  hashPasswordMenu model.isHashedPasswd
                , input [id passwdRequiredId, class ("form-control anim-show  " ++ classDeactivatePasswdInput), type_ isHidden, placeholder phMsg, onInput Password, attribute "autocomplete" "new-password"] []
                , hashType
            ]
        hashType =
            if model.isHashedPasswd then
                div[class "hash-block"][i[class "fa fa-key hash-icon"][],div[class "hash-type"][text model.digest]]
            else
               div[][]

    in
    if (not isPasswordMandatory) then
       div[class "msg-providers"]
       [
            i [class "fa fa-exclamation-circle info-passwd-icon"][]
          , text "Since the authentication method used an external provider, no password will be asked for this user."
          , br[][]
          , text "If you want to add a password anyway, "
          , a [class "click-here", onClick AddPasswdAnyway][text "Click here"]
          , if (model.userForcePasswdInput) then
                div [class "force-password"] (
                    div[class "info-force-passwd"]
                    [
                      text "The password value won't be use with currently default authentication provider "
                    , b [style "color" "#2557D6"] [text mainProvider]
                    , text ", you should let it blank."
                    ] :: passwordInput
                )
            else
                div [][]
       ]
    else
        div [] (passwordInput)

displayRightPanelAddUser : Model -> Html Msg
displayRightPanelAddUser model =
   let
       emptyUsername =
           case model.panelMode of
               AddMode ->
                   if (model.isValidInput == InvalidUsername) then "invalid-username" else "valid-input"
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
               , displayPasswordBlock model
               , div [class "btn-container"] [ button [class "btn btn-sm btn-success btn-save", type_ "button", onClick (SubmitNewUser (User model.login [] []))][ i[ class "fa fa-download"][] ]  ]
           ]
       ]
   ]

displayRoleListOverrideWarning : RoleListOverride -> Html Msg
displayRoleListOverrideWarning rlo =
  case rlo of
      None -> div [][]
      x    ->
        div [class "msg-providers"][
          i [class "fa fa-exclamation-triangle warning-icon"][]
        , span [][text " Be careful! Displayed user roles originate from Rudder static configuration file, but providers currently configured can change them. This property can be check in provider configuration options."]
       ]

getDiffRoles : Roles -> List String -> List String
getDiffRoles total sample =
    let
        t =  keys total
    in
        (List.filter (\r -> (List.all (\r2 -> r /= r2) sample)) t)

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
           , input [class "form-control username-input", type_ "text", placeholder "New username", onInput Login] []
           , displayPasswordBlock model
           , h4 [class "role-title"][text "Roles"]
           , (displayRoleListOverrideWarning model.roleListOverride)
           , div [class "role-management-wrapper"]
           [
                  div [id "input-role"][(displayAddAuth model user)]
                , div [class "dropdown"]
                [
                      addBtn
                    , (displayDropdownRoleList availableRoles)
                ]
           ]
           , div[class "btn-container"]
             [ button [class "btn btn-sm btn-danger btn-delete" , onClick (OpenDeleteModal user.login)] [text "Delete"]
             , button [class "btn btn-sm btn-success btn-save", type_ "button", onClick (SubmitUpdatedInfos {user | permissions = user.permissions ++ model.authzToAddOnSave})][ i[ class "fa fa-download"][] ]
             ]
        ]
    ]

displayUsersConf : Model -> Users -> Html Msg
displayUsersConf model u =
    let
        users =
            (List.map (\(name, rights) -> (User name rights.custom rights.permissions)) (Dict.toList u)) |> List.map (\user -> displayUser user)
        newUserMenu =
            if model.panelMode == AddMode then
                displayRightPanelAddUser model
            else
                div [] []
        panel =
            case model.panelMode of
                EditMode _    -> displayRightPanel model
                _             -> div [][]
        hasExternal =  (takeFirstExtProvider model.providers)
        lstOfExtProviders = case hasExternal of
            Nothing ->
                div [][]
            Just _ ->
                div [class "provider-list"](List.map (\s -> span [class "providers"][text s]) model.providers)
        msgProvider = case hasExternal of
            Nothing ->
                "Default authentication method is used"
            Just _ ->
                "Authentication providers priority: "
    in
    div [ class "row" ]
        [
            div [class "header-plugin"]
            [
                div [id "header-flex"]
                [
                    div []
                    [
                          h2 [][text "User management"]
                        , div [class"description-plugin"]
                        [
                            p [] [text "This page shows you the current Rudder users and their rights."]
                        ]
                        , button [class "btn btn-sm btn-success new-icon btn-add", onClick ActivePanelAddUser][text "Create"]
                        , button [class "btn btn-box-tool btn-blue btn-sm btn-reload", onClick SendReload]
                        [
                              text "Reload"
                            , span [id "reloadBtn", class "fa fa-refresh"][]
                        ]
                    ]
                    , div [ class "callout-fade callout-info" ]
                    [
                          div [ class "marker marker" ] [ span [ class "fa fa-info-circle" ] [] ]
                        , text msgProvider
                        , lstOfExtProviders
                        , (displayRoleListOverrideWarning model.roleListOverride)
                    ]
                ]
                , newUserMenu
                , panel
            ]
            , div [ class "col-xs-12 user-list" ][ div [ class "row " ] users ]
        ]

displayUser : User -> Html Msg
displayUser user =
    div [class "user-card-wrapper", onClick (ActivePanelSettings user)]
    [
        div [class "user-card"]
        [
              div [class "user-card-inner"][h3 [id "name"][text user.login]]
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

            ) (user.permissions ++ user.authz)
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
            ) (user.permissions ++ user.authz)
    in
    if (List.isEmpty userRoles) then
        span[class "list-auths-empty"]
        [
            i[class "fa fa-lock fa-4x"][]
            , p [][text "No rights found"]
        ]
    else
        span[class "list-auths"](userRoles)
