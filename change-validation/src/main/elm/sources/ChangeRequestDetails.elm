module ChangeRequestDetails exposing (init)

import Browser
import ChangeRequestChangesForm as ChangesForm
import ChangeRequestEditForm as EditForm
import ErrorMessages exposing (decodeErrorDetails)
import Html exposing (Attribute, Html, a, b, button, div, form, h1, h2, h3, h4, h5, label, option, p, select, span, text, textarea)
import Html.Attributes exposing (attribute, class, disabled, href, id, placeholder, selected, style, tabindex, type_, value)
import Html.Events exposing (onClick, onInput)
import Http exposing (Error, emptyBody, header, request)
import Http.Detailed as Detailed
import Json.Decode exposing (Decoder, Value, at, bool, field, index, int, map2)
import List
import List.Extra exposing (uniqueBy)
import Ports exposing (errorNotification, readUrl, successNotification)
import RudderDataTypes exposing (AllNextSteps, AllStepChanges(..), BackStatus(..), ChangeRequestDetails, ChangeRequestMainDetails, ChangeRequestMainDetailsMetadata, Event(..), EventLog, NextStatus(..), StepChange(..), ViewState(..), decodeChangeRequestMainDetails)
import RudderLinkUtil exposing (ContextPath, changeRequestsPageUrl, getApiUrl, getContextPath)
import String
import String.Extra
import Url.Builder



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
                (ChangeMessageSettings False False)
    in
    ( initModel, getChangeMessageSettings initModel )



------------------------------
-- MODEL
------------------------------


type alias ChangeStepForm =
    { step : AllStepChanges
    , reasonsField : String
    }


type alias ChangeMessageSettings =
    { changeMessageEnabled : Bool
    , changeMessageMandatory : Bool
    }


type alias Model =
    { contextPath : ContextPath
    , hasValidatorWriteRights : Bool
    , hasDeployerWriteRights : Bool
    , editForm : EditForm.Model
    , changesForm : ChangesForm.Model
    , viewState : ViewState ChangeRequestMainDetails
    , changeStepPopup : ViewState ChangeStepForm
    , changeMessageSettings : ChangeMessageSettings
    }


type Msg
    = GetChangeRequestIdFromUrl String
    | GetChangeRequestMainDetails (Result (Detailed.Error String) ( Http.Metadata, ChangeRequestMainDetailsMetadata ))
    | EditFormMsg EditForm.Msg
    | ChangesFormMsg ChangesForm.Msg
    | OpenChangeStepPopup AllStepChanges
    | CloseChangeStepPopup
    | FormInputReasonsField String
    | ChangeRequestStepChange (Result (Detailed.Error String) ( Http.Metadata, Int ))
    | SubmitChangeStepForm Int StepChange
    | GetChangeMessageSettings (Result (Detailed.Error String) ( Http.Metadata, ChangeMessageSettings ))
    | ChangeSelectedNextStep NextStatus
    | Ignore



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
                , expect = Detailed.expectJson GetChangeRequestMainDetails decodeChangeRequestMainDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


acceptChangeRequest : Int -> Maybe String -> NextStatus -> Model -> Cmd Msg
acceptChangeRequest crId reasonOpt nextStatus model =
    let
        reasonParam =
            case reasonOpt of
                Just reasonField ->
                    [ Url.Builder.string "reason" reasonField ]

                Nothing ->
                    []

        params =
            Url.Builder.toQuery
                ([ Url.Builder.string "status" (stepChangeToString (Next nextStatus)) ] ++ reasonParam)

        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changeRequests/" ++ String.fromInt crId ++ "/accept") ++ params
                , body = emptyBody
                , expect = Detailed.expectJson ChangeRequestStepChange decodeChangeRequestId
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


