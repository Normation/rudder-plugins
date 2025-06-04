module ChangeValidationSettings exposing (..)

import ApiCalls exposing (getUsers, getValidateAllSetting)
import Browser
import DataTypes exposing (Msg(..), WorkflowUsersMsg)
import Html exposing (Html, b, div, h3, li, p, text, ul)
import Html.Attributes exposing (class, id)
import SupervisedTargets exposing (getTargets)
import View
import WorkflowSettings
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
    , workflowSettingsModel = WorkflowSettings.initModel contextPath False
    }



------------------------------
-- MODEL --
------------------------------


type alias Model =
    { workflowUsersModel : DataTypes.Model
    , supervisedTargetsModel : SupervisedTargets.Model
    , workflowSettingsModel : WorkflowSettings.Model
    }



------------------------------
-- VIEW
------------------------------


view : Model -> Html Msg
view model =
    div [ id "changeRequestTriggers" ]
        [ h3 [ class "page-title" ] [ text "Configure change request triggers" ]
        , div []
            [ p []
                [ text " By default, change request are created for all users. You can change when a change request is created with below options: " ]
            , ul []
                [ li [] [ text "exempt some users from validation;" ]
                , li [] [ text "trigger change request only for changes impacting nodes belonging to some supervised groups; " ]
                ]
            , p []
                [ text "Be careful: a change request is created when "
                , b [] [ text "at least one" ]
                , text " predicate matches, so an exempted user still need a change request to modify a node from a supervised group. "
                ]
            ]
        , h3
            [ class "page-subtitle" ]
            [ text "Configure users with change validation" ]
        , div []
            [ View.view model.workflowUsersModel
            , SupervisedTargets.view model.supervisedTargetsModel
            ]
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

        WorkflowSettingsMsg wsMsg ->
            let
                ( wsModel, wsCmd ) =
                    WorkflowSettings.update wsMsg model.workflowSettingsModel
            in
            ( { model | workflowSettingsModel = wsModel }, wsCmd )



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none
