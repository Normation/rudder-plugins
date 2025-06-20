module ChangeRequestChangesForm exposing (init)

import Browser
import Dict
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, button, div, li, table, text, ul)
import Html.Attributes exposing (attribute, class, id, style, tabindex, type_)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, Value, andThen, at, bool, fail, field, index, int, lazy, list, map, map2, map3, map4, map6, maybe, oneOf, string, succeed, value)
import Json.Decode.Pipeline exposing (optional, required)
import List.Nonempty as NonEmptyList
import Ports exposing (errorNotification, readUrl)
import RudderDataTable exposing (ColumnName(..))
import RudderTree exposing (initFlatTree)



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
                , sortBy = Just (ColumnName "Date")
                , sortOrder = Nothing
                , filter = Nothing
                , options = { refresh = Nothing }
                }
                []

        initModel =
            Model
                flags.contextPath
                ChangeRequestIdNotSet
                NoView
                table
                RudderTree.init
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
    , tree : RudderTree.Model
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestDetailsWithHistory (Result Error ChangeRequestDetailsWithHistory)
    | ChangesTableMsg RudderDataTable.Msg
    | TreeMsg RudderTree.Msg


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


type alias Var =
    { name : String
    , value : String
    }


type alias Section =
    { name : String
    , sections : Maybe SectionList
    , vars : Maybe (List Var)
    }


type SectionList
    = SectionList (List Section)


type TargetComposition
    = Or TargetList
    | And TargetList


type alias TargetComposed =
    { include : TargetComposition, exclude : TargetComposition }


type Target
    = Simple String
    | Composed TargetComposed
    | Composition TargetComposition


type TargetList
    = TargetList (List Target)


type alias DiffChange fieldType =
    { from : fieldType
    , to : fieldType
    }


type Diff fieldType
    = NoChange fieldType
    | Change (DiffChange fieldType)


type alias Directive =
    { id : String
    , displayName : String
    , shortDescription : String
    , techniqueName : String
    , techniqueVersion : String
    , priority : Int
    , enabled : Bool
    , system : Bool
    , longDescription : String
    , policyMode : String
    , parameters : Section
    }


type alias DirectiveDiff =
    { -- unmodifiable
      id : String
    , techniqueName : String

    -- modifiable
    , displayName : Diff String
    , shortDescription : Maybe (Diff String)
    , longDescription : Maybe (Diff String)
    , techniqueVersion : Maybe (Diff String)
    , priority : Maybe (Diff Int)
    , enabled : Maybe (Diff Bool)
    , system : Maybe (Diff Bool)
    , policyMode : Maybe (Diff String)
    , parameters : Maybe (Diff Section)
    }


type alias Rule =
    { id : String
    , displayName : String
    , shortDescription : String
    , longDescription : String
    , targets : List Target -- List of targets
    , directives : List String -- List of directive ids
    , enabled : Bool
    , system : Bool
    , categoryId : String -- Rule category id
    }


type alias RuleDiff =
    { -- unmodifiable
      id : String

    -- modifiable
    , displayName : Diff String
    , shortDescription : Maybe (Diff String)
    , longDescription : Maybe (Diff String)
    , targets : Maybe (Diff (List String)) -- List of targets
    , directives : Maybe (Diff (List String)) -- List of directive ids
    , enabled : Maybe (Diff Bool)
    , system : Maybe (Diff Bool)
    , categoryId : Maybe (Diff String) -- Rule category id
    }


type alias NodeGroup =
    { id : String
    , displayName : String
    , description : String
    , enabled : Bool
    , dynamic : Bool
    , system : Bool
    , properties : Value
    , query : Value
    , nodeList : List String -- List of node ids
    }


type alias NodeGroupDiff =
    { -- unmodifiable
      id : String

    -- modifiable
    , displayName : Diff String
    , description : Maybe (Diff String)
    , enabled : Maybe (Diff Bool)
    , dynamic : Maybe (Diff Bool)
    , properties : Maybe (Diff Value)
    , query : Maybe (Diff Value)
    , nodeList : Maybe (Diff (List String)) -- List of node ids
    }


type alias GlobalParameter =
    { name : String
    , value : Value
    , description : String
    }


type alias GlobalParameterDiff =
    { -- unmodifiable
      name : String

    -- modifiable
    , value : Maybe (Diff Value)
    , description : Maybe (Diff String)
    }


type ResourceDiff resource modifyDiff
    = Create resource
    | Delete resource
    | Modify modifyDiff


type alias Changes =
    { directives : List (ResourceDiff Directive DirectiveDiff)
    , rules : List (ResourceDiff Rule RuleDiff)
    , groups : List (ResourceDiff NodeGroup NodeGroupDiff)
    , parameters : List (ResourceDiff GlobalParameter GlobalParameterDiff)
    }


