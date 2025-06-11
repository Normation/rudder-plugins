module RudderDiff exposing (Diff(..), DiffChange, decodeDiffField, decodeDiffFieldAt, displayBoolDiff, displayBoolField, displayDiffField, displayField, displayFormDiff, displayIdentList, displayIdentListDiff, displayIntDiff, displayMaybe, displayMaybeField, displayResourceIdent, displayRuleTarget, displayRuleTargetDiff, displayStringDiff, displayStringField, displayValue, displayValueDiff, displayValueField)

import Html exposing (Attribute, Html, b, br, li, node, pre, span, text, ul)
import Html.Attributes exposing (style)
import Json.Decode exposing (Decoder, Value, at, field, map, map2, oneOf)
import Json.Encode exposing (encode)
import RudderDataTypes exposing (..)
import RudderLinkUtil exposing (ContextPath, targetLinkWithId)



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


type ChangeType
    = Unchanged
    | Added
    | Deleted



------------------------------
-- JSON DECODERS
------------------------------


decodeDiffField : String -> Decoder fieldType -> Decoder (Diff fieldType)
decodeDiffField fieldName dec =
    let
        diffDec =
            map Change
                (at [ fieldName ] (map2 DiffChange (field "from" dec) (field "to" dec)))

        noChangeDec =
            map NoChange (field fieldName dec)
    in
    oneOf [ diffDec, noChangeDec ]


decodeDiffFieldAt : List String -> Decoder fieldType -> Decoder (Diff fieldType)
decodeDiffFieldAt path dec =
    let
        diffDec =
            map Change
                (at path (map2 DiffChange (field "from" dec) (field "to" dec)))

        noChangeDec =
            map NoChange (at path dec)
    in
    oneOf [ diffDec, noChangeDec ]



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
    displayDiffField displayValue fieldName diffValue


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
    displayField fieldName (displayValue v)


displayValue : Value -> Html msg
displayValue =
    encode 0 >> text


displayMaybe : (ty -> Html msg) -> Maybe ty -> Html msg
displayMaybe toHtml optValue =
    case optValue of
        Just value ->
            toHtml value

        Nothing ->
            text ""


displayMaybeField : String -> Maybe ty -> (ty -> Html msg) -> Html msg
displayMaybeField fieldName optValue toHtml =
    case optValue of
        Just value ->
            displayField fieldName (toHtml value)

        Nothing ->
            displayField fieldName (text "")



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


displayChangeElem : ChangeType -> String -> Html msg
displayChangeElem change txt =
    displayChangeElemWith change txt [] []


displayChangeElemWith : ChangeType -> String -> List (Attribute msg) -> List (Html msg) -> Html msg
displayChangeElemWith change txt style nodes =
    case change of
        Unchanged ->
            li (unchangedStyle ++ style) ([ span [] [ text txt ] ] ++ nodes)

        Added ->
            li (addedStyle ++ style) ([ span [] [ text ("+ " ++ txt ++ " ") ] ] ++ nodes)

        Deleted ->
            li (deletedStyle ++ style) ([ span [] [ text ("- " ++ txt ++ " ") ] ] ++ nodes)


displaySimpleTarget : ContextPath -> SimpleTarget -> ChangeType -> Html msg
displaySimpleTarget contextPath target change =
    case target.targetType of
        NonGroup ->
            displayChangeElemWith change "" [] [ targetLinkWithId contextPath target ]

        Group ->
            displayChangeElemWith change "  Group  " [] [ targetLinkWithId contextPath target ]


displayExclusionTarget : ContextPath -> TargetExclusion -> ChangeType -> Html msg
displayExclusionTarget contextPath exclusion change =
    li []
        ([ displayChangeElem change "Include" ]
            ++ displayCompositionTarget contextPath exclusion.include change
            ++ [ displayChangeElem change "Exclude" ]
            ++ displayCompositionTarget contextPath exclusion.exclude change
        )


