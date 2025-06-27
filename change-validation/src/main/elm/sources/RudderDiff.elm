module RudderDiff exposing (Diff(..), DiffChange, displayFormDiff, displayFormDiffStr)

import Html exposing (Html, node, pre, text)
import Html.Attributes exposing (attribute, style)


type alias DiffChange fieldType =
    { from : fieldType
    , to : fieldType
    }


type Diff fieldType
    = NoChange fieldType
    | Change (DiffChange fieldType)


displayFormDiff : (ty -> Html msg) -> Diff ty -> Html msg
displayFormDiff toHtml change =
    case change of
        NoChange field ->
            toHtml field

        Change diff ->
            pre [ style "white-space" "pre-line", style "word-break" "break-word", style "overflow" "auto" ]
                [ node "del" [] [ toHtml diff.from ]
                , node "ins" [] [ toHtml diff.to ]
                ]


displayFormDiffStr : (ty -> String) -> Diff ty -> Html msg
displayFormDiffStr toString change =
    displayFormDiff (\ch -> text (toString ch)) change
