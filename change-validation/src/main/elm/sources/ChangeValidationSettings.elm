module ChangeValidationSettings exposing (..)

import ApiCalls exposing (getUsers, getValidateAllSetting)
import Browser
import DataTypes exposing (Msg(..), WorkflowUsersMsg)
import Html exposing (Html, div)
import SupervisedTargets exposing (getTargets)
import View
import WorkflowUsers



------------------------------
-- Init and main --
------------------------------


init : { contextPath : String, hasWriteRights : Bool } -> ( Model, Cmd Msg )
init flags =
    let
        m =
            initModel flags.contextPath flags.hasWriteRights
    in
    ( m, Cmd.batch [ getUsers m.workflowUsersModel, getValidateAllSetting m.workflowUsersModel, getTargets m.supervisedTargetsModel ] )


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }


initModel : String -> Bool -> Model
initModel contextPath hasWriteRights =
    { workflowUsersModel = WorkflowUsers.initModel contextPath hasWriteRights
    , supervisedTargetsModel = SupervisedTargets.initModel contextPath
    }



------------------------------
-- MODEL --
------------------------------


type alias Model =
    { workflowUsersModel : DataTypes.Model
    , supervisedTargetsModel : SupervisedTargets.Model
    }



------------------------------
-- VIEW
------------------------------


view : Model -> Html Msg
view model =
    div []
        [ View.view model.workflowUsersModel
        , SupervisedTargets.view model.supervisedTargetsModel
        ]



------------------------------
-- UPDATE
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        WorkflowUsersMsg wuMsg ->
            let
                ( wuModel, wuCmd ) =
                    WorkflowUsers.update wuMsg model.workflowUsersModel
            in
            ( { model | workflowUsersModel = wuModel }, wuCmd )

        SupervisedTargetsMsg stMsg ->
            let
                ( stModel, stCmd ) =
                    SupervisedTargets.update stMsg model.supervisedTargetsModel
            in
            ( { model | supervisedTargetsModel = stModel }, stCmd )



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
