module RudderDataTypes exposing (ResourceIdent, SimpleTarget, Target(..), TargetComposition(..), TargetExclusion, TargetList(..), decodeTargetList)

import Json.Decode exposing (Decoder, andThen, fail, field, lazy, list, map, map2, map3, string)



------------------------------
-- DATATYPES
------------------------------


type alias ResourceIdent =
    { id : String
    , name : String
    }


type alias SimpleTarget =
    { id : String
    , name : String
    , targetType : String
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
                    "SimpleRuleTargetExtended" ->
                        map Simple
                            (map3 SimpleTarget
                                (field "id" string)
                                (field "name" string)
                                (field "targetType" string)
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
