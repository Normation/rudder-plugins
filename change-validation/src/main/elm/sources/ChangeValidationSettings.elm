module ChangeValidationSettings exposing (..)

import ApiCalls exposing (getAllWorkflowSettings, getUsers)
import Browser
import DataTypes exposing (Model, Msg(..), WorkflowSettingsModel, WorkflowUsersModel)
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, b, div, h3, i, li, p, span, text, ul)
import Html.Attributes exposing (attribute, class, id, title)
import Ports exposing (errorNotification)
import SupervisedTargets exposing (getTargets)
import View
import WorkflowSettings exposing (getChangeValidationStatus)
import WorkflowUsers



------------------------------
-- Init and main --
------------------------------


init : { contextPath : String, hasWriteRights : Bool } -> ( Model, Cmd Msg )
init flags =
    let
        m =
            initModel flags.contextPath flags.hasWriteRights
    in
    ( m
    , Cmd.batch
        [ getChangeValidationStatus m.workflowSettingsModel
        , getAllWorkflowSettings m
        , getUsers m.workflowUsersModel
        , getTargets m.supervisedTargetsModel
        ]
    )


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }


initModel : String -> Bool -> Model
initModel contextPath hasWriteRights =
    { contextPath = contextPath
    , workflowUsersModel = WorkflowUsers.initModel contextPath hasWriteRights
    , supervisedTargetsModel = SupervisedTargets.initModel contextPath
    , workflowSettingsModel = WorkflowSettings.initModel contextPath hasWriteRights
    }



------------------------------
-- VIEW
------------------------------


view : Model -> Html Msg
view model =
    div
        [ class "main-details"
        , attribute "data-bs-spy" "scroll"
        , attribute "data-bs-target" "#navbar-changevalidation"
        , attribute "data-bs-smooth-scroll" "true"
        ]
        [ workflowSettingsView model
        , emailNotificationsView
        , changeRequestTriggersView model
        ]


workflowSettingsView : Model -> Html Msg
workflowSettingsView model =
    WorkflowSettings.view model.workflowSettingsModel


emailNotificationsView : Html Msg
emailNotificationsView =
    div [ id "emailNotifications" ]
        [ h3 [ class "page-title" ] [ text "Configure email notification" ]
        , div
            [ class "section-with-doc" ]
            [ div [ class "section-left" ]
                [ p [] [ text "You can modify the email's template of each steps here: " ]
                , ul [ class "clipboard-list" ]
                    [ emailClipboardElement "validation-mail"
                    , emailClipboardElement "deployment-mail"
                    , emailClipboardElement "cancelled-mail"
                    , emailClipboardElement "deployed-mail"
                    ]
                ]
            , div
                [ class "section-right" ]
                [ div [ class "doc doc-info" ]
                    [ div [ class "marker" ]
                        [ span [ class "fa fa-info-circle" ] [] ]
                    , p []
                        [ text " By default, email notifications are disabled. To enable them, make sure that the "
                        , b [] [ text "smtp.hostServer" ]
                        , text " parameter is not left empty in the configuration file: "
                        , b [] [ text "/opt/rudder/etc/plugins/change-validation.conf" ]
                        ]
                    ]
                ]
            ]
        ]


emailClipboardElement : String -> Html Msg
emailClipboardElement templateName =
    let
        path =
            getEmailTemplatePath templateName
    in
    li []
        [ span [] [ text path ]
        , a
            [ class "btn-goto btn-clipboard"
            , attribute "onclick" ("copy('" ++ path ++ "')")
            , attribute "data-toggle" "tooltip"
            , attribute "data-placement" "bottom"
            , attribute "data-container" "html"
            , title "Copy to clipboard"
            ]
            [ i [ class "far fa-clipboard" ] [] ]
        ]


getEmailTemplatePath : String -> String
getEmailTemplatePath templateName =
    "/var/rudder/plugins/change-validation/" ++ templateName ++ ".template"


changeRequestTriggersView : Model -> Html Msg
changeRequestTriggersView model =
    div [ id "changeRequestTriggers" ]
        [ h3 [ class "page-title" ] [ text "Configure change request triggers" ]
        , div []
            [ p []
                [ text " By default, change request are created for all users. You can change when a change request is created with below options: " ]
            , ul []
                [ li [] [ text "exempt some users from validation;" ]
                , li [] [ text "trigger change request only for changes impacting nodes belonging to some supervised groups; " ]
                ]
            , p []
                [ text "Be careful: a change request is created when "
                , b [] [ text "at least one" ]
                , text " predicate matches, so an exempted user still need a change request to modify a node from a supervised group. "
                ]
            ]
        , h3
            [ class "page-subtitle" ]
            [ text "Configure users with change validation" ]
        , div []
            [ View.view model.workflowUsersModel
            , SupervisedTargets.view model.supervisedTargetsModel
            ]
        ]



------------------------------
-- UPDATE
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        WorkflowUsersMsg wuMsg ->
            let
                ( wuModel, wuCmd ) =
                    WorkflowUsers.update wuMsg model.workflowUsersModel
            in
            ( { model | workflowUsersModel = wuModel }, wuCmd )

        SupervisedTargetsMsg stMsg ->
            let
                ( stModel, stCmd ) =
                    SupervisedTargets.update stMsg model.supervisedTargetsModel
            in
            ( { model | supervisedTargetsModel = stModel }, stCmd )

        WorkflowSettingsMsg wsMsg ->
            let
                ( wsModel, wsCmd ) =
                    WorkflowSettings.update wsMsg model.workflowSettingsModel
            in
            ( { model | workflowSettingsModel = wsModel }, wsCmd )

        GetAllWorkflowSettings res ->
            case res of
                Ok settings ->
                    let
                        wsmodel =
                            model.workflowSettingsModel |> WorkflowSettings.initView settings
                    in
                    let
                        wumodel =
                            model.workflowUsersModel |> WorkflowUsers.initValidateAllForm settings.workflowValidateAll
                    in
                    ( { model | workflowSettingsModel = wsmodel, workflowUsersModel = wumodel }, Cmd.none )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to get workflow settings : " ++ getErrorMessage error) )



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
