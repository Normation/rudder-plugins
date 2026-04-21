module RudderDataTypes exposing (Action(..), AllNextSteps, AllStepChanges(..), BackStatus(..), ChangeRequestDetails, ChangeRequestFormDetails, ChangeRequestMainDetails, ChangeRequestMainDetailsMetadata, ChangesSummary, Event(..), EventLog, LinkWithId, NextStatus(..), ResourceChange, ResourceIdent, ResourceReference(..), ResourceType(..), SimpleTarget, StepChange(..), Target(..), TargetComposition(..), TargetExclusion, TargetList(..), TargetType(..), TypedResourceReference, ViewState(..), actionToString, decodeAction, decodeChangeRequestMainDetails, decodeFormDetails, decodeResourceIdent, decodeResourceReference, decodeTargetList, resourceTypeWithIdToString)

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


type alias LinkWithId =
    { resourceId : String, resourceName : String }


type ResourceReference
    = PlainText String
    | Link LinkWithId


type alias TypedResourceReference =
    { resourceType : ResourceType
    , reference : ResourceReference
    }


type Action
    = CreateAction
    | DeleteAction
    | ModifyAction


type alias ResourceChange =
    { typedReference : TypedResourceReference
    , action : Action
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


{-| Decoder for ResourceReferences that are found in the change request changes.
This function's behavior is analogous to the decodeDisplayLink function.
-}
decodeResourceReference : String -> String -> Action -> Decoder ResourceReference
decodeResourceReference nameFieldLabel changeRequestStatus actionType =
    case ( changeRequestStatus, actionType ) of
        ( "Deployed", DeleteAction ) ->
            succeed PlainText |> required nameFieldLabel string

        _ ->
            map Link
                (succeed LinkWithId
                    |> required "id" string
                    |> required nameFieldLabel string
                )


{-| Decoder for ResourceReferences that are found in the event logs list.
This function's behavior is analogous to the decodeResourceReference function.
-}
decodeDisplayLink : String -> Action -> Decoder ResourceReference
decodeDisplayLink changeRequestStatus actionType =
    case ( changeRequestStatus, actionType ) of
        ( "Deployed", DeleteAction ) ->
            map PlainText (field "resourceName" string)

        _ ->
            map Link
                (map2 LinkWithId
                    (field "resourceId" string)
                    (field "resourceName" string)
                )


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


decodeEventLog : String -> Decoder EventLog
decodeEventLog changeRequestStatus =
    map4
        EventLog
        (field "action" (decodeEvent changeRequestStatus))
        (field "actor" string)
        (field "date" string)
        (maybe (field "reason" string))


decodeAction : Decoder Action
decodeAction =
    string
        |> andThen
            (\s ->
                case s of
                    "create" ->
                        succeed CreateAction

                    "delete" ->
                        succeed DeleteAction

                    "modify" ->
                        succeed ModifyAction

                    _ ->
                        fail "Invalid action type"
            )


decodeEvent : String -> Decoder Event
decodeEvent changeRequestStatus =
    let
        decodeResourceRef action =
            field "resourceType" string
                |> andThen
                    (\s ->
                        let
                            link =
                                decodeDisplayLink changeRequestStatus action
                        in
                        case s of
                            "directive" ->
                                map2 TypedResourceReference (succeed DirectiveRes) link

                            "node group" ->
                                map2 TypedResourceReference (succeed NodeGroupRes) link

                            "rule" ->
                                map2 TypedResourceReference (succeed RuleRes) link

                            "global parameter" ->
                                map2 TypedResourceReference (succeed GlobalParameterRes) link

                            _ ->
                                fail "Invalid resource type"
                    )
    in
    field "type" string
        |> andThen
            (\eventType ->
                case eventType of
                    "ChangeLogEvent" ->
                        map ChangeLogEvent (field "action" string)

                    "ResourceChangeEvent" ->
                        field "action" decodeAction
                            |> andThen
                                (\action ->
                                    map ResourceChangeEvent
                                        (map2 ResourceChange
                                            (decodeResourceRef action)
                                            (succeed action)
                                        )
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

        decodeEventLogList : String -> Decoder (List EventLog)
        decodeEventLogList changeRequestStatus =
            list (decodeEventLog changeRequestStatus)
    in
    at
        [ "data" ]
        (field "workflow"
            (index 0
                (field "changeRequest" decodeChangeRequestDetails
                    |> andThen
                        (\changeRequestDetails ->
                            map5 ChangeRequestMainDetailsMetadata
                                (succeed changeRequestDetails)
                                (field "isPending" bool)
                                (field "eventLogs" (decodeEventLogList changeRequestDetails.state))
                                (maybe (field "backStatus" decodePrevStatus))
                                (field "allNextSteps" (list decodeNextStatus))
                        )
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



------------------------------
-- UTIL
------------------------------


actionToString : Action -> String
actionToString action =
    case action of
        CreateAction ->
            "create"

        DeleteAction ->
            "delete"

        ModifyAction ->
            "modify"


resourceTypeWithIdToString : ResourceType -> String
resourceTypeWithIdToString resourceType =
    case resourceType of
        DirectiveRes ->
            "directive"

        NodeGroupRes ->
            "group"

        RuleRes ->
            "rule"

        GlobalParameterRes ->
            "parameter"
