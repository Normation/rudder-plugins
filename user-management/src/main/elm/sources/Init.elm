module Init exposing (..)

import ApiCalls exposing (getUsersConf)
import DataTypes exposing (Model, Msg(..), PanelMode(..), RoleListOverride(..), StateInput(..))
import Dict exposing (fromList)
import Html.Attributes exposing (style)
import Http
import Toasty
import Toasty.Defaults

subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none

init : { contextPath : String } -> ( Model, Cmd Msg )
init flags =
    let
        initModel = Model flags.contextPath "" (fromList []) (fromList []) [] None Toasty.initialState Closed "" "" True ValidInputs [] [] False False
    in
    ( initModel
    , getUsersConf initModel
    )

------------------------------
-- NOTIFICATIONS --
------------------------------

getErrorMessage : Http.Error -> String
getErrorMessage e =
    let
        errMessage =
            case e of
                Http.BadStatus b ->
                    let
                        status =
                            b.status

                        message =
                            status.message
                    in
                    "Code " ++ String.fromInt status.code ++ " : " ++ message

                Http.BadUrl str ->
                    "Invalid API url"

                Http.Timeout ->
                    "It took too long to get a response"

                Http.NetworkError ->
                    "Network error"

                Http.BadPayload str rstr ->
                    str
    in
    errMessage


defaultConfig : Toasty.Config Msg
defaultConfig =
    Toasty.Defaults.config
        |> Toasty.delay 999999999
        |> Toasty.containerAttrs
            [ style "position" "fixed"
            , style "top" "50px"
            , style "right" "30px"
            , style "width" "100%"
            , style "max-width" "500px"
            , style "list-style-type" "none"
            , style "padding" "0"
            , style "margin" "0"
            , style "z-index" "9999"
            ]


tempConfig : Toasty.Config Msg
tempConfig =
    defaultConfig |> Toasty.delay 3000


addTempToast : Toasty.Defaults.Toast -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
addTempToast toast ( model, cmd ) =
    Toasty.addToast tempConfig ToastyMsg toast ( model, cmd )


addToast : Toasty.Defaults.Toast -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
addToast toast ( model, cmd ) =
    Toasty.addToast defaultConfig ToastyMsg toast ( model, cmd )


createSuccessNotification : String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createSuccessNotification message =
    addTempToast (Toasty.Defaults.Success "Success!" message)


createErrorNotification : String -> Http.Error -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createErrorNotification message e =
    addToast (Toasty.Defaults.Error "Error..." (message ++ "  (" ++ getErrorMessage e ++ ")"))


createDecodeErrorNotification : String -> String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createDecodeErrorNotification message e =
    addToast (Toasty.Defaults.Error "Error..." (message ++ "  (" ++ e ++ ")"))
