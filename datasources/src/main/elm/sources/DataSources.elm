module DataSources exposing (..)

import APICalls exposing (getDataSources)
import Browser
import Model exposing (..)
import Messages exposing (Msg)
import Update exposing (..)
import View exposing (..)

main : Program Flags Model Msg
main = Browser.element
  { init = init
  , view = view
  , update = update
  , subscriptions = subscriptions
  }

subscriptions : Model -> Sub Msg
subscriptions _ = Sub.batch
  [ ]

init : Flags -> (Model, Cmd Msg)
init flags =
 let
   model = initModel flags
 in
   (model , getDataSources model )

initModel : Flags -> Model
initModel { contextPath, hasWriteRights } =
  Model [] hasWriteRights (UI {name = "", value = ""}  {name = "", value = ""} False False False False False Nothing) Init contextPath

