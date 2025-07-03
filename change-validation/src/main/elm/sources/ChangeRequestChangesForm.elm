module ChangeRequestChangesForm exposing (init)

import Browser
import Dict exposing (Dict)
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Attribute, Html, button, div, h4, li, pre, table, text, ul)
import Html.Attributes exposing (attribute, class, id, style, tabindex, type_)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, Value, andThen, at, bool, fail, field, index, int, list, map, map2, map3, map4, map5, maybe, string, succeed, value)
import Json.Decode.Pipeline exposing (optionalAt, required, requiredAt)
import List.Nonempty as NonEmptyList
import Ports exposing (errorNotification, readUrl)
import RudderDataTable exposing (ColumnName(..))
import RudderDataTypes exposing (..)
import RudderDiff exposing (..)
import RudderLinkUtil exposing (ContextPath, directiveLink, getApiUrl, getContextPath, groupLink, nodeLink, paramLink, ruleLink)
import RudderTree exposing (..)



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
                (getContextPath flags.contextPath)
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
    { contextPath : ContextPath
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
    | CallApi (Model -> Cmd Msg)
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
    , system : Bool
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
    , categoryName : String -- Rule category name
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
    , query : Maybe Value
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
    , query : Diff (Maybe Value)
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



------------------------------
-- UPDATE
------------------------------


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


buildChangesTree : Changes -> RudderTree.Model
buildChangesTree changes =
    let
        getLeaf res =
            Leaf
                (case res of
                    Create resource ->
                        TreeNode resource.id resource.displayName

                    Delete resource ->
                        TreeNode resource.id resource.displayName

                    Modify modifyDiff ->
                        case modifyDiff.displayName of
                            NoChange name ->
                                TreeNode modifyDiff.id name

                            Change nameDiff ->
                                TreeNode modifyDiff.id nameDiff.from
                )

        getParamLeaf param =
            Leaf
                (case param of
                    Create res ->
                        TreeNode res.name res.name

                    Delete res ->
                        TreeNode res.name res.name

                    Modify res ->
                        TreeNode res.name res.name
                )

        makeTree name children =
            let
                node =
                    TreeNode ("__" ++ name) name
            in
            Branch node children Open

        directives =
            List.map getLeaf changes.directives |> makeTree "Directives"

        rules =
            List.map getLeaf changes.rules |> makeTree "Rules"

        groups =
            List.map getLeaf changes.groups |> makeTree "Groups"

        params =
            List.map getParamLeaf changes.parameters |> makeTree "Global Parameters"
    in
    RudderTree.Model (Root [ makeTree "Changes" [ directives, rules, groups, params ] ])


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestIdFromUrl crIdStr ->
            case String.toInt crIdStr of
                Just crId ->
                    ( model, Cmd.batch [ getChangeRequestDetailsWithHistory model crId, getChangeRequestChanges crId model ] )

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
                    ( { model | changeRequest = NotSet, viewState = ViewError errMsg }
                    , errorNotification ("Error while trying to fetch change request details: " ++ errMsg)
                    )

        GetChangeRequestChanges result ->
            case result of
                Ok changes ->
                    ( { model
                        | changes = Success changes
                        , tree = buildChangesTree changes
                      }
                    , Cmd.none
                    )

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

        CallApi call ->
            ( model, call model )



------------------------------
-- API CALLS
------------------------------


getChangeRequestDetailsWithHistory : Model -> Int -> Cmd Msg
getChangeRequestDetailsWithHistory model crId =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changevalidation/workflow/changeRequestWithHistory/" ++ String.fromInt crId)
                , body = emptyBody
                , expect = expectJson GetChangeRequestDetailsWithHistory decodeChangeRequestDetailsWithHistory
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getChangeRequestChanges : Int -> Model -> Cmd Msg
getChangeRequestChanges crId model =
    request
        { method = "GET"
        , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
        , url = getApiUrl model.contextPath ("changevalidation/workflow/changeRequestChanges/" ++ String.fromInt crId)
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
                |> requiredAt [ "change", "id" ] string
                |> requiredAt [ "change", "displayName" ] string
                |> requiredAt [ "change", "shortDescription" ] string
                |> requiredAt [ "change", "techniqueName" ] string
                |> requiredAt [ "change", "techniqueVersion" ] string
                |> requiredAt [ "change", "priority" ] int
                |> requiredAt [ "change", "enabled" ] bool
                |> requiredAt [ "change", "system" ] bool
                |> requiredAt [ "change", "longDescription" ] string
                |> requiredAt [ "change", "policyMode" ] decodePolicyMode
                |> required "parameters" string
    in
    field "action" string
        |> andThen
            (\action ->
                case action of
                    "create" ->
                        map Create decodeDirective

                    "delete" ->
                        map Delete decodeDirective

                    "modify" ->
                        map Modify
                            (succeed DirectiveDiff
                                |> requiredAt [ "change", "id" ] string
                                |> map2 (|>) (at [ "change", "techniqueName" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "displayName" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "shortDescription" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "longDescription" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "techniqueVersion" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "priority" ] int)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "enabled" ] bool)
                                |> map2 (|>) (at [ "change", "system" ] bool)
                                |> map2 (|>) (decodeDiffField "parameters" string)
                            )

                    _ ->
                        fail "Invalid action type"
            )


