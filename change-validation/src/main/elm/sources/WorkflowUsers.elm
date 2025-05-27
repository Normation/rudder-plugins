module WorkflowUsers exposing (..)

import ApiCalls exposing (getUsers, getValidateAllSetting)
import Browser
import DataTypes exposing (ColPos(..), EditMod(..), Model, Msg(..), User, UserList, ViewState(..))
import ErrorMessages exposing (getErrorMessage)
import Init exposing (initModel, subscriptions)
import List exposing (filter, member)
import Notifications exposing (errorNotification, successNotification)
import String
import View exposing (view)


filterValidatedUsers : UserList -> UserList
filterValidatedUsers users =
    filter (\u -> u.isValidated) users


filterUnvalidatedUsers : UserList -> UserList
filterUnvalidatedUsers users =
    filter (\u -> not u.isValidated) users


mainInit : { contextPath : String, hasWriteRights : Bool } -> ( Model, Cmd Msg )
mainInit initValues =
    let
        m =
            initModel initValues.contextPath initValues.hasWriteRights
    in
    ( m, Cmd.batch [ getUsers m, getValidateAllSetting m ] )


main =
    Browser.element
        { init = mainInit
        , view = view
        , update = update
        , subscriptions = subscriptions
        }


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetUsers result ->
            case result of
                Ok users ->
                    ( { model
                        | users = users
                        , unvalidatedUsers = filterUnvalidatedUsers users
                        , validatedUsers = filterValidatedUsers users
                        , leftChecked = []
                        , rightChecked = []
                        , hasMoved = []
                      }
                    , Cmd.none
                    )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to get users:" ++ getErrorMessage error) )

        RemoveUser result ->
            case result of
                Ok removeUser ->
                    ( { model | users = filter (\m -> m.username /= removeUser) model.users }, Cmd.none )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to delete a validated users:" ++ getErrorMessage error) )

        SaveWorkflow result ->
            case result of
                Ok updatedUsers ->
                    ( { model
                        | users = updatedUsers
                        , unvalidatedUsers = filterUnvalidatedUsers updatedUsers
                        , validatedUsers = filterValidatedUsers updatedUsers
                        , leftChecked = []
                        , rightChecked = []
                        , hasMoved = []
                      }
                    , successNotification ""
                    )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to save validated users:" ++ getErrorMessage error) )

        CallApi call ->
            ( model, call model )

        RightToLeft ->
            let
                newUnvalidatedUsers =
                    filter (\u -> not (member u model.rightChecked)) model.unvalidatedUsers

                newValidatedUsers =
                    model.rightChecked ++ model.validatedUsers
            in
            ( { model
                | unvalidatedUsers = newUnvalidatedUsers
                , hasMoved = model.hasMoved ++ model.rightChecked
                , validatedUsers = newValidatedUsers
                , leftChecked = []
                , rightChecked = []
              }
            , Cmd.none
            )

        LeftToRight ->
            let
                newValidatedUsers =
                    filter (\u -> not (member u model.leftChecked)) model.validatedUsers

                newUnvalidatedUsers =
                    model.leftChecked ++ model.unvalidatedUsers
            in
            ( { model
                | unvalidatedUsers = newUnvalidatedUsers
                , hasMoved = model.hasMoved ++ model.leftChecked
                , validatedUsers = newValidatedUsers
                , leftChecked = []
                , rightChecked = []
              }
            , Cmd.none
            )

        AddLeftChecked user isChecked ->
            if not (member user model.leftChecked) && isChecked then
                ( { model | leftChecked = user :: model.leftChecked, rightChecked = [] }, Cmd.none )

            else
                ( { model | leftChecked = filter (\u -> user /= u) model.leftChecked }, Cmd.none )

        AddRightChecked user isChecked ->
            if not (member user model.rightChecked) && isChecked then
                ( { model | rightChecked = user :: model.rightChecked, leftChecked = [] }, Cmd.none )

            else
                ( { model | rightChecked = filter (\u -> user /= u) model.rightChecked }, Cmd.none )

        CheckAll colPos isChecked ->
            case colPos of
                Left ->
                    if isChecked then
                        ( { model | leftChecked = model.validatedUsers, rightChecked = [] }, Cmd.none )

                    else
                        ( { model | leftChecked = [] }, Cmd.none )

                Right ->
                    if isChecked then
                        ( { model | rightChecked = model.unvalidatedUsers, leftChecked = [] }, Cmd.none )

                    else
                        ( { model | rightChecked = [] }, Cmd.none )

        SwitchMode ->
            case model.editMod of
                On ->
                    ( { model | editMod = Off }, Cmd.none )

                Off ->
                    ( { model | editMod = On }, Cmd.none )

        ExitEditMod ->
            ( { model | editMod = Off, leftChecked = [], rightChecked = [] }, Cmd.none )

        SaveValidateAllSetting result ->
            case result of
                Ok newSetting ->
                    ( { model | viewState = initForm newSetting }
                    , successNotification "Successfully saved setting"
                    )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to save validate_all_enabled setting :" ++ getErrorMessage error) )

        GetValidateAllSetting result ->
            case result of
                Ok setting ->
                    ( { model | viewState = initForm setting }, Cmd.none )

                Err error ->
                    ( model, errorNotification ("An error occurred while trying to get validate_all_enabled setting :" ++ getErrorMessage error) )

        ChangeValidateAllSetting enable_validate_all ->
            ( { model | viewState = setValidateAll enable_validate_all model.viewState }, Cmd.none )


initForm : Bool -> ViewState
initForm value =
   let
            formState =
                { validateAll = value }
   in
       Form { initValues = formState, formValues = formState }


setValidateAll : Bool -> ViewState -> ViewState
setValidateAll newValue viewState  =
    case viewState of
        Form formState ->
            let
                newFormState =
                    { validateAll = newValue }
            in
            Form { formState | formValues = newFormState }

        _ ->
            viewState