declineChangeRequest : Int -> Maybe String -> Model -> Cmd Msg
declineChangeRequest crId reasonOpt model =
    let
        params =
            case reasonOpt of
                Just reasonField ->
                    Url.Builder.toQuery [ Url.Builder.string "reason" reasonField ]

                Nothing ->
                    ""

        req =
            request
                { method = "DELETE"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changeRequests/" ++ String.fromInt crId) ++ params
                , body = emptyBody
                , expect = Detailed.expectJson ChangeRequestStepChange decodeChangeRequestId
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


getChangeMessageSettings : Model -> Cmd Msg
getChangeMessageSettings model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath "settings"
                , body = emptyBody
                , expect = Detailed.expectJson GetChangeMessageSettings decodeChangeMessageSettings
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- JSON DECODERS
------------------------------


decodeChangeMessageSettings : Decoder ChangeMessageSettings
decodeChangeMessageSettings =
    at [ "data" ]
        (field "settings"
            (map2 ChangeMessageSettings
                (field "enable_change_message" bool)
                (field "mandatory_change_message" bool)
            )
        )


decodeChangeRequestId : Decoder Int
decodeChangeRequestId =
    at [ "data" ]
        (field "changeRequests"
            (index 0 (field "id" int))
        )



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
                Ok ( _, cr ) ->
                    let
                        isReachableStatus =
                            \status ->
                                ((status == PendingDeployment) && model.hasValidatorWriteRights)
                                    || ((status == Deployed) && model.hasDeployerWriteRights)

                        reachable =
                            cr.allNextSteps
                                |> List.filter isReachableStatus
                                |> uniqueBy (Next >> stepChangeToString)

                        next =
                            case List.head reachable of
                                Just selected ->
                                    Just
                                        { reachableNextSteps = reachable
                                        , selected = selected
                                        }

                                Nothing ->
                                    Nothing

                        crDetails =
                            { changeRequest = cr.changeRequest
                            , isPending = cr.isPending
                            , eventLogs = cr.eventLogs
                            , prevStatus = cr.prevStatus
                            , reachableNextSteps = next
                            }

                        formDetails =
                            { title = cr.changeRequest.title
                            , id = cr.changeRequest.id
                            , state = cr.changeRequest.state
                            , description = cr.changeRequest.description
                            }
                    in
                    ( { model
                        | viewState = Success crDetails
                        , changesForm = model.changesForm |> ChangesForm.updateChangeRequestDetails crDetails
                        , editForm = model.editForm |> EditForm.updateChangeRequestDetails formDetails
                      }
                    , Cmd.none
                    )

                Err error ->
                    let
                        errMsg =
                            processApiErrorMsg " trying to fetch change request details" error
                    in
                    ( { model | viewState = ViewError errMsg }, Cmd.none )

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
                Ok ( _, crId ) ->
                    ( model
                    , Cmd.batch
                        [ successNotification "Change request step successfully changed"
                        , getChangeRequestMainDetails model crId
                        ]
                    )

                Err error ->
                    let
                        errMsg =
                            processApiErrorMsg " trying to change step of change request" error
                    in
                    ( model, errorNotification errMsg )

        FormInputReasonsField newReason ->
            ( model |> updateChangeStepForm (setReasonsField newReason), Cmd.none )

        SubmitChangeStepForm crId (Next next) ->
            case model.changeStepPopup of
                Success changeStepForm ->
                    ( { model | changeStepPopup = NoView }, acceptChangeRequest crId (Just changeStepForm.reasonsField) next model )

                _ ->
                    ( model, Cmd.none )

        SubmitChangeStepForm crId (Back _) ->
            case model.changeStepPopup of
                Success changeStepForm ->
                    ( { model | changeStepPopup = NoView }, declineChangeRequest crId (Just changeStepForm.reasonsField) model )

                _ ->
                    ( model, Cmd.none )

        GetChangeMessageSettings result ->
            case result of
                Ok ( _, settings ) ->
                    ( { model | changeMessageSettings = settings }, Cmd.none )

                Err error ->
                    let
                        errMsg =
                            processApiErrorMsg " getting change message settings" error
                    in
                    ( { model | viewState = ViewError errMsg }, Cmd.none )

        ChangeSelectedNextStep nextStatus ->
            ( model |> updateChangeStepForm (setSelectedNextStep nextStatus), Cmd.none )

        Ignore ->
            ( model, Cmd.none )


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


setSelectedNextStep : NextStatus -> ViewState ChangeStepForm -> ViewState ChangeStepForm
setSelectedNextStep nextStatus changeStepFormState =
    case changeStepFormState of
        Success changeStepForm ->
            case changeStepForm.step of
                BackSteps _ ->
                    changeStepFormState

                NextSteps next ->
                    if List.member nextStatus next.reachableNextSteps then
                        Success { changeStepForm | step = NextSteps { next | selected = nextStatus } }

                    else
                        changeStepFormState

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
    let
        ( body, banner ) =
            case model.viewState of
                Success changeRequest ->
                    ( [ Html.map EditFormMsg (EditForm.view model.editForm)
                      , warnOnUnmergeableView changeRequest.changeRequest
                      , Html.map ChangesFormMsg (ChangesForm.view model.changesForm)
                      ]
                    , bannerView model changeRequest
                    )

                NoView ->
                    ( [ errorView model "The change request id was not set." ], text "" )

                ViewError errMsg ->
                    ( [ errorView model errMsg ], text "" )
    in
    div [ class "one-col" ]
        [ banner
        , div [ class "one-col-main" ]
            [ div [ class "template-main" ]
                [ div [ class "main-container" ]
                    [ div [ class "main-details" ]
                        body
                    ]
                ]
            ]
        ]


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
                , actionButtons model cr.changeRequest.id canChangeStep cr.prevStatus cr.reachableNextSteps
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


actionButtons : Model -> Int -> Bool -> Maybe BackStatus -> Maybe AllNextSteps -> Html Msg
actionButtons model crId canChangeStep backStatus allNextSteps =
    let
        canChangeStepAttr =
            disabled (not canChangeStep)

        backStepButton back =
            case back of
                Cancelled ->
                    button
                        [ id "backStep"
                        , class backStepBtnClass
                        , canChangeStepAttr
                        , onClick (OpenChangeStepPopup (BackSteps back))
                        ]
                        [ text "Decline" ]

        nextStepButton next =
            let
                action =
                    case next.selected of
                        PendingDeployment ->
                            "Validate"

                        Deployed ->
                            "Deploy"
            in
            button
                [ id "nextStep"
                , style "float" "right"
                , class "btn btn-success"
                , canChangeStepAttr
                , onClick (OpenChangeStepPopup (NextSteps next))
                ]
                [ text action ]

        buttons =
            case ( backStatus, allNextSteps ) of
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
        , changeStepPopupView model.changeStepPopup model.changeMessageSettings.changeMessageEnabled crId
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
                            ("The affected resources have been modified since this change request was created."
                                ++ " If you accept this change request, previous modifications will be lost."
                            )
                        ]
                    , p [] [ text "Please double-check that this is what you want before deploying this change request." ]
                    ]
                ]
            ]

    else
        text ""


