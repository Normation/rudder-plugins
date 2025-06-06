module WorkflowUsers exposing (EditMod, ValidateAllView, WorkflowUsersModel, WorkflowUsersMsg, WorkflowUsersView, getUsers, initModel, initValidateAllForm, update, view)

import ErrorMessages exposing (getErrorMessage)
import Html exposing (..)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, style, type_, value)
import Html.Events exposing (onCheck, onClick)
import Http exposing (Error, emptyBody, expectJson, jsonBody, request)
import Json.Decode exposing (Decoder, at, bool, list, string, succeed)
import Json.Decode.Pipeline exposing (required)
import Json.Encode exposing (Value)
import JsonUtils exposing (decodeSetting, encodeSetting)
import List exposing (filter, isEmpty, length, member)
import Ports exposing (errorNotification, successNotification)
import String exposing (fromInt)



------------------------------
-- UTIL                     --
------------------------------


getUsernames : UserList -> List Username
getUsernames users =
    List.map .username users


filterValidatedUsers : UserList -> UserList
filterValidatedUsers users =
    filter (\u -> u.isValidated) users


filterUnvalidatedUsers : UserList -> UserList
filterUnvalidatedUsers users =
    filter (\u -> not u.isValidated) users



------------------------------
-- INIT                     --
------------------------------


initModel : String -> Bool -> WorkflowUsersModel
initModel contextPath hasWriteRights =
    WorkflowUsersModel contextPath Off hasWriteRights WorkflowUsersInitView ValidateAllInitView



------------------------------
-- MODEL                    --
------------------------------


type alias UserList =
    List User


type alias Username =
    String


type alias ApiMsg =
    String


type EditMod
    = On
    | Off



{--
  Left  : validated users
  Right : unvalidated users
--}


type ColPos
    = Left
    | Right


type alias User =
    { username : Username
    , isValidated : Bool
    , isInFile : Bool
    }


type alias WorkflowUsersModel =
    { contextPath : String
    , editMod : EditMod
    , hasWriteRights : Bool
    , workflowUsersView : WorkflowUsersView
    , validateAllView : ValidateAllView
    }


type alias WorkflowUsersForm =
    { users : UserList
    , validatedUsers : UserList
    , unvalidatedUsers : UserList
    , rightChecked : UserList
    , leftChecked : UserList
    , hasMoved : UserList -- To track updates
    }


type WorkflowUsersView
    = WorkflowUsersInitView
    | WorkflowUsers WorkflowUsersForm


type UserListField
    = Users
    | ValidatedUsers
    | UnvalidatedUsers
    | RightChecked
    | LeftChecked
    | HasMoved


type ValidateAllView
    = ValidateAllInitView
    | ValidateAll { initValues : FormState, formValues : FormState }


type alias FormState =
    { validateAll : Bool -- "enable_validate_all" setting
    }


type WorkflowUsersMsg
    = {--Messages for the "Workflow Users" table --}
      {--API CALLS --}
      GetUsers (Result Error UserList)
    | RemoveUser (Result Error Username)
    | SaveWorkflowUsers (Result Error UserList)
    | CallApi (WorkflowUsersModel -> Cmd WorkflowUsersMsg)
      {--TABLE MANAGE CONTENT --}
    | LeftToRight
    | RightToLeft
    | AddLeftChecked User Bool
    | AddRightChecked User Bool
    | CheckAll ColPos Bool
      {--MOD MANAGEMENT --}
    | SwitchMode
    | ExitEditMod
      {--Messages for the "Validate all changes" checkbox and button --}
      {--API CALLS--}
    | SaveValidateAllSetting (Result Error Bool)
      {--VIEW UPDATE--}
    | ChangeValidateAllSetting Bool



------------------------------
-- UPDATE                   --
------------------------------


