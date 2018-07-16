module View exposing (..)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick,onInput,on, keyCode)
import DataTypes exposing (..)
import Ui.FileInput as FileInput
import Ui.ColorPicker as ColorPicker
import Toasty
import Toasty.Defaults
import Http exposing (Error)
import Color exposing (Color)
import Ext.Color exposing (toHsv, toCSSRgba)
import ApiCall exposing (saveSettings)

--- VIEW ---
view : Model -> Html Msg
view model =
  let
    settings = model.settings

    customBar =
      [ div[class "panel-col col-md-6"]
          [ checkbox settings.displayBar ToggleCustomBar "enable-bar" "Display Custom Bar"
          , div[class "form-group"]
            [ label[][text "Background color"]
            , div[class "input-group"]
              [ label[for "color-bar", class "input-group-addon"]
                  [span[class "fa fa-hashtag"][]]
                , Html.map BgColorPicker (ColorPicker.view model.bgColorPicker)
              ]
            ]
          , div[class "form-group"]
            [ label[][text "Label color"]
            , div[class "input-group"]
              [ label[for "color-txt", class "input-group-addon"]
                  [span[class "fa fa-hashtag"][]]
                , Html.map TxtColorPicker (ColorPicker.view model.labelColorPicker)
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
            [ label[][text "Favicon"]
            , div[class "input-group"]
              [ label[for "favicon-file", class "input-group-addon"]
                  [input[type_ "checkbox", id "favicon-file", checked settings.useFavicon, onClick ToggleFavicon][]]
              , Html.map FaviconFileInput (FileInput.view model.faviconFile)
              ]
            , div[][text (toString favFile)]
            ]
          , div[class "form-group"]
            [ label[][text "Wide logo"]
            , div[class "input-group"]
              [ label[for "wide-file", class "input-group-addon"]
                  [input[type_ "checkbox", id "wide-file", checked settings.useWideLogo, onClick ToggleWideLogo][]]
              , Html.map WideLogoFileInput (FileInput.view model.wideLogoFile)
              ]
            ]
          , div[class "form-group"]
            [ label[][text "Square logo"]
            , div[class "input-group"]
              [ label[for "square-file", class "input-group-addon"]
                  [input[type_ "checkbox", id "square-file", checked settings.useSquareLogo, onClick ToggleSquareLogo][]]
              , Html.map SquareLogoFileInput (FileInput.view model.squareLogoFile)
              ]
            ]
          ]
      , div[class "panel-col col-md-6 bg-pattern fix-height"][]
      ]

    loginPage =
      [ div[class "panel-col col-md-6"]
          [ checkbox    settings.displayBarLogin ToggleCustomBarLogin "display-bar"  "Display custom bar"
          {-, div[class "form-group"]
            [ label[][text "Custom logo"]
            , div[class "input-group"]
              [ label[for "logo-login", class "input-group-addon"]
                  [input[type_ "checkbox", id "logo-login", checked settings.useLoginLogo, onClick ToggleLoginLogo][]]
              , Html.map LoginLogoFileInput (FileInput.view model.loginLogoFile)
              ]
            ]-}
          , textField ToggleMotd EditMotd True settings.motd "text-motd" "MOTD"
          ]
      , div[class "panel-col col-md-6 bg-pattern"][loginPagePreview model.settings]
      ]
  in
    Html.form[]
      [ createPanel "Custom Bar"   "custom-bar"   customBar
      --, createPanel "Custom Logos" "custom-logos" customLogos
      , createPanel "Login Page"   "login-page"   loginPage
      , div[ class "toolbar"]
        [ button[type_ "button", class "btn btn-success", onClick SendSave][text "Save"]
        ]
      , div[class "toasties"][Toasty.view defaultConfig Toasty.Defaults.view ToastyMsg model.toasties]
      ]

-- HTML HELPERS
checkbox : Bool -> msg -> String -> String -> Html msg
checkbox chckd actionMsg inputId txt =
  div[class "form-group"]
  [ div[class "input-group"]
    [ label[for inputId, class "input-group-addon"]
      [ input[type_ "checkbox", checked chckd, id inputId, onClick actionMsg][] ]
    , label[for inputId, class "form-control"][text txt]

    ]
  ]
textField : msg -> (String -> msg) -> Bool -> String -> String -> String -> Html msg
textField actionCheckboxMsg actionInputMsg chckd inputVal inputId txt =
  let
    fieldId = inputId++"-text"
  in
    div[class "form-group"]
    [ label[for fieldId][text txt]
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
    [ label[][text txt]
    , div[class "input-group"]
      [ label[for inputId, class "input-group-addon"]
          [input[type_ "checkbox", id inputId][]]
      , label[for fieldId, class "form-control"][text "No file chosen."]
      , label[class "input-group-btn"]
          [button[class "btn btn-primary", type_ "button", id fieldId][text "Choose file..."]]
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
          [ a[(attribute "data-toggle" "collapse"), (attribute "data-target" hrefId), href hrefId][text panelTitle]
          ]
        ]
      , div[id panelId, class "panel-collapse collapse in"]
        [ div[class "panel-body"] bodyContent ]
      ]

barStyle : Color -> Color -> Attribute msg
barStyle bgColor txtColor =
  let
    bgCss = toHsv bgColor
            |> toCSSRgba
    textCss = toHsv txtColor
            |> toCSSRgba
  in
  style
    [ ("backgroundColor", bgCss )
    , ("color"          , textCss)
    ]

customBar : Settings -> String -> String -> Html msg
customBar settings barClass labelClass=
  let
    bgColor  = settings.bgColorValue
    txtColor = settings.labelColorValue
    labelTxt = settings.labelTxt
  in
    div[class ("custom-bar " ++ barClass), barStyle bgColor txtColor]
      [span[class labelClass][text labelTxt]]

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
        , div[class "text-motd"][span[class customMotdClass][text settings.motd]]
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

-- NOTIFICATIONS --
getErrorMessage : Http.Error -> String
getErrorMessage e =
  let
    errMessage = case e of
      Http.BadStatus b  ->
        let
          status = b.status
          message = status.message
        in
          ("Code "++Basics.toString(status.code)++" : "++message)
      Http.BadUrl str -> "Invalid API url"
      Http.Timeout      -> "It took too long to get a response"
      Http.NetworkError -> "Network error"
      Http.BadPayload str rstr -> str
  in
    errMessage

tempConfig : Toasty.Config Msg
tempConfig =
  Toasty.Defaults.config
    |> Toasty.delay 3000

defaultConfig : Toasty.Config Msg
defaultConfig =
  Toasty.Defaults.config
    |> Toasty.delay 999999999

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
  addToast (Toasty.Defaults.Error "Error..." (message ++ " ("++(getErrorMessage e)++")"))

createDecodeErrorNotification : String -> String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createDecodeErrorNotification message e =
  addToast (Toasty.Defaults.Error "Error..." (message ++ " ("++(e)++")"))