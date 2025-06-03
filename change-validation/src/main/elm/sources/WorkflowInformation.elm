module WorkflowInformation exposing (..)

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, i, li, span, text, ul)
import Html.Attributes as Attr
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, at, bool, field, index, int, list, map2, maybe)
import Ports exposing (errorNotification)



------------------------------
-- Init and main --
------------------------------


getApiUrl : Model -> String -> String
getApiUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


getPendingValidationUrl : Model -> String
getPendingValidationUrl m =
    m.contextPath ++ "/secure/configurationManager/changes/changeRequests/Pending_validation"


getPendingDeploymentUrl : Model -> String
getPendingDeploymentUrl m =
    m.contextPath ++ "/secure/configurationManager/changes/changeRequests/Pending_deployment"


init : { contextPath : String } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model flags.contextPath NotSet
    in
    ( initModel, getWorkflowEnabledSetting initModel )


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


type alias PendingCount =
    { pendingValidation : Maybe Int
    , pendingDeployment : Maybe Int
    , totalCount : Int
    }


type PendingCountOpt
    = NotSet
    | PendingCountWithTotal PendingCount


type WorkflowInfoStatus
    = Enabled
    | Disabled


type alias Model =
    { contextPath : String
    , pendingCount : PendingCountOpt
    }


type Msg
    = GetPendingCount (Result Error PendingCountOpt)
    | GetWorkflowEnabledSetting (Result Error WorkflowInfoStatus)



------------------------------
-- API --
------------------------------
-- API call to get the number of pending change request for each respective "pending" status


getPendingCount : Model -> Cmd Msg
getPendingCount model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model "changevalidation/workflow/pendingCountByStatus"
                , body = emptyBody
                , expect = expectJson GetPendingCount decodePendingCountList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getWorkflowEnabledSetting : Model -> Cmd Msg
getWorkflowEnabledSetting model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model "settings/enable_change_request"
                , body = emptyBody
                , expect = expectJson GetWorkflowEnabledSetting decodeEnabledSetting
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- ENCODE / DECODE JSON --
------------------------------


pendingCountOpt : Maybe Int -> Maybe Int -> PendingCountOpt
pendingCountOpt pendingValidation pendingDeployment =
    case ( pendingValidation, pendingDeployment ) of
        ( Nothing, Nothing ) ->
            NotSet

        -- At least one field (pendingValidation and/or pendingDeployment) is not equal to Nothing
        ( _, _ ) ->
            PendingCountWithTotal
                { pendingDeployment = pendingDeployment
                , pendingValidation = pendingValidation
                , totalCount = Maybe.withDefault 0 pendingValidation + Maybe.withDefault 0 pendingDeployment
                }


decodePendingCountOpt : Decoder PendingCountOpt
decodePendingCountOpt =
    map2
        pendingCountOpt
        (maybe (field "pendingValidation" int))
        (maybe (field "pendingDeployment" int))


decodePendingCountList : Decoder PendingCountOpt
decodePendingCountList =
    at [ "data" ] (field "workflow" (index 0 decodePendingCountOpt))


decodeEnabledSetting : Decoder WorkflowInfoStatus
decodeEnabledSetting =
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
            field "settings" (field "enable_change_request" (Json.Decode.map boolToStatus bool))
    in
    at [ "data" ] decSetting



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetPendingCount result ->
            case result of
                Ok pendingCount ->
                    ( { model | pendingCount = pendingCount }, Cmd.none )

                -- Unauthorized access
                Err (Http.BadStatus 403) ->
                    ( { model | pendingCount = NotSet }, Cmd.none )

                Err err ->
                    ( model, errorNotification ("Error while trying to fetch pending change requests: " ++ getErrorMessage err) )

        GetWorkflowEnabledSetting result ->
            case result of
                Ok setting ->
                    case setting of
                        Enabled ->
                            ( model, getPendingCount model )

                        Disabled ->
                            ( { model | pendingCount = NotSet }, Cmd.none )

                -- Unauthorized access
                Err (Http.BadStatus 403) ->
                    ( { model | pendingCount = NotSet }, Cmd.none )

                Err err ->
                    ( model, errorNotification ("Error while trying to fetch change_requests_enabled setting: " ++ getErrorMessage err) )



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    let
        viewDropdown =
            case model.pendingCount of
                NotSet ->
                    viewDropdownToggle True "-"

                PendingCountWithTotal pc ->
                    viewDropdownToggle False ( String.fromInt pc.totalCount )
    in
    li
        [ Attr.class "nav-item dropdown notifications-menu"
        , Attr.id "workflow-app"
        ]
        [ viewDropdown
        , ul
            [ Attr.class "dropdown-menu"
            , Attr.attribute "role" "menu"
            ]
            [ li [] [ viewDropDownMenu model ] ]
        ]



viewDropDownMenu : Model -> Html Msg
viewDropDownMenu model =
    case model.pendingCount of
        NotSet ->
            ul [ Attr.class "menu" ] []

        PendingCountWithTotal pc ->
            ul [ Attr.class "menu" ]
                [ displayPendingCount pc.pendingValidation "Pending Validation" (getPendingValidationUrl model) "pe-2 fa fa-flag-o"
                , displayPendingCount pc.pendingDeployment "Pending Deployment" (getPendingDeploymentUrl model) "pe-2 fa fa-flag-checkered"
                ]


viewDropdownToggle : Bool -> String -> Html Msg
viewDropdownToggle isLoading displayedCount =
    a
        [ Attr.href "#"
        , Attr.class ( "dropdown-toggle " ++ if isLoading then "placeholder-glow" else "" )
        , Attr.attribute "data-bs-toggle" "dropdown"
        , Attr.attribute "role" "button"
        , Attr.attribute "aria-expanded" "false"
        ]
        [ span [] [ text "CR" ]
        , span
            [ Attr.id "number"
            , Attr.class ( "badge rudder-badge " ++ if isLoading then "placeholder" else "" )
            ]
            [ Html.text displayedCount ]
        ]


displayPendingCount : Maybe Int -> String -> String -> String -> Html Msg
displayPendingCount countOpt countName link flag =
    case countOpt of
        Nothing ->
            Html.text ""

        Just count ->
            li []
                [ a
                    [ Attr.href link
                    , Attr.class "pe-auto"
                    ]
                    [ span []
                        [ i
                            [ Attr.class flag
                            ]
                            []
                        , Html.text countName
                        ]
                    , span
                        [ Attr.class "float-end badge bg-light text-dark px-2"
                        ]
                        [ Html.text (String.fromInt count) ]
                    ]
                ]



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
