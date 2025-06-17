module ChangeRequestChangesForm exposing (..)

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, button, div, i, li, span, table, text, ul)
import Html.Attributes exposing (attribute, class, href, id, style, tabindex, type_)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, andThen, at, bool, fail, field, index, int, list, map, map3, map4, maybe, string, succeed)
import List.Nonempty as NonEmptyList
import Ports exposing (errorNotification, readUrl)
import RudderDataTable



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
                []

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
    | GetChangeRequestDetailsWithHistory (Result Error ChangeRequestDetailsWithHistory)
    | ChangesTableMsg RudderDataTable.Msg


type ViewState
    = NoView
    | ViewError String


type alias ResourceChange =
    { resourceType : String
    , resourceName : String
    , resourceId : String
    , action : String
    }


type Event
    = ChangeLogEvent String
    | ResourceChangeEvent ResourceChange


type alias EventLog =
    { action : Event
    , actor : String
    , date : String
    , reason : Maybe String
    }


type alias ChangeRequestDetailsWithHistory =
    { changeRequest : ChangeRequestDetails
    , isPending : Bool
    , eventLogs : List EventLog
    }


type alias ChangeRequestDetails =
    { title : String
    , state : String
    , id : Int
    , description : String
    }


type ChangeRequestDetailsOpt
    = Success ChangeRequestDetailsWithHistory
    | ChangeRequestIdNotSet


type alias TableRow =
    { action : String
    , actor : String
    , date : String
    , reason : Maybe String
    }


eventLogToTableRow : EventLog -> TableRow
eventLogToTableRow log =
    let
        action =
            case log.action of
                ChangeLogEvent aaa ->
                    aaa

                ResourceChangeEvent change ->
                    change.action ++ " " ++ change.resourceType ++ " " ++ change.resourceName
    in
    { action = action, actor = log.actor, date = log.date, reason = log.reason }



------------------------------
-- UPDATE
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestIdFromUrl crIdStr ->
            case String.toInt crIdStr of
                Just crId ->
                    ( model, getChangeRequestDetailsWithHistory model crId )

                Nothing ->
                    let
                        errMsg =
                            crIdStr ++ " is not a valid change request id"
                    in
                    ( { model | viewState = ViewError errMsg }, Cmd.none )

        GetChangeRequestDetailsWithHistory result ->
            case result of
                Ok cr ->
                    let
                        updatedTable =
                            RudderDataTable.updateData (List.map eventLogToTableRow cr.eventLogs) model.changesTableModel
                    in
                    ( { model
                        | changeRequest = Success cr
                        , changesTableModel = updatedTable
                      }
                    , Cmd.none
                    )

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


getChangeRequestDetailsWithHistory : Model -> Int -> Cmd Msg
getChangeRequestDetailsWithHistory model crId =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("changevalidation/workflow/changeRequestWithHistory/" ++ String.fromInt crId)
                , body = emptyBody
                , expect = expectJson GetChangeRequestDetailsWithHistory decodeChangeRequestDetailsWithHistory
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


decodeChangeRequestDetailsWithHistory : Decoder ChangeRequestDetailsWithHistory
decodeChangeRequestDetailsWithHistory =
    at [ "data" ]
        (field "workflow"
            (index 0
                (map3 ChangeRequestDetailsWithHistory
                    (field "changeRequest" decodeChangeRequestDetails)
                    (field "isPending" bool)
                    (field "eventLogs" (list decodeEventLog))
                )
            )
        )


decodeEventLog : Decoder EventLog
decodeEventLog =
    map4
        EventLog
        (field "action" decodeEvent)
        (field "actor" string)
        (field "date" string)
        (maybe (field "reason" string))


decodeEvent : Decoder Event
decodeEvent =
    field "type" string
        |> andThen decodeEventContent


decodeEventContent : String -> Decoder Event
decodeEventContent eventType =
    case eventType of
        "ChangeLogEvent" ->
            map ChangeLogEvent (field "action" string)

        "ResourceChangeEvent" ->
            map ResourceChangeEvent
                (map4 ResourceChange
                    (field "resourceType" decodeResourceType)
                    (field "resourceName" string)
                    (field "resourceId" string)
                    (field "action" decodeAction)
                )

        _ ->
            fail "Invalid event log type"


decodeAction : Decoder String
decodeAction =
    string
        |> andThen
            (\s ->
                case s of
                    "create" ->
                        succeed "Create"

                    "delete" ->
                        succeed "Delete"

                    "modify" ->
                        succeed "Modify"

                    _ ->
                        fail "Invalid action"
            )


decodeResourceType : Decoder String
decodeResourceType =
    string
        |> andThen
            (\s ->
                case s of
                    "directive" ->
                        succeed s

                    "node group" ->
                        succeed s

                    "rule" ->
                        succeed s

                    "global parameter" ->
                        succeed s

                    _ ->
                        fail "Invalid resource type"
            )


decodeChangeRequestDetails : Decoder ChangeRequestDetails
decodeChangeRequestDetails =
    map4
        ChangeRequestDetails
        (field "displayName" string)
        (field "status" decodeChangeRequestStatus)
        (field "id" int)
        (field "description" string)


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
