module ChangeValidationSettings exposing (..)

import Html exposing (Html)
import Http exposing (Error, emptyBody, expectJson, header, jsonBody, request)
import Json.Decode exposing (Decoder, at, bool, field)
import Json.Encode as E



------------------------------
-- MODEL --
------------------------------


getApiUrl : Model -> String -> String
getApiUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


type ConfigType
    = WorkflowConfiguration
    | ValidationConfiguration


type alias Model =
    { contextPath : String
    , config : ConfigType
    , enabled : Bool
    , selfVal : Bool
    , selfDep : Bool
    }


type WorkflowInfoStatus
    = Enabled
    | Disabled


type
    Msg
    -- GET
    = GetWorkflowEnabledSetting (Result Error WorkflowInfoStatus)
    | GetWorkflowSelfValidationSetting (Result Error WorkflowInfoStatus)
    | GetWorkflowSelfDeploymentSetting (Result Error WorkflowInfoStatus)
    | GetWorkflowValidateAllSetting (Result Error WorkflowInfoStatus)
      -- SET
    | SetWorkflowEnabledSetting (Result Error WorkflowInfoStatus)
    | SetWorkflowSelfValidationSetting (Result Error WorkflowInfoStatus)
    | SetWorkflowSelfDeploymentSetting (Result Error WorkflowInfoStatus)
    | SetWorkflowValidateAllSetting (Result Error WorkflowInfoStatus)
      -- TODO : workflowLevelService.workflowLevelAllowsEnable
    | WorkflowLevelAllowsEnable



------------------------------
-- API --
------------------------------


getSetting : Model -> String -> (Result Error a -> Msg) -> Decoder a -> Cmd Msg
getSetting model key msg dec =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("settings/" ++ key)
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
                , url = getApiUrl model ("settings/" ++ settingId)
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



------------------------------
-- ENCODE / DECODE JSON --
------------------------------


decodeEnabledSetting : String -> Decoder WorkflowInfoStatus
decodeEnabledSetting fieldName =
    let
        boolToStatus b =
            case b of
                True ->
                    Enabled

                False ->
                    Disabled
    in
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
    Debug.todo "UPDATE"



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    Debug.todo "VIEW"



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