decodeRuleDiff : Decoder (ResourceDiff Rule RuleDiff)
decodeRuleDiff =
    let
        decodeRule : Decoder Rule
        decodeRule =
            succeed Rule
                |> requiredAt [ "change", "id" ] string
                |> requiredAt [ "change", "displayName" ] string
                |> requiredAt [ "change", "shortDescription" ] string
                |> requiredAt [ "change", "longDescription" ] string
                |> required "ruleTargetInfo" decodeTargetList
                |> required "directiveInfo" (list decodeResourceIdent)
                |> requiredAt [ "change", "enabled" ] bool
                |> requiredAt [ "change", "system" ] bool
                |> required "categoryName" string
    in
    field "action" string
        |> andThen
            (\action ->
                case action of
                    "create" ->
                        map Create decodeRule

                    "delete" ->
                        map Delete decodeRule

                    "modify" ->
                        map Modify
                            (succeed RuleDiff
                                |> requiredAt [ "change", "id" ] string
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "displayName" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "shortDescription" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "longDescription" ] string)
                                |> map2 (|>) (decodeDiffField "ruleTargetInfo" decodeTargetList)
                                |> map2 (|>) (decodeDiffField "directiveInfo" (list decodeResourceIdent))
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "enabled" ] bool)
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
        decodeNodeGroup : Decoder NodeGroup
        decodeNodeGroup =
            succeed NodeGroup
                |> requiredAt [ "change", "id" ] string
                |> requiredAt [ "change", "displayName" ] string
                |> requiredAt [ "change", "description" ] string
                |> requiredAt [ "change", "enabled" ] bool
                |> requiredAt [ "change", "dynamic" ] bool
                |> requiredAt [ "change", "system" ] bool
                |> requiredAt [ "change", "properties" ] value
                |> optionalAt [ "change", "query" ] (maybe value) Nothing
                |> required "nodeInfo" (list decodeResourceIdent)
    in
    field "action" string
        |> andThen
            (\action ->
                case action of
                    "create" ->
                        map Create decodeNodeGroup

                    "delete" ->
                        map Delete decodeNodeGroup

                    "modify" ->
                        map Modify
                            (succeed NodeGroupDiff
                                |> requiredAt [ "change", "id" ] string
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "displayName" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "description" ] string)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "enabled" ] bool)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "dynamic" ] bool)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "properties" ] value)
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "query" ] (maybe value))
                                |> map2 (|>) (decodeDiffField "nodeInfo" (list decodeResourceIdent))
                            )

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
            [ div [ id "changeSelector" ] [ viewChangeTree model.tree ]
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
                        [ div [ id "history" ] [ viewChangesTable model.changesTableModel ] ]
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


viewChangeTree : RudderTree.Model -> Html Msg
viewChangeTree tree =
    Html.map TreeMsg (RudderTree.view tree)


viewChangesTable : RudderDataTable.Model TableRow -> Html Msg
viewChangesTable table =
    Html.map ChangesTableMsg (RudderDataTable.view table)


diff : Model -> Html Msg
diff model =
    case model.changes of
        Success data ->
            let
                defaultOrDiff f1 f2 contextPath resource =
                    case resource of
                        Create r ->
                            f1 contextPath r

                        Delete r ->
                            f1 contextPath r

                        Modify r ->
                            f2 contextPath r

                directives =
                    List.map (defaultOrDiff displayDirective displayDirectiveDiff model.contextPath) data.directives

                groups =
                    List.map (defaultOrDiff displayGroup displayGroupDiff model.contextPath) data.groups

                rules =
                    List.map (defaultOrDiff displayRule displayRuleDiff model.contextPath) data.rules

                params =
                    List.map (defaultOrDiff displayParameter displayParameterDiff model.contextPath) data.parameters

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


displayDirective : ContextPath -> Directive -> Html Msg
displayDirective contextPath directive =
    displayResourceDiff "Directive"
        [ displayField "Directive" (directiveLink contextPath directive.id directive.displayName)
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


displayDirectiveDiff : ContextPath -> DirectiveDiff -> Html Msg
displayDirectiveDiff contextPath directive =
    displayResourceDiff "Directive"
        [ displayField "Directive" (directiveLink contextPath directive.id (diffDefault directive.displayName))
        , displayStringDiff "Name" directive.displayName
        , displayStringDiff "Short description" directive.shortDescription
        , displayStringField "Technique name" directive.techniqueName
        , displayStringDiff "Technique version" directive.techniqueVersion
        , displayIntDiff "Priority" directive.priority
        , displayBoolDiff "Enabled" directive.enabled
        , displayBoolField "System" directive.system
        , displayStringDiff "Long description" directive.longDescription
        , displayStringField "Policy Mode" ""
        , displayDiffField (\param -> pre [] [ text param ]) "Parameters" directive.parameters
        ]


displayGroup : ContextPath -> NodeGroup -> Html Msg
displayGroup contextPath group =
    displayResourceDiff "Node Group"
        [ displayField "Group" (groupLink contextPath group.id group.displayName)
        , displayStringField "Name" group.displayName
        , displayStringField "Description" group.description
        , displayBoolField "Enabled" group.enabled
        , displayBoolField "Dynamic" group.dynamic
        , displayBoolField "System" group.system
        , displayValueField "Properties" group.properties
        , displayMaybeField "Query" group.query displayValue
        , displayField "Node list" (displayIdentList "Node" (nodeLink contextPath) group.nodeList)
        ]


displayGroupDiff : ContextPath -> NodeGroupDiff -> Html Msg
displayGroupDiff contextPath group =
    displayResourceDiff "Node Group"
        [ displayField "Group" (groupLink contextPath group.id (diffDefault group.displayName))
        , displayStringDiff "Name" group.displayName
        , displayStringDiff "Description" group.description
        , displayBoolDiff "Enabled" group.enabled
        , displayBoolDiff "Dynamic" group.dynamic
        , displayStringField "System" ""
        , displayValueDiff "Properties" group.properties
        , displayDiffField (displayMaybe displayValue) "Query" group.query
        , displayField "Node list" (displayIdentListDiff "Node" (nodeLink contextPath) group.nodeList)
        ]


displayRule : ContextPath -> Rule -> Html Msg
displayRule contextPath rule =
    displayResourceDiff "Rule"
        [ displayField "Rule" (ruleLink contextPath rule.id rule.displayName)
        , displayStringField "Name" rule.displayName
        , displayStringField "Category" rule.categoryName
        , displayStringField "Short description" rule.shortDescription
        , displayRuleTarget "Target" contextPath rule.targets
        , displayField "Directives" (displayIdentList "Directive" (directiveLink contextPath) rule.directives)
        , displayBoolField "Enabled" rule.enabled
        , displayBoolField "System" rule.system
        , displayStringField "Long description" rule.longDescription
        ]


displayRuleDiff : ContextPath -> RuleDiff -> Html Msg
displayRuleDiff contextPath rule =
    displayResourceDiff "Rule"
        [ displayField "Rule" (ruleLink contextPath rule.id (diffDefault rule.displayName))
        , displayStringDiff "Name" rule.displayName
        , displayStringField "Category" ""
        , displayStringDiff "Short description" rule.shortDescription
        , displayRuleTargetDiff "Target" contextPath rule.targets
        , displayField "Directives" (displayIdentListDiff "Directive" (directiveLink contextPath) rule.directives)
        , displayBoolDiff "Enabled" rule.enabled
        , displayStringField "System" ""
        , displayStringDiff "Long description" rule.longDescription
        ]


displayParameter : ContextPath -> GlobalParameter -> Html Msg
displayParameter contextPath param =
    displayResourceDiff "Global parameter"
        [ displayField "Global Parameter" (paramLink contextPath param.name)
        , displayStringField "Name" param.name
        , displayValueField "Value" param.value
        , displayStringField "Description" param.description
        ]


displayParameterDiff : ContextPath -> GlobalParameterDiff -> Html Msg
displayParameterDiff contextPath param =
    displayResourceDiff "Global parameter"
        [ displayField "Global Parameter" (paramLink contextPath param.name)
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
