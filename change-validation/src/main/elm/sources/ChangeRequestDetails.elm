module ChangeRequestDetails exposing (init)

import Browser
import ChangeRequestChangesForm as ChangesForm
import ChangeRequestEditForm as EditForm
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Attribute, Html, a, b, button, div, form, h1, h4, h5, input, label, p, span, text, textarea)
import Html.Attributes exposing (attribute, class, disabled, href, id, placeholder, style, tabindex, type_, value)
import Html.Events exposing (onClick)
import Http exposing (Error, emptyBody, expectJson, header, jsonBody, request)
import Json.Decode exposing (Decoder, Value, succeed)
import List.Extra
import Ports exposing (errorNotification, readUrl, successNotification)
import RudderDataTypes exposing (BackStatus(..), ChangeRequestDetails, ChangeRequestMainDetails, Event(..), EventLog, NextStatus(..), ViewState(..), decodeChangeRequestMainDetails, encodeNextStatus)
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
                NoView
    in
    ( initModel, Cmd.none )



------------------------------
-- MODEL
------------------------------


type StepChange
    = BackStep BackStatus
    | NextStep NextStatus


type alias ChangeStepForm =
    { step : StepChange
    , reasonsField : String
    }


type alias Model =
    { contextPath : ContextPath
    , hasValidatorWriteRights : Bool
    , hasDeployerWriteRights : Bool
    , editForm : EditForm.Model
    , changesForm : ChangesForm.Model
    , viewState : ViewState ChangeRequestMainDetails
    , changeStepPopup : ViewState ChangeStepForm
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestMainDetails (Result Error ChangeRequestMainDetails)
    | EditFormMsg EditForm.Msg
    | ChangesFormMsg ChangesForm.Msg
    | OpenChangeStepPopup StepChange
    | CloseChangeStepPopup
    | FormInputReasonsField String
    | ChangeRequestStepChange (Result Error Int)
    | SubmitChangeStepForm (Model -> Cmd Msg)



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


acceptChangeRequest : Int -> NextStatus -> Model -> Cmd Msg
acceptChangeRequest crId nextStatus model =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changeRequests/" ++ String.fromInt crId ++ "/accept")
                , body = jsonBody (encodeNextStatus nextStatus)
                , expect = expectJson ChangeRequestStepChange (succeed crId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


declineChangeRequest : Int -> Model -> Cmd Msg
declineChangeRequest crId model =
    let
        req =
            request
                { method = "DELETE"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changeRequests/" ++ String.fromInt crId)
                , body = emptyBody
                , expect = expectJson ChangeRequestStepChange (succeed crId)
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

        OpenChangeStepPopup stepType ->
            ( { model | changeStepPopup = Success { step = stepType, reasonsField = "" } }, Cmd.none )

        CloseChangeStepPopup ->
            ( { model | changeStepPopup = NoView }, Cmd.none )

        ChangeRequestStepChange result ->
            case result of
                Ok crId ->
                    ( model
                    , Cmd.batch
                        [ successNotification "Change request step successfully changed"
                        , getChangeRequestMainDetails model crId
                        ]
                    )

                Err error ->
                    let
                        errMsg =
                            getErrorMessage error
                    in
                    ( model
                    , errorNotification ("Error while trying to change step of change request: " ++ errMsg)
                    )

        FormInputReasonsField newReason ->
            ( model |> updateChangeStepForm (setReasonsField newReason), Cmd.none )

        SubmitChangeStepForm apiCall ->
            ( { model | changeStepPopup = NoView }, Cmd.batch [ apiCall model ] )


updateChangeStepForm : (ViewState ChangeStepForm -> ViewState ChangeStepForm) -> Model -> Model
updateChangeStepForm f model =
    { model | changeStepPopup = f model.changeStepPopup }


setReasonsField : String -> ViewState ChangeStepForm -> ViewState ChangeStepForm
setReasonsField newReason changeStepFormState =
    case changeStepFormState of
        Success changeStepForm ->
            Success { changeStepForm | reasonsField = newReason }

        _ ->
            changeStepFormState



------------------------------
-- VIEW
------------------------------


nextStepBtnClass : String
nextStepBtnClass =
    "btn btn-success"


backStepBtnClass : String
backStepBtnClass =
    "btn btn-danger"


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
                                , warnOnUnmergeableView changeRequest.changeRequest
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
                |> List.Extra.maximumBy (\( _, date, _ ) -> date)
                |> Maybe.map (\( action, date, actor ) -> eventLogToString action date actor)
                |> Maybe.withDefault ("Error: no action was recorded for change request with id" ++ String.fromInt cr.changeRequest.id)
    in
    div [ class "main-header", id "changeRequestHeader" ]
        [ div [ class "header-title" ]
            [ h1 [] [ span [ id "nameTitle" ] [ text cr.changeRequest.title ] ]
            , div [ class "flex-container" ]
                [ div [ id "CRStatus" ] [ text cr.changeRequest.state ]
                , actionButtons model.changeStepPopup cr.changeRequest.id canChangeStep cr.prevStatus cr.nextStatus
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


actionButtons : ViewState ChangeStepForm -> Int -> Bool -> Maybe BackStatus -> Maybe NextStatus -> Html Msg
actionButtons changeStepForm crId canChangeStep backStatus nextStatus =
    let
        canChangeStepAttr =
            if canChangeStep then
                []

            else
                [ disabled True ]

        backStepButton back =
            case back of
                Cancelled ->
                    button
                        ([ id "backStep"
                         , class backStepBtnClass
                         , onClick (OpenChangeStepPopup (BackStep back))
                         ]
                            ++ canChangeStepAttr
                        )
                        [ text "Decline" ]

        nextStepButton next =
            let
                action =
                    case next of
                        PendingDeployment ->
                            "Validate"

                        Deployed ->
                            "Deploy"
            in
            button
                ([ id "nextStep"
                 , style "float" "right"
                 , class "btn btn-success"
                 , onClick (OpenChangeStepPopup (NextStep next))
                 ]
                    ++ canChangeStepAttr
                )
                [ text action ]

        buttons =
            case ( backStatus, nextStatus ) of
                ( Just back, Just next ) ->
                    [ backStepButton back, nextStepButton next ]

                ( Just back, Nothing ) ->
                    [ backStepButton back ]

                ( Nothing, Just next ) ->
                    [ nextStepButton next ]

                ( Nothing, Nothing ) ->
                    []
    in
    div [ id "actionBtns" ]
        [ div [ class "header-buttons", id "workflowActionButtons" ] buttons
        , changeStepPopupView changeStepForm crId
        ]


warnOnUnmergeableView : ChangeRequestDetails -> Html Msg
warnOnUnmergeableView changeRequest =
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


changeStepPopupView : ViewState ChangeStepForm -> Int -> Html Msg
changeStepPopupView changeStepFormState crId =
    let
        visiblePopupAttr =
            [ attribute "data-bs-backdrop" "false"
            , tabindex -1
            , attribute "data-bs-keyboard" "true"
            , class "modal fade show"
            , id "popupContent"
            , style "display" "block"
            , style "padding-left" "0px"
            , attribute "aria-modal" "true"
            , attribute "role" "dialog"
            ]

        hiddenPopupAttr =
            [ attribute "data-bs-backdrop" "false"
            , tabindex -1
            , attribute "data-bs-keyboard" "true"
            , class "modal fade"
            , id "popupContent"
            , style "display" "none"
            , attribute "aria-hidden" "true"
            ]

        popupBody stepStr action btnClass btnClickCmd =
            [ div [ id "changeStatePopup" ]
                [ div [ class "modal-dialog" ]
                    [ div [ class "modal-content" ]
                        [ div [ class "modal-header" ]
                            [ h5
                                [ class "modal-title" ]
                                [ text "Change Request state" ]
                            , button
                                [ attribute "aria-label" "Close"
                                , attribute "data-bs-dismiss" "modal"
                                , class "btn-close"
                                , type_ "button"
                                , onClick CloseChangeStepPopup
                                ]
                                []
                            ]
                        , div [ id "form" ]
                            [ form []
                                [ div [ class "modal-body" ]
                                    [ div [ id "intro" ]
                                        [ h5
                                            [ class "text-center" ]
                                            [ text (" The change request will be sent to the '" ++ stepStr ++ "' status ") ]
                                        ]
                                    , div [ id "formError" ] []
                                    , div
                                        [ class "mt-3 from-group" ]
                                        [ label [] [ b [] [ text "Next state " ] ]
                                        , span
                                            [ class "well" ]
                                            [ text ("  " ++ stepStr ++ "  ") ]
                                        ]
                                    , div [ id "reason", class "mt-3" ]
                                        [ h4
                                            [ class "col-lg-12 col-sm-12 col-xs-12 audit-title" ]
                                            [ text "Change Audit Log" ]
                                        , div
                                            [ class "row wbBaseField form-group " ]
                                            [ label
                                                [ class "col-xl-3 col-md-12 col-sm-12 wbBaseFieldLabel" ]
                                                [ span [ class "fw-normal" ] [ text "Change audit message" ] ]
                                            , div
                                                [ class "col-xl-9 col-md-12 col-sm-12" ]
                                                [ textarea
                                                    [ style "height" "8em"
                                                    , placeholder "Please enter a reason explaining this change."
                                                    , class "rudderBaseFieldClassName form-control vresize col-xl-12 col-md-12"
                                                    ]
                                                    []
                                                ]
                                            ]
                                        ]
                                    ]
                                , div
                                    [ class "modal-footer" ]
                                    [ button
                                        [ attribute "aria-label" "Close"
                                        , attribute "data-bs-dismiss" "modal"
                                        , class "btn btn-default"
                                        , type_ "button"
                                        , onClick CloseChangeStepPopup
                                        ]
                                        [ text "Cancel" ]
                                    , input
                                        [ id "confirm"
                                        , class btnClass
                                        , value action
                                        , onClick btnClickCmd
                                        ]
                                        []
                                    ]
                                ]
                            ]
                        ]
                    ]
                ]
            ]
    in
    case changeStepFormState of
        Success changeStepForm ->
            let
                stepStr =
                    stepChangeToString changeStepForm.step

                action =
                    stepChangeAction changeStepForm.step

                ( btnClass, btnClickCmd ) =
                    case changeStepForm.step of
                        BackStep _ ->
                            ( backStepBtnClass, SubmitChangeStepForm (declineChangeRequest crId) )

                        NextStep next ->
                            ( nextStepBtnClass, SubmitChangeStepForm (acceptChangeRequest crId next) )
            in
            div visiblePopupAttr (popupBody stepStr action btnClass btnClickCmd)

        _ ->
            div hiddenPopupAttr (popupBody "" "" "" CloseChangeStepPopup)



------------------------------
-- UTIL
------------------------------


stepChangeToString : StepChange -> String
stepChangeToString stepChange =
    case stepChange of
        BackStep backStatus ->
            case backStatus of
                Cancelled ->
                    "Cancelled"

        NextStep nextStatus ->
            case nextStatus of
                PendingDeployment ->
                    "Pending deployment"

                Deployed ->
                    "Deployed"


stepChangeAction : StepChange -> String
stepChangeAction stepChange =
    case stepChange of
        BackStep backStatus ->
            case backStatus of
                Cancelled ->
                    "Decline"

        NextStep nextStatus ->
            case nextStatus of
                PendingDeployment ->
                    "Validate"

                Deployed ->
                    "Deploy"



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
