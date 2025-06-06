module WorkflowUsers exposing (..)

import DataTypes exposing (ColPos(..), EditMod(..), Msg, User, UserList, UserListField(..), Username, ValidateAllView(..), WorkflowUsersForm, WorkflowUsersModel, WorkflowUsersMsg(..), WorkflowUsersView(..))
import ErrorMessages exposing (getErrorMessage)
import Http exposing (emptyBody, expectJson, header, jsonBody, request)
import Json.Decode exposing (Decoder, at, bool, list, string, succeed)
import Json.Decode.Pipeline exposing (required)
import Json.Encode exposing (Value, object)
import JsonUtils exposing (decodeSetting, encodeSetting)
import List exposing (filter, map, member)
import Ports exposing (errorNotification, successNotification)
import String



------------------------------
-- UTIL                     --
------------------------------


getUsernames : UserList -> List Username
getUsernames users =
    map .username users


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
-- UPDATE                   --
------------------------------


update : WorkflowUsersMsg -> WorkflowUsersModel -> ( WorkflowUsersModel, Cmd Msg )
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


getUsers : WorkflowUsersModel -> Cmd Msg
getUsers model =
    let
        req =
            request
                { method = "GET"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "users"
                , body = emptyBody
                , expect = expectJson (DataTypes.WorkflowUsersMsg << GetUsers) decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveWorkflowUsers : List Username -> WorkflowUsersModel -> Cmd Msg
saveWorkflowUsers usernames model =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model "validatedUsers"
                , body = jsonBody (encodeUsernames usernames)
                , expect = expectJson (DataTypes.WorkflowUsersMsg << SaveWorkflowUsers) decodeUserList
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


setSetting : WorkflowUsersModel -> String -> (Result Http.Error Bool -> WorkflowUsersMsg) -> Bool -> Cmd Msg
setSetting model settingId msg newValue =
    let
        req =
            request
                { method = "POST"
                , headers = [ header "X-Requested-With" "XMLHttpRequest" ]
                , url = getUrl model ("settings/" ++ settingId)
                , body = jsonBody (encodeSetting newValue)
                , expect = expectJson (DataTypes.WorkflowUsersMsg << msg) (decodeSetting settingId)
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req


saveValidateAllSetting : Bool -> WorkflowUsersModel -> Cmd Msg
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
    object
        [ ( "action", Json.Encode.string "addValidatedUsersList" )
        , ( "validatedUsers", Json.Encode.list Json.Encode.string usernames )
        ]
