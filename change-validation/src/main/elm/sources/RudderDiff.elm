module RudderDiff exposing (Diff(..), DiffChange, ResourceIdent, SimpleTarget(..), Target(..), TargetComposition(..), TargetExclusion, TargetList(..), directiveLink, displayBoolDiff, displayBoolField, displayDiffField, displayField, displayFormDiff, displayIdentList, displayIdentListDiff, displayIntDiff, displayResourceIdent, displayRuleTarget, displayRuleTargetDiff, displayStringDiff, displayStringField, displayValueDiff, displayValueField, groupLink, paramLink, ruleLink)

import Html exposing (Attribute, Html, a, b, br, li, node, pre, span, text, ul)
import Html.Attributes exposing (href, style)
import Json.Decode exposing (Value)
import Json.Encode exposing (encode)



------------------------------
-- DATATYPES
------------------------------


type alias DiffChange fieldType =
    { from : fieldType
    , to : fieldType
    }


type Diff fieldType
    = NoChange fieldType
    | Change (DiffChange fieldType)


type alias ResourceIdent =
    { id : String
    , name : String
    }


type SimpleTarget
    = Group ResourceIdent
    | NonGroup String


type TargetComposition
    = Or TargetList
    | And TargetList


type alias TargetExclusion =
    { include : TargetComposition, exclude : TargetComposition }


type Target
    = Simple SimpleTarget
    | Exclusion TargetExclusion
    | Composition TargetComposition


type TargetList
    = TargetList (List Target)


type ChangeType
    = Unchanged
    | Added
    | Deleted



------------------------------
-- UTIL
------------------------------


unchangedStyle : List (Attribute msg)
unchangedStyle =
    [ style "list-style-type" "none" ]


addedStyle : List (Attribute msg)
addedStyle =
    [ style "list-style-type" "none"
    , style "background" "none repeat scroll 0 0 #D6FFD6"
    ]


deletedStyle : List (Attribute msg)
deletedStyle =
    [ style "list-style-type" "none"
    , style "background" "none repeat scroll 0 0 #FFD6D6"
    ]


strFromBool b =
    if b then
        "true"

    else
        "false"



------------------------------
-- GENERIC DIFF DISPLAY
------------------------------


displayFormDiff : (ty -> Html msg) -> Diff ty -> Html msg
displayFormDiff toHtml change =
    case change of
        NoChange field ->
            field |> toHtml

        Change diff ->
            pre [ style "white-space" "pre-line", style "word-break" "break-word", style "overflow" "auto" ]
                [ node "del" [] [ text "- ", toHtml diff.from ]
                , br [] []
                , node "ins" [] [ text "+ ", toHtml diff.to ]
                ]


displayStringDiff : String -> Diff String -> Html msg
displayStringDiff fieldName diffValue =
    displayDiffField text fieldName diffValue


displayBoolDiff : String -> Diff Bool -> Html msg
displayBoolDiff fieldName diffValue =
    displayDiffField (strFromBool >> text) fieldName diffValue


displayIntDiff : String -> Diff Int -> Html msg
displayIntDiff fieldName diffValue =
    displayDiffField (String.fromInt >> text) fieldName diffValue


displayValueDiff : String -> Diff Value -> Html msg
displayValueDiff fieldName diffValue =
    displayDiffField (encode 4 >> text) fieldName diffValue


displayDiffField : (ty -> Html msg) -> String -> Diff ty -> Html msg
displayDiffField toHtml fieldName diffValue =
    li []
        [ b [] [ text (fieldName ++ " : ") ]
        , displayFormDiff toHtml diffValue
        ]


displayField : String -> Html msg -> Html msg
displayField fieldName v =
    li []
        [ b [] [ text (fieldName ++ " : ") ]
        , node "value" [] [ v ]
        ]


displayBoolField : String -> Bool -> Html msg
displayBoolField fieldName b =
    displayField fieldName (b |> strFromBool |> text)


displayStringField : String -> String -> Html msg
displayStringField fieldName s =
    displayField fieldName (s |> text)


