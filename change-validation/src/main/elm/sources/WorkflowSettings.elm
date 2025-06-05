module WorkflowSettings exposing (..)

import DataTypes exposing (Msg, Settings, ViewState(..), WorkflowSetting(..), WorkflowSettings, WorkflowSettingsForm, WorkflowSettingsModel, WorkflowSettingsMsg(..))
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, b, br, div, form, h3, i, input, label, li, p, span, strong, text, ul)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, name, style, type_, value)
import Html.Events exposing (onClick)
import Http exposing (Error, emptyBody, expectJson, header, jsonBody, request)
import JsonDecoders exposing (decodePluginStatus, decodeSetting)
import JsonEncoders exposing (encodeSetting)
import Ports exposing (errorNotification, successNotification)



------------------------------
-- Init and main --
------------------------------


initModel : String -> Bool -> WorkflowSettingsModel
initModel contextPath canWrite =
    WorkflowSettingsModel contextPath False canWrite InitWorkflowSettingsView


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

                Err error ->
                    ( model, errorNotification (getErrorMessage error) )

        SaveWorkflowEnabledSetting result ->
            model |> updateSetting result WorkflowEnabled

        SaveSelfValidationSetting result ->
            model |> updateSetting result SelfValidation

        SaveSelfDeploymentSetting result ->
            model |> updateSetting result SelfDeployment

        SwapWorkflowEnabled ->
            ( model |> swapSettingOn WorkflowEnabled, Cmd.none )

        SwapSelfValidation ->
            ( model |> swapSettingOn SelfValidation, Cmd.none )

        SwapSelfDeployment ->
            ( model |> swapSettingOn SelfDeployment, Cmd.none )

        SaveSettings ->
            case model.viewState of
                InitWorkflowSettingsView ->
                    ( model, Cmd.none )

                WorkflowSettingsView form ->
                    let
                        cmdList =
                            [ WorkflowEnabled, SelfValidation, SelfDeployment ]
                                -- Select only the settings that were modified by the user
                                |> List.filter (\setting -> formFieldModified form setting)
                                -- For each modified setting, make an API request to modify the setting
                                |> List.map (\setting -> saveWorkflowSetting model setting form)
                    in
                    ( model, Cmd.batch cmdList )


updateSetting : Result Error Bool -> WorkflowSetting -> WorkflowSettingsModel -> ( WorkflowSettingsModel, Cmd Msg )
updateSetting result setting model =
    let
        settingName =
            case setting of
                WorkflowEnabled ->
                    "enable_change_request"

                SelfValidation ->
                    "enable_self_validation"

                SelfDeployment ->
                    "enable_self_deployment"
    in
    case result of
        Ok value ->
            ( model |> setSettingOn setting value, successNotification ("Successfully updated " ++ settingName ++ " setting") )

        Err error ->
            ( model, errorNotification ("Could not update " ++ settingName ++ " setting : " ++ getErrorMessage error) )


{-| Set the new value of a given setting in the model
-}
setSettingOn : WorkflowSetting -> Bool -> WorkflowSettingsModel -> WorkflowSettingsModel
setSettingOn setting newValue model =
    case model.viewState of
        InitWorkflowSettingsView ->
            model

        WorkflowSettingsView { initSettings, formSettings } ->
            let
                newSettings =
                    case setting of
                        WorkflowEnabled ->
                            { formSettings | workflowEnabled = newValue }

                        SelfValidation ->
                            { formSettings | selfValidation = newValue }

                        SelfDeployment ->
                            { formSettings | selfDeployment = newValue }
            in
            { model | viewState = WorkflowSettingsView { initSettings = newSettings, formSettings = newSettings } }


{-| Swap the current value of a setting in the view (when a given setting is clicked)
-}
swapSettingOn : WorkflowSetting -> WorkflowSettingsModel -> WorkflowSettingsModel
swapSettingOn setting model =
    let
        swapSettingOnForm s f =
            case s of
                WorkflowEnabled ->
                    { f | workflowEnabled = not f.workflowEnabled }

                SelfValidation ->
                    { f | selfValidation = not f.selfValidation }

                SelfDeployment ->
                    { f | selfDeployment = not f.selfDeployment }
    in
    case model.viewState of
        InitWorkflowSettingsView ->
            model

        WorkflowSettingsView form ->
            let
                editedForm =
                    swapSettingOnForm setting form.formSettings
            in
            { model | viewState = WorkflowSettingsView { form | formSettings = editedForm } }



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


