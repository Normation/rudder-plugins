module WorkflowSettings exposing (..)

import DataTypes exposing (Msg, Settings, WorkflowSettingsMsg(..))
import Html exposing (Html, b, br, div, form, h3, i, input, label, li, p, span, strong, text, ul)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, name, style, type_, value)



------------------------------
-- Init and main --
------------------------------


initModel : String -> Bool -> Model
initModel contextPath canWrite =
    Model contextPath True canWrite Nothing



------------------------------
-- MODEL --
------------------------------


type alias Model =
    { contextPath : String
    , pluginStatus : Bool
    , canWrite : Bool
    , settings : Maybe Settings
    }


type alias ViewState =
    { initSettings : Settings
    , formSettings : Settings
    }



------------------------------
-- UPDATE --
------------------------------


update : WorkflowSettingsMsg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeValidationStatus result ->
            case result of
                Ok value ->
                    ( { model | pluginStatus = value }, Cmd.none )

                Err _ ->
                    ( model, Cmd.none )

        _ ->
            ( model, Cmd.none )



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


view : Model -> Html Msg
view model =
    case model.pluginStatus of
        False ->
            text ""

        True ->
            div
                [ id "workflowForm" ]
                [ h3 [ class "page-title", style "margin-top" "0" ] [ text "Change validation status" ]
                , div [ class "section-with-doc" ]
                    [ div [ class "section-left" ]
                        [ form
                            [ id "workflowSettings" ]
                            [ ul []
                                [ settingInput model "workflowEnabled" " Enable change requests " Nothing
                                , settingInput model "selfVal" " Allow self validation " (Just selfValTooltip)
                                , settingInput model "selfDep" " Allow self deployment " (Just selfDepTooltip)
                                ]
                            , saveButton model
                            , saveMsg model
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


settingInput : Model -> String -> String -> Maybe String -> Html Msg
settingInput model settingId settingName tooltipDescOpt =
    let
        tooltip =
            case tooltipDescOpt of
                Just tooltipDesc ->
                    span
                        [ id (settingId ++ "Tooltip") ]
                        [ span
                            [ class "fa fa-info-circle icon-info"
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
                    , checked True
                    ]
                    []
                , label [ class "label-radio", for settingId ]
                    [ span [ class "ion ion-checkmark-round" ] [] ]
                , span [ class "ion ion-checkmark-round check-icon" ] []
                ]
            , label [ for settingId, class "form-control" ] [ text settingName, tooltip ]
            ]
        ]


saveButton : Model -> Html Msg
saveButton model =
    input
        [ id "workflowSubmit"
        , name "workflowSubmit"
        , type_ "submit"
        , class "btn btn-default"
        , value "Save change"
        , disabled True
        ]
        []


saveMsg : Model -> Html Msg
saveMsg model =
    span [ id "updateWorkflow" ] []
