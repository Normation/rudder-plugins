module ChangeRequestEditForm exposing (..)

------------------------------
-- Init and main --
------------------------------

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, div, h3, p, text)
import Html.Attributes as Attr
import Http exposing (Error, emptyBody, expectJson, header, request)
import Json.Decode exposing (Decoder, andThen, at, fail, field, int, map, map4, string, succeed)
import Notifications exposing (errorNotification)


getApiUrl : Model -> String -> String
getApiUrl m url =
    m.contextPath ++ "/api/latest/" ++ url


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }


init : { contextPath : String, changeRequestId : Int } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model flags.contextPath flags.changeRequestId Nothing
    in
    ( initModel, getChangeRequestDetails initModel )



------------------------------
-- MODEL --
------------------------------


type alias Model =
    { contextPath : String
    , changeRequestId : Int
    , changeRequest : Maybe ChangeRequestDetails
    }


type Msg
    = GetChangeRequestDetails (Result Error ChangeRequestDetails)


type ChangeRequestStatus
    = Open
    | Closed
    | PendingValidation
    | PendingDeployment
    | Cancelled
    | Deployed


type alias ChangeRequestDetails =
    { title : String
    , state : ChangeRequestStatus
    , id : Int
    , description : String
    }



------------------------------
-- API --
------------------------------


getChangeRequestDetails : Model -> Cmd Msg
getChangeRequestDetails model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "X-API-Token" ]
                , url = getApiUrl model ("changeRequests/" ++ String.fromInt model.changeRequestId)
                , body = emptyBody
                , expect = expectJson GetChangeRequestDetails decodeChangeRequestDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- ENCODE / DECODE JSON --
------------------------------


decodeChangeRequestStatus : Decoder ChangeRequestStatus
decodeChangeRequestStatus =
    string
        |> andThen
            (\str ->
                case str of
                    "Open" ->
                        succeed Open

                    "Closed" ->
                        succeed Closed

                    "Pending validation" ->
                        succeed PendingValidation

                    "Pending deployment" ->
                        succeed PendingDeployment

                    "Cancelled" ->
                        succeed Cancelled

                    "Deployed" ->
                        succeed Deployed

                    _ ->
                        fail "Invalid change request status"
            )


decodeChangeRequestDetails : Decoder ChangeRequestDetails
decodeChangeRequestDetails =
    at [ "data" ]
        (field "changeRequests"
            (map4
                ChangeRequestDetails
                (field "displayName" string)
                (field "status" decodeChangeRequestStatus)
                (field "id" int)
                (field "description" string)
            )
        )



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestDetails result ->
            case result of
                Ok changeRequestDetails ->
                    ( { model | changeRequest = Just changeRequestDetails }, Cmd.none )

                Err err ->
                    ( model, errorNotification ("Error while trying to fetch change request details: " ++ getErrorMessage err) )



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    div
        [ Attr.id "change-request-edit-form"
        ]
        [ p []
            [ text ("TODO Change request #" ++ String.fromInt model.changeRequestId ++ " edit form") ]
        ]



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
