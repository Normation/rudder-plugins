module View exposing (..)

------------------------------
-- VIEW --
------------------------------


import ApiCalls exposing (deleteUser)
import DataTypes exposing (EditMod(..), Model, Msg(..), RoleConf, User, Username, Users, UsersConf)
import Dict exposing (keys)
import Html exposing (Html, a, br, button, div, h3, h4, h6, i, input, option, p, select, span, text)
import Html.Attributes exposing (attribute, class, disabled, hidden, id, placeholder, required, type_, value)
import Html.Events exposing (onClick, onInput)
import Init exposing (defaultConfig)
import List exposing (filter, map)
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
            if (model.clearPasswd == False) && (model.hashedPasswd == True) then
                "active"
            else
                ""
        clearPasswdIsActivate =
            if (model.clearPasswd == True) && (model.hashedPasswd == False) then
                "active"
            else
                ""
    in
        div [class "btn-group", attribute "role" "group"]
        [
              a [class ("btn btn-default " ++ hashPasswdIsActivate), onClick PreHashedPasswd][text "Enter pre-hashed value"]
            , a [class ("btn btn-default " ++ clearPasswdIsActivate), onClick ClearPasswd][text "Use clear text password"]
        ]

displayRightPanelAddUser : Model -> Html Msg
displayRightPanelAddUser model =
   div [class "panel-wrap"]
   [
       div [class "panel"]
       [
             a [class "close close-panel", onClick DeactivatePanel][]
           , h4 [] [text ("Create user " ++ model.login)]
           , div []
           [
                 input [class "form-control", type_ "text", placeholder "Username", onInput Login, value model.login, required True] []
               , input [type_ "text", disabled True, hidden True] []
               , hashPasswordMenu model
               , input [type_ "password", disabled True, hidden True] []
               , input [class "form-control", type_ "password", placeholder "Password", onInput Password, value model.password , attribute "autocomplete" "new-password", required True] []
               , button [class "btn btn-sm btn-primary", onClick (SubmitNewUser (User model.login [] []) model.password)] [text "Submit"]
           ]
       ]
   ]

displayRightPanel : Model -> Html Msg
displayRightPanel model =
    div [class "panel-wrap"]
    [
        div [class "panel"]
        [
            a [class "close close-panel", onClick DeactivatePanel][]
           , h4 [] [text model.userFocusOn.login]
           ,
              input [class "form-control", type_ "text", placeholder "New Username", onInput Login] []
              , br [] []
              , hashPasswordMenu model
              , input [class "form-control", type_ "password", placeholder "New Password", onInput Password, attribute "autocomplete" "new-password" ] []
              , br [] []
              , button [class "btn btn-sm btn-danger",onClick (CallApi ( deleteUser model.userFocusOn.login))] [text "Delete "]
              , button [class "btn btn-sm btn-primary", onClick (SubmitUpdatedInfos model.userFocusOn)] [text "Submit"]

        ]
    ]

displayUsersConf : Model -> Users -> Html Msg
displayUsersConf model u =
    let
        users =
            (map (\(name, rights) -> (User name rights.custom rights.roles)) (Dict.toList u)) |> List.map (\user -> displayUser model user)
        newUserMenu =
            if model.addMod == On then
                displayRightPanelAddUser model
            else
                div [] []

    in
    div [ class "row" ]
        [ div [ class "col-xs-12" ]
            [ h3 []
                [ text "Rudder Users"
                , button [class "btn btn-box-tool btn-blue btn-sm", onClick SendReload]
                [
                     text "Reload file"
                   , span [class "fa fa-refresh"][]
                ]
                , button [class "btn btn-sm btn-success new-icon", onClick ActivePanelAddUser]
                [
                   text "Add User"
                ]
                , newUserMenu
                ]

            ]
        , div [ class "col-xs-12" ]
            [ p [ class "callout-fade callout-info" ]
                [ div [ class "marker" ] [ span [ class "glyphicon glyphicon-info-sign" ] [] ]
                , text ("Password encoder: " ++ model.digest)
                ]
            ]
        , div [ class "col-xs-12 user-list" ]
            [ div [ class "row " ] users ]
        ]


displayUser : Model -> User -> Html Msg
displayUser model user =
    let
        panel = if model.editMod == Off then div [][] else displayRightPanel model
    in
        div [ class "col-xs-12 col-sm-6 col-md-3" ]
            [ div [ class "user", id "fast-transition"]
                [ div [ class "row" ]
                    [ h4 [ class "col-xs-12", onClick (ActivePanelSettings user) ]
                        [ span [ class "fa fa-user user-icon" ] []
                        , text user.login
                        ]
                    ]
                , div []
                    [ h6 [] [ span [] [ text "ROLES" ] ]
                    , div [ class "list-auths" ] (displayAuth model user )

                    ]
                ]
                , panel
            ]


displayAuth : Model -> User -> List (Html Msg)
displayAuth model user  =
    let
        test =
            List.map (
                \x ->
                    let
                        updatedUser = {user | role = filter (\r -> r == x) user.role, authz = filter (\r -> r == x) user.authz}
                    in
                    span [ class "auth" ]
                    [
                          text x
                        , a [ class "close remove-role", onClick (RemoveRole user x)] []
                    ]
            ) (user.role ++ user.authz)
        selection = displaySelectRole model user
    in
        test ++ [selection]


displaySelectRole: Model -> User -> Html Msg
displaySelectRole model user =
    div []
    [
        select [class "add form-control", onInput (AddRole user)] (List.map (\x ->option [value  (x.id)] [text x.id]) model.rolesConf)
    ]