update : WorkflowUsersMsg -> WorkflowUsersModel -> ( WorkflowUsersModel, Cmd WorkflowUsersMsg )
update msg model =
    case msg of
        GetUsers result ->
            case result of
                Ok users ->
                    ( { model
                        | workflowUsersView =
                            WorkflowUsers
                                { users = users
                                , unvalidatedUsers = filterUnvalidatedUsers users
                                , validatedUsers = filterValidatedUsers users
                                , leftChecked = []
                                , rightChecked = []
                                , hasMoved = []
                                }
                      }
                    , Cmd.none
                    )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to get users:" ++ getErrorMessage error) )

        RemoveUser result ->
            case result of
                Ok removeUser ->
                    ( { model | workflowUsersView = mapUserList Users (filter (\m -> m.username /= removeUser)) model.workflowUsersView }, Cmd.none )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to delete a validated users:" ++ getErrorMessage error) )

        SaveWorkflowUsers result ->
            case result of
                Ok updatedUsers ->
                    ( { model
                        | workflowUsersView =
                            model.workflowUsersView
                                |> setUserListOn Users updatedUsers
                                |> setUserListOn UnvalidatedUsers (filterUnvalidatedUsers updatedUsers)
                                |> setUserListOn ValidatedUsers (filterValidatedUsers updatedUsers)
                                |> setUserListOn HasMoved []
                                |> setChecked [] []
                      }
                    , successNotification ""
                    )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to save validated users:" ++ getErrorMessage error) )

        CallApi call ->
            ( model, call model )

        RightToLeft ->
            case model.workflowUsersView of
                WorkflowUsers form ->
                    ( { model
                        | workflowUsersView =
                            model.workflowUsersView
                                |> mapUserList UnvalidatedUsers (filter (\u -> not (member u form.rightChecked)))
                                |> setUserListOn HasMoved (form.hasMoved ++ form.rightChecked)
                                |> setUserListOn ValidatedUsers (form.rightChecked ++ form.validatedUsers)
                                |> setChecked [] []
                      }
                    , Cmd.none
                    )

                _ ->
                    ( model, Cmd.none )

        LeftToRight ->
            case model.workflowUsersView of
                WorkflowUsers form ->
                    ( { model
                        | workflowUsersView =
                            model.workflowUsersView
                                |> setUserListOn UnvalidatedUsers (form.leftChecked ++ form.unvalidatedUsers)
                                |> setUserListOn HasMoved (form.hasMoved ++ form.leftChecked)
                                |> mapUserList ValidatedUsers (filter (\u -> not (member u form.leftChecked)))
                                |> setChecked [] []
                      }
                    , Cmd.none
                    )

                _ ->
                    ( model, Cmd.none )

        AddLeftChecked user isChecked ->
            case model.workflowUsersView of
                WorkflowUsers form ->
                    if not (member user form.leftChecked) && isChecked then
                        ( { model
                            | workflowUsersView =
                                model.workflowUsersView
                                    |> setChecked (user :: form.leftChecked) []
                          }
                        , Cmd.none
                        )

                    else
                        ( { model
                            | workflowUsersView = model.workflowUsersView |> mapUserList LeftChecked (filter (\u -> user /= u))
                          }
                        , Cmd.none
                        )

                _ ->
                    ( model, Cmd.none )

        AddRightChecked user isChecked ->
            case model.workflowUsersView of
                WorkflowUsers form ->
                    if not (member user form.rightChecked) && isChecked then
                        ( { model
                            | workflowUsersView =
                                model.workflowUsersView
                                    |> setChecked [] (user :: form.rightChecked)
                          }
                        , Cmd.none
                        )

                    else
                        ( { model
                            | workflowUsersView = model.workflowUsersView |> mapUserList RightChecked (filter (\u -> user /= u))
                          }
                        , Cmd.none
                        )

                _ ->
                    ( model, Cmd.none )

        CheckAll colPos isChecked ->
            case colPos of
                Left ->
                    if isChecked then
                        ( { model | workflowUsersView = model.workflowUsersView |> checkAllView Left }, Cmd.none )

                    else
                        ( { model | workflowUsersView = model.workflowUsersView |> setUserListOn LeftChecked [] }, Cmd.none )

                Right ->
                    if isChecked then
                        ( { model | workflowUsersView = model.workflowUsersView |> checkAllView Right }, Cmd.none )

                    else
                        ( { model | workflowUsersView = model.workflowUsersView |> setUserListOn RightChecked [] }, Cmd.none )

        SwitchMode ->
            case model.editMod of
                On ->
                    ( { model | editMod = Off }, Cmd.none )

                Off ->
                    ( { model | editMod = On }, Cmd.none )

        ExitEditMod ->
            ( { model
                | editMod = Off
                , workflowUsersView =
                    model.workflowUsersView
                        |> setChecked [] []
              }
            , Cmd.none
            )

        SaveValidateAllSetting result ->
            case result of
                Ok newSetting ->
                    ( { model | validateAllView = initValidateAllForm newSetting }, successNotification "Successfully saved setting" )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to save validate_all_enabled setting :" ++ getErrorMessage error) )

        ChangeValidateAllSetting enableValidateAll ->
            ( model |> setValidateAll enableValidateAll, Cmd.none )


