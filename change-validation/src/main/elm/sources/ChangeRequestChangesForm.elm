module ChangeRequestChangesForm exposing (init)

import Browser
import Dict exposing (Dict)
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Attribute, Html, button, div, h4, li, pre, table, text, ul)
import Html.Attributes exposing (attribute, class, id, style, tabindex, type_)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, Value, andThen, at, bool, fail, field, index, int, lazy, list, map, map2, map3, map4, map5, maybe, oneOf, string, succeed, value)
import Json.Decode.Pipeline exposing (hardcoded, required)
import List.Nonempty as NonEmptyList
import Ports exposing (errorNotification, readUrl)
import RudderDataTable exposing (ColumnName(..))
import RudderDiff exposing (..)
import RudderTree



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
                NotSet
                NotSet
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
    , changeRequest : DetailsOpt ChangeRequestDetailsWithHistory
    , changes : DetailsOpt Changes
    , viewState : ViewState
    , changesTableModel : RudderDataTable.Model TableRow
    , tree : RudderTree.Model
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestDetailsWithHistory (Result Error ChangeRequestDetailsWithHistory)
    | GetChangeRequestChanges (Result Error Changes)
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


type alias Diff ty =
    RudderDiff.Diff ty


type alias DiffChange ty =
    RudderDiff.DiffChange ty


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
    , parameters : String
    }


type alias DirectiveDiff =
    { -- unmodifiable
      id : String
    , techniqueName : String

    -- modifiable
    , displayName : Diff String
    , shortDescription : Diff String
    , longDescription : Diff String
    , techniqueVersion : Diff String
    , priority : Diff Int
    , enabled : Diff Bool
    , system : Diff Bool
    , parameters : Diff String
    }


type alias Rule =
    { id : String
    , displayName : String
    , shortDescription : String
    , longDescription : String
    , targets : TargetList -- List of targets
    , directives : List ResourceIdent -- List of (DirectiveId, DirectiveName) pairs
    , enabled : Bool
    , system : Bool
    , categoryId : String -- Rule category id
    }


type alias RuleDiff =
    { -- unmodifiable
      id : String

    -- modifiable
    , displayName : Diff String
    , shortDescription : Diff String
    , longDescription : Diff String
    , targets : Diff TargetList -- List of targets
    , directives : Diff (List ResourceIdent) -- List of (DirectiveId, directiveName) pairs
    , enabled : Diff Bool
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
    , nodeList : List ResourceIdent -- (NodeId,name) pairs
    }


type alias NodeGroupDiff =
    { -- unmodifiable
      id : String

    -- modifiable
    , displayName : Diff String
    , description : Diff String
    , enabled : Diff Bool
    , dynamic : Diff Bool
    , properties : Diff Value
    , query : Diff Value
    , nodeList : Diff (List ResourceIdent) -- (NodeId,name) pairs
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
    , value : Diff Value
    , description : Diff String
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
    }