displayValueField : String -> Value -> Html msg
displayValueField fieldName v =
    displayField fieldName (v |> encode 4 >> text)



------------------------------
-- (name,id) PAIR DISPLAY
------------------------------


displayResourceIdent : String -> (String -> String -> Html msg) -> ChangeType -> ResourceIdent -> Html msg
displayResourceIdent resourceType linkFun change ident =
    case change of
        Unchanged ->
            li unchangedStyle
                [ span [] [ text (" " ++ resourceType ++ " "), linkFun ident.id ident.name ] ]

        Added ->
            li addedStyle
                [ span [] [ text ("+ " ++ resourceType ++ " "), linkFun ident.id ident.name ] ]

        Deleted ->
            li deletedStyle
                [ span [] [ text ("- " ++ resourceType ++ " "), linkFun ident.id ident.name ] ]


displayIdentList : String -> (String -> String -> Html msg) -> List ResourceIdent -> Html msg
displayIdentList resourceType linkFun identList =
    ul [ style "padding-left" "10px" ] (List.map (displayResourceIdent resourceType linkFun Unchanged) identList)


displayIdentListDiff : String -> (String -> String -> Html msg) -> Diff (List ResourceIdent) -> Html msg
displayIdentListDiff resourceType linkFun ls =
    case ls of
        NoChange d ->
            displayIdentList resourceType linkFun d

        Change change ->
            let
                unchanged =
                    List.filter (\e -> List.member e change.to) change.from
                        |> List.map (displayResourceIdent resourceType linkFun Unchanged)

                added =
                    List.filter (\e -> not (List.member e change.from)) change.to
                        |> List.map (displayResourceIdent resourceType linkFun Added)

                deleted =
                    List.filter (\e -> not (List.member e change.to)) change.from
                        |> List.map (displayResourceIdent resourceType linkFun Deleted)
            in
            ul [ style "padding-left" "10px" ] (deleted ++ unchanged ++ added)



------------------------------
-- TARGET DISPLAY
------------------------------


displaySimpleTarget : SimpleTarget -> ChangeType -> Html msg
displaySimpleTarget target change =
    let
        displayNonGroup nonGroup =
            case change of
                Unchanged ->
                    li unchangedStyle
                        [ span [] [ text (" " ++ nonGroup ++ " ") ] ]

                Added ->
                    li addedStyle
                        [ span [] [ text ("+ " ++ nonGroup ++ " ") ] ]

                Deleted ->
                    li deletedStyle
                        [ span [] [ text ("- " ++ nonGroup ++ " ") ] ]
    in
    case target of
        Group group ->
            displayResourceIdent "Group" groupLink change group

        NonGroup nonGroup ->
            displayNonGroup nonGroup


displayExclusionTarget : TargetExclusion -> ChangeType -> Html msg
displayExclusionTarget exclusion change =
    li []
        ([ span [] [ text "Include" ] ]
            ++ displayCompositionTarget exclusion.include change
            ++ [ span [] [ text "Exclude" ] ]
            ++ displayCompositionTarget exclusion.exclude change
        )


displayCompositionTarget : TargetComposition -> ChangeType -> List (Html msg)
displayCompositionTarget composition change =
    case composition of
        Or targetList ->
            [ li [ style "list-style-type" "none", style "padding-left" "10px" ]
                [ span [] [ text "Nodes that belong to any of the following groups :" ]
                , ul [ style "padding-left" "10px" ] (displayTargetList targetList change)
                ]
            ]

        And targetList ->
            [ li [ style "list-style-type" "none", style "padding-left" "10px" ]
                [ span [] [ text "  Nodes that belong to all of the following groups :" ]
                , ul [ style "padding-left" "10px" ] (displayTargetList targetList change)
                ]
            ]


displayTargetList : TargetList -> ChangeType -> List (Html msg)
displayTargetList targetList change =
    case targetList of
        TargetList targets ->
            List.map (displayTarget change) targets
                |> List.concat


