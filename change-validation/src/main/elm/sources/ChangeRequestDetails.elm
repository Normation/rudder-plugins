module ChangeRequestDetails exposing (init)

import Browser
import ChangeRequestChangesForm as ChangesForm
import ChangeRequestEditForm as EditForm
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, div, p, span, text)
import Html.Attributes exposing (class, id)
import Http exposing (Error, emptyBody, expectJson, header, request)
import Ports exposing (errorNotification, readUrl)
import RudderDataTypes exposing (ChangeRequestDetails, ChangeRequestMainDetails, ViewState(..), decodeChangeRequestMainDetails)
import RudderLinkUtil exposing (ContextPath, getApiUrl, getContextPath)



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


init : { contextPath : String, hasWriteRights : Bool } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model
                (getContextPath flags.contextPath)
                flags.hasWriteRights
                (EditForm.initModel flags)
                (ChangesForm.initModel flags)
                NoView
    in
    ( initModel, Cmd.none )



------------------------------
-- MODEL
------------------------------


type alias Model =
    { contextPath : ContextPath
    , hasWriteRights : Bool
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
                [ div [ id "changeRequestHeader", class "main-header" ]
                    [ bannerView changeRequest ]
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


bannerView : ChangeRequestMainDetails -> Html Msg
bannerView changeRequest =
    div [ class "change-request-details" ]
        [ text "todo banner" ]


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
