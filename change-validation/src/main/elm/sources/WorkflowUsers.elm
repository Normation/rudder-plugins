module WorkflowUsers exposing (..)

import DataTypes exposing (ColPos(..), EditMod(..), Msg, User, UserList, UserListField(..), Username, ValidateAllView(..), WorkflowUsersForm, WorkflowUsersModel, WorkflowUsersMsg(..), WorkflowUsersView(..))
import ErrorMessages exposing (getErrorMessage)
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
    WorkflowUsersModel contextPath Off WorkflowUsersInitView hasWriteRights ValidateAllInitView



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

        SaveWorkflow result ->
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
                    ( model |> initValidateAllForm newSetting, successNotification "Successfully saved setting" )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to save validate_all_enabled setting :" ++ getErrorMessage error) )

        ChangeValidateAllSetting enable_validate_all ->
            ( { model | validateAllView = setValidateAll enable_validate_all model.validateAllView }, Cmd.none )


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


initValidateAllForm : Bool -> WorkflowUsersModel -> WorkflowUsersModel
initValidateAllForm validateAll model =
    let
        formState =
            { validateAll = validateAll }
    in
    { model | validateAllView = ValidateAll { initValues = formState, formValues = formState } }


setValidateAll : Bool -> ValidateAllView -> ValidateAllView
setValidateAll newValue viewState =
    case viewState of
        ValidateAll formState ->
            let
                newFormState =
                    { validateAll = newValue }
            in
            ValidateAll { formState | formValues = newFormState }

        _ ->
            viewState
