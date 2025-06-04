module WorkflowSettings exposing (..)

import DataTypes exposing (Msg, Settings, WorkflowSettingsMsg(..))
import Html exposing (Html, div, form, input, label, li, span, text, ul)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, name, type_, value)



------------------------------
-- Init and main --
------------------------------


initModel : String -> Bool -> Model
initModel contextPath canWrite =
    Model contextPath False canWrite Nothing



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


view : Model -> Html Msg
view model =
    case model.pluginStatus of
        False ->
            text ""

        True ->
            form
                [ id "workflowSettings" ]
                [ ul []
                    [ settingInput model "workflowEnabled" " Enable change requests " Nothing
                    , settingInput model "selfVal" " Allow self validation " (Just selfValTooltip)
                    , settingInput model "selfDep" " Allow self deployment " (Just selfDepTooltip)
                    ]
                , saveButton model
                , saveMsg model
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
                    , class "twoCol"
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
