module Init exposing (..)

import DataTypes exposing (EditMod(..), Model, Msg(..))
import Html.Attributes exposing (style)
import Http
import Toasty
import Toasty.Defaults

subscriptions : Model -> Sub Msg
subscriptions _ =
  Sub.none

initModel : String -> Model
initModel contextPath  =
  Model contextPath [] Toasty.initialState [] [] [] [] [] Off


{----------------------------
------ NOTIFICATIONS --------
-----------------------------}

addToast : Toasty.Defaults.Toast -> Toasty.Config Msg -> (Model, Cmd Msg) -> (Model, Cmd Msg)
addToast toast conf  m =
  Toasty.addToast conf Notification toast m

defaultConfig : Toasty.Config Msg
defaultConfig =
  Toasty.Defaults.config
    |> Toasty.delay 3.0
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

getErrorMessage : Http.Error -> String
getErrorMessage e =
  let
    errMessage =
      case e of
        Http.BadStatus status ->
          "Code " ++ String.fromInt status
        Http.BadUrl _ ->
          "Invalid API url"
        Http.Timeout ->
          "It took too long to get a response"
        Http.NetworkError ->
          "Network error"
        Http.BadBody c->
          "Wront content in request body" ++ c
  in
    errMessage

tempConfig : Toasty.Config Msg
tempConfig =
  defaultConfig |> Toasty.delay 300000

addTempToast : Toasty.Defaults.Toast -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
addTempToast toast ( model, cmd ) =
  Toasty.addToast tempConfig ToastyMsg toast ( model, cmd )

createSuccessNotification : String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createSuccessNotification message =
  addTempToast (Toasty.Defaults.Success "Success!" message)

httpErrorNotification : String -> Http.Error -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
httpErrorNotification message e =
  addToast (Toasty.Defaults.Error "Error..." (message ++ " (" ++ getErrorMessage e ++ ")")) defaultConfig