type DetailsOpt data
    = Success data
    | NotSet


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
                    ( model, Cmd.batch [ getChangeRequestDetailsWithHistory model crId, getChangeRequestChanges model crId ] )

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
                    ( { model | changeRequest = NotSet, viewState = ViewError errMsg }
                    , errorNotification ("Error while trying to fetch change request details: " ++ errMsg)
                    )

        GetChangeRequestChanges result ->
            case result of
                Ok changes ->
                    ( { model | changes = Success changes }, Cmd.none )

                Err err ->
                    let
                        errMsg =
                            getErrorMessage err
                    in
                    ( { model | changes = NotSet, viewState = ViewError errMsg }
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


getChangeRequestChanges : Model -> Int -> Cmd Msg
getChangeRequestChanges model crId =
    request
        { method = "GET"
        , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
        , url = getApiUrl model ("changevalidation/workflow/changeRequestChanges/" ++ String.fromInt crId)
        , body = emptyBody
        , expect = expectJson GetChangeRequestChanges decodeChanges
        , timeout = Nothing
        , tracker = Nothing
        }



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
        diffDec =
            map Change
                (at [ fieldName ] (map2 RudderDiff.DiffChange (field "from" dec) (field "to" dec)))

        noChangeDec =
            map NoChange (field fieldName dec)
    in
    oneOf [ diffDec, noChangeDec ]


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

        decodeDirective : String -> Decoder Directive
        decodeDirective parameters =
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
                |> hardcoded parameters
    in
    field "action" string
        |> andThen
            (\action ->
                case action of
                    "modify" ->
                        decodeDiffField "parameters" string
                            |> andThen
                                (\parameters ->
                                    at [ "change" ]
                                        (map Modify
                                            (succeed DirectiveDiff
                                                |> required "id" string
                                                |> map2 (|>) (field "techniqueName" string)
                                                |> map2 (|>) (decodeDiffField "displayName" string)
                                                |> map2 (|>) (decodeDiffField "shortDescription" string)
                                                |> map2 (|>) (decodeDiffField "longDescription" string)
                                                |> map2 (|>) (decodeDiffField "techniqueVersion" string)
                                                |> map2 (|>) (decodeDiffField "priority" int)
                                                |> map2 (|>) (decodeDiffField "enabled" bool)
                                                |> map2 (|>) (decodeDiffField "system" bool)
                                                |> hardcoded parameters
                                            )
                                        )
                                )

                    "create" ->
                        field "parameters" string
                            |> andThen (\parameters -> at [ "change" ] (map Create (decodeDirective parameters)))

                    "delete" ->
                        field "parameters" string
                            |> andThen (\parameters -> at [ "change" ] (map Delete (decodeDirective parameters)))

                    _ ->
                        fail "Invalid action type"
            )


decodeTarget : Decoder Target
decodeTarget =
    field "type" string
        |> andThen
            (\targetType ->
                case targetType of
                    "GroupTargetExtended" ->
                        map Simple (map Group decodeResourceIdent)

                    "NonGroupTargetExtended" ->
                        map Simple (map NonGroup (field "nonGroupTarget" string))

                    "ComposedTarget" ->
                        map Exclusion
                            (map2 TargetExclusion
                                (field "include" targetCompositionDec)
                                (field "exclude" targetCompositionDec)
                            )

                    _ ->
                        compositionDec
            )


decodeTargetList : Decoder TargetList
decodeTargetList =
    map TargetList (list (lazy (\_ -> decodeTarget)))


targetCompositionDec : Decoder TargetComposition
targetCompositionDec =
    field "type" string
        |> andThen
            (\compositionType ->
                case compositionType of
                    "OrComposition" ->
                        map Or (field "or" (lazy (\_ -> decodeTargetList)))

                    "AndComposition" ->
                        map And (field "and" (lazy (\_ -> decodeTargetList)))

                    _ ->
                        fail ""
            )


compositionDec =
    map Composition (lazy (\_ -> targetCompositionDec))


decodeRuleDiff : Decoder (ResourceDiff Rule RuleDiff)
decodeRuleDiff =
    let
        decodeRule : TargetList -> List ResourceIdent -> Decoder Rule
        decodeRule targets directives =
            succeed Rule
                |> required "id" string
                |> required "displayName" string
                |> required "shortDescription" string
                |> required "longDescription" string
                |> hardcoded targets
                |> hardcoded directives
                |> required "enabled" bool
                |> required "system" bool
                |> required "categoryId" string
    in
    field "action" string
        |> andThen
            (\action ->
                case action of
                    "modify" ->
                        decodeDiffField "ruleTargetInfo" decodeTargetList
                            |> andThen
                                (\targets ->
                                    decodeDiffField "directiveInfo" (list decodeResourceIdent)
                                        |> andThen
                                            (\directives ->
                                                at [ "change" ]
                                                    (map
                                                        Modify
                                                        (succeed RuleDiff
                                                            |> required "id" string
                                                            |> map2 (|>) (decodeDiffField "displayName" string)
                                                            |> map2 (|>) (decodeDiffField "shortDescription" string)
                                                            |> map2 (|>) (decodeDiffField "longDescription" string)
                                                            |> hardcoded targets
                                                            |> hardcoded directives
                                                            |> map2 (|>) (decodeDiffField "enabled" bool)
                                                        )
                                                    )
                                            )
                                )

                    "create" ->
                        field "ruleTargetInfo" decodeTargetList
                            |> andThen
                                (\targets ->
                                    field "directiveInfo" (list decodeResourceIdent)
                                        |> andThen (\directives -> at [ "change" ] (map Create (decodeRule targets directives)))
                                )

                    "delete" ->
                        field "ruleTargetInfo" decodeTargetList
                            |> andThen
                                (\targets ->
                                    field "directiveInfo" (list decodeResourceIdent)
                                        |> andThen
                                            (\directives ->
                                                at [ "change" ] (map Delete (decodeRule targets directives))
                                            )
                                )

                    _ ->
                        fail "Invalid action type"
            )


decodeResourceIdent : Decoder ResourceIdent
decodeResourceIdent =
    succeed ResourceIdent
        |> required "id" string
        |> required "name" string


decodeGroupDiff : Decoder (ResourceDiff NodeGroup NodeGroupDiff)
decodeGroupDiff =
    let
        decodeNodeGroup : List ResourceIdent -> Decoder NodeGroup
        decodeNodeGroup nodeList =
            succeed NodeGroup
                |> required "id" string
                |> required "displayName" string
                |> required "description" string
                |> required "enabled" bool
                |> required "dynamic" bool
                |> required "system" bool
                |> required "properties" value
                |> required "query" value
                |> hardcoded nodeList
    in
    field "action" string
        |> andThen
            (\action ->
                case action of
                    "modify" ->
                        decodeDiffField "nodeInfo" (list decodeResourceIdent)
                            |> andThen
                                (\nodeList ->
                                    at [ "change" ]
                                        (map
                                            Modify
                                            (succeed NodeGroupDiff
                                                |> required "id" string
                                                |> map2 (|>) (decodeDiffField "displayName" string)
                                                |> map2 (|>) (decodeDiffField "description" string)
                                                |> map2 (|>) (decodeDiffField "enabled" bool)
                                                |> map2 (|>) (decodeDiffField "dynamic" bool)
                                                |> map2 (|>) (decodeDiffField "properties" value)
                                                |> map2 (|>) (decodeDiffField "query" value)
                                                |> hardcoded nodeList
                                            )
                                        )
                                )

                    "create" ->
                        field "nodeInfo" (list decodeResourceIdent)
                            |> andThen (\nodeList -> at [ "change" ] (map Create (decodeNodeGroup nodeList)))

                    "delete" ->
                        field "nodeInfo" (list decodeResourceIdent)
                            |> andThen (\nodeList -> at [ "change" ] (map Delete (decodeNodeGroup nodeList)))

                    _ ->
                        fail "Invalid action type"
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
                                    (decodeDiffField "value" value)
                                    (decodeDiffField "description" string)
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
    at [ "data" ]
        (field "workflow"
            (index 0
                (field "changes"
                    (map4
                        Changes
                        (field "directives" (list decodeDirectiveDiff))
                        (field "rules" (list decodeRuleDiff))
                        (field "groups" (list decodeGroupDiff))
                        (field "parameters" (list decodeParameterDiff))
                    )
                )
            )
        )


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
    map5
        ChangeRequestDetails
        (field "displayName" string)
        (field "status" decodeChangeRequestStatus)
        (field "id" int)
        (field "description" string)
        (maybe (field "isMergeable" bool))



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
                        , attribute "role" "tabpanel"
                        , id "historyTab"
                        ]
                        [ div [ id "history" ] [ changesTable model ] ]
                    , div
                        [ style "max-height" "345px"
                        , style "overflow" "auto"
                        , style "margin-top" "-10px"
                        , class "tab-pane"
                        , attribute "role" "tabpanel"
                        , id "diffTab"
                        ]
                        [ div [ id "diff" ] [ diff model ] ]
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
    case model.changes of
        Success data ->
            let
                defaultOrDiff f1 f2 resource =
                    case resource of
                        Create r ->
                            f1 r

                        Delete r ->
                            f1 r

                        Modify r ->
                            f2 r

                directives =
                    List.map (defaultOrDiff displayDirective displayDirectiveDiff) data.directives

                groups =
                    List.map (defaultOrDiff displayGroup displayGroupDiff) data.groups

                rules =
                    List.map (defaultOrDiff displayRule displayRuleDiff) data.rules

                params =
                    List.map (defaultOrDiff displayParameter displayParameterDiff) data.parameters

                changes =
                    List.map (\change -> li [] [ change ]) (directives ++ groups ++ rules ++ params)
            in
            ul [] changes

        NotSet ->
            text ""


diffDefault : Diff ty -> ty
diffDefault diffVal =
    case diffVal of
        NoChange default ->
            default

        Change diffChange ->
            diffChange.from


displayDirective : Directive -> Html Msg
displayDirective directive =
    displayResourceDiff "Directive"
        [ displayField "Directive" (directiveLink directive.id directive.displayName)
        , displayStringField "Name" directive.displayName
        , displayStringField "Short description" directive.shortDescription
        , displayStringField "Technique name" directive.techniqueName
        , displayStringField "Technique version" directive.techniqueVersion
        , displayStringField "Priority" (String.fromInt directive.priority)
        , displayBoolField "Enabled" directive.enabled
        , displayBoolField "System" directive.system
        , displayStringField "Long description" directive.longDescription
        , displayStringField "Policy Mode" directive.policyMode
        , displayField "Parameters" (pre [] [ text directive.parameters ])
        ]


displayDirectiveDiff : DirectiveDiff -> Html Msg
displayDirectiveDiff directive =
    displayResourceDiff "Directive"
        [ displayField "Directive" (directiveLink directive.id (diffDefault directive.displayName))
        , displayStringDiff "Name" directive.displayName
        , displayStringDiff "Short description" directive.shortDescription
        , displayStringField "Technique name" directive.techniqueName
        , displayStringDiff "Technique version" directive.techniqueVersion
        , displayIntDiff "Priority" directive.priority
        , displayBoolDiff "Enabled" directive.enabled
        , displayBoolDiff "System" directive.system
        , displayStringDiff "Long description" directive.longDescription
        , displayStringField "Policy Mode" ""
        , displayDiffField (\param -> pre [] [ text param ]) "Parameters" directive.parameters
        ]


displayGroup : NodeGroup -> Html Msg
displayGroup group =
    displayResourceDiff "Node Group"
        [ displayField "Group" (groupLink group.id group.displayName)
        , displayStringField "Name" group.displayName
        , displayStringField "Description" group.description
        , displayBoolField "Enabled" group.enabled
        , displayBoolField "Dynamic" group.dynamic
        , displayBoolField "System" group.system
        , displayValueField "Properties" group.properties
        , displayValueField "Query" group.query
        , displayField "Node list" (displayIdentList "Group" groupLink group.nodeList)
        ]


displayGroupDiff : NodeGroupDiff -> Html Msg
displayGroupDiff group =
    displayResourceDiff "Node Group"
        [ displayField "Group" (groupLink group.id (diffDefault group.displayName))
        , displayStringDiff "Name" group.displayName
        , displayStringDiff "Description" group.description
        , displayBoolDiff "Enabled" group.enabled
        , displayBoolDiff "Dynamic" group.dynamic
        , displayStringField "System" ""
        , displayValueDiff "Properties" group.properties
        , displayValueDiff "Query" group.query
        , displayField "Node list" (displayIdentListDiff "Group" groupLink group.nodeList)
        ]


displayRule : Rule -> Html Msg
displayRule rule =
    displayResourceDiff "Rule"
        [ displayField "Rule" (ruleLink rule.id rule.displayName)
        , displayStringField "Name" rule.displayName
        , displayStringField "Category" rule.categoryId
        , displayStringField "Short description" rule.shortDescription
        , displayRuleTarget "Target" rule.targets
        , displayField "Directives" (displayIdentList "Directive" directiveLink rule.directives)
        , displayBoolField "Enabled" rule.enabled
        , displayBoolField "System" rule.system
        , displayStringField "Long description" rule.longDescription
        ]


displayRuleDiff : RuleDiff -> Html Msg
displayRuleDiff rule =
    displayResourceDiff "Rule"
        [ displayField "Rule" (ruleLink rule.id (diffDefault rule.displayName))
        , displayStringDiff "Name" rule.displayName
        , displayStringField "Category" ""
        , displayStringDiff "Short description" rule.shortDescription
        , displayRuleTargetDiff "Target" rule.targets
        , displayField "Directives" (displayIdentListDiff "Directive" directiveLink rule.directives)
        , displayBoolDiff "Enabled" rule.enabled
        , displayStringField "System" ""
        , displayStringDiff "Long description" rule.longDescription
        ]


displayParameter : GlobalParameter -> Html Msg
displayParameter param =
    displayResourceDiff "Global parameter"
        [ displayField "Global Parameter" (paramLink param.name)
        , displayStringField "Name" param.name
        , displayValueField "Value" param.value
        , displayStringField "Description" param.description
        ]


displayParameterDiff : GlobalParameterDiff -> Html Msg
displayParameterDiff param =
    displayResourceDiff "Global parameter"
        [ displayField "Global Parameter" (paramLink param.name)
        , displayStringField "Name" param.name
        , displayValueDiff "Value" param.value
        , displayStringDiff "Description" param.description
        ]


displayResourceDiff : String -> List (Html msg) -> Html msg
displayResourceDiff resourceType fields =
    div []
        [ h4 [] [ text (resourceType ++ " overview:") ]
        , ul [ class "evlogviewpad" ] fields
        ]



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
