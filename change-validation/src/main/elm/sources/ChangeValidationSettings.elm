module ChangeValidationSettings exposing (..)

import Browser
import Html exposing (Html, div, form, h3, input, label, li, p, span, text, ul)
import Html.Attributes as Attr
import Http exposing (Error, emptyBody, expectJson, header, jsonBody, request)
import Json.Decode exposing (Decoder, at, bool, field, index, list, map, map2, string)
import Json.Encode as E
import Task exposing (Task, andThen)



------------------------------
-- Init and main --
------------------------------


getApiUrl : Model -> String -> String
getApiUrl m url =
    m.contextPath ++ url


changeValidationPluginId : String
changeValidationPluginId =
    "com.normation.plugins.changevalidation.ChangeValidationPluginDef"


init : { contextPath : String } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model flags.contextPath (Debug.todo "plugin status") (Debug.todo "settings")
    in
    ( initModel, Task.attempt GetAllWorkflowSettings getAllWorkflowSettings )


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }



------------------------------
-- MODEL --
------------------------------


type alias Model =
    { contextPath : String
    , pluginStatus : WorkflowInfoStatus
    , settings : Maybe Settings
    }


type alias Settings =
    { workflowEnabled : WorkflowInfoStatus
    , selfVal : WorkflowInfoStatus
    , selfDep : WorkflowInfoStatus
    , workflowValidateAll : WorkflowInfoStatus
    }


type alias ViewState =
    { initSettings : Settings
    , formSettings : Settings
    }


type WorkflowInfoStatus
    = Enabled
    | Disabled


type
    Msg
    -- GET plugin status
    = GetChangeValidationStatus (Result Error WorkflowInfoStatus)
    | GetAllWorkflowSettings (Result Error Settings)
      -- GET setting
    | GetWorkflowEnabledSetting (Result Error WorkflowInfoStatus)
    | GetWorkflowSelfValidationSetting (Result Error WorkflowInfoStatus)
    | GetWorkflowSelfDeploymentSetting (Result Error WorkflowInfoStatus)
    | GetWorkflowValidateAllSetting (Result Error WorkflowInfoStatus)
      -- SET setting
    | SetWorkflowEnabledSetting (Result Error WorkflowInfoStatus)
    | SetWorkflowSelfValidationSetting (Result Error WorkflowInfoStatus)
    | SetWorkflowSelfDeploymentSetting (Result Error WorkflowInfoStatus)
    | SetWorkflowValidateAllSetting (Result Error WorkflowInfoStatus)


type alias PluginInfo =
    { pluginId : String
    , pluginStatus : WorkflowInfoStatus
    }



------------------------------
-- API --
------------------------------


getChangeValidationStatus : Model -> Cmd Msg
getChangeValidationStatus model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "X-API-Token" ]
                , url = getApiUrl model "/api/latest/plugins/info"
                , body = emptyBody
                , expect = expectJson GetChangeValidationStatus decodePluginStatus
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getSetting : Model -> String -> (Result Error a -> Msg) -> Decoder a -> Cmd Msg
getSetting model key msg dec =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("/secure/api/settings/" ++ key)
                , body = emptyBody
                , expect = expectJson msg dec
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getWorkflowEnabledSetting : Model -> Cmd Msg
getWorkflowEnabledSetting model =
    let
        settingId =
            "enable_change_request"
    in
    getSetting model settingId GetWorkflowEnabledSetting (decodeEnabledSetting settingId)


getWorkflowSelfValidationSetting : Model -> Cmd Msg
getWorkflowSelfValidationSetting model =
    let
        settingId =
            "enable_self_validation"
    in
    getSetting model settingId GetWorkflowSelfValidationSetting (decodeEnabledSetting settingId)


getWorkflowSelfDeploymentSetting : Model -> Cmd Msg
getWorkflowSelfDeploymentSetting model =
    let
        settingId =
            "enable_self_deployment"
    in
    getSetting model settingId GetWorkflowSelfDeploymentSetting (decodeEnabledSetting settingId)


getWorkflowValidateAllSetting : Model -> Cmd Msg
getWorkflowValidateAllSetting model =
    let
        settingId =
            "enable_validate_all"
    in
    getSetting model settingId GetWorkflowValidateAllSetting (decodeEnabledSetting settingId)


