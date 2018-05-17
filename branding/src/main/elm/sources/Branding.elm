port module Branding exposing (..)

import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick,onInput,on, keyCode)
import Debug exposing (log)
import Http
import Json.Decode as Decode
import Json.Decode.Pipeline as Pipeline
import Json.Encode as Encode
import Ext.Color
import Color
import Ui.FileInput
import Ui.ColorPicker
import Ui.Native.FileManager
import Task
--- PORTS ---
port check : String -> Cmd msg

--- INITIALIZATION ---
apiToken : String
apiToken = ""

--- MODEL ---
type alias Model =
  { test             : String
  , bgColorPicker    : Ui.ColorPicker.Model
  , labelColorPicker : Ui.ColorPicker.Model
  , faviconFile      : Ui.FileInput.Model
  , wideLogoFile     : Ui.FileInput.Model
  , squareLogoFile   : Ui.FileInput.Model
  , loginLogoFile    : Ui.FileInput.Model
  , settings         : Settings
  }

type alias Settings = 
  --GENERAL
  { displayBar         : Bool
  , displayLabel       : Bool
  , labelTxt           : String
  , bgColorValue       : String
  , labelColorValue    : String
  --LOGOS
    , useCustomLogos   : Bool
  , useFavicon         : Bool
  , faviconFileData    : String
  , useWideLogo        : Bool
  , wideLogoFileData   : String
  , useSquareLogo      : Bool
  , squareLogoFileData : String
  --LOGIN
  , displayBarLogin    : Bool
  , useLoginLogo       : Bool
  , loginLogoFileData  : String
  , displayMotd        : Bool
  , motd               : String
  }

initSettings : (Model, Cmd Msg)
initSettings =
  let
    favFileInit     = Ui.FileInput.init   ()
    favFileInput    = {favFileInit | accept="image/*"}
    wideFileInput   = Ui.FileInput.init   ()
    squareFileInput = Ui.FileInput.init   ()
    loginFileInput  = Ui.FileInput.init   ()
    ( bgColorPicker, cmdBg ) = 
      Ui.ColorPicker.init ()
      |> Ui.ColorPicker.setValue Color.red
    bgColorModel = bgColorPicker.colorPanel
    bgColor = (Ext.Color.toCSSRgba bgColorModel.value)

    ( txtColorPicker, cmdTxt ) = 
      Ui.ColorPicker.init ()
      |> Ui.ColorPicker.setValue Color.white
    txtColorModel = txtColorPicker.colorPanel
    txtColor = (Ext.Color.toCSSRgba txtColorModel.value)

    model = Model "" bgColorPicker txtColorPicker favFileInput wideFileInput squareFileInput loginFileInput (Settings True True "Production" bgColor txtColor True True "data" True "data" True "data" True True "data" True "Welcome, please sign in :")
  in
    (model, Cmd.none)

