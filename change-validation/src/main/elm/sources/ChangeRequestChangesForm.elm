module ChangeRequestChangesForm exposing (Changes, Diff, Directive, DirectiveDiff, GlobalParameter, GlobalParameterDiff, Model, Msg, NodeGroup, NodeGroupDiff, ResourceDiff, Rule, RuleDiff, initModel, update, updateChangeRequestDetails, view)

import ErrorMessages exposing (getErrorMessage)
import Html exposing (Attribute, Html, button, div, h4, li, pre, table, text, ul)
import Html.Attributes exposing (attribute, class, id, style, tabindex, type_)
import Html.Events exposing (onClick)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, Value, andThen, at, bool, fail, field, index, int, list, map, map2, map3, map4, maybe, string, succeed, value)
import Json.Decode.Pipeline exposing (optionalAt, required, requiredAt)
import List.Nonempty as NonEmptyList
import Ordering
import Ports exposing (errorNotification)
import RudderDataTable exposing (Column, ColumnName(..))
import RudderDataTypes exposing (..)
import RudderDiff exposing (..)
import RudderLinkUtil exposing (ContextPath, directiveLink, directiveLinkWithId, getApiUrl, getContextPath, groupLink, groupLinkWithId, nodeLinkWithId, paramLink, ruleLink, ruleLinkWithId)
import RudderTree exposing (..)



------------------------------
-- INIT & MAIN
------------------------------


initModel : { contextPath : String } -> Model
initModel { contextPath } =
    let
        columns =
            NonEmptyList.Nonempty (Column (ColumnName "Action") (\{ action } -> displayEventAction (getContextPath contextPath) action) Nothing (Ordering.byFieldWith Ordering.natural (.action >> eventActionText)))
                [ Column (ColumnName "Actor") (\{ actor } -> text actor) Nothing (Ordering.byField .actor)
                , Column (ColumnName "Date") (\{ date } -> text date) Nothing (Ordering.byField .date)
                , Column (ColumnName "Reason") (\{ reason } -> text <| Maybe.withDefault "" reason) Nothing (Ordering.byFieldWith Ordering.natural (.reason >> Maybe.withDefault ""))
                ]

        filters =
            RudderDataTable.filterByValues (\{ action, actor, date, reason } -> [ eventActionText action, actor, date, reason |> Maybe.withDefault "" ])

        table =
            RudderDataTable.init
                (RudderDataTable.buildConfig.newConfig columns filters
                    |> RudderDataTable.buildConfig.withSortBy (ColumnName "Date")
                    |> RudderDataTable.buildConfig.withSortOrder RudderDataTable.Desc
                    |> RudderDataTable.buildConfig.withOptions
                        (RudderDataTable.buildOptions.newOptions |> RudderDataTable.buildOptions.withFilter [])
                )
                []

        model =
            Model
                (getContextPath contextPath)
                NoView
                NoView
                table
                RudderTree.init
    in
    model



------------------------------
-- MODEL
------------------------------


type alias Model =
    { contextPath : ContextPath
    , changesView : ViewState Changes
    , changeRequestView : ViewState ChangeRequestMainDetails
    , changesTableModel : RudderDataTable.Model EventLog Msg
    , tree : RudderTree.Model
    }


type Msg
    = GetChangeRequestChanges (Result Error Changes)
    | CallApi (Model -> Cmd Msg)
    | ChangesTableMsg (RudderDataTable.Msg Msg)
    | TreeMsg RudderTree.Msg


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
    , policyMode : Diff (Maybe String)
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
    , system : Diff Bool
    , categoryName : Diff String
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
    , system : Diff Bool
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


type alias TableRow =
    { action : Html (RudderDataTable.Msg Msg)
    , actor : String
    , date : String
    , reason : Maybe String
    }



------------------------------
-- UPDATE
------------------------------


