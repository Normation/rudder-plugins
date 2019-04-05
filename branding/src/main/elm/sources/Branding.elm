port module Branding exposing (applyCss, defaultSettings, initSettings, main, onKeyDown, subscriptions, update)

import ApiCall
import Color
import DataTypes exposing (..)
import Color
import Html exposing (..)
import Html.Events exposing (keyCode, on)
import Json.Decode as Decode
import JsonDecoder exposing (..)
import JsonEncoder exposing (..)
import Toasty
import Toasty.Defaults
import ColorPicker
import View exposing (..)
import Browser



--- PORTS ---


port applyCss : ( String, String, String ) -> Cmd msg



--- MODEL ---


defaultSettings : Settings
defaultSettings =
    let
        bgColor =
            Color.red

        txtColor =
            Color.white
    in
    Settings True True "Production" bgColor txtColor True True True True True True True "Welcome, please sign in:"


initSettings : { contextPath : String } -> ( Model, Cmd Msg )
initSettings initValues =
    let {-
        favFileInit =
            FileInput.init ()

        favFileInput =
            { favFileInit | accept = "image/*" }

        wideFileInput =
            FileInput.init ()

        squareFileInput =
            FileInput.init ()

        loginFileInput =
            FileInput.init ()
        -}
        toasties =
            Toasty.initialState

        initData =
            FileData "" "" "" ""

        model =
            Model initValues.contextPath ColorPicker.empty ColorPicker.empty defaultSettings initData toasties
    in
    ( model
    , ApiCall.getSettings model
    )


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

        ToggleLoginLogo ->
            let
                settings =
                    model.settings

                activate =
                    not settings.useLoginLogo

                newSettings =
                    { settings | useLoginLogo = activate }

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
                    ( model, Cmd.none )
                        |> createErrorNotification "Error while trying to fetch settings." err

        SaveSettings result ->
            case result of
                Ok newSettings ->
                    let
                        settings = model.settings

                        bgColor  = Color.toCssString settings.bgColorValue

                        txtColor = Color.toCssString settings.labelColorValue

                        labelTxt =
                            settings.labelTxt
                    in
                    ( model, applyCss ( bgColor, txtColor, labelTxt ) )
                        |> createSuccessNotification "Your changes have been saved."

                Err err ->
                    ( model, Cmd.none )
                        |> createErrorNotification "Error while trying to save changes." err

        SendSave ->
            ( model, ApiCall.saveSettings model )

        ToastyMsg subMsg ->
            Toasty.update defaultConfig ToastyMsg subMsg model


onKeyDown : (Int -> msg) -> Html.Attribute msg
onKeyDown tagger =
    on "keydown" (Decode.map tagger keyCode)



--- MAIN ---


subscriptions : Model -> Sub Msg
subscriptions model =
  Sub.none
  {-
    Sub.batch
        [ Sub.map BgColorPicker (ColorPicker.subscriptions model.bgColorPicker)
        , Sub.map TxtColorPicker (ColorPicker.subscriptions model.labelColorPicker)
        ]
  -}
main =
    Browser.element
        { init = initSettings
        , view = view
        , update = update
        , subscriptions = subscriptions
        }
