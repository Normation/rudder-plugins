module WorkflowSettings exposing (..)

import DataTypes exposing (Msg, Settings, ViewState(..), WorkflowSettingsForm, WorkflowSettingsModel, WorkflowSettingsMsg(..))
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, b, br, div, form, h3, i, input, label, li, p, span, strong, text, ul)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, name, style, type_, value)
import Http exposing (emptyBody, expectJson, header, jsonBody, request)
import JsonDecoders exposing (decodePluginStatus, decodeSetting)
import JsonEncoders exposing (encodeSetting)
import Ports exposing (errorNotification)



------------------------------
-- Init and main --
------------------------------


initModel : String -> Bool -> WorkflowSettingsModel
initModel contextPath canWrite =
    WorkflowSettingsModel contextPath True canWrite InitWorkflowSettingsView



------------------------------
-- UPDATE --
------------------------------


update : WorkflowSettingsMsg -> WorkflowSettingsModel -> ( WorkflowSettingsModel, Cmd Msg )
update msg model =
    case msg of
        GetChangeValidationStatus result ->
            case result of
                Ok value ->
                    ( { model | pluginStatus = value }, Cmd.none )

                Err _ ->
                    ( model, Cmd.none )

        SaveWorkflowEnabledSetting result ->
            ( model, Cmd.none )

        SaveWorkflowSelfValidationSetting result ->
            ( model, Cmd.none )

        SaveWorkflowSelfDeploymentSetting result ->
            ( model, Cmd.none )



------------------------------
-- API CALLS --
------------------------------


getUrl : WorkflowSettingsModel -> String -> String
getUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


setSetting : WorkflowSettingsModel -> String -> (Result Http.Error Bool -> WorkflowSettingsMsg) -> Bool -> Cmd Msg
setSetting model settingId msg newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson (DataTypes.WorkflowSettingsMsg << msg) (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveWorkflowEnabledSetting : Bool -> WorkflowSettingsModel -> Cmd Msg
saveWorkflowEnabledSetting newValue model =
    setSetting model "enable_change_request" SaveWorkflowEnabledSetting newValue


saveWorkflowSelfValidationSetting : WorkflowSettingsModel -> Bool -> Cmd Msg
saveWorkflowSelfValidationSetting model newValue =
    setSetting model "enable_self_validation" SaveWorkflowSelfValidationSetting newValue


saveWorkflowSelfDeploymentSetting : WorkflowSettingsModel -> Bool -> Cmd Msg
saveWorkflowSelfDeploymentSetting model newValue =
    setSetting model "enable_self_deployment" SaveWorkflowSelfDeploymentSetting newValue


getChangeValidationStatus : WorkflowSettingsModel -> Cmd Msg
getChangeValidationStatus model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "X-API-Token" ]
                , url = model.contextPath ++ "/api/latest/plugins/info"
                , body = emptyBody
                , expect = expectJson (DataTypes.WorkflowSettingsMsg << GetChangeValidationStatus) decodePluginStatus
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- VIEW --
------------------------------


selfValTooltip : String
selfValTooltip =
    "Allow users to validate Change Requests they created themselves? Validating is moving a Change Request to the \"<b>Pending deployment</b>\" status"


selfDepTooltip : String
selfDepTooltip =
    "Allow users to deploy Change Requests they created themselves? Deploying is effectively applying a Change Request in the \"<b>Pending deployment</b>\" status."


createRightInfoSection : List (Html Msg) -> Html Msg
createRightInfoSection contents =
    div [ class "section-right" ]
        [ div [ class "doc doc-info" ]
            ([ div [ class "marker" ] [ span [ class "fa fa-info-circle" ] [] ] ] ++ contents)
        ]


