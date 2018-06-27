port module Branding exposing (..)

import Html exposing (..)
import Html.Events exposing (on, keyCode)
import Ext.Color exposing(hsvToRgb)
import Color
import Ui.FileInput as FileInput
import Ui.ColorPicker as ColorPicker
import DataTypes exposing (..)
import View exposing(..)
import JsonDecoder exposing (..)
import JsonEncoder exposing (..)
import Json.Decode as Decode
import ApiCall

--- PORTS ---
port check : String -> Cmd msg

--- MODEL ---

defaultSettings : Settings
defaultSettings =
  let
    bgColor = Color.red
    txtColor = Color.white
  in
    Settings True True "Production" bgColor txtColor True True True True True True  True "Welcome, please sign in:"


initSettings : String -> (Model, Cmd Msg)
initSettings contextPath =
  let
    favFileInit     = FileInput.init   ()
    favFileInput    = {favFileInit | accept="image/*"}
    wideFileInput   = FileInput.init   ()
    squareFileInput = FileInput.init   ()
    loginFileInput  = FileInput.init   ()
    (bgColorPicker, _) =
      ColorPicker.init ()
      |> ColorPicker.setValue Color.red
    (txtColorPicker, _) =
      ColorPicker.init ()
      |> ColorPicker.setValue Color.white

    initData = FileData ""  ""  ""  ""
    model = Model contextPath bgColorPicker txtColorPicker favFileInput wideFileInput squareFileInput loginFileInput defaultSettings initData
  in
    model ! [ ApiCall.getSettings model ]




update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  case msg of

{-- Common branding messages --}
    ToggleCustomBar ->
      let
        settings    = model.settings
        activate    = not settings.displayBar
        newSettings = {settings | displayBar = activate   }
        newModel    = {model    | settings   = newSettings}
      in
        (newModel, Cmd.none)
    ToggleLabel ->
      let
        settings    = model.settings
        activate    = not settings.displayLabel
        newSettings = {settings | displayLabel = activate   }
        newModel    = {model    | settings     = newSettings}
      in
        (newModel, Cmd.none)
    EditLabelText label ->
      let
        settings    = model.settings
        newSettings = {settings | labelTxt = label      }
        newModel    = {model    | settings = newSettings}
      in
        (newModel, Cmd.none)
    BgColorPicker msg ->
      let
        settings = model.settings
        ( updatedColorPicker, cmd ) =
          ColorPicker.update msg model.bgColorPicker
        colorModel  = updatedColorPicker.colorPanel
        color       = (hsvToRgb colorModel.value)
        newSettings = {settings | bgColorValue  = color}
        newModel    = {model    | bgColorPicker = updatedColorPicker, settings = newSettings}
      in
        ( newModel, Cmd.map BgColorPicker cmd )
    TxtColorPicker msg ->
      let
        settings = model.settings
        ( updatedColorPicker, cmd ) =
          ColorPicker.update msg model.labelColorPicker
        colorModel  = updatedColorPicker.colorPanel
        color       = (hsvToRgb colorModel.value)
        newSettings = {settings | labelColorValue  = color}
        newModel    = {model    | labelColorPicker = updatedColorPicker, settings = newSettings}
      in
        ( newModel, Cmd.map TxtColorPicker cmd )

