module ChangeRequestChangesForm exposing (..)

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, button, div, i, input, label, li, option, select, span, table, text, ul)
import Html.Attributes exposing (attribute, class, href, id, name, placeholder, style, tabindex, type_, value)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, andThen, at, fail, field, index, int, map4, string, succeed)
import List.Nonempty as NonEmptyList
import Ports exposing (errorNotification, readUrl)
import RudderDataTable



------------------------------
-- INIT & MAIN
------------------------------


data =
    [ { action = "a"
      , actor = "b"
      , date = "23/5/2000"
      , reason = Nothing
      }
    , { action = "yyyyy"
      , actor = "zzzzz"
      , date = "23/5/2000"
      , reason = Nothing
      }
    ]


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
        table =
            RudderDataTable.init
                { columns =
                    NonEmptyList.Nonempty
                        { name = RudderDataTable.ColumnName "Action", accessor = .action }
                        [ { name = RudderDataTable.ColumnName "Actor", accessor = .actor }
                        , { name = RudderDataTable.ColumnName "Date", accessor = .date }
                        , { name = RudderDataTable.ColumnName "Reason", accessor = .reason >> Maybe.withDefault "" }
                        ]
                , sortBy = Nothing
                , sortOrder = Nothing
                , filter = Nothing
                }
                data

        initModel =
            Model
                flags.contextPath
                ChangeRequestIdNotSet
                NoView
                table
    in
    ( initModel, Cmd.none )



------------------------------
-- MODEL
------------------------------


type alias Model =
    { contextPath : String
    , changeRequest : ChangeRequestDetailsOpt
    , viewState : ViewState
    , changesTableModel : RudderDataTable.Model TableRow
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestDetails (Result Error ChangeRequestDetails)
    | ChangesTableMsg RudderDataTable.Msg


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


type alias TableRow =
    { action : String
    , actor : String
    , date : String
    , reason : Maybe String
    }



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

        ChangesTableMsg tableMsg ->
            let
                ( updatedModel, cmd, _ ) =
                    RudderDataTable.update tableMsg model.changesTableModel
            in
            ( { model | changesTableModel = updatedModel }, Cmd.map ChangesTableMsg cmd )



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
            [ div [ id "changeSelector" ] [ changeTree model ]
            , div [ id "changeDisplay" ]
                [ ul
                    [ class "nav nav-underline"
                    , id "changeRequestTabMenu"
                    , attribute "role" "tablist"
                    ]
                    [ li [ class "nav-item", attribute "role" "presentation" ]
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
                    , li [ class "nav-item", attribute "role" "presentation" ]
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
                            [ changesTable model ]
                        ]
                    ]
                ]
            ]
        ]


changeTree : Model -> Html Msg
changeTree model =
    div
        [ id "changeTree"
        , class "jstree jstree-1 jstree-default"
        , attribute "role" "tree"
        , attribute "aria-multiselectable" "true"
        , tabindex 0
        , attribute "aria-activedescendant" "changes"
        , attribute "aria-busy" "false"
        ]
        [ ul [ class "jstree-container-ul jstree-children", attribute "role" "presentation" ]
            [ li
                [ attribute "role" "none"
                , attribute "data-jstree" "{ \"type\" : \"changeType\" }"
                , id "changes"
                , class "jstree-node  jstree-closed jstree-last"
                ]
                [ i [ class "jstree-icon jstree-ocl", attribute "role" "presentation" ] []
                , a
                    [ class "jstree-anchor"
                    , href "#"
                    , tabindex -1
                    , attribute "role" "treeitem"
                    , attribute "aria-selected" "false"
                    , attribute "aria-level" "1"
                    , attribute "aria-expanded" "false"
                    ]
                    [ i
                        [ class "jstree-icon jstree-themeicon fa fa-folder jstree-themeicon-custom"
                        , attribute "role" "presentation"
                        ]
                        []
                    , span [] [ text "Changes" ]
                    ]
                ]
            ]
        ]


changesTable : Model -> Html Msg
changesTable model =
    Html.map ChangesTableMsg (RudderDataTable.view model.changesTableModel)



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
