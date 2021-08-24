module View exposing (addTempToast, addToast, barStyle, checkbox, createDecodeErrorNotification, createErrorNotification, createPanel, createSuccessNotification, customBar, customBarPreview, defaultConfig, fileField, getErrorMessage, loginPagePreview, tempConfig, textField, view)

import ApiCall exposing (saveSettings)
import Color exposing (Color)
import DataTypes exposing (..)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import Http exposing (Error)
import Toasty
import Toasty.Defaults
import ColorPicker

import Browser
import File exposing (File)
import File.Select as Select

import Json.Decode as D
import Task
--- VIEW ---


view : Model -> Html Msg
view model =
  let
    settings =
      model.settings

    wideLogo  = settings.wideLogo
    smallLogo = settings.smallLogo

    (wideLogoTxtInfo, wideActionBtn, wideClassBtn) = case wideLogo.data of
      Just d  -> case wideLogo.name of
        Just n  -> ( " ("++ n ++")"      , (RemoveFile "wide"), "remove-btn"  )
        Nothing -> ( "Unknown file name" , (RemoveFile "wide"), "remove-btn"  )
      Nothing ->   ( "(No file chosen)"  , (UploadFile "wide"), "upload-btn"  )
    (smallLogoTxtInfo, smallActionBtn, smallClassBtn) = case smallLogo.data of
      Just d  -> case smallLogo.name of
        Just n  -> ( " ("++ n ++")"      , (RemoveFile "small"), "remove-btn"  )
        Nothing -> ( "Unknown file name" , (RemoveFile "small"), "remove-btn"  )
      Nothing ->   ( "(No file chosen)"  , (UploadFile "small"), "upload-btn"  )

    customLogos =
      [ div [ class "panel-col col-md-6" ]
        [ div [ class "form-group" ]
          [ div [ class "input-group" ]
            [ label [ for "enable-wide-logo", class "input-group-addon" ]
              [ input [ type_ "checkbox", checked wideLogo.enable, id "enable-wide-logo", onClick (ToggleLogo "wide")] [] ]
            , label [ class "form-control", onClick (UploadFile "wide") ] [ text "Upload main custom logo", i[][ text wideLogoTxtInfo] ]
            , span [ class ("input-group-addon " ++ wideClassBtn), onClick wideActionBtn ] [ i[class "fa fa-upload"][], i[class "fa fa-times"][]]
            ]
          ]
        , div [ class "form-group" ]
          [ div [ class "input-group" ]
            [ label [ for "enable-small-logo", class "input-group-addon" ]
              [ input [ type_ "checkbox", checked smallLogo.enable, id "enable-small-logo", onClick (ToggleLogo "small")] [] ]
            , label [ class "form-control", onClick (UploadFile "small") ] [ text "Upload reponsive custom logo", i[][ text smallLogoTxtInfo] ]
            , span [ class ("input-group-addon " ++ smallClassBtn), onClick smallActionBtn ] [ i[class "fa fa-upload"][], i[class "fa fa-times"][]]
            ]
          ]
        ]
      , div [ class "panel-col col-md-6 bg-pattern" ][ viewPreview settings.smallLogo settings.wideLogo]
      ]

    viewPreview : Logo -> Logo -> Html msg
    viewPreview smLogo lgLogo=
      let
        wideLogoUrl  = case lgLogo.data of
          Just l  -> l
          Nothing -> defaultWideLogoData
        smallLogoUrl = case smLogo.data of
          Just l  -> l
          Nothing -> defaultSmallLogoData
        lgDisabled = if (lgLogo.enable && wideLogoUrl  /= defaultWideLogoData ) then "" else " lg-disabled"
        smDisabled = if (smLogo.enable && smallLogoUrl /= defaultSmallLogoData) then "" else " sm-disabled"

      in
        div[class ("preview-logo" ++ lgDisabled ++ smDisabled)]
        [ div[ style "background-image" ("url('" ++ smallLogoUrl ++ "')") ][]
        , div[ style "background-image" ("url('" ++ wideLogoUrl  ++ "')") ][]
        ]

    bar =
      [ div [ class "panel-col col-md-6" ]
        [ checkbox settings.displayBar ToggleCustomBar "enable-bar" "Display Custom Bar"
        , div [ class "form-group" ]
          [ label [] [ text "Background color" ]
          , div [ class "input-group" ]
            [ label [ for "color-bar", class "input-group-addon" ]
              [ span [ class "fa fa-hashtag" ] [] ]
              , Html.map BgColorPicker (ColorPicker.view model.settings.bgColorValue model.bgColorPicker)
            ]
          ]
        , div [ class "form-group" ]
          [ label [] [ text "Label color" ]
          , div [ class "input-group" ]
            [ label [ for "color-txt", class "input-group-addon" ]
              [ span [ class "fa fa-hashtag" ] [] ]
              , Html.map TxtColorPicker (ColorPicker.view model.settings.labelColorValue model.labelColorPicker)
              ]
            ]
          , textField ToggleLabel EditLabelText settings.displayLabel settings.labelTxt "text-label" "Label text"
        ]
      , div [ class "panel-col col-md-6 bg-pattern" ] [ customBarPreview model.settings ]
      ]

    loginPage =
      [ div [ class "panel-col col-md-6" ]
        [ checkbox settings.displayBarLogin ToggleCustomBarLogin "display-bar" "Display custom bar"
        , textField ToggleMotd EditMotd settings.displayMotd settings.motd "text-motd" "MOTD"
        ]
      , div [ class "panel-col col-md-6 bg-pattern" ] [ loginPagePreview model.settings ]
      ]
    in
    Html.form []
        [ createPanel "Custom Logos" "custom-logos" customLogos
        , createPanel "Custom Bar" "custom-bar" bar
        , createPanel "Login Page" "login-page" loginPage
        , div [ class "toolbar" ]
            [ button [ type_ "button", class "btn btn-success", onClick SendSave ] [ text "Save" ]
            ]
        , div [ class "toasties" ] [ Toasty.view defaultConfig Toasty.Defaults.view ToastyMsg model.toasties ]
        ]



-- HTML HELPERS


checkbox : Bool -> msg -> String -> String -> Html msg
checkbox chckd actionMsg inputId txt =
    div [ class "form-group" ]
        [ div [ class "input-group" ]
            [ label [ for inputId, class "input-group-addon" ]
                [ input [ type_ "checkbox", checked chckd, id inputId, onClick actionMsg ] [] ]
            , label [ for inputId, class "form-control" ] [ text txt ]
            ]
        ]


textField : msg -> (String -> msg) -> Bool -> String -> String -> String -> Html msg
textField actionCheckboxMsg actionInputMsg chckd inputVal inputId txt =
    let
        fieldId =
            inputId ++ "-text"
    in
    div [ class "form-group" ]
        [ label [ for fieldId ] [ text txt ]
        , div [ class "input-group" ]
            [ label [ for inputId, class "input-group-addon" ]
                [ input [ type_ "checkbox", id inputId, onClick actionCheckboxMsg, checked chckd ] [] ]
            , input [ id fieldId, class "form-control", onInput actionInputMsg, value inputVal ] []
            ]
        ]