saveWorkflowSetting : WorkflowSettingsModel -> WorkflowSetting -> WorkflowSettingsForm -> Cmd Msg
saveWorkflowSetting model setting form =
    case setting of
        WorkflowEnabled ->
            setSetting model "enable_change_request" SaveWorkflowEnabledSetting form.formSettings.workflowEnabled

        SelfValidation ->
            setSetting model "enable_self_validation" SaveSelfValidationSetting form.formSettings.selfValidation

        SelfDeployment ->
            setSetting model "enable_self_deployment" SaveSelfDeploymentSetting form.formSettings.selfDeployment


getChangeValidationStatus : WorkflowSettingsModel -> Cmd Msg
getChangeValidationStatus model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "plugins/info"
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
                                        [ settingInput model WorkflowEnabled settings.formSettings
                                        , settingInput model SelfValidation settings.formSettings
                                        , settingInput model SelfDeployment settings.formSettings
                                        ]
                                    , saveButton model.canWrite settings
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


settingInput : WorkflowSettingsModel -> WorkflowSetting -> WorkflowSettings -> Html Msg
settingInput model setting form =
    let
        mkTooltip sid tooltipDesc =
            span [ id (sid ++ "Tooltip") ]
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

        ( settingId, settingName, tooltip ) =
            case setting of
                WorkflowEnabled ->
                    ( "workflowEnabled", " Enable change requests ", text "" )

                SelfValidation ->
                    ( "selfVal", " Allow self validation ", mkTooltip "selfVal" selfValTooltip )

                SelfDeployment ->
                    ( "selfDep", " Allow self deployment ", mkTooltip "selfDep" selfDepTooltip )

        ( isChecked, isDisabled, clickAction ) =
            case setting of
                WorkflowEnabled ->
                    ( form.workflowEnabled, not model.canWrite, SwapWorkflowEnabled )

                SelfValidation ->
                    ( form.selfValidation, not model.canWrite || not form.workflowEnabled, SwapSelfValidation )

                SelfDeployment ->
                    ( form.selfDeployment, not model.canWrite || not form.workflowEnabled, SwapSelfDeployment )

        inputGroupClass =
            case isDisabled of
                True ->
                    "input-group disabled"

                False ->
                    "input-group"
    in
    li
        [ class "rudder-form" ]
        [ div
            [ class inputGroupClass ]
            [ label [ for settingId, class "input-group-addon" ]
                [ input
                    [ type_ "checkbox"
                    , id settingId
                    , checked isChecked
                    , disabled isDisabled
                    , onClick (clickAction |> DataTypes.WorkflowSettingsMsg)
                    ]
                    []
                , label [ class "label-radio", for settingId ]
                    [ span [ class "ion ion-checkmark-round" ] [] ]
                , span [ class "ion ion-checkmark-round check-icon" ] []
                ]
            , label [ for settingId, class "form-control" ] [ text settingName, tooltip ]
            ]
        ]


saveButton : Bool -> WorkflowSettingsForm -> Html Msg
saveButton canWrite formState =
    if canWrite then
        input
            [ id "workflowSubmit"
            , name "workflowSubmit"
            , type_ "button"
            , class "btn btn-default"
            , value "Save change"
            , disabled (not (formModified formState))
            , onClick (SaveSettings |> DataTypes.WorkflowSettingsMsg)
            ]
            []

    else
        text ""


formModified : WorkflowSettingsForm -> Bool
formModified form =
    formFieldModified form WorkflowEnabled
        || formFieldModified form SelfValidation
        || formFieldModified form SelfDeployment


formFieldModified : WorkflowSettingsForm -> WorkflowSetting -> Bool
formFieldModified { initSettings, formSettings } setting =
    case setting of
        WorkflowEnabled ->
            not initSettings.workflowEnabled == formSettings.workflowEnabled

        SelfValidation ->
            not initSettings.selfValidation == formSettings.selfValidation

        SelfDeployment ->
            not initSettings.selfDeployment == formSettings.selfDeployment