buildChangesTree : ChangesSummary -> RudderTree.Model
buildChangesTree changes =
    let
        getLeaf resName =
            Leaf (TreeNode resName resName)

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
            List.map getLeaf changes.parameters |> makeTree "Global Parameters"
    in
    RudderTree.Model (Root [ makeTree "Changes" [ directives, rules, groups, params ] ])


updateChangeRequestDetails : ChangeRequestMainDetails -> Model -> Model
updateChangeRequestDetails cr model =
    let
        updatedTable =
            RudderDataTable.updateData cr.eventLogs model.changesTableModel
    in
    { model
        | changeRequestView = Success cr
        , changesTableModel = updatedTable
        , tree = buildChangesTree cr.changeRequest.changesSummary
    }


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestChanges result ->
            case result of
                Ok changes ->
                    ( { model | changesView = Success changes }, Cmd.none )

                Err err ->
                    let
                        errMsg =
                            getErrorMessage err
                    in
                    ( { model | changesView = ViewError errMsg }
                    , errorNotification ("Error while trying to fetch change request details: " ++ errMsg)
                    )

        ChangesTableMsg tableMsg ->
            let
                ( updatedModel, cmd, _ ) =
                    RudderDataTable.update tableMsg model.changesTableModel
            in
            ( { model | changesTableModel = updatedModel }, cmd )

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
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "policyMode" ] (maybe decodePolicyMode))
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
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "system" ] bool)
                                |> map2 (|>) (decodeDiffField "categoryName" string)
                            )

                    _ ->
                        fail "Invalid action type"
            )


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
                                |> map2 (|>) (decodeDiffFieldAt [ "change", "system" ] bool)
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



------------------------------
-- VIEW
------------------------------