setSetting : Model -> String -> (Result Error a -> Msg) -> Decoder a -> Bool -> Cmd Msg
setSetting model settingId msg dec newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("/secure/api/settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson msg dec
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


setWorkflowEnabledSetting : Model -> Bool -> Cmd Msg
setWorkflowEnabledSetting model newValue =
    let
        settingId =
            "enable_change_request"
    in
    setSetting model settingId SetWorkflowEnabledSetting (decodeEnabledSetting settingId) newValue


setWorkflowSelfValidationSetting : Model -> Bool -> Cmd Msg
setWorkflowSelfValidationSetting model newValue =
    let
        settingId =
            "enable_self_validation"
    in
    setSetting model settingId SetWorkflowSelfValidationSetting (decodeEnabledSetting settingId) newValue


setWorkflowSelfDeploymentSetting : Model -> Bool -> Cmd Msg
setWorkflowSelfDeploymentSetting model newValue =
    let
        settingId =
            "enable_self_deployment"
    in
    setSetting model settingId SetWorkflowSelfDeploymentSetting (decodeEnabledSetting settingId) newValue


setWorkflowValidateAllSetting : Model -> Bool -> Cmd Msg
setWorkflowValidateAllSetting model newValue =
    let
        settingId =
            "enable_validate_all"
    in
    setSetting model settingId SetWorkflowValidateAllSetting (decodeEnabledSetting settingId) newValue


getAllWorkflowSettings : Task Error Settings
getAllWorkflowSettings =
    Debug.todo ""



------------------------------
-- ENCODE / DECODE JSON --
------------------------------


decodePluginInfo : Decoder PluginInfo
decodePluginInfo =
    map2
        PluginInfo
        (field "id" string)
        (field "status" (Json.Decode.map boolToStatus bool))


findChangeValidationStatus : List PluginInfo -> WorkflowInfoStatus
findChangeValidationStatus ls =
    case List.head (List.filter (\elt -> elt.pluginId == changeValidationPluginId) ls) of
        Just elt ->
            elt.pluginStatus

        Nothing ->
            Disabled


decodePluginStatus : Decoder WorkflowInfoStatus
decodePluginStatus =
    at [ "data" ]
        (field "plugins"
            (index 0
                (map findChangeValidationStatus (field "details" (list decodePluginInfo)))
            )
        )


boolToStatus b =
    case b of
        True ->
            Enabled

        False ->
            Disabled


decodeEnabledSetting : String -> Decoder WorkflowInfoStatus
decodeEnabledSetting fieldName =
    let
        decSetting =
            field "settings" (field fieldName (Json.Decode.map boolToStatus bool))
    in
    at [ "data" ] decSetting


encodeSetting : Bool -> E.Value
encodeSetting value =
    E.object [ ( "value", E.bool value ) ]



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
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
        Disabled ->
            text ""

        Enabled ->
            form
                [ Attr.id "change-validation-settings" ]
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
                        [ Attr.id (settingId ++ "Tooltip") ]
                        [ span
                            [ Attr.class "fa fa-info-circle icon-info"
                            , Attr.attribute "data-bs-toggle" "tooltip"
                            , Attr.attribute "data-bs-placement" "bottom"
                            , Attr.attribute "aria-label" tooltipDesc
                            , Attr.attribute "data-bs-original-title" tooltipDesc
                            ]
                            []
                        ]

                Nothing ->
                    text ""
    in
    li
        [ Attr.class "rudder-form" ]
        [ div
            [ Attr.class "input-group" ]
            [ label [ Attr.for settingId, Attr.class "input-group-addon" ]
                [ input
                    [ Attr.type_ "checkbox"
                    , Attr.id settingId
                    , Attr.class "twoCol"
                    , Attr.checked True
                    ]
                    []
                , label [ Attr.class "label-radio", Attr.for settingId ]
                    [ span [ Attr.class "ion ion-checkmark-round" ] [] ]
                , span [ Attr.class "ion ion-checkmark-round check-icon" ] []
                ]
            , label [ Attr.for settingId, Attr.class "form-control" ] [ text settingName, tooltip ]
            ]
        ]


saveButton : Model -> Html Msg
saveButton model =
    input
        [ Attr.id "workflowSubmit"
        , Attr.name "workflowSubmit"
        , Attr.type_ "submit"
        , Attr.class "btn btn-default"
        , Attr.value "Save change"
        , Attr.disabled True
        ]
        []


saveMsg : Model -> Html Msg
saveMsg model =
    span [ Attr.id "updateWorkflow" ] []



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
