port module Branding exposing (applyCss, defaultSettings, initSettings, main, onKeyDown, subscriptions, update)

import ApiCall
import Color exposing (rgb255)
import DataTypes exposing (..)
import Html exposing (..)
import Html.Events exposing (keyCode, on)
import Http exposing (Error)
import Json.Decode as Decode
import ColorPicker
import View exposing (..)
import Browser
import File exposing (File)
import File.Select as Select
import Task

--- PORTS ---
port applyCss : ( CssObj ) -> Cmd msg
port successNotification : String -> Cmd msg
port errorNotification : String -> Cmd msg

--- MODEL ---
defaultSettings : Settings
defaultSettings =
  let
    --- #da291c, from 7.0 color palette
    bgColor  = rgb255 218 41 28
    txtColor = Color.white
  in
    Settings True True "Production" bgColor txtColor (Logo True Nothing Nothing) (Logo True Nothing Nothing) True True "Welcome, please sign in:"


initSettings : { contextPath : String } -> ( Model, Cmd Msg )
initSettings initValues =
  let
    model    = Model initValues.contextPath ColorPicker.empty ColorPicker.empty defaultSettings False
  in
    ( model, ApiCall.getSettings model)


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        {--Common branding messages --}
        ToggleCustomBar ->
            let
                settings =
                    model.settings

                activate =
                    not settings.displayBar

                newSettings =
                    { settings | displayBar = activate }

                newModel =
                    { model | settings = newSettings }
            in
            ( newModel, Cmd.none )

        ToggleLabel ->
            let
                settings =
                    model.settings

                activate =
                    not settings.displayLabel

                newSettings =
                    { settings | displayLabel = activate }

                newModel =
                    { model | settings = newSettings }
            in
            ( newModel, Cmd.none )

        EditLabelText label ->
            let
                settings =
                    model.settings

                newSettings =
                    { settings | labelTxt = label }

                newModel =
                    { model | settings = newSettings }
            in
            ( newModel, Cmd.none )

        BgColorPicker colorMsg ->
            let
                settings =
                    model.settings

                ( updatedColorPicker, newColor ) =
                    ColorPicker.update colorMsg model.settings.bgColorValue model.bgColorPicker


                newSettings =
                    { settings | bgColorValue = Maybe.withDefault  model.settings.bgColorValue newColor }

                newModel =
                    { model | bgColorPicker = updatedColorPicker, settings = newSettings }
            in
            ( newModel, Cmd.none  )

        TxtColorPicker colorMsg ->
            let
                settings =
                    model.settings

                ( updatedColorPicker, newColor ) =
                    ColorPicker.update colorMsg model.settings.labelColorValue model.labelColorPicker


                newSettings =
                    { settings | labelColorValue = Maybe.withDefault model.settings.labelColorValue newColor }

                newModel =
                    { model | labelColorPicker = updatedColorPicker, settings = newSettings }
            in
            ( newModel, Cmd.none )

        {--Login page Messages --}
        ToggleCustomBarLogin ->
            let
                settings =
                    model.settings

                activate =
                    not settings.displayBarLogin

                newSettings =
                    { settings | displayBarLogin = activate }

                newModel =
                    { model | settings = newSettings }
            in
            ( newModel, Cmd.none )

        ToggleMotd ->
            let
                settings =
                    model.settings

                activate =
                    not settings.displayMotd

                newSettings =
                    { settings | displayMotd = activate }

                newModel =
                    { model | settings = newSettings }
            in
            ( newModel, Cmd.none )

        EditMotd motd ->
            let
                settings =
                    model.settings

                newSettings =
                    { settings | motd = motd }

                newModel =
                    { model | settings = newSettings }
            in
            ( newModel, Cmd.none )

        {--Api Calls message --}
        GetSettings result ->
            case result of
                Ok settings ->
                    let
                        newModel =
                            { model
                                | settings = settings
                            }
                    in
                    ( newModel, Cmd.none )

                Err err ->
                    ( model, errorNotification ("Error while trying to fetch settings: " ++ (getErrorMessage err) ++ "") )

        SaveSettings result ->
            case result of
                Ok newSettings ->
                    let
                        settings        = model.settings
                        bgColor         = Color.toCssString settings.bgColorValue
                        txtColor        = Color.toCssString settings.labelColorValue
                        labelTxt        = settings.labelTxt
                        wideLogoData    = case settings.wideLogo.data of
                          Just d  -> d
                          Nothing -> ""
                        wideLogoEnable  = settings.wideLogo.enable
                        smallLogoData   = case settings.smallLogo.data of
                          Just d  -> d
                          Nothing -> ""
                        smallLogoEnable = settings.smallLogo.enable
                        cssObj = CssObj bgColor txtColor labelTxt --wideLogoEnable wideLogoData smallLogoEnable smallLogoData

                    in
                    ( model, Cmd.batch[applyCss ( cssObj ), successNotification ""] )

                Err err ->
                    ( model, errorNotification ("Error while trying to save changes: " ++ (getErrorMessage err) ++ ""))

        SendSave ->
            ( model, ApiCall.saveSettings model )

        {-- LOGO UPLOAD & PREVIEW --}
        ToggleLogo logoType->
          let
            settings    = model.settings
            logo        = case logoType of
              "small" -> settings.smallLogo
              _       -> settings.wideLogo
            activate    = not logo.enable
            newLogo     = { logo     | enable     = activate    }
            newSettings = case logoType of
              "small" ->  { settings | smallLogo  = newLogo     }
              _       ->  { settings | wideLogo   = newLogo     }
            newModel    = { model    | settings   = newSettings }
          in
            ( newModel, Cmd.none )
        UploadFile logoType->
            ( model
            , Select.file [ "image/*" ] (GotFile logoType)
            )
        RemoveFile logoType ->
          let
            settings    = model.settings
            logo        = case logoType of
              "small" -> settings.smallLogo
              _       -> settings.wideLogo
            newLogo     = { logo     | enable     = False, name = Nothing, data = Nothing }
            newSettings = case logoType of
              "small" ->  { settings | smallLogo  = newLogo     }
              _       ->  { settings | wideLogo   = newLogo     }
            newModel    = { model    | settings   = newSettings }
          in
            ( newModel, Cmd.none )
        GotFile logoType file->
          let
            settings    = model.settings
            logo        = case logoType of
              "small" -> settings.smallLogo
              _       -> settings.wideLogo
            newLogo     = { logo     | enable     = True, name = Just (File.name file) }
            (newSettings, actionMsg) = case logoType of
              "small" ->  ({ settings | smallLogo  = newLogo    }, Task.perform GotPreviewSmallLogo (File.toUrl file))
              _       ->  ({ settings | wideLogo   = newLogo    }, Task.perform GotPreviewWideLogo  (File.toUrl file))
            newModel    = { model    | settings   = newSettings, hover = False }
          in
            ( newModel
            , actionMsg
            )

        GotPreviewWideLogo url->
          let
            settings    = model.settings
            logo        = settings.wideLogo
            newLogo     = { logo     | enable   = logo.enable, data = Just url}
            newSettings = { settings | wideLogo   = newLogo     }
            newModel    = { model    | settings = newSettings   }
          in
            (newModel, Cmd.none)

        GotPreviewSmallLogo url->
          let
            settings    = model.settings
            logo        = settings.smallLogo
            newLogo     = { logo     | enable   = logo.enable, data = Just url}
            newSettings = { settings | smallLogo  = newLogo     }
            newModel    = { model    | settings = newSettings   }
          in
            (newModel, Cmd.none)

onKeyDown : (Int -> msg) -> Html.Attribute msg
onKeyDown tagger =
  on "keydown" (Decode.map tagger keyCode)


getErrorMessage : Http.Error -> String
getErrorMessage e =
    let
        errMessage =
            case e of
                Http.BadStatus status ->
                    "Code " ++ String.fromInt status

                Http.BadUrl str ->
                    "Invalid API url"

                Http.Timeout ->
                    "It took too long to get a response"

                Http.NetworkError ->
                    "Network error"

                Http.BadBody str ->
                    str
    in
    errMessage

--- MAIN ---
subscriptions : Model -> Sub Msg
subscriptions model =
  Sub.none

main =
  Browser.element
    { init = initSettings
    , view = view
    , update = update
    , subscriptions = subscriptions
    }