fileField : msg -> String -> String -> Html msg
fileField msg inputId txt =
    let
        fieldId =
            inputId ++ "-text"
    in
    div [ class "form-group" ]
        [ label [] [ text txt ]
        , div [ class "input-group" ]
            [ label [ for inputId, class "input-group-addon" ]
                [ input [ type_ "checkbox", id inputId ] [] ]
            , label [ for fieldId, class "form-control" ] [ text "No file chosen." ]
            , label [ class "input-group-btn" ]
                [ button [ class "btn btn-primary", type_ "button", id fieldId ] [ text "Choose file..." ] ]
            ]
        ]


createPanel : String -> String -> List (Html msg) -> Html msg
createPanel panelTitle panelId bodyContent =
    let
        hrefId =
            "#" ++ panelId
    in
    div [ class "panel panel-default" ]
        [ div [ class "panel-heading" ]
            [ h2 [ class "panel-title" ]
                [ a [ attribute "data-toggle" "collapse", attribute "data-target" hrefId, href hrefId ] [ text panelTitle ]
                ]
            ]
        , div [ id panelId, class "panel-collapse collapse in" ]
            [ div [ class "panel-body" ] bodyContent ]
        ]


barStyle : Color -> Color -> List (Attribute msg)
barStyle bgColor txtColor =
    let
        bgCss = Color.toCssString  bgColor
        textCss = Color.toCssString txtColor
    in

        [ style "backgroundColor" bgCss
        , style "color" textCss
        ]


customBar : Settings -> String -> String -> Html msg
customBar settings barClass labelClass =
    let
        bgColor =
            settings.bgColorValue

        txtColor =
            settings.labelColorValue

        labelTxt =
            settings.labelTxt
    in
    div (class ("custom-bar " ++ barClass) :: barStyle bgColor txtColor)
        [ span [ class labelClass ] [ text labelTxt ] ]



-- PREVIEWS --
customBarPreview : Settings -> Html msg
customBarPreview settings =
  let
    customBarClass =
      case settings.displayBar of
        True  -> ""
        False -> "hidden"

    labelClass =
      case settings.displayLabel of
        True  -> ""
        False -> "hidden"
  in
    div [ class "preview-window" ]
      [ customBar settings customBarClass labelClass
      , div [ class "top-menu"  ]
        [ div []
          [ span[style "background-image" (logoUrl settings)][]
          ]
        ]
      , div [ class "left-menu" ] []
      ]


loginPagePreview : Settings -> Html msg
loginPagePreview settings =
    let
      customBarClass = case settings.displayBarLogin of
        True -> ""
        False -> "invisible"

      customMotdClass = case settings.displayMotd of
          True -> ""
          False -> "invisible"

  in
    div [ class "preview-window login" ]
      [ div [ class "custom-form" ]
        [ div [ class "form-head" ] [span[style "background-image" (logoUrl settings)][]]
        , customBar settings customBarClass ""
        , div [ class "text-motd" ] [ span [ class customMotdClass ] [ text settings.motd ] ]
        , div [ class "form-body" ]
          [ div [ class "fake-form" ]
            [ div [ class "fake-input" ] []
            , div [ class "fake-input" ] []
            , div [ class "fake-btn"   ] []
            ]
          ]
        , div [ class "form-foot" ] []
        ]
      ]