{-- Custom Logos messages --}
    ToggleCustomLogos ->
      let
        settings    = model.settings
        activate    = not settings.useCustomLogos
        newSettings = {settings | useCustomLogos = activate   }
        newModel    = {model    | settings       = newSettings}
      in
        (newModel, Cmd.none)
    ToggleFavicon ->
      let
        settings    = model.settings
        activate    = not settings.useFavicon
        newSettings = {settings | useFavicon = activate   }
        newModel    = {model    | settings   = newSettings}
      in
        (newModel, Cmd.none)
    FaviconFileInput msg ->
      let
        ( updatedFileInput, cmd ) =
          FileInput.update msg model.faviconFile
        newModel = {model | faviconFile = updatedFileInput}
      in
        ( newModel, Cmd.map FaviconFileInput cmd )
    ToggleWideLogo ->
      let
        settings    = model.settings
        activate    = not settings.useWideLogo
        newSettings = {settings | useWideLogo = activate   }
        newModel    = {model    | settings    = newSettings}
      in
        (newModel, Cmd.none)
    WideLogoFileInput msg ->
      let
        ( updatedFileInput, cmd ) =
          FileInput.update msg model.wideLogoFile
        newModel = {model | wideLogoFile = updatedFileInput}
      in
        ( newModel, Cmd.map WideLogoFileInput cmd )
    ToggleSquareLogo ->
      let
        settings    = model.settings
        activate    = not settings.useSquareLogo
        newSettings = {settings | useSquareLogo = activate   }
        newModel    = {model    | settings      = newSettings}
      in
        (newModel, Cmd.none)
    SquareLogoFileInput msg ->
      let
        ( updatedFileInput, cmd ) =
          FileInput.update msg model.squareLogoFile
        newModel = {model | squareLogoFile = updatedFileInput}
      in
        ( newModel, Cmd.map SquareLogoFileInput cmd )


{-- Login page Messages --}
    ToggleCustomBarLogin->
      let
        settings    = model.settings
        activate    = not settings.displayBarLogin
        newSettings = {settings | displayBarLogin = activate   }
        newModel    = {model    | settings        = newSettings}
      in
        (newModel, Cmd.none)
    ToggleMotd->
      let
        settings    = model.settings
        activate    = not settings.displayMotd
        newSettings = {settings | displayMotd = activate   }
        newModel    = {model    | settings    = newSettings}
      in
        (newModel, Cmd.none)
    EditMotd motd->
      let
        settings    = model.settings
        newSettings = {settings | motd     = motd       }
        newModel    = {model    | settings = newSettings}
      in
        (newModel, Cmd.none)
    ToggleLoginLogo ->
      let
        settings    = model.settings
        activate    = not settings.useLoginLogo
        newSettings = {settings | useLoginLogo = activate   }
        newModel    = {model    | settings     = newSettings}
      in
        (newModel, Cmd.none)
    LoginLogoFileInput msg ->
      let
        ( updatedFileInput, cmd ) =
          FileInput.update msg model.loginLogoFile
        newModel = {model | loginLogoFile = updatedFileInput}
      in
        ( newModel, Cmd.map LoginLogoFileInput cmd )

{-- Api Calls message --}
    GetSettings result  ->
      case result of
        Ok settings ->
          let
            (bgColorPicker, _) = ColorPicker.setValue settings.bgColorValue model.bgColorPicker
            (textColorPicker, _) = ColorPicker.setValue settings.labelColorValue model.labelColorPicker
            newModel = {model
              | settings = settings, bgColorPicker = bgColorPicker, labelColorPicker = textColorPicker
            }
        in
          (newModel, Cmd.none)
        Err err  ->
          -- Need to display an error somehow
          (model, Cmd.none)
    SaveSettings result ->
      case result of
        Ok settings ->
          (model, Cmd.none)
        Err err  ->
          -- Need to display an error somehow
          (model, Cmd.none)
    SendSave ->
      (model, ApiCall.saveSettings model)


onKeyDown : (Int -> msg) -> Html.Attribute msg
onKeyDown tagger =
  on "keydown" (Decode.map tagger keyCode)

--- MAIN ---
subscriptions : Model -> Sub Msg
subscriptions model =
  Sub.batch
    [ Sub.map BgColorPicker  (ColorPicker.subscriptions model.bgColorPicker   )
    , Sub.map TxtColorPicker (ColorPicker.subscriptions model.labelColorPicker)
    ]

main = programWithFlags
  { init   = initSettings
  , update = update
  , view   = view
  , subscriptions = subscriptions
  }