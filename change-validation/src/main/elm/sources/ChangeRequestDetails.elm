module ChangeRequestDetails exposing (init)

import Browser
import ChangeRequestChangesForm as ChangesForm
import ChangeRequestEditForm as EditForm
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, button, div, h1, p, span, text)
import Html.Attributes exposing (attribute, class, disabled, href, id, style, tabindex)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Ports exposing (errorNotification, readUrl)
import RudderDataTypes exposing (BackStatus(..), ChangeRequestDetails, ChangeRequestMainDetails, Event(..), EventLog, NextStatus(..), ViewState(..), decodeChangeRequestMainDetails)
import RudderLinkUtil exposing (ContextPath, changeRequestsPageUrl, getApiUrl, getContextPath)



------------------------------
-- Init and main --
------------------------------


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }


init : { contextPath : String, hasValidatorWriteRights : Bool, hasDeployerWriteRights : Bool } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model
                (getContextPath flags.contextPath)
                flags.hasValidatorWriteRights
                flags.hasDeployerWriteRights
                (EditForm.initModel flags)
                (ChangesForm.initModel { contextPath = flags.contextPath })
                NoView
    in
    ( initModel, Cmd.none )



------------------------------
-- MODEL
------------------------------


type alias Model =
    { contextPath : ContextPath
    , hasValidatorWriteRights : Bool
    , hasDeployerWriteRights : Bool
    , editForm : EditForm.Model
    , changesForm : ChangesForm.Model
    , viewState : ViewState ChangeRequestMainDetails
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestMainDetails (Result Error ChangeRequestMainDetails)
    | EditFormMsg EditForm.Msg
    | ChangesFormMsg ChangesForm.Msg



------------------------------
-- API CALLS
------------------------------


getChangeRequestMainDetails : Model -> Int -> Cmd Msg
getChangeRequestMainDetails model crId =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changevalidation/workflow/changeRequestMainDetails/" ++ String.fromInt crId)
                , body = emptyBody
                , expect = expectJson GetChangeRequestMainDetails decodeChangeRequestMainDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- UPDATE
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestIdFromUrl crIdStr ->
            case String.toInt crIdStr of
                Just crId ->
                    ( model, getChangeRequestMainDetails model crId )

                Nothing ->
                    let
                        errMsg =
                            crIdStr ++ " is not a valid change request id"
                    in
                    ( { model | viewState = ViewError errMsg }, Cmd.none )

        GetChangeRequestMainDetails result ->
            case result of
                Ok cr ->
                    let
                        formDetails =
                            { title = cr.changeRequest.title
                            , id = cr.changeRequest.id
                            , state = cr.changeRequest.state
                            , description = cr.changeRequest.description
                            }
                    in
                    ( { model
                        | viewState = Success cr
                        , changesForm = model.changesForm |> ChangesForm.updateChangeRequestDetails cr
                        , editForm = model.editForm |> EditForm.updateChangeRequestDetails formDetails
                      }
                    , Cmd.none
                    )

                Err error ->
                    let
                        errMsg =
                            getErrorMessage error
                    in
                    ( { model | viewState = ViewError errMsg }
                    , errorNotification ("Error while trying to fetch change request details: " ++ errMsg)
                    )

        EditFormMsg editFormMsg ->
            let
                ( efModel, efCmd ) =
                    EditForm.update editFormMsg model.editForm
            in
            ( { model | editForm = efModel }, Cmd.map EditFormMsg efCmd )

        ChangesFormMsg changesFormMsg ->
            let
                ( cfModel, cfCmd ) =
                    ChangesForm.update changesFormMsg model.changesForm
            in
            ( { model | changesForm = cfModel }, Cmd.map ChangesFormMsg cfCmd )



------------------------------
-- VIEW
------------------------------


view : Model -> Html Msg
view model =
    case model.viewState of
        Success changeRequest ->
            div [ class "one-col" ]
                [ bannerView model changeRequest
                , div [ class "one-col-main" ]
                    [ div [ class "template-main" ]
                        [ div [ class "main-container" ]
                            [ div [ class "main-details" ]
                                [ Html.map EditFormMsg (EditForm.view model.editForm)
                                , warnOnUnmergeable changeRequest.changeRequest
                                , Html.map ChangesFormMsg (ChangesForm.view model.changesForm)
                                ]
                            ]
                        ]
                    ]
                ]

        _ ->
            text ""


eventLogToString : String -> String -> String -> String
eventLogToString action date actor =
    action ++ " on " ++ date ++ " by " ++ actor


bannerView : Model -> ChangeRequestMainDetails -> Html Msg
bannerView model cr =
    let
        canChangeStep =
            case cr.changeRequest.state of
                "Pending validation" ->
                    model.hasValidatorWriteRights

                "Pending deployment" ->
                    model.hasDeployerWriteRights

                _ ->
                    False

        lastLog =
            cr.eventLogs
                |> List.filterMap
                    (\log ->
                        case log.action of
                            ChangeLogEvent action ->
                                Just ( action, log.date, log.actor )

                            _ ->
                                Nothing
                    )
                |> List.sortBy (\( _, date, _ ) -> date)
                |> List.reverse
                |> List.head
                |> Maybe.map (\( action, date, actor ) -> eventLogToString action date actor)
                |> Maybe.withDefault ("Error: no action was recorded for change request with id" ++ String.fromInt cr.changeRequest.id)
    in
    div [ class "main-header", id "changeRequestHeader" ]
        [ div [ class "header-title" ]
            [ h1 [] [ span [ id "nameTitle" ] [ text cr.changeRequest.title ] ]
            , div [ class "flex-container" ]
                [ div [ id "CRStatus" ] [ text cr.changeRequest.state ]
                , actionButtons canChangeStep cr.prevStatus cr.nextStatus
                ]
            ]
        , div
            [ class "header-description" ]
            [ p [ id "CRLastAction" ] [ text lastLog ]
            ]
        , a [ href (changeRequestsPageUrl model.contextPath), id "backButton" ]
            [ span [ class "fa fa-arrow-left" ] []
            , text " Back to change request list "
            ]
        ]


actionButtons : Bool -> Maybe BackStatus -> Maybe NextStatus -> Html Msg
actionButtons canChangeStep backStatus nextStatus =
    let
        canChangeStepAttr =
            if canChangeStep then
                []

            else
                [ disabled True ]

        getBackAction back =
            case back of
                Cancelled ->
                    "Decline"

        getNextAction next =
            case next of
                PendingDeployment ->
                    "Validate"

                Deployed ->
                    "Deploy"

        buttons =
            case ( backStatus, nextStatus ) of
                ( Just back, Just next ) ->
                    [ button
                        ([ id "backStep", class "btn btn-danger" ] ++ canChangeStepAttr)
                        [ text (getBackAction back) ]
                    , button
                        ([ id "nextStep", style "float" "right", class "btn btn-success" ] ++ canChangeStepAttr)
                        [ text (getNextAction next) ]
                    ]

                ( Just back, Nothing ) ->
                    [ button
                        ([ id "backStep", class "btn btn-danger" ] ++ canChangeStepAttr)
                        [ text (getBackAction back) ]
                    ]

                ( Nothing, Just next ) ->
                    [ button
                        ([ id "nextStep", style "float" "right", class "btn btn-success" ] ++ canChangeStepAttr)
                        [ text (getNextAction next) ]
                    ]

                ( Nothing, Nothing ) ->
                    []
    in
    div [ id "actionBtns" ]
        [ div [ class "header-buttons", id "workflowActionButtons" ] buttons
        , div
            [ attribute "data-bs-backdrop" "false"
            , tabindex -1
            , attribute "data-bs-keyboard" "true"
            , class "modal fade"
            , id "popupContent"
            ]
            []
        ]


warnOnUnmergeable : ChangeRequestDetails -> Html Msg
warnOnUnmergeable changeRequest =
    let
        isPending cr =
            cr.state == "Pending validation" || cr.state == "Pending deployment"
    in
    if (changeRequest |> isPending) && not (Maybe.withDefault False changeRequest.isMergeable) then
        div [ id "warnOnUnmergeable" ]
            [ div [ class "callout-fade callout-warning" ]
                [ div [ class "marker" ]
                    [ span [ class "fa fa-info-circle" ] [] ]
                , div []
                    [ p []
                        [ text
                            ("The affected resource(s) has/have been modified since this change request was created."
                                ++ " If you accept the change request, previous modifications will be lost."
                            )
                        ]
                    , p [] [ text "Please double-check that this is what you want before deploying this change request." ]
                    ]
                ]
            ]

    else
        text ""



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
