module ChangeRequestChangesForm exposing (..)

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, button, div, input, label, li, option, select, table, text, ul)
import Html.Attributes exposing (attribute, class, id, name, placeholder, style, tabindex, type_, value)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, andThen, at, fail, field, index, int, map4, string, succeed)
import Ports exposing (errorNotification, readUrl)



------------------------------
-- INIT & MAIN
------------------------------


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }


init : { contextPath : String, hasWriteRights : Bool } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model
                flags.contextPath
                ChangeRequestIdNotSet
                NoView
    in
    ( initModel, Cmd.none )



------------------------------
-- MODEL
------------------------------


type alias Model =
    { contextPath : String
    , changeRequest : ChangeRequestDetailsOpt
    , viewState : ViewState
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestDetails (Result Error ChangeRequestDetails)


type ViewState
    = NoView
    | ViewError String


type alias ChangeRequestDetails =
    { title : String
    , state : String
    , id : Int
    , description : String
    }


type ChangeRequestDetailsOpt
    = Success ChangeRequestDetails
    | ChangeRequestIdNotSet



------------------------------
-- UPDATE
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestIdFromUrl crIdStr ->
            case String.toInt crIdStr of
                Just crId ->
                    ( model, getChangeRequestDetails model crId )

                Nothing ->
                    let
                        errMsg =
                            crIdStr ++ " is not a valid change request id"
                    in
                    ( { model | viewState = ViewError errMsg }, Cmd.none )

        GetChangeRequestDetails result ->
            case result of
                Ok changeRequestDetails ->
                    ( { model | changeRequest = Success changeRequestDetails }, Cmd.none )

                Err err ->
                    let
                        errMsg =
                            getErrorMessage err
                    in
                    ( { model | viewState = ViewError errMsg }
                    , errorNotification ("Error while trying to fetch change request details: " ++ errMsg)
                    )



------------------------------
-- API CALLS
------------------------------


getApiUrl : Model -> String -> String
getApiUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


getChangeRequestDetails : Model -> Int -> Cmd Msg
getChangeRequestDetails model crId =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("changeRequests/" ++ String.fromInt crId)
                , body = emptyBody
                , expect = expectJson GetChangeRequestDetails decodeChangeRequestDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


decodeChangeRequestDetails : Decoder ChangeRequestDetails
decodeChangeRequestDetails =
    at [ "data" ]
        (field "changeRequests"
            (index 0
                (map4
                    ChangeRequestDetails
                    (field "displayName" string)
                    (field "status" decodeChangeRequestStatus)
                    (field "id" int)
                    (field "description" string)
                )
            )
        )


decodeChangeRequestStatus : Decoder String
decodeChangeRequestStatus =
    string
        |> andThen
            (\str ->
                case str of
                    "Open" ->
                        succeed str

                    "Closed" ->
                        succeed str

                    "Pending validation" ->
                        succeed str

                    "Pending deployment" ->
                        succeed str

                    "Cancelled" ->
                        succeed str

                    "Deployed" ->
                        succeed str

                    _ ->
                        fail "Invalid change request status"
            )



------------------------------
-- VIEW
------------------------------


view : Model -> Html Msg
view model =
    div [ id "changeRequestChanges" ]
        [ div [ id "changesContainer" ]
            [ div [ id "changeSelector" ]
                [ div [ id "changeTree" ]
                    [ ul [] []
                    ]
                ]
            , div [ id "changeDisplay" ]
                [ ul
                    [ class "nav nav-underline"
                    , id "changeRequestTabMenu"
                    , attribute "role" "tablist"
                    ]
                    [ li
                        [ class "nav-item"
                        , attribute "role" "presentation"
                        ]
                        [ button
                            [ attribute "aria-selected" "true"
                            , attribute "aria-controls" "historyTab"
                            , attribute "role" "tab"
                            , type_ "button"
                            , attribute "data-bs-target" "#historyTab"
                            , attribute "data-bs-toggle" "tab"
                            , class "nav-link active"
                            ]
                            [ text "Change history" ]
                        ]
                    , li
                        [ class "nav-item"
                        , attribute "role" "presentation"
                        ]
                        [ button
                            [ attribute "aria-selected" "false"
                            , attribute "aria-controls" "diffTab"
                            , attribute "role" "tab"
                            , type_ "button"
                            , attribute "data-bs-target" "#diffTab"
                            , attribute "data-bs-toggle" "tab"
                            , class "nav-link"
                            , tabindex -1
                            ]
                            [ text "Diff" ]
                        ]
                    ]
                , div [ class "tab-content my-3" ]
                    [ div
                        [ style "max-height" "345px"
                        , style "overflow" "auto"
                        , class "tab-pane active"
                        , id "historyTab"
                        ]
                        [ div [ id "history" ]
                            [ div
                                [ id "changeHistory_wrapper"
                                , class "dataTables_wrapper no-footer"
                                ]
                                [ div [ class "dataTables_wrapper_top" ]
                                    [ div
                                        [ id "changeHistory_filter"
                                        , class "dataTables_filter"
                                        ]
                                        [ label []
                                            [ input
                                                [ type_ "search"
                                                , class ""
                                                , placeholder "Filter"
                                                , attribute "aria_controls" "changeHistory"
                                                ]
                                                []
                                            ]
                                        ]
                                    , div
                                        [ class "dataTables_length"
                                        , id "changeHistory_length"
                                        ]
                                        [ label []
                                            [ text "Show "
                                            , select
                                                [ name "changeHistory_length"
                                                , attribute "aria-controls" "changeHistory"
                                                , class ""
                                                ]
                                                [ option [ value "10" ] [ text "10" ]
                                                , option [ value "25" ] [ text "25" ]
                                                , option [ value "50" ] [ text "50" ]
                                                , option [ value "100" ] [ text "100" ]
                                                ]
                                            , text " entries"
                                            ]
                                        ]
                                    , table [] []
                                    ]
                                ]
                            ]
                        ]
                    ]
                ]
            ]
        ]



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
