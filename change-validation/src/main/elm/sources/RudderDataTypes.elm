module RudderDataTypes exposing (AllNextSteps, AllStepChanges(..), BackStatus(..), ChangeRequestDetails, ChangeRequestFormDetails, ChangeRequestMainDetails, ChangeRequestMainDetailsMetadata, ChangesSummary, Event(..), EventLog, NextStatus(..), ResourceChange, ResourceIdent, ResourceType(..), SimpleTarget, StepChange(..), Target(..), TargetComposition(..), TargetExclusion, TargetList(..), TargetType(..), ViewState(..), decodeChangeRequestMainDetails, decodeFormDetails, decodeResourceIdent, decodeTargetList)

import Json.Decode exposing (Decoder, andThen, at, bool, fail, field, index, int, lazy, list, map, map2, map4, map5, map6, maybe, string, succeed)
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


type BackStatus
    = Cancelled


type NextStatus
    = PendingDeployment
    | Deployed


type StepChange
    = Back BackStatus
    | Next NextStatus


type AllStepChanges
    = BackSteps BackStatus
    | NextSteps AllNextSteps


type alias AllNextSteps =
    { reachableNextSteps : List NextStatus
    , selected : NextStatus
    }


type alias ChangeRequestMainDetailsMetadata =
    { changeRequest : ChangeRequestDetails
    , isPending : Bool
    , eventLogs : List EventLog
    , prevStatus : Maybe BackStatus
    , allNextSteps : List NextStatus
    }


type alias ChangeRequestMainDetails =
    { changeRequest : ChangeRequestDetails
    , isPending : Bool
    , eventLogs : List EventLog
    , prevStatus : Maybe BackStatus
    , reachableNextSteps : Maybe AllNextSteps
    }


type alias ChangeRequestDetails =
    { title : String
    , state : String
    , id : Int
    , description : String
    , isMergeable : Maybe Bool
    , changesSummary : ChangesSummary
    }


type alias ChangesSummary =
    { directives : List String
    , rules : List String
    , groups : List String
    , parameters : List String
    }


type ResourceType
    = DirectiveRes
    | NodeGroupRes
    | RuleRes
    | GlobalParameterRes


type alias ResourceChange =
    { resourceType : ResourceType
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


type ViewState ty
    = NoView
    | ViewError String
    | Success ty


type alias ChangeRequestFormDetails =
    { title : String
    , state : String
    , id : Int
    , description : String
    }



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
    let
        decodeResourceType =
            string
                |> andThen
                    (\s ->
                        case s of
                            "directive" ->
                                succeed DirectiveRes

                            "node group" ->
                                succeed NodeGroupRes

                            "rule" ->
                                succeed RuleRes

                            "global parameter" ->
                                succeed GlobalParameterRes

                            _ ->
                                fail "Invalid resource type"
                    )

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
    in
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


decodeResourceIdent : Decoder ResourceIdent
decodeResourceIdent =
    succeed ResourceIdent
        |> required "id" string
        |> required "name" string


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


decodeChangeRequestDetails : Decoder ChangeRequestDetails
decodeChangeRequestDetails =
    map6
        ChangeRequestDetails
        (field "displayName" string)
        (field "status" decodeChangeRequestStatus)
        (field "id" int)
        (field "description" string)
        (maybe (field "isMergeable" bool))
        (field "changesSummary"
            (map4 ChangesSummary
                (field "directives" (list string))
                (field "rules" (list string))
                (field "groups" (list string))
                (field "parameters" (list string))
            )
        )


decodeChangeRequestMainDetails : Decoder ChangeRequestMainDetailsMetadata
decodeChangeRequestMainDetails =
    let
        decodePrevStatus =
            string
                |> andThen
                    (\s ->
                        case s of
                            "Cancelled" ->
                                succeed Cancelled

                            _ ->
                                fail "Invalid back status"
                    )

        decodeNextStatus =
            string
                |> andThen
                    (\s ->
                        case s of
                            "Pending deployment" ->
                                succeed PendingDeployment

                            "Deployed" ->
                                succeed Deployed

                            _ ->
                                fail "Invalid next status"
                    )
    in
    at
        [ "data" ]
        (field "workflow"
            (index 0
                (map5 ChangeRequestMainDetailsMetadata
                    (field "changeRequest" decodeChangeRequestDetails)
                    (field "isPending" bool)
                    (field "eventLogs" (list decodeEventLog))
                    (maybe (field "backStatus" decodePrevStatus))
                    (field "allNextSteps" (list decodeNextStatus))
                )
            )
        )


decodeFormDetails : Decoder ChangeRequestFormDetails
decodeFormDetails =
    at [ "data" ]
        (field "changeRequests"
            (index 0
                (map4
                    ChangeRequestFormDetails
                    (field "displayName" string)
                    (field "status" decodeChangeRequestStatus)
                    (field "id" int)
                    (field "description" string)
                )
            )
        )