--- UPDATE ---
type Msg =
  -- CUSTOM BAR
    ToggleCustomBar
  | ToggleLabel
  | EditLabelText String
  --LOGOS
  | ToggleCustomLogos
  | ToggleFavicon
  | FaviconFileInput    Ui.FileInput.Msg
  | ToggleWideLogo
  | WideLogoFileInput   Ui.FileInput.Msg
  | ToggleSquareLogo
  | SquareLogoFileInput Ui.FileInput.Msg
  -- LOGIN PAGE
  | ToggleCustomBarLogin
  | ToggleMotd
  | EditMotd String
  | ToggleLoginLogo
  | LoginLogoFileInput  Ui.FileInput.Msg
  | BgColorPicker       Ui.ColorPicker.Msg
  | TxtColorPicker      Ui.ColorPicker.Msg
  -- SAVE
  | SaveSettings
  -- TEST


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  case msg of
{-- CUSTOM BAR --}
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
          Ui.ColorPicker.update msg model.bgColorPicker
        colorModel  = updatedColorPicker.colorPanel
        color       = (Ext.Color.toCSSRgba colorModel.value)
        newSettings = {settings | bgColorValue  = color}
        newModel    = {model    | bgColorPicker = updatedColorPicker, settings = newSettings}
      in
        ( newModel, Cmd.map BgColorPicker cmd )
    TxtColorPicker msg ->
      let
        settings = model.settings
        ( updatedColorPicker, cmd ) =
          Ui.ColorPicker.update msg model.labelColorPicker
        colorModel  = updatedColorPicker.colorPanel
        color       = (Ext.Color.toCSSRgba colorModel.value)
        newSettings = {settings | labelColorValue  = color}
        newModel    = {model    | labelColorPicker = updatedColorPicker, settings = newSettings}
      in
        ( newModel, Cmd.map TxtColorPicker cmd )
{-- CUSTOM LOGOS --}
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
          Ui.FileInput.update msg model.faviconFile
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
          Ui.FileInput.update msg model.wideLogoFile
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
          Ui.FileInput.update msg model.squareLogoFile
        newModel = {model | squareLogoFile = updatedFileInput}
      in
        ( newModel, Cmd.map SquareLogoFileInput cmd )
{-- LOGIN PAGE --}
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
          Ui.FileInput.update msg model.loginLogoFile
        newModel = {model | loginLogoFile = updatedFileInput}
      in
        ( newModel, Cmd.map LoginLogoFileInput cmd )
{-- SAVE --}
    SaveSettings ->
      let
        encodedSettings = Encode.encode 4 (encodeSettings model.settings)
        modelDecoder    = Decode.decodeString decodeSettings encodedSettings
        decodedModel    = case modelDecoder of
          Ok m -> m
          Err e-> model.settings
      in
        Debug.log(encodedSettings)
        (model, Cmd.none)

onKeyDown : (Int -> msg) -> Html.Attribute msg
onKeyDown tagger =
  on "keydown" (Decode.map tagger keyCode)


--- VIEW ---
view : Model -> Html Msg
view model =
  let
    settings = model.settings

    customBar =
      [ div[class "panel-col col-md-6"]
          [ checkbox settings.displayBar ToggleCustomBar "enable-bar" "Display Custom Bar"
          , div[class "form-group"]
            [ label[][Html.text "Background color"]
            , div[class "input-group"]
              [ label[for "color-bar", class "input-group-addon"]
                  [span[class "fa fa-hashtag"][]]
                , Html.map BgColorPicker (Ui.ColorPicker.view model.bgColorPicker)
              ]
            ]
          , div[class "form-group"]
            [ label[][Html.text "Label color"]
            , div[class "input-group"]
              [ label[for "color-txt", class "input-group-addon"]
                  [span[class "fa fa-hashtag"][]]
                , Html.map TxtColorPicker (Ui.ColorPicker.view model.labelColorPicker)
              ]
            ]
          , textField ToggleLabel EditLabelText settings.displayLabel settings.labelTxt "text-label" "Label text"
          ] 
      , div[class "panel-col col-md-6 bg-pattern"][customBarPreview model.settings]
      ]
    favInput = model.faviconFile
    favFile = favInput.file
    fileData = case favFile of 
      Just f -> f.name
      Maybe.Nothing -> "No selected file"

    customLogos =
      [ div[class "panel-col col-md-6"]
          [ checkbox True ToggleCustomLogos "enable-logos" "Use Custom logos"
          , div[class "form-group"]
            [ label[][Html.text "Favicon"]
            , div[class "input-group"]
              [ label[for "favicon-file", class "input-group-addon"]
                  [input[type_ "checkbox", id "favicon-file", checked settings.useFavicon, onClick ToggleFavicon][]]
              , Html.map FaviconFileInput (Ui.FileInput.view model.faviconFile)
              ]
            , div[][Html.text (toString favFile)]
            ]
          , div[class "form-group"]
            [ label[][Html.text "Wide logo"]
            , div[class "input-group"]
              [ label[for "wide-file", class "input-group-addon"]
                  [input[type_ "checkbox", id "wide-file", checked settings.useWideLogo, onClick ToggleWideLogo][]]
              , Html.map WideLogoFileInput (Ui.FileInput.view model.wideLogoFile)
              ]
            ]
          , div[class "form-group"]
            [ label[][Html.text "Square logo"]
            , div[class "input-group"]
              [ label[for "square-file", class "input-group-addon"]
                  [input[type_ "checkbox", id "square-file", checked settings.useSquareLogo, onClick ToggleSquareLogo][]]
              , Html.map SquareLogoFileInput (Ui.FileInput.view model.squareLogoFile)
              ]
            ]
          ]
      , div[class "panel-col col-md-6 bg-pattern fix-height"][]
      ]

    loginPage =
      [ div[class "panel-col col-md-6"]
          [ checkbox    settings.displayBarLogin ToggleCustomBarLogin "display-bar"  "Display custom bar"
          , div[class "form-group"]
            [ label[][Html.text "Custom logo"]
            , div[class "input-group"]
              [ label[for "logo-login", class "input-group-addon"]
                  [input[type_ "checkbox", id "logo-login", checked settings.useLoginLogo, onClick ToggleLoginLogo][]]
              , Html.map LoginLogoFileInput (Ui.FileInput.view model.loginLogoFile)
              ]
            ]
          , textField ToggleMotd EditMotd True settings.motd "text-motd" "MOTD"
          ]
      , div[class "panel-col col-md-6 bg-pattern"][loginPagePreview model.settings]
      ]
  in
    Html.form[]
      [ createPanel "Custom Bar"   "custom-bar"   customBar
      , createPanel "Custom Logos" "custom-logos" customLogos
      , createPanel "Login Page"   "login-page"   loginPage
      , div[][button[type_ "button", class "btn btn-success", onClick SaveSettings][Html.text "Save"]]
      ]