setUserListOn : UserListField -> UserList -> WorkflowUsersView -> WorkflowUsersView
setUserListOn field newList viewState =
    case viewState of
        WorkflowUsers formState ->
            case field of
                Users ->
                    WorkflowUsers { formState | users = newList }

                ValidatedUsers ->
                    WorkflowUsers { formState | validatedUsers = newList }

                UnvalidatedUsers ->
                    WorkflowUsers { formState | unvalidatedUsers = newList }

                RightChecked ->
                    WorkflowUsers { formState | rightChecked = newList }

                LeftChecked ->
                    WorkflowUsers { formState | leftChecked = newList }

                HasMoved ->
                    WorkflowUsers { formState | hasMoved = newList }

        _ ->
            viewState


setChecked : UserList -> UserList -> WorkflowUsersView -> WorkflowUsersView
setChecked left right =
    setUserListOn LeftChecked left >> setUserListOn RightChecked right


checkAllView : ColPos -> WorkflowUsersView -> WorkflowUsersView
checkAllView colPos viewState =
    case viewState of
        WorkflowUsers formState ->
            case colPos of
                Left ->
                    viewState |> setChecked formState.validatedUsers []

                Right ->
                    viewState |> setChecked [] formState.unvalidatedUsers

        _ ->
            viewState


mapUserList : UserListField -> (UserList -> UserList) -> WorkflowUsersView -> WorkflowUsersView
mapUserList field f viewState =
    case viewState of
        WorkflowUsers formState ->
            case field of
                Users ->
                    WorkflowUsers { formState | users = f formState.users }

                ValidatedUsers ->
                    WorkflowUsers { formState | validatedUsers = f formState.validatedUsers }

                UnvalidatedUsers ->
                    WorkflowUsers { formState | unvalidatedUsers = f formState.unvalidatedUsers }

                RightChecked ->
                    WorkflowUsers { formState | rightChecked = f formState.rightChecked }

                LeftChecked ->
                    WorkflowUsers { formState | leftChecked = f formState.leftChecked }

                HasMoved ->
                    WorkflowUsers { formState | hasMoved = f formState.hasMoved }

        _ ->
            viewState


initValidateAllForm : Bool -> ValidateAllView
initValidateAllForm validateAll =
    let
        formState =
            { validateAll = validateAll }
    in
    ValidateAll { initValues = formState, formValues = formState }


setValidateAll : Bool -> WorkflowUsersModel -> WorkflowUsersModel
setValidateAll newValue model =
    let
        setView viewState =
            case viewState of
                ValidateAll formState ->
                    let
                        newFormState =
                            { validateAll = newValue }
                    in
                    ValidateAll { formState | formValues = newFormState }

                _ ->
                    viewState
    in
    { model | validateAllView = setView model.validateAllView }



------------------------------
-- API CALLS                --
------------------------------


getUrl : WorkflowUsersModel -> String -> String
getUrl m url =
    m.contextPath ++ "/secure/api/" ++ url


