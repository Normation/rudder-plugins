module RudderDataTypes exposing (ResourceIdent, SimpleTarget(..), Target(..), TargetComposition(..), TargetExclusion, TargetList(..))


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