-- HTML HELPERS
checkbox : Bool -> msg -> String -> String -> Html msg
checkbox chckd actionMsg inputId txt =
  div[class "form-group"]
  [ div[class "input-group"]
    [ label[for inputId, class "input-group-addon"]
      [ input[type_ "checkbox", checked chckd, id inputId, onClick actionMsg][] ]
    , label[for inputId, class "form-control"][Html.text txt]

    ]
  ]
textField : msg -> (String -> msg) -> Bool -> String -> String -> String -> Html msg
textField actionCheckboxMsg actionInputMsg chckd inputVal inputId txt =
  let
    fieldId = inputId++"-text"
  in
    div[class "form-group"]
    [ label[for fieldId][Html.text txt]
    , div[class "input-group"]
      [ label[for inputId, class "input-group-addon"]
        [ input[type_ "checkbox", id inputId, onClick actionCheckboxMsg, checked chckd][] ]
      , input[id fieldId, class "form-control", onInput actionInputMsg, value inputVal][]
      ]
    ]

fileField : msg -> String -> String -> Html msg
fileField msg inputId txt =
  let
    fieldId = inputId++"-text"
  in
    div[class "form-group"]
    [ label[][Html.text txt]
    , div[class "input-group"]
      [ label[for inputId, class "input-group-addon"]
          [input[type_ "checkbox", id inputId][]]
      , label[for fieldId, class "form-control"][Html.text "No file chosen."]
      , label[class "input-group-btn"]
          [button[class "btn btn-primary", type_ "button", id fieldId][Html.text "Choose file..."]]
      ]
    ]

createPanel : String -> String -> List (Html msg) -> Html msg
createPanel panelTitle panelId bodyContent =
  let
    hrefId = "#"++panelId
  in
    div[class "panel panel-default"]
      [ div[class "panel-heading"]
        [ h2[class "panel-title"]
          [ a[(attribute "data-toggle" "collapse"), (attribute "data-target" hrefId), href hrefId][Html.text panelTitle]
          ]
        ]
      , div[id panelId, class "panel-collapse collapse in"]
        [ div[class "panel-body"] bodyContent ]
      ]

barStyle : String -> String -> Attribute msg
barStyle bgColor txtColor =
  style
    [ ("backgroundColor", bgColor )
    , ("color"          , txtColor)
    ]

customBar : Settings -> String -> String -> Html msg
customBar settings barClass labelClass=
  let
    bgColor  = settings.bgColorValue
    txtColor = settings.labelColorValue
    labelTxt = settings.labelTxt
  in  
    div[class ("custom-bar " ++ barClass), barStyle bgColor txtColor]
      [span[class labelClass][Html.text labelTxt]]

