module ChangeRequestEditForm exposing (..)

------------------------------
-- Init and main --
------------------------------

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, a, div, form, h2, h3, input, label, p, span, text, textarea)
import Html.Attributes exposing (class, disabled, for, href, id, maxlength, name, readonly, required, style, type_, value)
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
                Form { initValues, formValues } ->
                    if canSaveChanges initValues formValues then
                        ( model, updateChangeRequestDetails model formValues )

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
        Form ({ formValues } as formState) ->
            Form { formState | formValues = { formValues | description = newDescription } }

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
                [ id "change-request-edit-form" ]
                [ div
                    [ class "col-lg-6 col-xs-12 pe-3 ps-3", id "detailsForm" ]
                    [ form
                        [ class "needs-validation", id "changeRequestEditForm" ]
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
                            []
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
        [ class "form-control col-xs-12"
        , disabled True
        , readonly True
        , value content
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
            [ name "CRName"
            , type_ "text"
            , value crName
            , id "CRNameLabel"
            ]
                ++ (if writeRights then
                        [ class inputClass
                        , required True
                        , onInput FormInputName
                        ]

                    else
                        [ class "form-control col-xs-12"
                        , disabled True
                        , readonly True
                        ]
                   )
    in
    div [ id "CRName" ]
        [ div
            [ class "row" ]
            [ label
                [ for "CRNameLabel", class "col-xs-12 form-label" ]
                [ span [] [ text "Change request title" ] ]
            , div
                [ class "needs-validation" ]
                [ input attributes []
                , div
                    [ class "invalid-feedback" ]
                    [ text "The change request title cannot be empty." ]
                ]
            ]
        ]


editCRDescription : String -> Bool -> Html Msg
editCRDescription crDescription writeRights =
    let
        inputClass =
            "form-control col-xs-12"

        attributes =
            [ name "CRDescription"
            , id "CRDescriptionLabel"
            ]
                ++ (if writeRights then
                        [ maxlength 255
                        , onInput FormInputDescription
                        , class inputClass
                        ]

                    else
                        [ class inputClass
                        , disabled True
                        , readonly True
                        ]
                   )
    in
    div
        [ id "CRDescription" ]
        [ div
            [ class "row" ]
            [ label
                [ for "CRDescriptionLabel", class "col-xs-12" ]
                [ span [ class "fw-normal" ] [ text "Description" ] ]
            , div
                [ class "col-xs-12" ]
                [ textarea attributes [ text crDescription ]
                ]
            ]
        ]


saveButton : ChangeRequestDetails -> ChangeRequestDetails -> Bool -> Html Msg
saveButton modelCR formCR writeRights =
    if writeRights then
        let
            attrList =
                [ value "Update"
                , class "btn btn-default"
                , type_ "button"
                , onClick FormSubmit
                , disabled <| not (canSaveChanges modelCR formCR)
                ]
        in
        div [ id "CRSave" ]
            [ input
                attrList
                [ text "Update" ]
            ]

    else
        div [ id "CRSave" ] []


errorView : Model -> String -> Html Msg
errorView model errMsg =
    div
        [ style "padding" "40px"
        , style "text-align" "center"
        ]
        [ h2 [] [ text "Change request id was not found" ]
        , h3 [] [ text errMsg ]
        , a [ href (changeRequestsPageUrl model) ] [ text "Back to change requests page" ]
        ]



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    readUrl (\id -> GetChangeRequestIdFromUrl id)
