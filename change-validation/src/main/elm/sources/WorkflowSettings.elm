module WorkflowSettings exposing (ViewState, WorkflowSettingsModel, WorkflowSettingsMsg, initModel, initWorkflowSettingsView, update, view)

import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, b, br, div, form, h3, i, input, label, li, p, span, strong, text, ul)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, name, style, type_, value)
import Html.Events exposing (onClick)
import Http exposing (Error, expectJson, header, jsonBody, request)
import JsonUtils exposing (Settings, decodeSetting, encodeSetting)
import Ports exposing (errorNotification, successNotification)



------------------------------
-- MODEL                    --
------------------------------


type alias WorkflowSettingsModel =
    { contextPath : String
    , hasWriteRights : Bool
    , viewState : ViewState
    }


type ViewState
    = InitWorkflowSettingsView
    | WorkflowSettingsView WorkflowSettingsForm


type alias WorkflowSettingsForm =
    { initSettings : WorkflowSettings
    , formSettings : WorkflowSettings
    }


type alias WorkflowSettings =
    { workflowEnabled : Bool
    , selfValidation : Bool
    , selfDeployment : Bool
    }


type WorkflowSetting
    = WorkflowEnabled
    | SelfValidation
    | SelfDeployment


type WorkflowSettingsMsg
    = {--Messages for the change-validation settings list--}
      -- SET workflow settings API call
      SaveWorkflowSetting WorkflowSetting (Result Error Bool)
      -- Edit form in view
    | ToggleSetting WorkflowSetting
    | SaveSettings



------------------------------
-- Init and main --
------------------------------


initModel : String -> Bool -> WorkflowSettingsModel
initModel contextPath hasWriteRights =
    WorkflowSettingsModel contextPath hasWriteRights InitWorkflowSettingsView


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


update : WorkflowSettingsMsg -> WorkflowSettingsModel -> ( WorkflowSettingsModel, Cmd WorkflowSettingsMsg )
update msg model =
    case msg of
        SaveWorkflowSetting setting result ->
            model |> updateSetting result setting

        ToggleSetting setting ->
            ( model |> toggleSettingOn setting, Cmd.none )

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


settingText : WorkflowSetting -> String
settingText setting =
    case setting of
        WorkflowEnabled ->
            "enable_change_request"

        SelfValidation ->
            "enable_self_validation"

        SelfDeployment ->
            "enable_self_deployment"


updateSetting : Result Error Bool -> WorkflowSetting -> WorkflowSettingsModel -> ( WorkflowSettingsModel, Cmd WorkflowSettingsMsg )
updateSetting result setting model =
    let
        settingName =
            settingText setting
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


{-| Toggle the current value of a setting in the view (when a given setting is clicked)
-}
toggleSettingOn : WorkflowSetting -> WorkflowSettingsModel -> WorkflowSettingsModel
toggleSettingOn setting model =
    let
        toggleSettingOnForm s f =
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
            { model | viewState = WorkflowSettingsView { form | formSettings = toggleSettingOnForm setting form.formSettings } }



------------------------------
-- API CALLS --
------------------------------


getUrl : WorkflowSettingsModel -> String -> String
getUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


setSetting : WorkflowSettingsModel -> String -> (Result Http.Error Bool -> WorkflowSettingsMsg) -> Bool -> Cmd WorkflowSettingsMsg
setSetting model settingId msg newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson msg (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveWorkflowSetting : WorkflowSettingsModel -> WorkflowSetting -> WorkflowSettingsForm -> Cmd WorkflowSettingsMsg
saveWorkflowSetting model setting form =
    let
        setter =
            setSetting model (settingText setting) (SaveWorkflowSetting setting)
    in
    case setting of
        WorkflowEnabled ->
            setter form.formSettings.workflowEnabled

        SelfValidation ->
            setter form.formSettings.selfValidation

        SelfDeployment ->
            setter form.formSettings.selfDeployment



------------------------------
-- VIEW --
------------------------------


selfValTooltip : String
selfValTooltip =
    "Allow users to validate Change Requests they created themselves? Validating is moving a Change Request to the \"<b>Pending deployment</b>\" status"


selfDepTooltip : String
selfDepTooltip =
    "Allow users to deploy Change Requests they created themselves? Deploying is effectively applying a Change Request in the \"<b>Pending deployment</b>\" status."


createRightInfoSection : List (Html msg) -> Html msg
createRightInfoSection contents =
    div [ class "section-right" ]
        [ div [ class "doc doc-info" ]
            ([ div [ class "marker" ] [ span [ class "fa fa-info-circle" ] [] ] ] ++ contents)
        ]


view : WorkflowSettingsModel -> Html WorkflowSettingsMsg
view model =
    case model.viewState of
        InitWorkflowSettingsView ->
            i [ class "fa fa-spinner fa-pulse" ] []

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
                            , saveButton model.hasWriteRights settings
                            ]
                        ]
                    , createRightInfoSection
                        [ p []
                            [ text
                                (" If enabled, all changes (directives, rules, groups and parameters)"
                                    ++ " will be submitted for validation via a change request based on node targeting (configured below)."
                                )
                            , br [] []
                            , text " A new change request will initially be in the "
                            , b [] [ text "Pending validation" ]
                            , text " status. It can then be moved to the "
                            , b [] [ text "Pending deployment" ]
                            , text " (approved but not yet deployed) or "
                            , b [] [ text "Deployed" ]
                            , text " (approved and deployed) statuses. "
                            ]
                        , p []
                            [ text " If you have the user management plugin, only users who have the "
                            , b [] [ text "validator" ]
                            , text " or "
                            , b [] [ text "deployer" ]
                            , text " roles can perform these steps (see "
                            , i [] [ strong [] [ text "/opt/rudder/etc/rudder-users.xml" ] ]
                            , text "). "
                            ]
                        , p [] [ text " If change requests are disabled or if the change is not submitted for validation, the configuration will immediately be deployed. " ]
                        ]
                    ]
                ]


settingInput : WorkflowSettingsModel -> WorkflowSetting -> WorkflowSettings -> Html WorkflowSettingsMsg
settingInput model setting form =
    let
        mkTooltip sid tooltipDesc =
            span [ id (sid ++ "Tooltip") ]
                [ span
                    [ class "fa fa-info-circle icon-info"
                    , disabled (not model.hasWriteRights)
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
                    ( form.workflowEnabled, not model.hasWriteRights, ToggleSetting WorkflowEnabled )

                SelfValidation ->
                    ( form.selfValidation, not model.hasWriteRights || not form.workflowEnabled, ToggleSetting SelfValidation )

                SelfDeployment ->
                    ( form.selfDeployment, not model.hasWriteRights || not form.workflowEnabled, ToggleSetting SelfDeployment )

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
                    , onClick clickAction
                    ]
                    []
                , label [ class "label-radio", for settingId ]
                    [ span [ class "fa fa-check" ] [] ]
                , span [ class "fa fa-check check-icon" ] []
                ]
            , label [ for settingId, class "form-control" ] [ text settingName, tooltip ]
            ]
        ]


saveButton : Bool -> WorkflowSettingsForm -> Html WorkflowSettingsMsg
saveButton hasWriteRights formState =
    if hasWriteRights then
        input
            [ id "workflowSubmit"
            , name "workflowSubmit"
            , type_ "button"
            , class "btn btn-default"
            , value "Save change"
            , disabled (not (formModified formState))
            , onClick SaveSettings
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
