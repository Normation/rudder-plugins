module WorkflowUsers exposing (..)

import ApiCalls exposing (getUsers)
import Browser
import DataTypes exposing (ColPos(..), EditMod(..), Model, Msg(..), User, UserList)
import Init exposing (createSuccessNotification, defaultConfig, httpErrorNotification, initModel, subscriptions)
import List exposing (filter, member)
import String
import Toasty
import View exposing (view)

filterValidatedUsers : UserList -> UserList
filterValidatedUsers users =
    filter (\u -> u.isValidated) users

filterUnvalidatedUsers : UserList -> UserList
filterUnvalidatedUsers users =
    filter (\u -> not u.isValidated) users

mainInit : {contextPath : String} -> ( Model, Cmd Msg )
mainInit initValues =
  let
    m =
      initModel initValues.contextPath
  in
    ( m, getUsers m )

main =
  Browser.element
  { init          = mainInit
  , view          = view
  , update        = update
  , subscriptions = subscriptions
  }

update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  case msg of
    GetUsers result ->
      case result of
        Ok users  ->
          (
            { model
            | users = users
            , unvalidatedUsers = filterUnvalidatedUsers users
            , validatedUsers   = filterValidatedUsers users
            , leftChecked      = []
            , rightChecked     = []
            , hasMoved         = []
            }
            , Cmd.none
          )
        Err error ->
          httpErrorNotification "An error occurred while trying to get users." error ( model, Cmd.none )

    RemoveUser result ->
      case result of
        Ok removeUser ->
          ( { model | users = filter  (\m -> m.username /= removeUser) model.users  }, Cmd.none )
        Err error     ->
          httpErrorNotification "An error occurred while trying to delete a validated users." error ( model, Cmd.none )

    SaveWorkflow result ->
      case result of
        Ok updatedUsers ->
          (
            { model
            | users = updatedUsers
            , unvalidatedUsers = filterUnvalidatedUsers updatedUsers
            , validatedUsers   = filterValidatedUsers updatedUsers
            , leftChecked      = []
            , rightChecked     = []
            , hasMoved         = []
            }
            , Cmd.none
          )
          |> createSuccessNotification "Your changes have been saved."
        Err error       ->
          httpErrorNotification "An error occurred while trying to save validated users." error ( model, Cmd.none )

    CallApi call ->
      (model, call model)

    RightToLeft ->
      let
        newUnvalidatedUsers =
          filter (\u -> not (member u model.rightChecked)) model.unvalidatedUsers
        newValidatedUsers   =
          model.rightChecked ++ model.validatedUsers
      in
        (
          { model
          | unvalidatedUsers = newUnvalidatedUsers
          , hasMoved         = model.hasMoved ++ model.rightChecked
          , validatedUsers   = newValidatedUsers
          , leftChecked      = []
          , rightChecked     = []
          }
          , Cmd.none
        )

    LeftToRight ->
      let
        newValidatedUsers   =
          filter (\u -> not (member u model.leftChecked)) model.validatedUsers
        newUnvalidatedUsers =
          model.leftChecked ++ model.unvalidatedUsers
      in
        (
          { model
          | unvalidatedUsers = newUnvalidatedUsers
          , hasMoved         = model.hasMoved ++ model.leftChecked
          , validatedUsers   = newValidatedUsers
          , leftChecked      = []
          , rightChecked     = []
          }
          , Cmd.none
        )

    AddLeftChecked user isChecked ->
      if  (not (member user model.leftChecked)) &&  isChecked  then
        ({model | leftChecked = user :: model.leftChecked, rightChecked = []}, Cmd.none)
      else
        ({model | leftChecked = filter (\u -> user /= u) model.leftChecked}, Cmd.none)


    AddRightChecked user isChecked ->
      if (not (member user model.rightChecked) ) &&  isChecked then
        ({model | rightChecked = user :: model.rightChecked, leftChecked = []}, Cmd.none)
      else
        ({model | rightChecked = filter (\u -> user /= u) model.rightChecked}, Cmd.none)

    CheckAll colPos isChecked->
      case colPos of
        Left  ->
          if  isChecked then
            ({model | leftChecked = model.validatedUsers, rightChecked = []}, Cmd.none)
          else
            ({model | leftChecked = []}, Cmd.none)
        Right ->
          if  isChecked then
            ({model | rightChecked = model.unvalidatedUsers, leftChecked = []}, Cmd.none)
          else
            ({model | rightChecked = []}, Cmd.none)

    SwitchMode ->
      case model.editMod of
        On  -> ({model | editMod = Off}, Cmd.none)
        Off -> ({model | editMod = On}, Cmd.none)

    ExitEditMod ->
      ({model | editMod = Off, leftChecked = [], rightChecked = []}, Cmd.none)

    ToastyMsg subMsg ->
       Toasty.update defaultConfig ToastyMsg subMsg model

    Notification subMsg ->
       Toasty.update defaultConfig Notification subMsg model