view : WorkflowSettingsModel -> Html Msg
view model =
    case model.pluginStatus of
        False ->
            text ""

        True ->
            case model.viewState of
                InitWorkflowSettingsView ->
                    text ""

                WorkflowSettingsView settings ->
                    div
                        [ id "workflowForm" ]
                        [ h3 [ class "page-title", style "margin-top" "0" ] [ text "Change validation status" ]
                        , div [ class "section-with-doc" ]
                            [ div [ class "section-left" ]
                                [ form
                                    [ id "workflowSettings" ]
                                    [ ul []
                                        [ settingInput model "workflowEnabled" " Enable change requests " Nothing settings.formSettings.workflowEnabled
                                        , settingInput model "selfVal" " Allow self validation " (Just selfValTooltip) settings.formSettings.selfValidation
                                        , settingInput model "selfDep" " Allow self deployment " (Just selfDepTooltip) settings.formSettings.selfDeployment
                                        ]
                                    , saveButton settings
                                    ]
                                ]
                            , createRightInfoSection
                                [ p []
                                    [ text
                                        (" If enabled, all change to configuration (directives, rules, groups and parameters)"
                                            ++ " will be submitted for validation via a change request based on node targeting (configured below)."
                                        )
                                    , br [] []
                                    , text " A new change request will enter the "
                                    , b [] [ text "Pending validation" ]
                                    , text " status, then can be moved to "
                                    , b [] [ text "Pending deployment" ]
                                    , text " (approved but not yet deployed) or "
                                    , b [] [ text "Deployed" ]
                                    , text " (approved and deployed) statuses. "
                                    ]
                                , p []
                                    [ text " If you have the user management plugin, only users with the "
                                    , b [] [ text "validator" ]
                                    , text " or "
                                    , b [] [ text "deployer" ]
                                    , text " roles are authorized to perform these steps (see "
                                    , i [] [ strong [] [ text "/opt/rudder/etc/rudder-users.xml" ] ]
                                    , text "). "
                                    ]
                                , p [] [ text " If disabled or if the change is not submitted to validation, the configuration will be immediately deployed. " ]
                                ]
                            ]
                        ]


settingInput : WorkflowSettingsModel -> String -> String -> Maybe String -> Bool -> Html Msg
settingInput model settingId settingName tooltipDescOpt formValue =
    let
        tooltip =
            case tooltipDescOpt of
                Just tooltipDesc ->
                    span
                        [ id (settingId ++ "Tooltip") ]
                        [ span
                            [ class "fa fa-info-circle icon-info"
                            , disabled (not model.canWrite)
                            , attribute "data-bs-toggle" "tooltip"
                            , attribute "data-bs-placement" "bottom"
                            , attribute "aria-label" tooltipDesc
                            , attribute "data-bs-original-title" tooltipDesc
                            ]
                            []
                        ]

                Nothing ->
                    text ""
    in
    li
        [ class "rudder-form" ]
        [ div
            [ class "input-group" ]
            [ label [ for settingId, class "input-group-addon" ]
                [ input
                    [ type_ "checkbox"
                    , id settingId
                    , checked formValue
                    ]
                    []
                , label [ class "label-radio", for settingId ]
                    [ span [ class "ion ion-checkmark-round" ] [] ]
                , span [ class "ion ion-checkmark-round check-icon" ] []
                ]
            , label [ for settingId, class "form-control" ] [ text settingName, tooltip ]
            ]
        ]


initView : Settings -> WorkflowSettingsModel -> WorkflowSettingsModel
initView settings model =
    { model | viewState = initWorkflowSettingsView settings }


initWorkflowSettingsView : Settings -> ViewState
initWorkflowSettingsView settings =
    let
        formState =
            { workflowEnabled = settings.workflowEnabled
            , selfValidation = settings.selfValidation
            , selfDeployment = settings.selfDeployment
            }
    in
    WorkflowSettingsView { initSettings = formState, formSettings = formState }


saveButton : WorkflowSettingsForm -> Html Msg
saveButton formState =
    input
        [ id "workflowSubmit"
        , name "workflowSubmit"
        , type_ "submit"
        , class "btn btn-default"
        , value "Save change"
        , disabled (not (formModified formState))
        ]
        []


formModified : WorkflowSettingsForm -> Bool
formModified { initSettings, formSettings } =
    (not initSettings.workflowEnabled == formSettings.workflowEnabled)
        || (not initSettings.selfDeployment == formSettings.selfDeployment)
        || (not initSettings.selfValidation == formSettings.selfValidation)