type alias ChangeRequestDetails =
    { title : String
    , state : String
    , id : Int
    , description : String
    , isMergeable : Maybe Bool
    , changes : Changes
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
                ChangeLogEvent event ->
                    event

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
                        , tree = RudderTree.initFlatTree (Dict.fromList [])
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

        TreeMsg treeMsg ->
            let
                ( updatedTree, cmd ) =
                    RudderTree.update treeMsg model.tree
            in
            ( { model | tree = updatedTree }, Cmd.map TreeMsg cmd )



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



------------------------------
-- JSON DECODERS
------------------------------


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
        |> andThen
            (\eventType ->
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
            )


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


decodeDiffField : String -> Decoder fieldType -> Decoder (Diff fieldType)
decodeDiffField fieldName dec =
    let
        noChangeDec =
            map NoChange (field fieldName dec)

        diffDec =
            map Change
                (at [ fieldName ] (map2 DiffChange (field "from" dec) (field "to" dec)))
    in
    oneOf [ noChangeDec, diffDec ]


decodeSection : Decoder Section
decodeSection =
    let
        decodeVar =
            map2 Var (field "name" decodeResourceType) (field "value" decodeResourceType)
    in
    field "section"
        (succeed Section
            |> required "name" string
            |> map2 (|>) (maybe (field "sections" (lazy (\_ -> decodeSectionList))))
            |> map2 (|>) (maybe (field "vars" (list decodeVar)))
        )


decodeSectionList : Decoder SectionList
decodeSectionList =
    map SectionList (list (lazy (\_ -> decodeSection)))


decodeDirectiveDiff : Decoder (ResourceDiff Directive DirectiveDiff)
decodeDirectiveDiff =
    let
        decodePolicyMode : Decoder String
        decodePolicyMode =
            string
                |> andThen
                    (\policyMode ->
                        if (policyMode == "default") || (policyMode == "audit") || (policyMode == "enforce") then
                            succeed policyMode

                        else
                            fail "Invalid policy mode"
                    )

        decodeDirective : Decoder Directive
        decodeDirective =
            succeed Directive
                |> required "id" string
                |> required "displayName" string
                |> required "shortDescription" string
                |> required "techniqueName" string
                |> required "techniqueVersion" string
                |> required "priority" int
                |> required "enabled" bool
                |> required "system" bool
                |> required "longDescription" string
                |> required "policyMode" decodePolicyMode
                |> required "parameters" decodeSection
    in
    field "action" string
        |> andThen
            (\action ->
                at [ "change" ]
                    (case action of
                        "modify" ->
                            map Modify
                                (succeed DirectiveDiff
                                    |> required "id" string
                                    |> map2 (|>) (field "techniqueName" string)
                                    |> map2 (|>) (decodeDiffField "displayName" string)
                                    |> map2 (|>) (maybe (decodeDiffField "shortDescription" string))
                                    |> map2 (|>) (maybe (decodeDiffField "longDescription" string))
                                    |> map2 (|>) (maybe (decodeDiffField "techniqueVersion" string))
                                    |> map2 (|>) (maybe (decodeDiffField "priority" int))
                                    |> map2 (|>) (maybe (decodeDiffField "enabled" bool))
                                    |> map2 (|>) (maybe (decodeDiffField "system" bool))
                                    |> map2 (|>) (maybe (decodeDiffField "policyMode" decodePolicyMode))
                                    |> map2 (|>) (maybe (decodeDiffField "parameters" decodeSection))
                                )

                        "create" ->
                            map Create decodeDirective

                        "delete" ->
                            map Delete decodeDirective

                        _ ->
                            fail "Invalid action type"
                    )
            )


decodeTarget : Decoder Target
decodeTarget =
    let
        simpleDec =
            map Simple string

        composedDec =
            map Composed
                (map2 TargetComposed
                    (field "include" targetCompositionDec)
                    (field "exclude" targetCompositionDec)
                )
    in
    oneOf [ simpleDec, composedDec, compositionDec ]


decodeTargetList : Decoder TargetList
decodeTargetList =
    map TargetList (list (lazy (\_ -> decodeTarget)))


targetCompositionDec : Decoder TargetComposition
targetCompositionDec =
    let
        decodeOr =
            map Or (field "or" (lazy (\_ -> decodeTargetList)))

        decodeAnd =
            map And (field "or" (lazy (\_ -> decodeTargetList)))
    in
    oneOf [ decodeOr, decodeAnd ]


compositionDec =
    map Composition (lazy (\_ -> targetCompositionDec))


decodeRuleDiff : Decoder (ResourceDiff Rule RuleDiff)
decodeRuleDiff =
    let
        decodeRule : Decoder Rule
        decodeRule =
            succeed Rule
                |> required "id" string
                |> required "displayName" string
                |> required "shortDescription" string
                |> required "longDescription" string
                |> required "targets" (list decodeTarget)
                |> required "directives" (list string)
                |> required "enabled" bool
                |> required "system" bool
                |> required "categoryId" string
    in
    field "action" string
        |> andThen
            (\action ->
                at [ "change" ]
                    (case action of
                        "modify" ->
                            map Modify
                                (succeed RuleDiff
                                    |> required "id" string
                                    |> map2 (|>) (decodeDiffField "displayName" string)
                                    |> map2 (|>) (maybe (decodeDiffField "shortDescription" string))
                                    |> map2 (|>) (maybe (decodeDiffField "longDescription" string))
                                    |> map2 (|>) (maybe (decodeDiffField "targets" (list string)))
                                    |> map2 (|>) (maybe (decodeDiffField "directives" (list string)))
                                    |> map2 (|>) (maybe (decodeDiffField "enabled" bool))
                                    |> map2 (|>) (maybe (decodeDiffField "system" bool))
                                    |> map2 (|>) (maybe (decodeDiffField "categoryId" string))
                                )

                        "create" ->
                            map Create decodeRule

                        "delete" ->
                            map Delete decodeRule

                        _ ->
                            fail "Invalid action type"
                    )
            )


decodeGroupDiff : Decoder (ResourceDiff NodeGroup NodeGroupDiff)
decodeGroupDiff =
    let
        decodeNodeGroup : Decoder NodeGroup
        decodeNodeGroup =
            succeed NodeGroup
                |> required "id" string
                |> required "displayName" string
                |> required "description" string
                |> required "enabled" bool
                |> required "dynamic" bool
                |> required "system" bool
                |> required "properties" value
                |> required "query" value
                |> optional "nodeList" (list string) []
    in
    field "action" string
        |> andThen
            (\action ->
                at [ "change" ]
                    (case action of
                        "modify" ->
                            map
                                Modify
                                (succeed NodeGroupDiff
                                    |> required "id" string
                                    |> map2 (|>) (decodeDiffField "displayName" string)
                                    |> map2 (|>) (maybe (decodeDiffField "description" string))
                                    |> map2 (|>) (maybe (decodeDiffField "enabled" bool))
                                    |> map2 (|>) (maybe (decodeDiffField "dynamic" bool))
                                    |> map2 (|>) (maybe (decodeDiffField "properties" value))
                                    |> map2 (|>) (maybe (decodeDiffField "query" value))
                                    |> map2 (|>) (maybe (decodeDiffField "nodeList" (list string)))
                                )

                        "create" ->
                            map Create decodeNodeGroup

                        "delete" ->
                            map Delete decodeNodeGroup

                        _ ->
                            fail "Invalid action type"
                    )
            )


decodeParameterDiff : Decoder (ResourceDiff GlobalParameter GlobalParameterDiff)
decodeParameterDiff =
    let
        decodeGlobalParameter : Decoder GlobalParameter
        decodeGlobalParameter =
            map3
                GlobalParameter
                (field "id" string)
                (field "value" value)
                (field "description" string)
    in
    field "action" string
        |> andThen
            (\action ->
                at [ "change" ]
                    (case action of
                        "modify" ->
                            map Modify
                                (map3
                                    GlobalParameterDiff
                                    (field "name" string)
                                    (maybe (decodeDiffField "value" value))
                                    (maybe (decodeDiffField "description" string))
                                )

                        "create" ->
                            map Create decodeGlobalParameter

                        "delete" ->
                            map Delete decodeGlobalParameter

                        _ ->
                            fail "Invalid action type"
                    )
            )


decodeChanges : Decoder Changes
decodeChanges =
    map4
        Changes
        (field "directives" (list decodeDirectiveDiff))
        (field "rules" (list decodeRuleDiff))
        (field "groups" (list decodeGroupDiff))
        (field "parameters" (list decodeParameterDiff))


decodeChangeRequestDetails : Decoder ChangeRequestDetails
decodeChangeRequestDetails =
    let
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
    in
    map6
        ChangeRequestDetails
        (field "displayName" string)
        (field "status" decodeChangeRequestStatus)
        (field "id" int)
        (field "description" string)
        (maybe (field "isMergeable" bool))
        (field "changes" decodeChanges)



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
                        [ div [ id "history" ] [ changesTable model ]
                        , div [ id "diff" ] [ diff model ]
                        ]
                    ]
                ]
            ]
        ]


changeTree : Model -> Html Msg
changeTree model =
    Html.map TreeMsg (RudderTree.view model.tree)


changesTable : Model -> Html Msg
changesTable model =
    Html.map ChangesTableMsg (RudderDataTable.view model.changesTableModel)


diff : Model -> Html Msg
diff model =
    text ""



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