displayCompositionTarget : ContextPath -> TargetComposition -> ChangeType -> List (Html msg)
displayCompositionTarget contextPath composition change =
    case composition of
        Or targetList ->
            [ displayChangeElemWith
                change
                "Nodes that belong to any of the following groups :"
                [ style "padding-left" "10px" ]
                [ ul [ style "padding-left" "10px" ] (displayTargetList contextPath targetList change) ]
            ]

        And targetList ->
            [ displayChangeElemWith
                change
                "Nodes that belong to all of the following groups :"
                [ style "padding-left" "10px" ]
                [ ul [ style "padding-left" "10px" ] (displayTargetList contextPath targetList change) ]
            ]


displayTargetList : ContextPath -> TargetList -> ChangeType -> List (Html msg)
displayTargetList contextPath targetList change =
    case targetList of
        TargetList targets ->
            List.concatMap (displayTarget contextPath change) targets


displayTarget : ContextPath -> ChangeType -> Target -> List (Html msg)
displayTarget contextPath change target =
    case target of
        Simple simpleTarget ->
            [ displaySimpleTarget contextPath simpleTarget change ]

        Exclusion targetExclusion ->
            [ displayExclusionTarget contextPath targetExclusion change ]

        Composition targetComposition ->
            displayCompositionTarget contextPath targetComposition change


displayRuleTarget : String -> ContextPath -> TargetList -> Html msg
displayRuleTarget fieldName contextPath targets =
    li []
        ([ b [] [ text (fieldName ++ " : ") ] ] ++ displayTargetList contextPath targets Unchanged)



------------------------------
-- TARGET DIFF DISPLAY
------------------------------


displayExclusionTargetDiff : ContextPath -> TargetExclusion -> TargetExclusion -> List (Html msg)
displayExclusionTargetDiff contextPath from to =
    [ li []
        ([ span [] [ text "Include" ] ]
            ++ displayCompositionTargetDiff contextPath from.include to.include
            ++ [ span [] [ text "Exclude" ] ]
            ++ displayCompositionTargetDiff contextPath from.exclude to.exclude
        )
    ]


displayCompositionTargetDiff : ContextPath -> TargetComposition -> TargetComposition -> List (Html msg)
displayCompositionTargetDiff contextPath from to =
    case ( from, to ) of
        ( Or l1, Or l2 ) ->
            [ li [ style "list-style-type" "none", style "padding-left" "10px" ]
                [ span [] [ text "Nodes that belong to any of the following groups :" ]
                , ul [ style "padding-left" "10px" ] (displayTargetListDiff contextPath l1 l2)
                ]
            ]

        ( And l1, And l2 ) ->
            [ li [ style "list-style-type" "none", style "padding-left" "10px" ]
                [ span [] [ text "Nodes that belong to all of the following groups :" ]
                , ul [ style "padding-left" "10px" ] (displayTargetListDiff contextPath l1 l2)
                ]
            ]

        ( _, _ ) ->
            displayCompositionTarget contextPath from Deleted ++ displayCompositionTarget contextPath to Added


displayTargetListDiff : ContextPath -> TargetList -> TargetList -> List (Html msg)
displayTargetListDiff contextPath from to =
    case ( from, to ) of
        ( TargetList [ Exclusion e1 ], TargetList [ Exclusion e2 ] ) ->
            displayExclusionTargetDiff contextPath e1 e2

        ( TargetList l1, TargetList l2 ) ->
            let
                unchanged =
                    List.filter (\e -> List.member e l2) l1
                        |> List.map (displayTarget contextPath Unchanged)

                added =
                    List.filter (\e -> not (List.member e l1)) l2
                        |> List.map (displayTarget contextPath Added)

                deleted =
                    List.filter (\e -> not (List.member e l2)) l1
                        |> List.map (displayTarget contextPath Deleted)
            in
            [ ul [ style "padding-left" "10px" ] (List.concat (deleted ++ unchanged ++ added)) ]


displayRuleTargetDiff : String -> ContextPath -> Diff TargetList -> Html msg
displayRuleTargetDiff fieldName contextPath diff =
    case diff of
        NoChange targetList ->
            displayRuleTarget fieldName contextPath targetList

        Change { from, to } ->
            li [] ([ b [] [ text (fieldName ++ " : ") ] ] ++ displayTargetListDiff contextPath from to)