-- PREVIEWS --
customBarPreview : Settings -> Html msg
customBarPreview settings =
  let
    customBarClass = case settings.displayBar of 
      True  -> ""
      False -> "hidden"
    labelClass = case settings.displayLabel of 
      True  -> ""
      False -> "hidden"
  in
    div[class "preview-window"]
      [ customBar settings customBarClass labelClass
      , div[class "top-menu"  ][]
      , div[class "left-menu" ][]
      ]

loginPagePreview : Settings -> Html msg
loginPagePreview settings =
  let
    customBarClass = case settings.displayBarLogin of 
      True  -> ""
      False -> "invisible"
    customMotdClass = case settings.displayMotd of 
      True  -> ""
      False -> "invisible"
  in
    div[class "preview-window login"]
      [ div[class "custom-form"]
        [ div[class "form-head"][]
        , customBar settings customBarClass ""
        , div[class "text-motd"][span[class customMotdClass][Html.text settings.motd]]
        , div[class "form-body"]
          [ div[class "fake-form"]
            [ div[class "fake-input"][]
            , div[class "fake-input"][]
            , div[class "fake-btn"][]
            ]
          ]
        , div[class "form-foot"][]
      ]
    ]

--- ENCODERS & DECODERS ---
encodeSettings : Settings -> Encode.Value
encodeSettings settings =
  let 
    data = 
    [ ("displayBar"      , Encode.bool   <| settings.displayBar      )
    , ("displayLabel"    , Encode.bool   <| settings.displayLabel    )
    , ("labelTxt"        , Encode.string <| settings.labelTxt        )
    , ("bgColorValue"    , Encode.string <| settings.bgColorValue    )
    , ("labelColorValue" , Encode.string <| settings.labelColorValue )
    , ("useCustomLogos"  , Encode.bool   <| settings.useCustomLogos  )
    , ("useFavicon"      , Encode.bool   <| settings.useFavicon      )
    , ("useWideLogo"     , Encode.bool   <| settings.useWideLogo     )
    , ("useSquareLogo"   , Encode.bool   <| settings.useSquareLogo   )
    , ("displayBarLogin" , Encode.bool   <| settings.displayBarLogin )
    , ("useLoginLogo"    , Encode.bool   <| settings.useLoginLogo    )
    , ("displayMotd"     , Encode.bool   <| settings.displayMotd     )
    , ("motd"            , Encode.string <| settings.motd            )
    ]
  in
    Encode.object data
decodeSettings : Decode.Decoder Settings
decodeSettings =
  Pipeline.decode Settings
    |> Pipeline.required "displayBar"         Decode.bool
    |> Pipeline.required "displayLabel"       Decode.bool
    |> Pipeline.required "labelTxt"           Decode.string
    |> Pipeline.required "bgColorValue"       Decode.string
    |> Pipeline.required "labelColorValue"    Decode.string
    |> Pipeline.required "useCustomLogos"     Decode.bool
    |> Pipeline.required "useFavicon"         Decode.bool
    |> Pipeline.required "faviconFileData"    Decode.string
    |> Pipeline.required "useWideLogo"        Decode.bool
    |> Pipeline.required "wideLogoFileData"   Decode.string
    |> Pipeline.required "useSquareLogo"      Decode.bool
    |> Pipeline.required "squareLogoFileData" Decode.string
    |> Pipeline.required "displayBarLogin"    Decode.bool
    |> Pipeline.required "useLoginLogo"       Decode.bool
    |> Pipeline.required "loginLogoFileData"  Decode.string
    |> Pipeline.required "displayMotd"        Decode.bool
    |> Pipeline.required "motd"               Decode.string



--- MAIN ---
subscriptions : Model -> Sub Msg
subscriptions model =
  Sub.batch
    [ Sub.map BgColorPicker  (Ui.ColorPicker.subscriptions model.bgColorPicker   )
    , Sub.map TxtColorPicker (Ui.ColorPicker.subscriptions model.labelColorPicker)
    ]

main = program
  { init   = initSettings
  , update = update
  , view   = view
  , subscriptions = subscriptions
  }