getUsers : WorkflowUsersModel -> Cmd WorkflowUsersMsg
getUsers model =
    let
        req =
            request
                { method = "GET"
                , headers = [ Http.header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "users"
                , body = emptyBody
                , expect = expectJson GetUsers decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveWorkflowUsers : List Username -> WorkflowUsersModel -> Cmd WorkflowUsersMsg
saveWorkflowUsers usernames model =
    let
        req =
            request
                { method = "POST"
                , headers = [ Http.header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "validatedUsers"
                , body = jsonBody (encodeUsernames usernames)
                , expect = expectJson SaveWorkflowUsers decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


setSetting : WorkflowUsersModel -> String -> (Result Http.Error Bool -> WorkflowUsersMsg) -> Bool -> Cmd WorkflowUsersMsg
setSetting model settingId msg newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ Http.header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson msg (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveValidateAllSetting : Bool -> WorkflowUsersModel -> Cmd WorkflowUsersMsg
saveValidateAllSetting newValue model =
    setSetting model "enable_validate_all" SaveValidateAllSetting newValue



------------------------------
-- JSON DECODERS            --
------------------------------


decodeUser : Decoder User
decodeUser =
    succeed User
        |> required "username" string
        |> required "isValidated" bool
        |> required "userExists" bool


decodeUserList : Decoder UserList
decodeUserList =
    at [ "data" ] (list decodeUser)



------------------------------
-- JSON ENCODERS            --
------------------------------


encodeUsernames : List Username -> Value
encodeUsernames usernames =
    Json.Encode.object
        [ ( "action", Json.Encode.string "addValidatedUsersList" )
        , ( "validatedUsers", Json.Encode.list Json.Encode.string usernames )
        ]



------------------------------
-- VIEW                     --
------------------------------


createInfoTootlip : String -> String -> Html WorkflowUsersMsg
createInfoTootlip content placement =
    span
        [ class "fa fa-exclamation-triangle center-box-element input-icon bstool"
        , attribute "data-bs-toggle" "tooltip"
        , attribute "data-bs-placement" placement
        , attribute "title" content
        ]
        []


createRightInfoSectionParagraphs : List String -> Html WorkflowUsersMsg
createRightInfoSectionParagraphs paragraphs =
    let
        paragraphElements =
            List.map (\paragraphContent -> p [] [ text paragraphContent ]) paragraphs
    in
    div [ class "section-right" ]
        [ div [ class "doc doc-info" ]
            ([ div [ class "marker" ] [ span [ class "fa fa-info-circle" ] [] ] ] ++ paragraphElements)
        ]



-- Displayed nb items and nb selected items when edition mod is On


displayFooter : WorkflowUsersForm -> ColPos -> Html WorkflowUsersMsg
displayFooter workflowUsersForm pos =
    let
        isChecked =
            case pos of
                Left ->
                    (length workflowUsersForm.leftChecked == length workflowUsersForm.validatedUsers) && not (isEmpty workflowUsersForm.leftChecked)

                Right ->
                    (length workflowUsersForm.rightChecked == length workflowUsersForm.unvalidatedUsers) && not (isEmpty workflowUsersForm.rightChecked)
    in
    div [ class "box-footer" ]
        [ label [ class "all-items" ]
            [ input
                [ class ""
                , onCheck (CheckAll pos)
                , type_ "checkbox"
                , value
                    (case pos of
                        Left ->
                            fromInt (length workflowUsersForm.validatedUsers)

                        Right ->
                            fromInt (length workflowUsersForm.unvalidatedUsers)
                    )
                , checked isChecked
                , disabled
                    (case pos of
                        Left ->
                            isEmpty workflowUsersForm.validatedUsers

                        Right ->
                            isEmpty workflowUsersForm.unvalidatedUsers
                    )
                ]
                []
            ]
        , div [ id "nb-items", class "footer-infos nb-items" ]
            [ case pos of
                Left ->
                    text (fromInt (length workflowUsersForm.validatedUsers) ++ " Items")

                Right ->
                    text (fromInt (length workflowUsersForm.unvalidatedUsers) ++ " Items")
            ]
        , div [ id "nb-selected", class "footer-infos" ]
            [ case pos of
                Left ->
                    if (length workflowUsersForm.leftChecked == length workflowUsersForm.validatedUsers) && not (isEmpty workflowUsersForm.leftChecked) then
                        text "All Selected"

                    else
                        text (fromInt (length workflowUsersForm.leftChecked) ++ " Selected")

                Right ->
                    if (length workflowUsersForm.rightChecked == length workflowUsersForm.unvalidatedUsers) && not (isEmpty workflowUsersForm.rightChecked) then
                        text "All Selected"

                    else
                        text (fromInt (length workflowUsersForm.rightChecked) ++ " Selected")
            ]
        ]



{--
  The box containing all users in the system who are not validated
  Displayed when edition mod is On
--}


displayRightCol : WorkflowUsersForm -> EditMod -> Html WorkflowUsersMsg
displayRightCol workflowUsersForm editMod =
    div [ class "box-users-container" ]
        [ h5 [ class "box-header" ] [ b [] [ text "Users" ] ]
        , div [ class "box-users-content" ]
            [ renderUsers workflowUsersForm.unvalidatedUsers Right editMod workflowUsersForm
            ]
        , displayFooter workflowUsersForm Right
        ]



{--
  Helper function to display individual user's row

  Implements checkbox and selectable logic according
  to which box the user should be in
    Left  -> validated
    Right -> unvalidated

  On edition mod "On" interactions are activated
--}


renderUserHelper : User -> ColPos -> EditMod -> WorkflowUsersForm -> Html WorkflowUsersMsg
renderUserHelper user pos editMod workflowUsersForm =
    let
        isEditActivate =
            if editMod == On then
                True

            else
                False

        sideChecked =
            case pos of
                Left ->
                    AddLeftChecked user (not (member user workflowUsersForm.leftChecked))

                Right ->
                    AddRightChecked user (not (member user workflowUsersForm.rightChecked))

        isChecked =
            case pos of
                Left ->
                    member user workflowUsersForm.leftChecked

                Right ->
                    member user workflowUsersForm.rightChecked

        content =
            li [ class "li-box-content-user" ]
                [ label [ style "vertical-align" "middle", style "display" "inline-block" ]
                    [ if isEditActivate then
                        input
                            [ class "box-input-element center-box-element"
                            , type_ "checkbox"
                            , value user.username
                            , checked isChecked
                            ]
                            []

                      else
                        div [ class "box-input-element center-box-element" ] []
                    ]
                , div [ class "center-box-element" ] [ text <| user.username ]
                , if not user.isInFile then
                    createInfoTootlip
                        """
            The user <b> doesn't exist anymore </b> but they are still a validated user, delete them by removing them from the validated users.
            """
                        "auto"

                  else
                    div [] []
                ]
    in
    if isEditActivate then
        if member user workflowUsersForm.hasMoved then
            if isChecked then
                div [ class "users moved-checked", onClick sideChecked ] [ content ]

            else
                div [ id "moved-user", class "users-clickable", onClick sideChecked ] [ content ]

        else if user.isInFile then
            if isChecked then
                div [ class "users normal-checked", onClick sideChecked ] [ content ]

            else
                div [ id "normal-user", class "users-clickable", onClick sideChecked ] [ content ]

        else if isChecked then
            div [ class "users not-in-file-checked", onClick sideChecked ] [ content ]

        else
            div [ id "not-in-file-user", class "users-clickable", onClick sideChecked ] [ content ]
        -- deactivate the ability to select users in edition mod "Off"

    else if user.isInFile then
        div [ id "normal-user", class "users" ] [ content ]

    else
        div [ id "not-in-file-user", class "users" ] [ content ]



{--
  Display all users according to the box they belong
      Left  -> validated
      Right -> unvalidated
--}


renderUsers : UserList -> ColPos -> EditMod -> WorkflowUsersForm -> Html WorkflowUsersMsg
renderUsers users pos editMod workflowUsersForm =
    ul [] (List.map (\u -> renderUserHelper u pos editMod workflowUsersForm) users)


displayArrows : WorkflowUsersForm -> Html WorkflowUsersMsg
displayArrows workflowUsersForm =
    let
        leftArrowBtnType =
            if isEmpty workflowUsersForm.rightChecked then
                "btn btn-sm move-left btn-default"

            else
                "btn btn-sm move-left btn-primary"

        rightArrowBtnType =
            if isEmpty workflowUsersForm.leftChecked then
                "btn btn-sm move-right btn-default"

            else
                "btn btn-sm move-right btn-primary"
    in
    div [ class "list-arrows arrows-validation" ]
        [ button [ onClick LeftToRight, class rightArrowBtnType, disabled (isEmpty workflowUsersForm.leftChecked) ]
            [ span [ class "fa fa-chevron-right" ] []
            ]
        , br [] []
        , br [] []
        , button [ onClick RightToLeft, class leftArrowBtnType, disabled (isEmpty workflowUsersForm.rightChecked) ]
            [ span [ class "fa fa-chevron-left" ] []
            ]
        ]



{--
  Display all users in the Left box (validated) according to the edition mode

  The "Save" and "Cancel" buttons are only displayed if the user made a modification
  otherwise, the "Exit" button is displayed to exit edition mode

  The footer is only displayed in edition mode
--}


displayLeftCol : WorkflowUsersForm -> EditMod -> Html WorkflowUsersMsg
displayLeftCol workflowUsersForm editMod =
    let
        cancelType =
            if editMod == On && isEmpty workflowUsersForm.hasMoved then
                ExitEditMod

            else
                CallApi getUsers

        actnBtnIfModif =
            if editMod == Off then
                div [] []

            else
                div []
                    [ button
                        [ id "cancel-workflow"
                        , class "btn btn-default btn-action-workflow"
                        , onClick cancelType
                        , type_ "button"
                        ]
                        [ text <|
                            if cancelType == ExitEditMod then
                                "Exit"

                            else
                                "Cancel"
                        ]
                    , if not (isEmpty workflowUsersForm.hasMoved) then
                        button
                            [ id "save-workflow "
                            , class "btn btn-success btn-action-workflow"
                            , onClick (CallApi (saveWorkflowUsers (getUsernames workflowUsersForm.validatedUsers)))
                            , type_ "button"
                            ]
                            [ text <| "Save" ]

                      else
                        div [] []
                    ]
    in
    div []
        [ div [ class "box-users-container " ]
            [ h5 [ class "box-header" ] [ b [] [ text "Validated users" ] ]
            , div [ class "box-users-content" ]
                [ if isEmpty workflowUsersForm.validatedUsers then
                    div [ style "text-align" "center" ]
                        [ if editMod == Off then
                            i [ class "fa fa-user-times empty-validated-user", style "margin-bottom" "10px" ]
                                [ br [] []
                                , p [ class "empty-box-msg" ] [ text "No validated users found" ]
                                ]

                          else
                            div [] []
                        ]

                  else
                    renderUsers workflowUsersForm.validatedUsers Left editMod workflowUsersForm
                ]
            , case editMod of
                On ->
                    div [] []

                Off ->
                    div [ class "circle-edit", onClick SwitchMode ]
                        [ i [ class "edit-icon-validated-user fa fa-pencil", style "margin" "0" ] []
                        ]
            ]
        , case editMod of
            On ->
                displayFooter workflowUsersForm Left

            Off ->
                div [] []
        , div [ class "action-button-container" ]
            [ actnBtnIfModif
            ]
        ]


view : WorkflowUsersModel -> Html WorkflowUsersMsg
view model =
    let
        validateAllForm =
            if model.hasWriteRights then
                [ Html.br [] [], displayValidateAllForm model ]

            else
                []
    in
    let
        workflowUsers =
            case model.workflowUsersView of
                WorkflowUsersInitView ->
                    i [ class "fa fa-spinner fa-pulse" ] []

                WorkflowUsers workflowUsersForm ->
                    div [ class "section-with-doc" ]
                        [ div [ class "section-left" ]
                            [ div
                                []
                                [ case model.editMod of
                                    On ->
                                        div [ class "inner-portlet", style "display" "flex", style "justify-content" "center", id "workflowUsers" ]
                                            [ displayLeftCol workflowUsersForm On
                                            , displayArrows workflowUsersForm
                                            , displayRightCol workflowUsersForm On
                                            ]

                                    Off ->
                                        div [ class "inner-portlet", style "display" "flex", style "justify-content" "center" ]
                                            [ displayLeftCol workflowUsersForm Off
                                            ]
                                ]
                            ]
                        , createRightInfoSectionParagraphs
                            [ " Any modification made by a validated user will be automatically deployed, "
                                ++ "without needing to be validated by another user first. "
                            ]
                        ]
    in
    div
        [ id "workflowUsers" ]
        (workflowUsers :: validateAllForm)


displayValidateAllForm : WorkflowUsersModel -> Html WorkflowUsersMsg
displayValidateAllForm model =
    case model.validateAllView of
        ValidateAll formState ->
            div
                [ class "section-with-doc" ]
                [ div [ class "section-left" ]
                    [ form []
                        [ ul []
                            [ li
                                [ class "rudder-form" ]
                                [ div [ class "input-group" ]
                                    [ label
                                        [ class "input-group-addon"
                                        , for "validationAutoValidatedUser"
                                        ]
                                        [ input
                                            [ type_ "checkbox"
                                            , id "validationAutoValidatedUser"
                                            , checked formState.formValues.validateAll
                                            , onClick (ChangeValidateAllSetting (not formState.formValues.validateAll))
                                            ]
                                            []
                                        , label
                                            [ for "validationAutoValidatedUser", class "label-radio" ]
                                            [ span [ class "fa fa-check" ] [] ]
                                        , span [ class "fa fa-check check-icon" ] []
                                        ]
                                    , label
                                        [ class "form-control", for "validationAutoValidatedUser" ]
                                        [ text " Validate all changes " ]
                                    ]
                                ]
                            ]
                        , input
                            [ type_ "button"
                            , value "Save change"
                            , id "validationAutoSubmit"
                            , class "btn btn-default"
                            , disabled (formState.formValues.validateAll == formState.initValues.validateAll)
                            , onClick (CallApi (saveValidateAllSetting formState.formValues.validateAll))
                            ]
                            []
                        ]
                    ]
                , createRightInfoSectionParagraphs
                    [ " Any modification made by a validated user will be automatically approved no matter the nature of the change. "
                    , " Hence, configuring the groups below will have no effect on validated users (in the list above), but will apply"
                        ++ " to non-validated users, who will still need to create a change request in order to modify a node from a supervised group. "
                    ]
                ]

        _ ->
            text ""