view : Model -> Html Msg
view model =
    let
        getDiff : List (Attribute Msg)
        getDiff =
            case ( model.changeRequestView, model.changesView ) of
                ( Success cr, NoView ) ->
                    [ onClick (CallApi (getChangeRequestChanges cr.changeRequest.id)) ]

                ( _, _ ) ->
                    []
    in
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
                            ([ attribute "aria-selected" "false"
                             , attribute "aria-controls" "diffTab"
                             , attribute "role" "tab"
                             , type_ "button"
                             , attribute "data-bs-target" "#diffTab"
                             , attribute "data-bs-toggle" "tab"
                             , class "nav-link"
                             , tabindex -1
                             ]
                                ++ getDiff
                            )
                            [ text "Diff" ]
                        ]
                    ]
                , div [ class "tab-content my-3" ]
                    [ div
                        [ class "tab-pane active"
                        , style "padding" "10px"
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


viewChangesTable : RudderDataTable.Model EventLog Msg -> Html Msg
viewChangesTable table =
    Html.map ChangesTableMsg (RudderDataTable.view table)


diff : Model -> Html Msg
diff model =
    case model.changesView of
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

        _ ->
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
        [ displayField "Directive" (directiveLinkWithId contextPath directive.id directive.displayName)
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
        [ displayField "Directive" (directiveLinkWithId contextPath directive.id (diffDefault directive.displayName))
        , displayStringDiff "Name" directive.displayName
        , displayStringDiff "Short description" directive.shortDescription
        , displayStringField "Technique name" directive.techniqueName
        , displayStringDiff "Technique version" directive.techniqueVersion
        , displayIntDiff "Priority" directive.priority
        , displayBoolDiff "Enabled" directive.enabled
        , displayBoolField "System" directive.system
        , displayStringDiff "Long description" directive.longDescription
        , displayDiffField (displayMaybe text) "Policy Mode" directive.policyMode
        , displayDiffField (\param -> pre [] [ text param ]) "Parameters" directive.parameters
        ]


displayGroup : ContextPath -> NodeGroup -> Html Msg
displayGroup contextPath group =
    displayResourceDiff "Node Group"
        [ displayField "Group" (groupLinkWithId contextPath group.id group.displayName)
        , displayStringField "Name" group.displayName
        , displayStringField "Description" group.description
        , displayBoolField "Enabled" group.enabled
        , displayBoolField "Dynamic" group.dynamic
        , displayBoolField "System" group.system
        , displayValueField "Properties" group.properties
        , displayMaybeField "Query" group.query displayValue
        , displayField "Node list" (displayIdentList "Node" (nodeLinkWithId contextPath) group.nodeList)
        ]


displayGroupDiff : ContextPath -> NodeGroupDiff -> Html Msg
displayGroupDiff contextPath group =
    displayResourceDiff "Node Group"
        [ displayField "Group" (groupLinkWithId contextPath group.id (diffDefault group.displayName))
        , displayStringDiff "Name" group.displayName
        , displayStringDiff "Description" group.description
        , displayBoolDiff "Enabled" group.enabled
        , displayBoolDiff "Dynamic" group.dynamic
        , displayBoolDiff "System" group.system
        , displayValueDiff "Properties" group.properties
        , displayDiffField (displayMaybe displayValue) "Query" group.query
        , displayField "Node list" (displayIdentListDiff "Node" (nodeLinkWithId contextPath) group.nodeList)
        ]


displayRule : ContextPath -> Rule -> Html Msg
displayRule contextPath rule =
    displayResourceDiff "Rule"
        [ displayField "Rule" (ruleLinkWithId contextPath rule.id rule.displayName)
        , displayStringField "Name" rule.displayName
        , displayStringField "Category" rule.categoryName
        , displayStringField "Short description" rule.shortDescription
        , displayRuleTarget "Target" contextPath rule.targets
        , displayField "Directives" (displayIdentList "Directive" (directiveLinkWithId contextPath) rule.directives)
        , displayBoolField "Enabled" rule.enabled
        , displayBoolField "System" rule.system
        , displayStringField "Long description" rule.longDescription
        ]


displayRuleDiff : ContextPath -> RuleDiff -> Html Msg
displayRuleDiff contextPath rule =
    displayResourceDiff "Rule"
        [ displayField "Rule" (ruleLinkWithId contextPath rule.id (diffDefault rule.displayName))
        , displayStringDiff "Name" rule.displayName
        , displayStringDiff "Category" rule.categoryName
        , displayStringDiff "Short description" rule.shortDescription
        , displayRuleTargetDiff "Target" contextPath rule.targets
        , displayField "Directives" (displayIdentListDiff "Directive" (directiveLinkWithId contextPath) rule.directives)
        , displayBoolDiff "Enabled" rule.enabled
        , displayBoolDiff "System" rule.system
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


displayEventAction : ContextPath -> Event -> Html msg
displayEventAction contextPath action =
    case action of
        ChangeLogEvent event ->
            text event

        ResourceChangeEvent change ->
            case change.resourceType of
                DirectiveRes ->
                    Html.span [] [ text (change.action ++ " directive "), directiveLink contextPath change.resourceId change.resourceName ]

                NodeGroupRes ->
                    Html.span [] [ text (change.action ++ " group "), groupLink contextPath change.resourceId change.resourceName ]

                RuleRes ->
                    Html.span [] [ text (change.action ++ " rule "), ruleLink contextPath change.resourceId change.resourceName ]

                GlobalParameterRes ->
                    Html.span [] [ text (change.action ++ " parameter "), paramLink contextPath change.resourceName ]


eventActionText : Event -> String
eventActionText e =
    case e of
        ChangeLogEvent event ->
            event

        ResourceChangeEvent { action, resourceName, resourceType } ->
            case resourceType of
                DirectiveRes ->
                    action ++ " directive " ++ resourceName

                NodeGroupRes ->
                    action ++ " group " ++ resourceName

                RuleRes ->
                    action ++ " rule " ++ resourceName

                GlobalParameterRes ->
                    action ++ " global parameter " ++ resourceName