displayTarget : ChangeType -> Target -> List (Html msg)
displayTarget change target =
    case target of
        Simple simpleTarget ->
            [ displaySimpleTarget simpleTarget change ]

        Exclusion targetExclusion ->
            [ displayExclusionTarget targetExclusion change ]

        Composition targetComposition ->
            displayCompositionTarget targetComposition change


displayRuleTarget : String -> TargetList -> Html msg
displayRuleTarget fieldName targets =
    li []
        ([ b [] [ text (fieldName ++ " : ") ] ] ++ displayTargetList targets Unchanged)



------------------------------
-- TARGET DIFF DISPLAY
------------------------------


displayExclusionTargetDiff : TargetExclusion -> TargetExclusion -> List (Html msg)
displayExclusionTargetDiff from to =
    [ li []
        ([ span [] [ text "Include" ] ]
            ++ displayCompositionTargetDiff from.include to.include
            ++ [ span [] [ text "Exclude" ] ]
            ++ displayCompositionTargetDiff from.exclude to.exclude
        )
    ]


displayCompositionTargetDiff : TargetComposition -> TargetComposition -> List (Html msg)
displayCompositionTargetDiff from to =
    case ( from, to ) of
        ( Or l1, Or l2 ) ->
            [ li [ style "list-style-type" "none", style "padding-left" "10px" ]
                [ span [] [ text "Nodes that belong to any of the following groups :" ]
                , ul [ style "padding-left" "10px" ] (displayTargetListDiff l1 l2)
                ]
            ]

        ( And l1, And l2 ) ->
            [ li [ style "list-style-type" "none", style "padding-left" "10px" ]
                [ span [] [ text "Nodes that belong to all of the following groups :" ]
                , ul [ style "padding-left" "10px" ] (displayTargetListDiff l1 l2)
                ]
            ]

        ( _, _ ) ->
            displayCompositionTarget from Deleted ++ displayCompositionTarget to Added


displayTargetListDiff : TargetList -> TargetList -> List (Html msg)
displayTargetListDiff from to =
    case ( from, to ) of
        ( TargetList [ Exclusion e1 ], TargetList [ Exclusion e2 ] ) ->
            displayExclusionTargetDiff e1 e2

        ( TargetList l1, TargetList l2 ) ->
            let
                unchanged =
                    List.filter (\e -> List.member e l2) l1
                        |> List.map (displayTarget Unchanged)

                added =
                    List.filter (\e -> not (List.member e l1)) l2
                        |> List.map (displayTarget Added)

                deleted =
                    List.filter (\e -> not (List.member e l2)) l1
                        |> List.map (displayTarget Deleted)
            in
            [ ul [ style "padding-left" "10px" ] (List.concat (deleted ++ unchanged ++ added)) ]


displayRuleTargetDiff : String -> Diff TargetList -> Html msg
displayRuleTargetDiff fieldName diff =
    case diff of
        NoChange targetList ->
            displayRuleTarget fieldName targetList

        Change { from, to } ->
            li [] ([ b [] [ text (fieldName ++ " : ") ] ] ++ displayTargetListDiff from to)



------------------------------
-- LINK DISPLAY
------------------------------


directiveLink : String -> String -> Html msg
directiveLink id name =
    span []
        [ a [ href ("/rudder/secure/configurationManager/directiveManagement#{\"directiveId\":\"" ++ id ++ "\"}") ]
            [ text name ]
        , text (" (Rudder ID : " ++ id ++ ")")
        ]


groupLink : String -> String -> Html msg
groupLink id name =
    span []
        [ a [ href ("/rudder/secure/nodeManager/groups#{\"groupId\":\"" ++ id ++ "\"}") ]
            [ text name ]
        , text (" (Rudder ID : " ++ id ++ ")")
        ]


ruleLink : String -> String -> Html msg
ruleLink ruleId ruleName =
    span []
        [ a [ href ("/rudder/secure/configurationManager/ruleManagement/rule/" ++ ruleId) ]
            [ text ruleName ]
        , text (" (Rudder ID : " ++ ruleId ++ ")")
        ]


paramLink : String -> Html msg
paramLink paramName =
    a [ href "/rudder/secure/configurationManager/parameterManagement" ] [ text paramName ]
