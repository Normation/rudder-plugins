module ChangeRequestEditForm exposing (Form, Model, Msg, Outcome(..), initModel, update, updateChangeRequestDetails, view)

import ErrorMessages exposing (getErrorMessage)
import Html exposing (Html, div, form, input, label, span, text, textarea)
import Html.Attributes exposing (class, disabled, for, id, maxlength, name, readonly, required, type_, value)
import Html.Events exposing (onClick, onInput)
import Http exposing (Error, expectJson, header, jsonBody, request)
import Json.Encode as Encode
import Ports exposing (errorNotification, successNotification)
import RudderDataTypes exposing (ChangeRequestFormDetails, ViewState(..), decodeFormDetails)
import RudderLinkUtil exposing (ContextPath, getApiUrl, getContextPath)



------------------------------
-- Init and main --
------------------------------


initModel : { contextPath : String, hasValidatorWriteRights : Bool, hasDeployerWriteRights : Bool } -> Model
initModel flags =
    Model
        (getContextPath flags.contextPath)
        flags.hasValidatorWriteRights
        flags.hasDeployerWriteRights
        NoView



------------------------------
-- MODEL --
------------------------------


type alias Form =
    { initValues : ChangeRequestFormDetails
    , formValues : ChangeRequestFormDetails
    }


type alias Model =
    { contextPath : ContextPath
    , hasValidatorWriteRights : Bool
    , hasDeployerWriteRights : Bool
    , viewState : ViewState Form
    }


type Msg
    = SetChangeRequestDetails (Result Error ChangeRequestFormDetails)
      -- Form input
    | FormInputName String
    | FormInputDescription String
    | FormSubmit


type Outcome
    = OutcomeNone
    | OutcomeFormModified Int



------------------------------
-- API --
------------------------------


saveChangeRequestDetails : Model -> ChangeRequestFormDetails -> Cmd Msg
saveChangeRequestDetails model changeRequestDetails =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getApiUrl model.contextPath ("changeRequests/" ++ String.fromInt changeRequestDetails.id)
                , body = encodeFormDetails changeRequestDetails |> jsonBody
                , expect = expectJson SetChangeRequestDetails decodeFormDetails
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



------------------------------
-- ENCODE / DECODE JSON --
------------------------------


encodeFormDetails : ChangeRequestFormDetails -> Encode.Value
encodeFormDetails changeRequestDetails =
    Encode.object
        [ ( "description", Encode.string changeRequestDetails.description )
        , ( "name", Encode.string changeRequestDetails.title )
        ]



------------------------------
-- UPDATE --
------------------------------


updateChangeRequestDetails : ChangeRequestFormDetails -> Model -> Model
updateChangeRequestDetails cr model =
    { model | viewState = initForm cr }


update : Msg -> Model -> ( Model, Cmd Msg, Outcome )
update msg model =
    case msg of
        SetChangeRequestDetails result ->
            case result of
                Ok changeRequestDetails ->
                    ( { model | viewState = initForm changeRequestDetails }
                    , successNotification "Successfully updated change request details"
                    , OutcomeFormModified changeRequestDetails.id
                    )

                Err err ->
                    let
                        errMsg =
                            getErrorMessage err
                    in
                    ( model
                    , errorNotification ("Error while trying to update change request details: " ++ errMsg)
                    , OutcomeNone
                    )

        FormInputName newName ->
            ( model |> updateForm (setName newName), Cmd.none, OutcomeNone )

        FormInputDescription newDescription ->
            ( model |> updateForm (setDescription newDescription), Cmd.none, OutcomeNone )

        FormSubmit ->
            case model.viewState of
                Success { initValues, formValues } ->
                    if canSaveChanges initValues formValues then
                        ( model, saveChangeRequestDetails model formValues, OutcomeNone )

                    else
                        ( model, Cmd.none, OutcomeNone )

                _ ->
                    ( model, Cmd.none, OutcomeNone )


initForm : ChangeRequestFormDetails -> ViewState Form
initForm cr =
    Success { initValues = cr, formValues = cr }


updateForm : (ViewState Form -> ViewState Form) -> Model -> Model
updateForm f model =
    { model | viewState = f model.viewState }


setDescription : String -> ViewState Form -> ViewState Form
setDescription newDescription viewState =
    case viewState of
        Success ({ formValues } as formState) ->
            Success { formState | formValues = { formValues | description = newDescription } }

        _ ->
            viewState


setName : String -> ViewState Form -> ViewState Form
setName newName viewState =
    case viewState of
        Success formState ->
            let
                formVal =
                    formState.formValues
            in
            Success { formState | formValues = { formVal | title = newName } }

        _ ->
            viewState


formModified : ChangeRequestFormDetails -> ChangeRequestFormDetails -> Bool
formModified initCR formCR =
    not (initCR.title == formCR.title) || not (initCR.description == formCR.description)


{-| In order to save the changes in the change request form, the form must have been modified
_and_ the change request title mustn't be empty |
-}
canSaveChanges : ChangeRequestFormDetails -> ChangeRequestFormDetails -> Bool
canSaveChanges initCR formCR =
    formModified initCR formCR && not (String.isEmpty formCR.title)



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    case model.viewState of
        Success formState ->
            let
                status =
                    formState.initValues.state

                canEdit =
                    case status of
                        "Pending validation" ->
                            model.hasValidatorWriteRights

                        "Pending deployment" ->
                            model.hasDeployerWriteRights

                        _ ->
                            False
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
                            , roInputField status
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

        _ ->
            text ""


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


saveButton : ChangeRequestFormDetails -> ChangeRequestFormDetails -> Bool -> Html Msg
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
