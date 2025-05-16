module ChangeRequestEditForm exposing (..)

------------------------------
-- Init and main --
------------------------------

import Browser
import Browser.Navigation exposing (load)
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, button, div, form, h2, h3, input, label, p, span, text, textarea)
import Html.Attributes as Attr exposing (placeholder, type_)
import Html.Events exposing (onClick, onInput, onSubmit)
import Http exposing (Error, emptyBody, expectJson, header, jsonBody, request)
import Json.Decode exposing (Decoder, andThen, at, fail, field, index, int, map3, map4, map5, string, succeed)
import Json.Encode as Encode
import Ports exposing (errorNotification, readUrl, successNotification)


getApiUrl : Model -> String -> String
getApiUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


changeRequestsPageUrl : Model -> String
changeRequestsPageUrl m =
    m.contextPath ++ "/secure/configurationManager/changes/changeRequests"


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
                flags.contextPath
                ChangeRequestIdNotSet
                flags.hasWriteRights
                NoView
    in
    ( initModel, Cmd.none )



------------------------------
-- MODEL --
------------------------------


type alias Model =
    { contextPath : String
    , changeRequest : ChangeRequestDetailsOpt
    , hasWriteRights : Bool
    , viewState : ViewState
    }


type Msg
    = GetChangeRequestDetails (Result Error ChangeRequestDetails)
    | SetChangeRequestDetails (Result Error ChangeRequestDetails)
    | GetChangeRequestIdFromUrl String
      -- Form input
    | FormInputName String
    | FormInputDescription String
    | FormSubmit


type alias ChangeRequestDetails =
    { title : String
    , state : String
    , id : Int
    , description : String
    }


type ChangeRequestDetailsOpt
    = Success ChangeRequestDetails
    | ChangeRequestIdNotSet


type ViewState
    = NoView
    | ViewError String
    | Form { initValues : ChangeRequestDetails, formValues : ChangeRequestDetails }



------------------------------
-- API --
------------------------------


getChangeRequestDetails : Model -> Int -> Cmd Msg
getChangeRequestDetails model crId =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("changeRequests/" ++ String.fromInt crId)
                , body = emptyBody
                , expect = expectJson GetChangeRequestDetails decodeChangeRequestDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


updateChangeRequestDetails : Model -> ChangeRequestDetails -> Cmd Msg
updateChangeRequestDetails model changeRequestDetails =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model ("changeRequests/" ++ String.fromInt changeRequestDetails.id)
                , body = encodeChangeRequestDetails changeRequestDetails |> jsonBody
                , expect = expectJson SetChangeRequestDetails decodeChangeRequestDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- ENCODE / DECODE JSON --
------------------------------


decodeChangeRequestStatus : Decoder String
decodeChangeRequestStatus =
    string
        |> andThen
            (\str ->
                case str of
                    "Open" ->
                        succeed "Open"

                    "Closed" ->
                        succeed "Closed"

                    "Pending validation" ->
                        succeed "Pending validation"

                    "Pending deployment" ->
                        succeed "Pending deployment"

                    "Cancelled" ->
                        succeed "Cancelled"

                    "Deployed" ->
                        succeed "Deployed"

                    _ ->
                        fail "Invalid change request status"
            )


decodeChangeRequestDetails : Decoder ChangeRequestDetails
decodeChangeRequestDetails =
    at [ "data" ]
        (field "changeRequests"
            (index 0
                (map4
                    ChangeRequestDetails
                    (field "displayName" string)
                    (field "status" decodeChangeRequestStatus)
                    (field "id" int)
                    (field "description" string)
                )
            )
        )


encodeChangeRequestDetails : ChangeRequestDetails -> Encode.Value
encodeChangeRequestDetails changeRequestDetails =
    Encode.object
        [ ( "description", Encode.string changeRequestDetails.description )
        , ( "name", Encode.string changeRequestDetails.title )
        ]



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetChangeRequestDetails result ->
            case result of
                Ok changeRequestDetails ->
                    ( { model | changeRequest = Success changeRequestDetails, viewState = initForm changeRequestDetails }, Cmd.none )

                Err err ->
                    let
                        errMsg =
                            getErrorMessage err
                    in
                    ( { model | viewState = ViewError errMsg }
                    , errorNotification ("Error while trying to fetch change request details: " ++ errMsg)
                    )

        GetChangeRequestIdFromUrl crIdStr ->
            case String.toInt crIdStr of
                Just crId ->
                    ( model, getChangeRequestDetails model crId )

                Nothing ->
                    let
                        errMsg =
                            crIdStr ++ " is not a valid change request id"
                    in
                    ( { model | viewState = ViewError errMsg }, errorNotification errMsg )

        SetChangeRequestDetails result ->
            case result of
                Ok changeRequestDetails ->
                    ( { model | changeRequest = Success changeRequestDetails, viewState = initForm changeRequestDetails }
                    , successNotification "Successfully updated change request details"
                    )

                Err err ->
                    let
                        errMsg =
                            getErrorMessage err
                    in
                    ( model, errorNotification ("Error while trying to update change request details: " ++ errMsg) )

        FormInputName newName ->
            ( model |> updateForm (setName newName), Cmd.none )

        FormInputDescription newDescription ->
            ( model |> updateForm (setDescription newDescription), Cmd.none )

        FormSubmit ->
            case model.viewState of
                Form formState ->
                    if canSaveChanges formState.initValues formState.formValues then
                        ( model, updateChangeRequestDetails model formState.formValues )

                    else
                        ( model, Cmd.none )

                _ ->
                    ( model, Cmd.none )


initForm : ChangeRequestDetails -> ViewState
initForm cr =
    Form { initValues = cr, formValues = cr }


