module View exposing (..)

import ApiCalls exposing (deleteUser)
import DataTypes exposing (Model, Msg(..), NewUser, PanelMode(..), RoleListOverride(..), Roles, StateInput(..), User, Users, takeFirstExtProvider)
import Dict exposing (keys)
import Html exposing (..)
import Html.Attributes exposing (attribute, class, disabled, href, id, placeholder, required, style, tabindex, type_)
import Html.Events exposing (onClick, onInput)
import Init exposing (defaultConfig)
import String exposing (isEmpty)
import Toasty
import Toasty.Defaults
import List
import Set

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
                  div [ tabindex -1, class "modal fade in", style "z-index" "1050", style "display" "block" ]
                  [
                      div [class "modal-dialog"]
                      [
                          div [class "modal-content"]
                          [
                              div [class "modal-header"]
                              [
                                div [class "close", attribute "data-dismiss" "modal", onClick CloseDeleteModal][i [class "fa fa-times"][]]
                              , h4 [class "modal-title"][text "Delete a user"]
                              ]
                            , div [class "modal-body"]
                              [
                                div [class "row"]
                                [
                                  h4 [class "col-lg-12 col-sm-12 col-xs-12 text-center"][text "Are you sure you want to delete user " , b[][text username] , text " ?"]
                                ]
                              ]
                            , div [class "modal-footer"]
                              [
                                button [class "btn btn-default", onClick CloseDeleteModal][text "Cancel"]
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
               , div [class "btn-container"] [ button [class "btn btn-sm btn-success btn-save", type_ "button", onClick (SubmitNewUser (NewUser model.login [] []))][ i[ class "fa fa-download"][] ]  ]
           ]
       ]
   ]

displayRoleListOverrideWarning : RoleListOverride -> Html Msg
displayRoleListOverrideWarning rlo =
  case rlo of
      None -> text ""
      _    ->
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

displayDropdownRoleList : Model -> User -> Html Msg
displayDropdownRoleList model user =
    let
        availableRoles = getDiffRoles model.roles (user.roles ++ model.rolesToAddOnSave)
        tokens = List.map (\r -> a [href "#", onClick (AddRole r)][text r]) availableRoles
        addBtn =
            if List.isEmpty availableRoles then
                button [id "addBtn-disabled", class "addBtn", disabled True][i [class "fa fa-plus"][]]
            else
                button [class "addBtn"][i [class "fa fa-plus"][]]
    in
        div [class "dropdown"]
        [
              addBtn
            , div [class "dropdown-content"] tokens
        ]
    
displayCoverageRoles : User -> Html Msg
displayCoverageRoles user = 
    let
        inferredRoles = Set.diff (Set.fromList user.rolesCoverage) (Set.fromList user.roles)
    in
        if Set.isEmpty inferredRoles
        then 
            text ""
        else
            div []
            [
                  h4 [class "role-title"][text "Inferred roles"]
                , div [class "callout-fade callout-info"]
                [
                      div[class "marker"][span[class "glyphicon glyphicon-info-sign"][]]
                    , text "Roles that are also included with the authorizations of this user. You can add them explicitly to user roles without changing user authorizations."
                ]
                , div [class "role-management-wrapper"] 
                [
                    div[id "input-role"](List.map (\x -> span [ class "auth" ][text x]) (Set.toList inferredRoles))
                ]
            ]

displayAuthorizations : User -> Html Msg
displayAuthorizations user =
    let
        userAuthz = List.map (\x -> span [ class "auth" ] [ text x ]) (user.authz)
    in
        if List.isEmpty user.authz
        then 
            text ""
        else
            div [class "row-foldable row-folded"]
            [
                  h4 [class "role-title"][text "Authorizations"]
                , text "Current rights of the user."
                , div [class "role-management-wrapper"]
                [
                    div[id "input-role"](userAuthz)
                ]
            ]

displayRightPanel : Model -> User -> Html Msg
displayRightPanel model user =
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
           , displayPasswordBlock model
           , h4 [class "role-title"][text "Roles"]
           , (displayRoleListOverrideWarning model.roleListOverride)
           , div [class "role-management-wrapper"]
           [
                  div [id "input-role"](displayAddRole model user)
                , displayDropdownRoleList model user
           ]
           , div[class "btn-container"]
             [ button [class "btn btn-sm btn-danger btn-delete" , onClick (OpenDeleteModal user.login)] [text "Delete"]
             , button [class "btn btn-sm btn-success btn-save", type_ "button", onClick (SubmitUpdatedInfos {user | roles = user.roles ++ model.rolesToAddOnSave})][ i[ class "fa fa-download"][] ]
             ]
           , hr [][]
            -- Additional RO information
           , displayCoverageRoles user
           , displayAuthorizations user
        ]
    ]

displayUsersConf : Model -> Users -> Html Msg
displayUsersConf model u =
    let
        users =
            Dict.values u |> List.map (\user -> displayUser user)
        newUserMenu =
            if model.panelMode == AddMode then
                displayRightPanelAddUser model
            else
                text ""
        panel =
            case model.panelMode of
                EditMode user    -> displayRightPanel model user
                _             -> text ""
        hasExternal =  (takeFirstExtProvider model.providers)
        lstOfExtProviders = case hasExternal of
            Nothing ->
                text ""
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
                          h2 [][text "User Management Configuration"]
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
                          div [ class "marker" ] [ span [ class "glyphicon glyphicon-info-sign" ] [] ]
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
            , displayRoles user
        ]
    ]

displayAddRole : Model -> User -> List (Html Msg)
displayAddRole model user  =
    let
        newAddedRole =
         List.map (
             \x ->
                 span [ class "auth-added" ][ text x ]
         ) (model.rolesToAddOnSave)

        userRoles =
            List.map (
                \x ->
                    span [ class "auth" ]
                    [
                          text x
                        , div [id "remove-role",class "fa fa-times", onClick (RemoveRole user x)] []
                    ]

            ) (user.roles)
    in
        userRoles ++ newAddedRole

displayRoles : User -> Html Msg
displayRoles user  =
    let
        userRoles =
            List.map (
                \x ->
                    span [ class "auth" ][text x]
            ) (user.roles)
    in
    if (List.isEmpty userRoles) then
        span[class "list-auths-empty"]
        [
            i[class "fa fa-lock fa-4x"][]
            , p [][text "No rights found"]
        ]
    else
        span[class "list-auths"](userRoles)