defaultWideLogoData  : String
defaultWideLogoData  = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjwhLS0gQ3JlYXRlZCB3aXRoIElua3NjYXBlIChodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy8pIC0tPgoKPHN2ZwogICB4bWxuczpkYz0iaHR0cDovL3B1cmwub3JnL2RjL2VsZW1lbnRzLzEuMS8iCiAgIHhtbG5zOmNjPSJodHRwOi8vY3JlYXRpdmVjb21tb25zLm9yZy9ucyMiCiAgIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyIKICAgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIgogICB4bWxuczpzb2RpcG9kaT0iaHR0cDovL3NvZGlwb2RpLnNvdXJjZWZvcmdlLm5ldC9EVEQvc29kaXBvZGktMC5kdGQiCiAgIHhtbG5zOmlua3NjYXBlPSJodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy9uYW1lc3BhY2VzL2lua3NjYXBlIgogICB2ZXJzaW9uPSIxLjEiCiAgIGlkPSJzdmcyIgogICB4bWw6c3BhY2U9InByZXNlcnZlIgogICB3aWR0aD0iMTMwMCIKICAgaGVpZ2h0PSIzMDAiCiAgIHZpZXdCb3g9IjAgMCAxMzAwIDMwMCIKICAgc29kaXBvZGk6ZG9jbmFtZT0ibG9nby1ydWRkZXItd2hpdGUuc3ZnIgogICBpbmtzY2FwZTp2ZXJzaW9uPSIwLjkyLjUgKDIwNjBlYzFmOWYsIDIwMjAtMDQtMDgpIj48bWV0YWRhdGEKICAgICBpZD0ibWV0YWRhdGE4Ij48cmRmOlJERj48Y2M6V29yawogICAgICAgICByZGY6YWJvdXQ9IiI+PGRjOmZvcm1hdD5pbWFnZS9zdmcreG1sPC9kYzpmb3JtYXQ+PGRjOnR5cGUKICAgICAgICAgICByZGY6cmVzb3VyY2U9Imh0dHA6Ly9wdXJsLm9yZy9kYy9kY21pdHlwZS9TdGlsbEltYWdlIiAvPjxkYzp0aXRsZSAvPjwvY2M6V29yaz48L3JkZjpSREY+PC9tZXRhZGF0YT48ZGVmcwogICAgIGlkPSJkZWZzNiIgLz48c29kaXBvZGk6bmFtZWR2aWV3CiAgICAgcGFnZWNvbG9yPSIjZmZmZmZmIgogICAgIGJvcmRlcmNvbG9yPSIjNjY2NjY2IgogICAgIGJvcmRlcm9wYWNpdHk9IjEiCiAgICAgb2JqZWN0dG9sZXJhbmNlPSIxMCIKICAgICBncmlkdG9sZXJhbmNlPSIxMCIKICAgICBndWlkZXRvbGVyYW5jZT0iMTAiCiAgICAgaW5rc2NhcGU6cGFnZW9wYWNpdHk9IjAiCiAgICAgaW5rc2NhcGU6cGFnZXNoYWRvdz0iMiIKICAgICBpbmtzY2FwZTp3aW5kb3ctd2lkdGg9IjE5MjAiCiAgICAgaW5rc2NhcGU6d2luZG93LWhlaWdodD0iMTA0MyIKICAgICBpZD0ibmFtZWR2aWV3NCIKICAgICBzaG93Z3JpZD0iZmFsc2UiCiAgICAgaW5rc2NhcGU6em9vbT0iMC41Mjg2OTgzIgogICAgIGlua3NjYXBlOmN4PSItMjY5Ljg3MjMzIgogICAgIGlua3NjYXBlOmN5PSI4NC43MTQ1MzkiCiAgICAgaW5rc2NhcGU6d2luZG93LXg9IjAiCiAgICAgaW5rc2NhcGU6d2luZG93LXk9IjAiCiAgICAgaW5rc2NhcGU6d2luZG93LW1heGltaXplZD0iMSIKICAgICBpbmtzY2FwZTpjdXJyZW50LWxheWVyPSJnMTAiIC8+PGcKICAgICBpZD0iZzEwIgogICAgIGlua3NjYXBlOmdyb3VwbW9kZT0ibGF5ZXIiCiAgICAgaW5rc2NhcGU6bGFiZWw9IjIwMjEtRklDLWxvZ28tcnVkZGVyIgogICAgIHRyYW5zZm9ybT0ibWF0cml4KDEuMzMzMzMzMywwLDAsMS4zMzMzMzMzLDAsNy41ZS02KSI+PGcKICAgICAgIGlkPSJnMTIiPjxwYXRoCiAgICAgICAgIGQ9Im0gMTQxLjc5NywxOC43NyBjIC01MS44MzYsMCAtOTMuODU2LDQxLjk2NCAtOTMuODU5LDkzLjcyNiAwLDI0Ljg1OSA5Ljg5LDQ4LjcwMyAyNy40OTIsNjYuMjgxIDE3LjYwMSwxNy41NzggNDEuNDcyLDI3LjQ1MyA2Ni4zNjcsMjcuNDUzIDI0Ljg5MSwtMC4wMDMgNDguNzYyLC05Ljg3OCA2Ni4zNjMsLTI3LjQ1NyAxNy42MDIsLTE3LjU3OCAyNy40ODgsLTQxLjQxOCAyNy40ODgsLTY2LjI3NyAtMC4wMzksLTIzLjI3MyAtOC43NSwtNDUuNjk5IC0yNC40MzMsLTYyLjkxNCBsIC02MS4yNDYsNjguMTg0IGMgLTEuODcxLDEuODgyIC00LjM0NCwyLjgxNiAtNi44MDksMi44MTYgLTIuNDY1LDAgLTQuOTQxLC0wLjkzNCAtNi44MTIsLTIuODE2IC0zLjc2NiwtMy43NTggLTMuNzY2LC05Ljg1MiAwLC0xMy42MTQgTCAyMDQuNjE3LDQzLjAwNCBDIDE4Ny4zOTgsMjcuNDQ1IDE2NS4wMTYsMTguODA5IDE0MS43OTcsMTguNzcgWiBtIC0xNS43MzgsMzQuMzEyIGMgMi42OTEsNi4wMTIgOC43MTgsMTAuMTk5IDE1Ljc0MiwxMC4xOTkgNy4wMTEsMCAxMy4wNDMsLTQuMTg3IDE1LjczNCwtMTAuMTk5IDUuMzk1LDEuNDMgMTAuNDg1LDMuNTc0IDE1LjE5OSw2LjMxMyAtMC42MDksMS41NjYgLTAuOTQ1LDMuMjA3IC0xLjA3NCw0Ljg0MyBsIC0yMC41NzgsMTcuODkxIGMgLTIuOTQ5LC0wLjkxIC02LjA1OSwtMS4zODcgLTkuMjgxLC0xLjM4NyAtMTcuNTM1LDAgLTMxLjgwMSwxNC4yMzggLTMxLjgwMSwzMS43NjYgMCwxNy41IDE0LjI2NiwzMS43NSAzMS44MDEsMzEuNzUgMTcuNTI3LDAgMzEuNzk3LC0xNC4yNSAzMS43OTcsLTMxLjc1IDAsLTMuMzgzIC0wLjU0MywtNi42MjkgLTEuNTE2LC05LjY4IGwgMTcuNTQ3LC0yMC4xNTYgYyAxLjgyLC0wLjA3NCAzLjYxMywtMC40MTggNS4zNDQsLTEuMDU5IDIuNzQyLDQuNjkyIDQuODksOS43ODIgNi4zMTYsMTUuMTU3IC02LjAxMiwyLjcwMyAtMTAuMjAzLDguNzIyIC0xMC4yMDMsMTUuNzM4IDAsNy4wMDQgNC4xOTEsMTMuMDE5IDEwLjIwMywxNS43MDcgLTEuNDI2LDUuMzkgLTMuNTc0LDEwLjQ3MyAtNi4zMTYsMTUuMTY4IC02LjE1MywtMi4zMjEgLTEzLjM2OCwtMS4wMzUgLTE4LjMyNSwzLjkxIC00Ljk2LDQuOTYxIC02LjI1MywxMi4xNTIgLTMuOTE0LDE4LjMwOSAtNC43MTQsMi43MzggLTkuODA0LDQuODg2IC0xNS4xOTksNi4zMDggLTIuNjkxLC02LjAwNCAtOC43MjMsLTEwLjE5NSAtMTUuNzM0LC0xMC4xOTUgLTcuMDI0LDAgLTEzLjA1MSw0LjE5MSAtMTUuNzQyLDEwLjE5NSAtNS4zOTksLTEuNDIyIC0xMC40ODksLTMuNTcgLTE1LjE4OCwtNi4zMDggMi4zMzYsLTYuMTU3IDEuMDM1LC0xMy4zNDggLTMuOTMsLTE4LjMwOSAtNC45MzcsLTQuOTQ1IC0xMi4xNiwtNi4yMzEgLTE4LjMxMiwtMy45MSAtMi43NTQsLTQuNjk1IC00Ljg4NywtOS43NzggLTYuMzI4LC0xNS4xNjggNi4wMTUsLTIuNjg4IDEwLjIxMSwtOC43MDMgMTAuMjExLC0xNS43MDcgMCwtNy4wMTYgLTQuMTk2LC0xMy4wMzUgLTEwLjIxMSwtMTUuNzIzIDEuNDQxLC01LjM4NyAzLjU3NCwtMTAuNDggNi4zMjgsLTE1LjE3MiA2LjE1MiwyLjMyMSAxMy4zNzUsMS4wMzUgMTguMzEyLC0zLjkyOSA0Ljk2NSwtNC45NDYgNi4yNjYsLTEyLjE0NSAzLjkzLC0xOC4yODkgNC42OTksLTIuNzM5IDkuNzg5LC00Ljg4MyAxNS4xODgsLTYuMzEzIHoiCiAgICAgICAgIHN0eWxlPSJmaWxsOiMxM2JlYjc7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmUiCiAgICAgICAgIGlkPSJwYXRoMTYiCiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiIC8+PC9nPjxwYXRoCiAgICAgICBpZD0icGF0aDE4LTIiCiAgICAgICBkPSJtIDU5NC4wMDc3Niw0NS4wMTg0NSB2IDUxLjYxNjM0IGMgLTIuNzI2OTcsLTUuMDg1OTUgLTYuNzUwMjcsLTkuMTY0NDcgLTEyLjA4MjE5LC0xMi4yMzg0MSAtNS4zMjc5MiwtMy4xOTU5NiAtMTEuMjQ1NTMsLTQuNzkwMTIgLTE3Ljc2MTQ0LC00Ljc5MDEyIC04LjA1MDg3LDAgLTE1LjE1NjM0LDIuMTI4OSAtMjEuMzEzMjUsNi4zODY4NyAtNi4xNTk5Miw0LjI1MzkyIC0xMS4wMTQ3NiwxMC4wNTE1OSAtMTQuNTY1NzEsMTcuMzgzNDcgLTMuNTU0OTUsNy4yMTQ5MyAtNS4zMjc3NCwxNS4zNzQyOSAtNS4zMjc3NCwyNC40NzkxMyAwLDYuODU5OTcgMS4wNjE2MiwxMy4yNDMyIDMuMTk0NTcsMTkuMTU3MTQgMi4xMzI5OCw1LjkxMzgzIDUuMTUyODMsMTEuMDU2OTYgOS4wNTg3OCwxNS40MzE4NCA0LjAyNjkyLDQuMzc0OTYgOC43MDMwOCw3LjgwNTA2IDE0LjAzNSwxMC4yODkwMyA1LjQ0NDkyLDIuMzYzOTcgMTEuMzY2NTIsMy41NDcyNiAxNy43NjE0MywzLjU0NzI2IDYuMjcyODksMCAxMi4yNTQyOCwtMS40MTgzNyAxNy45MzcxOSwtNC4yNTgzMyA1LjY4NzkxLC0yLjgzNDk3IDEwLjE4Nzc0LC03LjE1MTM0IDEzLjQ5OTcsLTEyLjk0ODMgbCAwLjUzNTI5LDQuNzg5IGMgMC43MTEsMy43ODQ5NCAyLjU0NjUzLDYuNzQxMzUgNS41MDM1MSw4Ljg3MDM3IDMuMDgxOTQsMi4xMjg5NSA3LjA0NzA2LDMuMTkyMyAxMS45MDI5OCwzLjE5MjMgMy4wNzc5NiwwIDcuMTA0NjYsLTAuNDcyNTYgMTIuMDc3NTksLTEuNDE3NTMgViAxNTAuOTEzOSBjIC0yLjEyODk3LC0wLjIzNDAyIC0zLjY2NzY5LC0wLjg4NjI3IC00LjYxNjY5LC0xLjk0ODIyIC0wLjk0OTk4LC0xLjE4NDA1IC0xLjQyMjEsLTMuMTk0NzkgLTEuNDIyMSwtNi4wMzA3NCBWIDQ1LjAxODQ1IFogbSAxMTYuMTY2MTMsMCB2IDUxLjYxNjM0IGMgLTIuNzI2OTMsLTUuMDg1OTUgLTYuNzUwMjMsLTkuMTY0NDcgLTEyLjA4MjE2LC0xMi4yMzg0MSAtNS4zMjc5MSwtMy4xOTU5NiAtMTEuMjQ1NTcsLTQuNzkwMTIgLTE3Ljc2MTQ4LC00Ljc5MDEyIC04LjA1MDgzLDAgLTE1LjE1NTU5LDIuMTI4OSAtMjEuMzE1NSw2LjM4Njg3IC02LjE1Njk0LDQuMjUzOTIgLTExLjAxMjUxLDEwLjA1MTU5IC0xNC41NjM0OCwxNy4zODM0NyAtMy41NTQ5Miw3LjIxNDkzIC01LjMyNzczLDE1LjM3NDI5IC01LjMyNzczLDI0LjQ3OTEzIDAsNi44NTk5NyAxLjA2MTYyLDEzLjI0MzIgMy4xOTQ1OSwxOS4xNTcxNCAyLjEzMjk2LDUuOTEzODMgNS4xNTI4MywxMS4wNTY5NiA5LjA1ODc1LDE1LjQzMTg0IDQuMDI2OTcsNC4zNzQ5NiA4LjcwMzExLDcuODA1MDYgMTQuMDM1MDMsMTAuMjg5MDMgNS40NDQ5MSwyLjM2Mzk3IDExLjM2NzY4LDMuNTQ3MjYgMTcuNzYyNiwzLjU0NzI2IDYuMjcyODksMCAxMi4yNTMxMiwtMS40MTgzNyAxNy45MzYwNSwtNC4yNTgzMyA1LjY4NzkyLC0yLjgzNDk3IDEwLjE4Nzc0LC03LjE1MTM0IDEzLjQ5OTY4LC0xMi45NDgzIGwgMC41MzUzMyw0Ljc4OSBjIDAuNzEwOTUsMy43ODQ5NCAyLjU0NjQ4LDYuNzQxMzUgNS41MDM0OSw4Ljg3MDM3IDMuMDgxOTEsMi4xMjg5NSA3LjA0NzA1LDMuMTkyMyAxMS45MDI5MywzLjE5MjMgMy4wNzc5OCwwIDcuMTA0NywtMC40NzI1NiAxMi4wNzc2MiwtMS40MTc1MyBWIDE1MC45MTM5IGMgLTIuMTI4OTcsLTAuMjM0MDIgLTMuNjcxNzIsLTAuODg2MjcgLTQuNjE2NjksLTEuOTQ4MjIgLTAuOTQ5OTcsLTEuMTg0MDUgLTEuNDIyMTIsLTMuMTk0NzkgLTEuNDIyMTIsLTYuMDMwNzQgViA0NS4wMTg0NSBaIE0gMzAxLjc2Miw0OC41NjMzNiB2IDEyNS45NDUxNSBoIDI5LjEzMjU2IHYgLTQwLjYyMDg1IGggMTkuNzEzMSBsIDE4LjY2NDMzLDMxLjM4MjkgYyAwLDAgMS4xMDYxNywxLjgyNzMxIDEuNzcwMTYsMi42NTkyMyAxLjM5OCwxLjc2NTAxIDMuMzkwMiw0LjAzOTI0IDQuNjI4MTcsNC45MjkxNyAyLjkwNTk2LDIuMDg5OTUgNi4zOTgyLDMuMDcwNTEgMTEuMzcxMTMsMy4wNzA1MSAxLjUzODk5LDAgMy4yNTgwMSwtMC4xMTk5MyA1LjE1MTk5LC0wLjM1NDk1IDIuMDE1OTYsLTAuMjM4MDEgNC4zMjUyNSwtMC41OTM5NiA2LjkzMDIsLTEuMDY2MDEgdiAtMjMuNTkwMDIgYyAtMi4zNTg5NSwwLjA5MzkgLTMuNDM0NjQsMC4xMTY1NyAtNC44NTU2MywtMC42NTI0OCAtMS42OTE5NiwtMC45MTA5MiAtMi45MjIwNCwtMi4zMDAyOSAtNC4wNDAwMiwtMy45NjQyIGwgLTEuMTcwNTQsLTEuOTc2OTMgLTkuODUxMzcsLTE2LjY0ODM1IGMgNC4yNjE5NCwtMi4zNjM5OCA3LjkzMzA3LC01LjM3ODIxIDExLjAxNTAxLC05LjA0NjEgMy4wNzc5NiwtMy43ODUwNCA1LjQ0NTM5LC03Ljk4NDYyIDcuMTAxMzYsLTEyLjU5NDUzIDEuNjU5OTgsLTQuNzMwMDIgMi40ODgxMiwtOS42OTgxNyAyLjQ4ODEyLC0xNC45MDExMSAwLC01LjIwMzAxIC0xLjA2NjIsLTEwLjM0NDc3IC0zLjE5OTE3LC0xNS40MzA3MyAtMi4wMTA5NywtNS4wODU4NCAtNC44NTE1MiwtOS42MzY0MiAtOC41MjM0NywtMTMuNjU5MzcgLTMuNjcxOTQsLTQuMTM2OTMgLTguMDU0ODEsLTcuMzkxMzYgLTEzLjE0NDc0LC05Ljc1ODM0IC00Ljk3MTkzLC0yLjQ3OTk2IC0xMC40MjE4NywtMy43MjI5OSAtMTYuMzQyNzYsLTMuNzIyOTkgeiBtIDI5LjEzMjU2LDI1LjU0MjgzIGggMjUuNzUxODkgYyAyLjI0OTk4LDAgNC4zODI5MywwLjcxMTc3IDYuMzk0OTEsMi4xMjk3MyAyLjAxMTk3LDEuNDE4MDYgMy42NzE4LDMuNDI5OTIgNC45NzI3OCw2LjAzMTkyIDEuNDIxOTgsMi40ODM5NiAyLjEzMjAyLDUuNDM2OTYgMi4xMzIwMiw4Ljg2Njk1IDAsMy40Mjg5MiAtMC41OTE5NCw2LjQ0NDIxIC0xLjc3NTkzLDkuMDQ2MDkgLTEuMTgyOTcsMi42MDEgLTIuNzIzODQsNC42NzIxNCAtNC42MTc4Miw2LjIxMTE0IC0xLjc3Njk4LDEuNDE3OTUgLTMuODQ3NTgsMi4xMjUwNyAtNi4yMTQ1NSwyLjEyNTA3IGggLTI2LjY0MzMgeiBtIDQ3NC4xNDEzNiw1LjM1NTM0IGMgLTEwLjU0MDY1LDAgLTE5LjU0MTQ3LDIuMzA4OTggLTI3LjAwMjg1LDYuOTI3OSAtNy40NjEzLDQuNTAwNTEgLTEzLjIwNjMsMTAuNDgxNTYgLTE3LjIzMzA1LDE3Ljk0MjkxIC0zLjkwODMyLDcuMzQyOTYgLTUuODYxODUsMTUuNTE0OSAtNS44NjE4NSwyNC41MTU4OCAwLDYuMjc3MDEgMS4xMjQ2MywxMi4zMTY1OCAzLjM3NDkzLDE4LjExOTgzIDIuMjUwMjIsNS42ODQ4NiA1LjUwNzMyLDEwLjcxODk1IDkuNzcwOTIsMTUuMTAxMDMgNC4zODIxLDQuMzgyMDYgOS42NTIzNSw3Ljg3NTMzIDE1LjgxMDk4LDEwLjQ4MDg1IDYuMTU4NTUsMi40ODcxNSAxMy4yMDU3NywzLjczMTA3IDIxLjE0MDkyLDMuNzMxMDcgNy4xMDYxLDAgMTMuNTYwNiwtMS4wNjYyNSAxOS4zNjM4OCwtMy4xOTgwMyA1LjkyMTYyLC0yLjEzMTgzIDExLjAxNDM1LC01LjAzMzUgMTUuMjc3ODcsLTguNzA0OTcgNC4zODIxOCwtMy43ODk5MyA3Ljc1NzMzLC04LjExMjA3IDEwLjEyNjA1LC0xMi45Njc4OCBsIC0yNC4xNjEwMiwtNi43NTA5OSBjIC0xLjQyMTEsMy40MzQ1NiAtNC4wMjU5Myw2LjE1ODYxIC03LjgxNTgzLDguMTcyIC0zLjY3MTQsMi4wMTMzMyAtNy42MzkzNSwzLjAxOTkzIC0xMS45MDI5NSwzLjAxOTkzIC0zLjQzNDYyLDAgLTYuNjkxNzIsLTAuNzEwNzcgLTkuNzcxMDcsLTIuMTMyMDIgLTMuMDc5MiwtMS41Mzk2MyAtNS42MjU1MywtMy43MzA1MiAtNy42Mzg5LC02LjU3Mjk2IC0xLjg5NDk1LC0yLjg0MjM2IC0zLjAxOTU4LC02LjMzNTY0IC0zLjM3NSwtMTAuNDgwODYgaCA2OC45Mjg3NSBjIDAuMTE4NDIsLTEuMTg0MzQgMC4yMzY1NSwtMi41NDYzMSAwLjM1NDk3LC00LjA4NTk0IDAuMjM2NzgsLTEuNjU4MSAwLjM1NDksLTMuMzE3MDEgMC4zNTQ5LC00Ljk3NTA5IDAsLTguNTI3MyAtMS45NTM1MiwtMTYuNDAyMjkgLTUuODYxOTIsLTIzLjYyNjc5IC0zLjkwODMzLC03LjM0Mjg5IC05LjU5MzU1LC0xMy4yNjU1NCAtMTcuMDU0OTMsLTE3Ljc2NiAtNy4zNDI4NywtNC41MDA1MyAtMTYuMjg0MTUsLTYuNzQ5ODcgLTI2LjgyNDgsLTYuNzQ5ODcgeiBtIDExNy4zOTUyNiwwLjUwMDg1IGMgLTUuNTY1OSwwLjExNjk5IC0xMC44OTMzLDEuOTQ4NDYgLTE1Ljk4MzE4LDUuNDk1NDMgLTUuMDk0LDMuNTUwOSAtOS4yMzc3NSw4LjQ1NzE5IC0xMi40MzM3MywxNC43MjMwNyBWIDgxLjM3OTg2IGggLTI2LjExMjUyIHYgOTMuMTI4NjUgaCAyOC40MjE0IHYgLTU1Ljg3ODA4IGMgMi40ODQsLTQuMjU3OTcgNi41MTE3MywtNy40NDkgMTIuMDc3NjMsLTkuNTc4MDEgNS41NjY5NSwtMi4yNDU5NCAxMS43ODAzMiwtMy4zNzE0NyAxOC42NDgzLC0zLjM3MTQ3IFYgODAuMTM4MTIgYyAtMC40NzMwMywtMC4xMTY5OCAtMS4wNjI2LC0wLjE3NTc0IC0xLjc3MzY4LC0wLjE3NTc0IHogbSAtNTA2LjQwNjUsMS40MTc0OCB2IDU5LjYwMTA5IGMgMCwxMS40NzI4OCAyLjQ4ODk5LDIwLjIyMTc5IDcuNDYwOTEsMjYuMjUyNzcgNC45NzI5NCw2LjAzMTg3IDEyLjE5OTgyLDkuMDQ3MjggMjEuNjcxNjcsOS4wNDcyOCA3LjgxMjg3LDAgMTQuODYwNiwtMS40MTgzNyAyMS4xMzc1LC00LjI1ODMzIDYuMzkzOTEsLTIuOTU3IDExLjcyMjMsLTcuNTA2NDYgMTUuOTgzMjMsLTEzLjY1OTM3IGwgMC41MzUzLDUuNjgwNCBjIDAuNzEwOTgsMy43ODA4NyAyLjU0NzEyLDYuNzM3OTkgNS41MDgwOCw4Ljg2Njk1IDIuOTU2OTYsMi4wMTE5MiA2LjkyNTQ1LDMuMDE1MzkgMTEuODk4NCwzLjAxNTM5IDEuNTM4OTYsMCAzLjI1NzAxLC0wLjExNzA5IDUuMTUxOTgsLTAuMzU2MTEgMi4wMTE5NywtMC4yMzQwMiA0LjMxOTYzLC0wLjU4OTM3IDYuOTI1NjEsLTEuMDYxNDIgViAxNTAuOTEzOSBjIC0yLjI0OTk3LC0wLjIzNDAyIC0zLjg0NzQ2LC0wLjg4NjI3IC00Ljc5MjQ0LC0xLjk0ODIyIC0wLjgzMTk5LC0xLjE4NDA1IC0xLjI0NjM2LC0zLjE5NDc5IC0xLjI0NjM2LC02LjAzMDc0IFYgODEuMzc5ODYgaCAtMjguNDE4MDcgdiA1Ny40NzI1NCBjIC0xLjc3Nzk3LDMuMTk0OTcgLTMuNzkyODEsNS43OTY0NCAtNi4wMzg3OCw3LjgwNDM1IC0yLjI0OTk4LDIuMDEyMDQgLTQuNTYyMSwzLjQ4ODQ1IC02LjkyOTA1LDQuNDM3NDcgLTIuMzY2OTcsMC44MjM5OSAtNC44NTQ5NCwxLjIzODM0IC03LjQ2MDkyLDEuMjM4MzQgLTQuMTQzOTIsMCAtNy4zNDA2NiwtMS40NzcwNSAtOS41OTA2MiwtNC40MzQwNiAtMi4yNDk5NywtMy4wNzM5NCAtMy4zNzQ5MiwtNy40NDkyOSAtMy4zNzQ5MiwtMTMuMTI1MjIgViA4MS4zNzk4NiBaIE0gODA0LjY3OTksOTkuODkxNDEgYyAzLjU1Mjk3LDAgNi44MSwwLjgyODkxIDkuNzcwODUsMi40ODY5NSAyLjk2MDkyLDEuNTM5NzEgNS4zMjk1LDMuNzkwMTYgNy4xMDYwMiw2Ljc1MTA0IDEuNzc2NDUsMi44NDIzOCAyLjkwMjI4LDYuMjE3NTYgMy4zNzU5OCwxMC4xMjU5MyBoIC00MC41MDQ3MyBjIDAuNDczNjMsLTMuOTA4MzcgMS41OTk1MywtNy4yODM1NSAzLjM3NTk4LC0xMC4xMjU5MyAxLjc3NjUyLC0yLjk2MDg4IDQuMDg1NDcsLTUuMjExMzMgNi45Mjc5NywtNi43NTEwNCAyLjk2MDkzLC0xLjY1ODA0IDYuMjc2MzgsLTIuNDg2OTUgOS45NDc5MywtMi40ODY5NSB6IG0gLTIzMS40NTY5OSwzLjg0MDE1IGMgMi44NDI5NiwwIDUuNjI0MDQsMC43MDc2NSA4LjM1LDIuMTI4NTkgMi44Mzk5NywxLjQxODAyIDUuMzI4NDgsMy4zMDg4NCA3LjQ1NzQ2LDUuNjc1ODIgMi4yNDk5NywyLjM2Mjk4IDMuOTY5MTYsNS4xNDUxOCA1LjE1MzEzLDguMzM2MjEgdiAxOS41MTA5IGMgLTAuOTQ1OTgsMS44OTM5MyAtMi4xMzI3LDMuNjY4MzEgLTMuNTUwNjYsNS4zMjQzMyAtMS40MjE5OSwxLjY1NTk4IC0zLjAyMDA4LDMuMDE1OTMgLTQuNzk3MDQsNC4wNzc4OSAtMS42NiwxLjA2NzAxIC0zLjQzMzc3LDEuOTUzNTEgLTUuMzI3NzUsMi42NjA0NiAtMS44OTQ5NywwLjU5Mzk3IC0zLjg1MjIxLDAuODg2OCAtNS44NjQxOSwwLjg4NjggLTMuMTk0OTYsMCAtNi4xNTU5MSwtMC41OTA2MiAtOC44ODE4NSwtMS43NzM2IC0yLjcyMjk3LC0xLjE3OTkzIC01LjA5MDU0LC0yLjgzNTc0IC03LjEwMjUyLC00Ljk2NDc1IC0yLjAxNTk2LC0yLjI0NTkzIC0zLjYxMzA0LC00Ljc4ODU4IC00Ljc5NzAzLC03LjYyODYxIC0xLjA2NTk3LC0yLjk1Njk0IC0xLjU5NjcyLC02LjE0OTA4IC0xLjU5NjcyLC05LjU3ODAxIDAsLTMuMzEzIDAuNDcyNjcsLTYuNDQ1MjIgMS40MTg2NywtOS40MDIyMiAxLjA2NTk5LC0yLjk1Njk0IDIuNTQ1OTUsLTUuNTU4NDcgNC40NDA5MywtNy44MDQ0MSAxLjg5Mzk2LC0yLjM2MzkxIDQuMDg1LC00LjE5OTk4IDYuNTcyOTUsLTUuNTAwMDEgMi42MDU5NywtMS4zMDA5NyA1LjQ0NTY3LC0xLjk0OTM5IDguNTI0NjIsLTEuOTQ5MzkgeiBtIDExNi4xNjYxNSwwIGMgMi44NDI5NiwwIDUuNjI1MjMsMC43MDc2NSA4LjM1MTE1LDIuMTI4NTkgMi44Mzk5NiwxLjQxODAyIDUuMzI3MzMsMy4zMDg4NCA3LjQ1NjI5LDUuNjc1ODIgMi4yNSwyLjM2Mjk4IDMuOTY5MDEsNS4xNDUxOCA1LjE1Miw4LjMzNjIxIHYgMTkuNTEwOSBjIC0wLjk0NDk2LDEuODkzOTMgLTIuMTMxNTMsMy42NjgzMSAtMy41NDk1NCw1LjMyNDMzIC0xLjQyMTk3LDEuNjU1OTggLTMuMDIwMDcsMy4wMTU5MyAtNC43OTcwMiw0LjA3Nzg5IC0xLjY1OTk3LDEuMDY3MDEgLTMuNDMzNzYsMS45NTM1MSAtNS4zMjc3NSwyLjY2MDQ2IC0xLjg5NDk4LDAuNTkzOTcgLTMuODUyMjEsMC44ODY4IC01Ljg2NDE4LDAuODg2OCAtMy4xOTQ5NiwwIC02LjE1NjQ5LC0wLjU5MDYyIC04Ljg3ODQyLC0xLjc3MzYgLTIuNzI2OTcsLTEuMTc5OTMgLTUuMDkzOTYsLTIuODM1NzQgLTcuMTA1OTMsLTQuOTY0NzUgLTIuMDE1OTcsLTIuMjQ1OTMgLTMuNjExOSwtNC43ODg1OCAtNC43OTU4OCwtNy42Mjg2MSAtMS4wNjYwMSwtMi45NTY5NCAtMS41OTc4NywtNi4xNDkwOCAtMS41OTc4NywtOS41NzgwMSAwLC0zLjMxMyAwLjQ3MjUxLC02LjQ0NTIyIDEuNDE3NDgsLTkuNDAyMjIgMS4wNjcwMSwtMi45NTY5NCAyLjU0NzEyLC01LjU1ODQ3IDQuNDQyMTIsLTcuODA0NDEgMS44OTM5NCwtMi4zNjM5MSA0LjA4NjEyLC00LjE5OTk4IDYuNTc0MDcsLTUuNTAwMDEgMi42MDU5NCwtMS4zMDA5NyA1LjQ0NDUsLTEuOTQ5MzkgOC41MjM0OCwtMS45NDkzOSB6IgogICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC45OTk5ODUxIgogICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIgLz48L2c+PC9zdmc+"