updateForm : (ViewState -> ViewState) -> Model -> Model
updateForm f model =
    { model | viewState = f model.viewState }


setDescription : String -> ViewState -> ViewState
setDescription newDescription viewState =
    case viewState of
        Form formState ->
            let
                formVal =
                    formState.formValues
            in
            Form { formState | formValues = { formVal | description = newDescription } }

        _ ->
            viewState


setName : String -> ViewState -> ViewState
setName newName viewState =
    case viewState of
        Form formState ->
            let
                formVal =
                    formState.formValues
            in
            Form { formState | formValues = { formVal | title = newName } }

        _ ->
            viewState


formModified : ChangeRequestDetails -> ChangeRequestDetails -> Bool
formModified initCR formCR =
    not (initCR.title == formCR.title) || not (initCR.description == formCR.description)


{-| In order to save the changes in the change request form, the form must have been modified
_and_ the change request title mustn't be empty |
-}
canSaveChanges : ChangeRequestDetails -> ChangeRequestDetails -> Bool
canSaveChanges initCR formCR =
    formModified initCR formCR && not (String.isEmpty formCR.title)



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    case model.viewState of
        Form formState ->
            let
                canEdit =
                    model.hasWriteRights
            in
            div
                [ Attr.id "change-request-edit-form" ]
                [ div
                    [ Attr.class "col-lg-6 col-xs-12 pe-3 ps-3", Attr.id "detailsForm" ]
                    [ form
                        [ Attr.class "needs-validation", Attr.id "changeRequestEditForm" ]
                        [ editCRName formState.formValues.title canEdit
                        , div
                            []
                            [ label [] [ text "State" ]
                            , roInputField formState.initValues.state
                            ]
                        , div []
                            [ label [] [ text "ID" ]
                            , roInputField (String.fromInt formState.formValues.id)
                            ]
                        , editCRDescription formState.formValues.description canEdit
                        , div
                            [ Attr.class "" ]
                            [ saveButton formState.initValues formState.formValues canEdit ]
                        ]
                    ]
                ]

        NoView ->
            errorView model "Change Request Id was not set."

        ViewError errMsg ->
            errorView model errMsg


roInputField : String -> Html Msg
roInputField content =
    input
        [ Attr.class "form-control col-xs-12"
        , Attr.disabled True
        , Attr.readonly True
        , Attr.id "CRStatusDetails"
        , Attr.value content
        ]
        []


editCRName : String -> Bool -> Html Msg
editCRName crName writeRights =
    let
        inputClass =
            if crName == "" then
                "form-control col-xs-12 is-invalid"

            else
                "form-control col-xs-12"

        attributes =
            [ Attr.name "CRName"
            , Attr.type_ "text"
            , Attr.value crName
            , Attr.id "CRNameLabel"
            ]
                ++ (if writeRights then
                        [ Attr.class inputClass
                        , Attr.required True
                        , onInput FormInputName
                        ]

                    else
                        [ Attr.class "form-control col-xs-12"
                        , Attr.disabled True
                        , Attr.readonly True
                        ]
                   )
    in
    div [ Attr.id "CRName" ]
        [ div
            [ Attr.class "row" ]
            [ label
                [ Attr.for "CRNameLabel", Attr.class "col-xs-12 form-label" ]
                [ span [] [ text "Change request title" ] ]
            , div
                [ Attr.class "input-group needs-validation" ]
                [ input attributes []
                , div
                    [ Attr.class "invalid-feedback" ]
                    [ text "The change request title cannot be empty." ]
                ]
            ]
        ]


editCRDescription : String -> Bool -> Html Msg
editCRDescription crDescription writeRights =
    let
        inputClass =
            if String.length crDescription > 255 then
                "form-control is-invalid col-xs-12"

            else
                "form-control col-xs-12"

        attributes =
            [ Attr.name "CRDescription"
            , Attr.id "CRDescriptionLabel"
            ]
                ++ (if writeRights then
                        [ Attr.maxlength 255
                        , onInput FormInputDescription
                        , Attr.class inputClass
                        ]

                    else
                        [ Attr.class "form-control col-xs-12"
                        , Attr.disabled True
                        , Attr.readonly True
                        ]
                   )
    in
    div
        [ Attr.id "CRDescription" ]
        [ div
            [ Attr.class "row" ]
            [ label
                [ Attr.for "CRDescriptionLabel", Attr.class "col-xs-12" ]
                [ span [ Attr.class "fw-normal" ] [ text "Description" ] ]
            , div
                [ Attr.class "col-xs-12" ]
                [ textarea attributes [ text crDescription ]
                ]
            ]
        ]


saveButton : ChangeRequestDetails -> ChangeRequestDetails -> Bool -> Html Msg
saveButton modelCR formCR writeRights =
    if writeRights then
        let
            attrList =
                [ Attr.value "Update"
                , Attr.class "btn btn-default"
                , Attr.type_ "button"
                , onClick FormSubmit
                ]
                    ++ (if canSaveChanges modelCR formCR then
                            []

                        else
                            [ Attr.disabled True ]
                       )
        in
        div [ Attr.id "CRSave" ]
            [ input
                attrList
                [ text "Update" ]
            ]

    else
        div [ Attr.id "CRSave" ] []


errorView : Model -> String -> Html Msg
errorView model errMsg =
    div
        [ Attr.style "padding" "40px"
        , Attr.style "text-align" "center"
        ]
        [ h2 [] [ text "Change request id was not found" ]
        , h3 [] [ text errMsg ]
        , a [ Attr.href (changeRequestsPageUrl model) ] [ text "Back to change requests page" ]
        ]



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