changeStepPopupView : ViewState ChangeStepForm -> Bool -> Int -> Html Msg
changeStepPopupView changeStepFormState changeMessageEnabled crId =
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

        reasonField =
            div [ id "reason", class "mt-3" ]
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
                            , onInput FormInputReasonsField
                            ]
                            []
                        ]
                    ]
                ]

        popupBody action intro btnClass btnClickCmd statusSelector =
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
                                , onClick CloseChangeStepPopup
                                ]
                                []
                            ]
                        , div [ id "form" ]
                            [ form []
                                [ div [ class "modal-body" ]
                                    ([ div [ id "intro" ]
                                        [ h5
                                            [ class "text-center" ]
                                            [ text intro ]
                                        ]
                                     , div [ id "formError" ] []
                                     , div
                                        [ class "mt-3 from-group" ]
                                        [ label [] [ b [] [ text "Next state " ] ]
                                        , statusSelector
                                        ]
                                     ]
                                        ++ (if changeMessageEnabled then
                                                [ reasonField ]

                                            else
                                                []
                                           )
                                    )
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
                                    , button
                                        [ id "confirm"
                                        , class btnClass
                                        , value action
                                        , type_ "button"
                                        , onClick btnClickCmd
                                        ]
                                        [ text action ]
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
                selectedStatus =
                    selectedStep changeStepForm.step

                action =
                    stepChangeAction selectedStatus

                btnClickCmd =
                    SubmitChangeStepForm crId selectedStatus

                statusSelector =
                    statusSelect changeStepForm.step

                ( btnClass, intro ) =
                    case changeStepForm.step of
                        BackSteps b ->
                            ( backStepBtnClass
                            , " The change request will be sent to the '" ++ stepChangeToString (Back b) ++ "' status "
                            )

                        NextSteps _ ->
                            ( nextStepBtnClass, " Please choose the next state of this change request " )
            in
            div visiblePopupAttr (popupBody action intro btnClass btnClickCmd statusSelector)

        _ ->
            text ""


errorView : Model -> String -> Html Msg
errorView model errMsg =
    div
        [ style "padding" "40px"
        , style "text-align" "center"
        ]
        [ h2 [] [ text "Error" ]
        , h3 [] [ text errMsg ]
        , a [ href (changeRequestsPageUrl model.contextPath) ] [ text "Back to change requests page" ]
        ]



------------------------------
-- UTIL
------------------------------


stepChangeToString : StepChange -> String
stepChangeToString stepChange =
    case stepChange of
        Back backStatus ->
            case backStatus of
                Cancelled ->
                    "Cancelled"

        Next nextStatus ->
            case nextStatus of
                PendingDeployment ->
                    "Pending deployment"

                Deployed ->
                    "Deployed"


stepChangeAction : StepChange -> String
stepChangeAction stepChange =
    case stepChange of
        Back backStatus ->
            case backStatus of
                Cancelled ->
                    "Decline"

        Next nextStatus ->
            case nextStatus of
                PendingDeployment ->
                    "Validate"

                Deployed ->
                    "Deploy"


selectedStep : AllStepChanges -> StepChange
selectedStep allStepChanges =
    case allStepChanges of
        BackSteps backStatus ->
            Back backStatus

        NextSteps allNextSteps ->
            Next allNextSteps.selected


statusSelect : AllStepChanges -> Html Msg
statusSelect allStepChanges =
    case allStepChanges of
        BackSteps backStatus ->
            span
                [ class "well" ]
                [ text <| String.Extra.surround "   " (stepChangeToString (Back backStatus)) ]

        NextSteps allNextSteps ->
            let
                options =
                    allNextSteps.reachableNextSteps
                        |> List.map
                            (\status ->
                                let
                                    stepStr =
                                        stepChangeToString (Next status)
                                in
                                option
                                    [ selected (status == allNextSteps.selected)
                                    , value stepStr
                                    ]
                                    [ text stepStr ]
                            )
            in
            select
                [ id "next"
                , class "form-select mb-3"
                , onInput
                    (\val ->
                        case val of
                            "Pending deployment" ->
                                ChangeSelectedNextStep PendingDeployment

                            "Deployed" ->
                                ChangeSelectedNextStep Deployed

                            _ ->
                                Ignore
                    )
                ]
                options


processApiErrorMsg : String -> Detailed.Error String -> String
processApiErrorMsg apiName err =
    let
        message =
            case err of
                Detailed.BadUrl url ->
                    "The URL " ++ url ++ " was invalid"

                Detailed.Timeout ->
                    "Unable to reach the server, try again"

                Detailed.NetworkError ->
                    "Unable to reach the server, check your network connection"

                Detailed.BadStatus _ body ->
                    let
                        ( title, errors ) =
                            decodeErrorDetails body
                    in
                    title ++ "\n" ++ errors

                Detailed.BadBody _ _ msg ->
                    msg
    in
    "Error while " ++ apiName ++ ", details: \n" ++ message



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
