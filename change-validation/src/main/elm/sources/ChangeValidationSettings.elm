module ChangeValidationSettings exposing (init)

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, b, div, h3, i, li, p, span, text, ul)
import Html.Attributes exposing (attribute, class, id, title)
import Html.Events exposing (onClick)
import Http exposing (Error, emptyBody, expectJson, header, request)
import JsonUtils exposing (Settings, decodeWorkflowSettings)
import Ports exposing (copyToClipboard, errorNotification)
import SupervisedTargets exposing (SupervisedTargetsModel, SupervisedTargetsMsg, getTargets)
import WorkflowSettings exposing (WorkflowSettingsModel, WorkflowSettingsMsg)
import WorkflowUsers exposing (WorkflowUsersModel, WorkflowUsersMsg, getUsers)



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
        [ getAllWorkflowSettings m
        , Cmd.map WorkflowUsersMsg (getUsers m.workflowUsersModel)
        , Cmd.map SupervisedTargetsMsg (getTargets m.supervisedTargetsModel)
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
-- MODEL                    --
------------------------------


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
        [ Html.map WorkflowSettingsMsg (WorkflowSettings.view model.workflowSettingsModel)
        , emailNotificationsView
        , changeRequestTriggersView model
        ]


emailNotificationsView : Html Msg
emailNotificationsView =
    div [ id "emailNotifications" ]
        [ h3 [ class "page-title" ] [ text "Configure email notification" ]
        , div
            [ class "section-with-doc" ]
            [ div [ class "section-left" ]
                [ p [] [ text "You can modify the email template of each step here: " ]
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
                        , text " parameter is not empty in the configuration file: "
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
            , onClick (CopyToClipboard path)
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
                [ text
                    (" By default, change requests are created when any user makes a modification. "
                        ++ "However, change requests can be configured with the options below in order to : "
                    )
                ]
            , ul []
                [ li [] [ text "Exempt some users from needing to create change requests, and" ]
                , li [] [ text "Trigger a change request only if a given change impacts a node from a supervised group. " ]
                ]
            , p []
                [ text "Be careful: a change request is created when "
                , b [] [ text "at least one" ]
                , text " predicate matches, so an exempted user still need a change request in order to edit a node from a supervised group. "
                ]
            ]
        , h3
            [ class "page-subtitle" ]
            [ text "Configure users with change validation" ]
        , div []
            [ Html.map WorkflowUsersMsg (WorkflowUsers.view model.workflowUsersModel)
            , Html.map SupervisedTargetsMsg (SupervisedTargets.view model.supervisedTargetsModel)
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
            ( { model | workflowUsersModel = wuModel }, Cmd.map WorkflowUsersMsg wuCmd )

        SupervisedTargetsMsg stMsg ->
            let
                ( stModel, stCmd ) =
                    SupervisedTargets.update stMsg model.supervisedTargetsModel
            in
            ( { model | supervisedTargetsModel = stModel }, Cmd.map SupervisedTargetsMsg stCmd )

        WorkflowSettingsMsg wsMsg ->
            let
                ( wsModel, wsCmd ) =
                    WorkflowSettings.update wsMsg model.workflowSettingsModel
            in
            ( { model | workflowSettingsModel = wsModel }, Cmd.map WorkflowSettingsMsg wsCmd )

        GetAllWorkflowSettings res ->
            case res of
                Ok settings ->
                    let
                        ( wsModel, wuModel ) =
                            ( model.workflowSettingsModel, model.workflowUsersModel )
                    in
                    ( { model
                        | workflowSettingsModel = { wsModel | viewState = WorkflowSettings.initWorkflowSettingsView settings }
                        , workflowUsersModel = { wuModel | validateAllView = WorkflowUsers.initValidateAllForm settings.workflowValidateAll }
                      }
                    , Cmd.none
                    )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to get workflow settings : " ++ getErrorMessage error) )

        CopyToClipboard selection ->
            ( model, copyToClipboard selection )



------------------------------
-- API CALLS                --
------------------------------


getUrl : Model -> String -> String
getUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


getAllWorkflowSettings : Model -> Cmd Msg
getAllWorkflowSettings model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "settings"
                , body = emptyBody
                , expect = expectJson GetAllWorkflowSettings decodeWorkflowSettings
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
