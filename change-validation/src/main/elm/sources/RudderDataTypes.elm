module RudderDataTypes exposing (ResourceIdent, SimpleTarget, Target(..), TargetComposition(..), TargetExclusion, TargetList(..), TargetType(..), decodeTargetList)

import Json.Decode exposing (Decoder, andThen, fail, field, lazy, list, map, map2, map3, string, succeed)
import Json.Decode.Pipeline exposing (hardcoded, required)



------------------------------
-- DATATYPES
------------------------------


type alias ResourceIdent =
    { id : String
    , name : String
    }


type TargetType
    = Group
    | NonGroup


type alias SimpleTarget =
    { id : String
    , name : String
    , targetType : TargetType
    }


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



------------------------------
-- JSON DECODERS
------------------------------


decodeTarget : Decoder Target
decodeTarget =
    field "type" string
        |> andThen
            (\targetType ->
                case targetType of
                    "groupId" ->
                        map Simple
                            (succeed SimpleTarget
                                |> required "id" string
                                |> required "name" string
                                |> hardcoded Group
                            )

                    "target" ->
                        map Simple
                            (succeed SimpleTarget
                                |> required "id" string
                                |> required "name" string
                                |> hardcoded NonGroup
                            )

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