defaultSmallLogoData : String
defaultSmallLogoData = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjwhLS0gQ3JlYXRlZCB3aXRoIElua3NjYXBlIChodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy8pIC0tPgoKPHN2ZwogICB4bWxuczpkYz0iaHR0cDovL3B1cmwub3JnL2RjL2VsZW1lbnRzLzEuMS8iCiAgIHhtbG5zOmNjPSJodHRwOi8vY3JlYXRpdmVjb21tb25zLm9yZy9ucyMiCiAgIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyIKICAgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIgogICB4bWxuczpzb2RpcG9kaT0iaHR0cDovL3NvZGlwb2RpLnNvdXJjZWZvcmdlLm5ldC9EVEQvc29kaXBvZGktMC5kdGQiCiAgIHhtbG5zOmlua3NjYXBlPSJodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy9uYW1lc3BhY2VzL2lua3NjYXBlIgogICB3aWR0aD0iNTBtbSIKICAgaGVpZ2h0PSI1MG1tIgogICB2aWV3Qm94PSIwIDAgMTc3LjE2NTM1IDE3Ny4xNjUzNSIKICAgaWQ9InN2ZzgzMTQiCiAgIHZlcnNpb249IjEuMSIKICAgaW5rc2NhcGU6dmVyc2lvbj0iMC45Mi41ICgyMDYwZWMxZjlmLCAyMDIwLTA0LTA4KSIKICAgc29kaXBvZGk6ZG9jbmFtZT0ibG9nby1ydWRkZXItc20uc3ZnIj4KICA8ZGVmcwogICAgIGlkPSJkZWZzODMxNiIgLz4KICA8c29kaXBvZGk6bmFtZWR2aWV3CiAgICAgaWQ9ImJhc2UiCiAgICAgcGFnZWNvbG9yPSIjZmZmZmZmIgogICAgIGJvcmRlcmNvbG9yPSIjNjY2NjY2IgogICAgIGJvcmRlcm9wYWNpdHk9IjEuMCIKICAgICBpbmtzY2FwZTpwYWdlb3BhY2l0eT0iMC4wIgogICAgIGlua3NjYXBlOnBhZ2VzaGFkb3c9IjIiCiAgICAgaW5rc2NhcGU6em9vbT0iMS45Nzk4OTkiCiAgICAgaW5rc2NhcGU6Y3g9IjQwLjM2Mjg0NSIKICAgICBpbmtzY2FwZTpjeT0iNjYuNTQ4NDk3IgogICAgIGlua3NjYXBlOmRvY3VtZW50LXVuaXRzPSJweCIKICAgICBpbmtzY2FwZTpjdXJyZW50LWxheWVyPSJsYXllcjEiCiAgICAgc2hvd2dyaWQ9ImZhbHNlIgogICAgIGlua3NjYXBlOnNob3dwYWdlc2hhZG93PSJmYWxzZSIKICAgICBzaG93Ym9yZGVyPSJ0cnVlIgogICAgIGlua3NjYXBlOndpbmRvdy13aWR0aD0iMTg0OCIKICAgICBpbmtzY2FwZTp3aW5kb3ctaGVpZ2h0PSIxMDE2IgogICAgIGlua3NjYXBlOndpbmRvdy14PSI3NTAiCiAgICAgaW5rc2NhcGU6d2luZG93LXk9IjExMDciCiAgICAgaW5rc2NhcGU6d2luZG93LW1heGltaXplZD0iMSIgLz4KICA8bWV0YWRhdGEKICAgICBpZD0ibWV0YWRhdGE4MzE5Ij4KICAgIDxyZGY6UkRGPgogICAgICA8Y2M6V29yawogICAgICAgICByZGY6YWJvdXQ9IiI+CiAgICAgICAgPGRjOmZvcm1hdD5pbWFnZS9zdmcreG1sPC9kYzpmb3JtYXQ+CiAgICAgICAgPGRjOnR5cGUKICAgICAgICAgICByZGY6cmVzb3VyY2U9Imh0dHA6Ly9wdXJsLm9yZy9kYy9kY21pdHlwZS9TdGlsbEltYWdlIiAvPgogICAgICAgIDxkYzp0aXRsZSAvPgogICAgICA8L2NjOldvcms+CiAgICA8L3JkZjpSREY+CiAgPC9tZXRhZGF0YT4KICA8ZwogICAgIGlua3NjYXBlOmxhYmVsPSJDYWxxdWUgMSIKICAgICBpbmtzY2FwZTpncm91cG1vZGU9ImxheWVyIgogICAgIGlkPSJsYXllcjEiCiAgICAgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMCwtODc1LjE5Njg1KSI+CiAgICA8ZwogICAgICAgaWQ9ImcxMiIKICAgICAgIHRyYW5zZm9ybT0ibWF0cml4KDAuNzk1MzM0MjcsMCwwLDAuNzk1MzM0MjcsLTI0LjE5MDE1OCw4NzQuMzA0NDIpIj4KICAgICAgPHBhdGgKICAgICAgICAgZD0ibSAxNDEuNzk3LDE4Ljc3IGMgLTUxLjgzNiwwIC05My44NTYsNDEuOTY0IC05My44NTksOTMuNzI2IDAsMjQuODU5IDkuODksNDguNzAzIDI3LjQ5Miw2Ni4yODEgMTcuNjAxLDE3LjU3OCA0MS40NzIsMjcuNDUzIDY2LjM2NywyNy40NTMgMjQuODkxLC0wLjAwMyA0OC43NjIsLTkuODc4IDY2LjM2MywtMjcuNDU3IDE3LjYwMiwtMTcuNTc4IDI3LjQ4OCwtNDEuNDE4IDI3LjQ4OCwtNjYuMjc3IC0wLjAzOSwtMjMuMjczIC04Ljc1LC00NS42OTkgLTI0LjQzMywtNjIuOTE0IGwgLTYxLjI0Niw2OC4xODQgYyAtMS44NzEsMS44ODIgLTQuMzQ0LDIuODE2IC02LjgwOSwyLjgxNiAtMi40NjUsMCAtNC45NDEsLTAuOTM0IC02LjgxMiwtMi44MTYgLTMuNzY2LC0zLjc1OCAtMy43NjYsLTkuODUyIDAsLTEzLjYxNCBMIDIwNC42MTcsNDMuMDA0IEMgMTg3LjM5OCwyNy40NDUgMTY1LjAxNiwxOC44MDkgMTQxLjc5NywxOC43NyBaIG0gLTE1LjczOCwzNC4zMTIgYyAyLjY5MSw2LjAxMiA4LjcxOCwxMC4xOTkgMTUuNzQyLDEwLjE5OSA3LjAxMSwwIDEzLjA0MywtNC4xODcgMTUuNzM0LC0xMC4xOTkgNS4zOTUsMS40MyAxMC40ODUsMy41NzQgMTUuMTk5LDYuMzEzIC0wLjYwOSwxLjU2NiAtMC45NDUsMy4yMDcgLTEuMDc0LDQuODQzIGwgLTIwLjU3OCwxNy44OTEgYyAtMi45NDksLTAuOTEgLTYuMDU5LC0xLjM4NyAtOS4yODEsLTEuMzg3IC0xNy41MzUsMCAtMzEuODAxLDE0LjIzOCAtMzEuODAxLDMxLjc2NiAwLDE3LjUgMTQuMjY2LDMxLjc1IDMxLjgwMSwzMS43NSAxNy41MjcsMCAzMS43OTcsLTE0LjI1IDMxLjc5NywtMzEuNzUgMCwtMy4zODMgLTAuNTQzLC02LjYyOSAtMS41MTYsLTkuNjggbCAxNy41NDcsLTIwLjE1NiBjIDEuODIsLTAuMDc0IDMuNjEzLC0wLjQxOCA1LjM0NCwtMS4wNTkgMi43NDIsNC42OTIgNC44OSw5Ljc4MiA2LjMxNiwxNS4xNTcgLTYuMDEyLDIuNzAzIC0xMC4yMDMsOC43MjIgLTEwLjIwMywxNS43MzggMCw3LjAwNCA0LjE5MSwxMy4wMTkgMTAuMjAzLDE1LjcwNyAtMS40MjYsNS4zOSAtMy41NzQsMTAuNDczIC02LjMxNiwxNS4xNjggLTYuMTUzLC0yLjMyMSAtMTMuMzY4LC0xLjAzNSAtMTguMzI1LDMuOTEgLTQuOTYsNC45NjEgLTYuMjUzLDEyLjE1MiAtMy45MTQsMTguMzA5IC00LjcxNCwyLjczOCAtOS44MDQsNC44ODYgLTE1LjE5OSw2LjMwOCAtMi42OTEsLTYuMDA0IC04LjcyMywtMTAuMTk1IC0xNS43MzQsLTEwLjE5NSAtNy4wMjQsMCAtMTMuMDUxLDQuMTkxIC0xNS43NDIsMTAuMTk1IC01LjM5OSwtMS40MjIgLTEwLjQ4OSwtMy41NyAtMTUuMTg4LC02LjMwOCAyLjMzNiwtNi4xNTcgMS4wMzUsLTEzLjM0OCAtMy45MywtMTguMzA5IC00LjkzNywtNC45NDUgLTEyLjE2LC02LjIzMSAtMTguMzEyLC0zLjkxIC0yLjc1NCwtNC42OTUgLTQuODg3LC05Ljc3OCAtNi4zMjgsLTE1LjE2OCA2LjAxNSwtMi42ODggMTAuMjExLC04LjcwMyAxMC4yMTEsLTE1LjcwNyAwLC03LjAxNiAtNC4xOTYsLTEzLjAzNSAtMTAuMjExLC0xNS43MjMgMS40NDEsLTUuMzg3IDMuNTc0LC0xMC40OCA2LjMyOCwtMTUuMTcyIDYuMTUyLDIuMzIxIDEzLjM3NSwxLjAzNSAxOC4zMTIsLTMuOTI5IDQuOTY1LC00Ljk0NiA2LjI2NiwtMTIuMTQ1IDMuOTMsLTE4LjI4OSA0LjY5OSwtMi43MzkgOS43ODksLTQuODgzIDE1LjE4OCwtNi4zMTMgeiIKICAgICAgICAgc3R5bGU9ImZpbGw6IzEzYmViNztmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGgxNiIKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIgLz4KICAgIDwvZz4KICA8L2c+Cjwvc3ZnPgo="

logoUrl : Settings -> String
logoUrl settings =
  let
    url = case settings.wideLogo.enable of
      True  -> case settings.wideLogo.data of
        Just d  -> d
        Nothing -> defaultWideLogoData
      False -> defaultWideLogoData
  in
    "url('" ++ url ++ "')"

-- NOTIFICATIONS --


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
    addToast (Toasty.Defaults.Error "Error..." (message ++ " (" ++ getErrorMessage e ++ ")"))


createDecodeErrorNotification : String -> String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createDecodeErrorNotification message e =
    addToast (Toasty.Defaults.Error "Error..." (message ++ " (" ++ e ++ ")"))
