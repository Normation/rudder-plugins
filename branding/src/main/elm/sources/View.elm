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
defaultWideLogoData  = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjwhLS0gQ3JlYXRlZCB3aXRoIElua3NjYXBlIChodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy8pIC0tPgoKPHN2ZwogICB4bWxuczpkYz0iaHR0cDovL3B1cmwub3JnL2RjL2VsZW1lbnRzLzEuMS8iCiAgIHhtbG5zOmNjPSJodHRwOi8vY3JlYXRpdmVjb21tb25zLm9yZy9ucyMiCiAgIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyIKICAgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIgogICB4bWxuczpzb2RpcG9kaT0iaHR0cDovL3NvZGlwb2RpLnNvdXJjZWZvcmdlLm5ldC9EVEQvc29kaXBvZGktMC5kdGQiCiAgIHhtbG5zOmlua3NjYXBlPSJodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy9uYW1lc3BhY2VzL2lua3NjYXBlIgogICB3aWR0aD0iMjAwIgogICBoZWlnaHQ9IjUwIgogICB2aWV3Qm94PSIwIDAgMjAwIDUwLjAwMDAwMSIKICAgaWQ9InN2ZzgwODciCiAgIHZlcnNpb249IjEuMSIKICAgaW5rc2NhcGU6dmVyc2lvbj0iMC45MSByMTM3MjUiCiAgIHNvZGlwb2RpOmRvY25hbWU9ImxvZ28tcnVkZGVyLW5vbG9nby5zdmciPgogIDxkZWZzCiAgICAgaWQ9ImRlZnM4MDg5IiAvPgogIDxzb2RpcG9kaTpuYW1lZHZpZXcKICAgICBpZD0iYmFzZSIKICAgICBwYWdlY29sb3I9IiNmZmZmZmYiCiAgICAgYm9yZGVyY29sb3I9IiM2NjY2NjYiCiAgICAgYm9yZGVyb3BhY2l0eT0iMSIKICAgICBpbmtzY2FwZTpwYWdlb3BhY2l0eT0iMCIKICAgICBpbmtzY2FwZTpwYWdlc2hhZG93PSIyIgogICAgIGlua3NjYXBlOnpvb209IjMuOTU5Nzk4IgogICAgIGlua3NjYXBlOmN4PSIzNi45OTM3MDQiCiAgICAgaW5rc2NhcGU6Y3k9IjQ5LjE4OTEzNiIKICAgICBpbmtzY2FwZTpkb2N1bWVudC11bml0cz0icHgiCiAgICAgaW5rc2NhcGU6Y3VycmVudC1sYXllcj0ibGF5ZXIxIgogICAgIHNob3dncmlkPSJmYWxzZSIKICAgICB1bml0cz0icHgiCiAgICAgaW5rc2NhcGU6d2luZG93LXdpZHRoPSIxODY1IgogICAgIGlua3NjYXBlOndpbmRvdy1oZWlnaHQ9IjEwNTYiCiAgICAgaW5rc2NhcGU6d2luZG93LXg9IjU1IgogICAgIGlua3NjYXBlOndpbmRvdy15PSIyNCIKICAgICBpbmtzY2FwZTp3aW5kb3ctbWF4aW1pemVkPSIxIiAvPgogIDxtZXRhZGF0YQogICAgIGlkPSJtZXRhZGF0YTgwOTIiPgogICAgPHJkZjpSREY+CiAgICAgIDxjYzpXb3JrCiAgICAgICAgIHJkZjphYm91dD0iIj4KICAgICAgICA8ZGM6Zm9ybWF0PmltYWdlL3N2Zyt4bWw8L2RjOmZvcm1hdD4KICAgICAgICA8ZGM6dHlwZQogICAgICAgICAgIHJkZjpyZXNvdXJjZT0iaHR0cDovL3B1cmwub3JnL2RjL2RjbWl0eXBlL1N0aWxsSW1hZ2UiIC8+CiAgICAgICAgPGRjOnRpdGxlPjwvZGM6dGl0bGU+CiAgICAgIDwvY2M6V29yaz4KICAgIDwvcmRmOlJERj4KICA8L21ldGFkYXRhPgogIDxnCiAgICAgaW5rc2NhcGU6bGFiZWw9IkNhbHF1ZSAxIgogICAgIGlua3NjYXBlOmdyb3VwbW9kZT0ibGF5ZXIiCiAgICAgaWQ9ImxheWVyMSIKICAgICB0cmFuc2Zvcm09InRyYW5zbGF0ZSgwLC0xMDAyLjM2MjIpIj4KICAgIDxnCiAgICAgICBpZD0iZzEyNjI3IgogICAgICAgdHJhbnNmb3JtPSJtYXRyaXgoMC4wMTI4OTcyNSwwLDAsMC4wMTI4OTcyNSw2OC4zOTg5NjIsMTA0MS4wNzY0KSI+CiAgICAgIDxwYXRoCiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiCiAgICAgICAgIGQ9Im0gLTIwNjYuNDA3MSwtMTA4NS4zMjI2IGMgOTIuNTk3NCwwIDE2Ni4yOTgsLTIyLjA0MjYgMjIxLjA0NTIsLTY2LjM1MDEgNTQuNzQ3MywtNDQuMjcxIDgyLjE3NzEsLTEwNC4zODg5IDgyLjE3NzEsLTE4MC4yMDE2IGwgMCwtNC4yNDEyIGMgMCwtODAuMTY5NCAtMjYuNzgyLC0xNDAuOTIzNiAtODAuMDM3MiwtMTgyLjQxNzggLTUzLjMzODgsLTQxLjQxNTMgLTEyOC40NzU4LC02Mi4xMDYxIC0yMjUuMjk3MSwtNjIuMTA2MSBsIC0zNzYuODM4NSwwIDAsNDk1LjMxNjggeiBtIC02MzUuMDg2MiwtNzI4LjMxMTIgNjU0LjkxMjQsMCBjIDkyLjI4NzIsMCAxNzQuMTU0NiwxMi45OTEyIDI0NS41NzQ1LDM4Ljc4NzggNzEuMzkwNiwyNS45NDU5IDEzMC44MTI5LDYxLjkyMDIgMTc4LjQwNzEsMTA4LjA2NjkgMzkuMTQ1Niw0MC42Mjk2IDY5LjIyMjksODcuNTI4MSA5MC4yMzE2LDE0MC42OTgzIDIxLjAwOTYsNTMuMjA5NyAzMS41NDIyLDExMS45NzI5IDMxLjU0MjIsMTc2LjI5NTQgbCAwLDQuMjA3NCBjIDAsNjAuMjI3OCAtOC43ODY3LDExNC4zMzg2IC0yNi4yNDcyLDE2Mi43MDE1IC0xNy41NzM1LDQ4LjIxMzcgLTQxLjczNjYsOTAuOTA3NiAtNzIuNDg5NSwxMjcuOTY5IC0zMC43ODE0LDM3LjE3NDEgLTY3LjUwNDgsNjguNjM5ODMgLTExMC4xMTQ0LDk0LjUxMjQzIC00Mi42OTM5LDI1Ljg3MjYgLTg5Ljk0OTksNDUuNzcyIC0xNDEuNzEyMyw1OS43Nzk5IGwgMzk2LjY2NDgsNTU2LjI1OTggLTMwNC4yOTI2LDAgLTM2Mi4xNjYzLC01MTIuMjE0MSAtMzI0LjE3NDYsMCAwLDUxMi4yMTQxIC0yNTYuMTM1NywwIDAsLTE0NjkuMjc4NDMiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmUiCiAgICAgICAgIGlkPSJwYXRoMTQtMyIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSAtMzI5LjcyODY5LC0zMjEuMzAxODcgYyAtOTUuMDc1NCwwIC0xODEuMTk1NCwtMTMuNTkxIC0yNTguMTM0NywtNDAuODkxNCAtNzYuOTY3MiwtMjcuMzM0MyAtMTQyLjc1NDgsLTY4LjIyNTggLTE5Ny4yNzYxLC0xMjIuNzg3MyAtNTQuNjA3MSwtNTQuNTk1MiAtOTYuNTQwMywtMTIyLjExMTIgLTEyNS45NDE5LC0yMDIuNTc5MSAtMjkuMzczLC04MC40MzEzIC00NC4wMTczLC0xNzMuODE3MSAtNDQuMDE3MywtMjgwLjE5NDIgbCAwLC04NDUuODc5OTMgMjU1Ljk5NDEsMCAwLDgzNS4zMjc1MyBjIDAsMTM3LjE2OTcgMzMuMDYyOSwyNDEuNDA2NCA5OS4yNDM2LDMxMi43ODkxIDY2LjA2ODUsNzEuMzQzNCAxNTcuNTk1NiwxMDcuMDUzIDI3NC40MTI3LDEwNy4wNTMgMTE1LjM4MDcsMCAyMDYuMDkxNCwtMzQuMjgxOCAyNzIuMjcyMTAxLC0xMDIuODQ4MyA2Ni4xNTI4LC02OC41MjcxIDk5LjI0MzYsLTE3MC43MzkgOTkuMjQzNiwtMzA2LjQ0MTQgbCAwLC04NDUuODc5OTMgMjU2LjA3OTE5OSwwIDAsODMzLjI2MzMzIGMgMCwxMDkuMTU2NiAtMTUuMDk1NCwyMDUuMDIwOCAtNDUuMTcyNywyODcuNTkyMyAtMzAuMTA1LDgyLjU3MTYgLTcyLjQzMjksMTUxLjQzNjYgLTEyNi45NTUsMjA2LjY3MTIgLTU0LjYwNjM5OSw1NS4zMTA1IC0xMjAuNjc0ODk4OCw5Ni41Nzk0IC0xOTguMzc0MTk5LDEyMy45MTM3IC03Ny43MDAwMDEsMjcuMzAwNCAtMTY0LjcyMDgwMSw0MC44OTE0IC0yNjEuMzczNDAxLDQwLjg5MTQiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmUiCiAgICAgICAgIGlkPSJwYXRoMTYtNiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSAxNDU0LjQzMTEsLTU3Ny4zODkxNyBjIDc2LjkxMDcsMCAxNDYuOTc4NywtMTIuMjQyIDIwOS44NjQ5LC0zNi44NzI3IDYyLjk5ODUsLTI0LjU1NzUgMTE2LjU2MzMsLTU4Ljk5MTQgMTYwLjYzNzEsLTEwMy4yNjI0IDQ0LjA3MzcsLTQ0LjI3MSA3OC4zMTkxLC05Ni45OTA2IDEwMi44MjA1LC0xNTguMDgyOSAyNC40NzI3LC02MS4xMzE2IDM2Ljc1MTIsLTEyNy40ODE4MyAzNi43NTEyLC0xOTkuMTYzMTMgbCAwLC00LjI4MDYgYyAwLC03MS41NzE0IC0xMi4yNzg1LC0xMzguNDA4NyAtMzYuNzUxMiwtMjAwLjE3NjkgLTI0LjUwMTQsLTYxLjg0NDIgLTU4Ljc0NjgsLTExNC44Mjg2IC0xMDIuODIwNSwtMTU5LjA5OTYgLTQ0LjA3MzgsLTQ0LjM4MzYgLTk3LjYzODYsLTc5LjExNiAtMTYwLjYzNzEsLTEwNC4zNDk0IC02Mi44ODYyLC0yNS4zNDYgLTEzMi45NTQyLC0zNy45NjI2IC0yMDkuODY0OSwtMzcuOTYyNiBsIC0yODkuNjQ4MiwwIDAsMTAwMy4yNTAyMyB6IG0gLTU0NS42OTkwOSwtMTIzNi4yNDQ2MyA1NDcuODExMDksMCBjIDExNC43MzMsMCAyMjAuMDMxNSwxOC41MTExIDMxNS45MjQxLDU1LjU3MjUgOTUuODA3NSwzNy4xNzQyIDE3OC4zNzkyLDg4LjU3ODYgMjQ3LjYwMjEsMTU0LjI1MjggNjkuMzM1MSw2NS43ODcgMTIyLjc4NjgsMTQzLjEwMzUgMTYwLjYwODQsMjMyLjA1NjYgMzcuNzY1OCw4OC44NDMzIDU2LjY2MjYsMTg1LjA0NTMgNTYuNjYyNiwyODguNTMzIGwgMCw0LjE2OCBjIDAsMTAzLjU5NzUzIC0xOC44OTY4LDIwMC4xMzc1MyAtNTYuNjYyNiwyODkuNzMyNzMgLTM3LjgyMTYsODkuNDc5OCAtOTEuMjczMywxNjcuMjA3NCAtMTYwLjYwODQsMjMyLjkxODMgLTY5LjIyMjksNjUuODI2NCAtMTUxLjc5NDYsMTE3LjYwNTQgLTI0Ny42MDIxLDE1NS4zNDI3IC05NS44OTI2LDM3Ljc3NjggLTIwMS4xOTExLDU2LjcwMTggLTMxNS45MjQxLDU2LjcwMTggbCAtNTQ3LjgxMTA5LDAgMCwtMTQ2OS4yNzg0MyIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGgxOCIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSAzMzQ3LjcxOTMsLTU3Ny4zODkxNyBjIDc2LjkzOTMsMCAxNDYuOTIzMywtMTIuMjQyIDIwOS44OTMzLC0zNi44NzI3IDYyLjk3LC0yNC41NTc1IDExNi41NjMsLTU4Ljk5MTQgMTYwLjU4LC0xMDMuMjYyNCA0NC4wNDYsLTQ0LjI3MSA3OC4zMiwtOTYuOTkwNiAxMDIuODQ5LC0xNTguMDgyOSAyNC40NzIsLTYxLjEzMTYgMzYuNzUyLC0xMjcuNDgxODMgMzYuNzUyLC0xOTkuMTYzMTMgbCAwLC00LjI4MDYgYyAwLC03MS41NzE0IC0xMi4yOCwtMTM4LjQwODcgLTM2Ljc1MiwtMjAwLjE3NjkgLTI0LjUyOSwtNjEuODQ0MiAtNTguODAzLC0xMTQuODI4NiAtMTAyLjg0OSwtMTU5LjA5OTYgLTQ0LjAxNywtNDQuMzgzNiAtOTcuNjEsLTc5LjExNiAtMTYwLjU4LC0xMDQuMzQ5NCAtNjIuOTcsLTI1LjM0NiAtMTMyLjk1NCwtMzcuOTYyNiAtMjA5Ljg5MzMsLTM3Ljk2MjYgbCAtMjg5LjYxOTcsMCAwLDEwMDMuMjUwMjMgeiBtIC01NDUuNzU0NywtMTIzNi4yNDQ2MyA1NDcuODEwOSwwIGMgMTE0Ljc4OTEsMCAyMjAuMDg3MSwxOC41MTExIDMxNS45NTIxLDU1LjU3MjUgOTUuNzc5LDM3LjE3NDIgMTc4LjQzNSw4OC41Nzg2IDI0Ny43MTQsMTU0LjI1MjggNjkuMTk1LDY1Ljc4NyAxMjIuNzAyLDE0My4xMDM1IDE2MC40OTYsMjMyLjA1NjYgMzcuNzY2LDg4Ljg0MzMgNTYuNjYzLDE4NS4wNDUzIDU2LjY2MywyODguNTMzIGwgMCw0LjE2OCBjIDAsMTAzLjU5NzUzIC0xOC44OTcsMjAwLjEzNzUzIC01Ni42NjMsMjg5LjczMjczIC0zNy43OTQsODkuNDc5OCAtOTEuMzAxLDE2Ny4yMDc0IC0xNjAuNDk2LDIzMi45MTgzIC02OS4yNzksNjUuODI2NCAtMTUxLjkzNSwxMTcuNjA1NCAtMjQ3LjcxNCwxNTUuMzQyNyAtOTUuODY1LDM3Ljc3NjggLTIwMS4xNjMsNTYuNzAxOCAtMzE1Ljk1MjEsNTYuNzAxOCBsIC01NDcuODEwOSwwIDAsLTE0NjkuMjc4NDMiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmUiCiAgICAgICAgIGlkPSJwYXRoMjAiIC8+CiAgICAgIDxwYXRoCiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiCiAgICAgICAgIGQ9Im0gNjk4OC41MDA2LC0xMDg1LjMyMjYgYyA5Mi42MjUsMCAxNjYuMzI2LC0yMi4wNDI2IDIyMS4wNDUsLTY2LjM1MDEgNTQuNzc1LC00NC4yNzEgODIuMTQ5LC0xMDQuMzg4OSA4Mi4xNDksLTE4MC4yMDE2IGwgMCwtNC4yNDEyIGMgMCwtODAuMTY5NCAtMjYuNzI2LC0xNDAuOTIzNiAtODAuMDM3LC0xODIuNDE3OCAtNTMuMzM5LC00MS40MTUzIC0xMjguNDIsLTYyLjEwNjEgLTIyNS4yNywtNjIuMTA2MSBsIC0zNzYuODk0LDAgMCw0OTUuMzE2OCB6IG0gLTYzNS4xMTQsLTcyOC4zMTEyIDY1NC45NjgsMCBjIDkyLjI4OCwwIDE3NC4xNTUsMTIuOTkxMiAyNDUuNTc1LDM4Ljc4NzggNzEuMzA3LDI1Ljk0NTkgMTMwLjc1Nyw2MS45MjAyIDE3OC4zNTEsMTA4LjA2NjkgMzkuMTczLDQwLjYyOTYgNjkuMjIzLDg3LjUyODEgOTAuMjg4LDE0MC42OTgzIDIwLjk4LDUzLjIwOTcgMzEuNDU3LDExMS45NzI5IDMxLjQ1NywxNzYuMjk1NCBsIDAsNC4yMDc0IGMgMCw2MC4yMjc4IC04Ljc1OSwxMTQuMzM4NiAtMjYuMjE5LDE2Mi43MDE1IC0xNy41MTcsNDguMjEzNyAtNDEuNjgsOTAuOTA3NiAtNzIuNDksMTI3Ljk2OSAtMzAuNzI1LDM3LjE3NDEgLTY3LjQ3Niw2OC42Mzk4MyAtMTEwLjE0Miw5NC41MTI0MyAtNDIuNjk0LDI1Ljg3MjYgLTg5LjkyMiw0NS43NzIgLTE0MS42MjgsNTkuNzc5OSBsIDM5Ni42NjUsNTU2LjI1OTggLTMwNC4zNDksMCAtMzYyLjE5NCwtNTEyLjIxNDEgLTMyNC4xNzUsMCAwLDUxMi4yMTQxIC0yNTYuMTA3LDAgMCwtMTQ2OS4yNzg0MyIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGgyMiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSA0OTUxLjMwMjYsLTE1ODAuNjM5NCA4MzEuMjA4LDAgMCwtMjMyLjk5NDQgLTEwODcuMjU4LDAgMCwyMzIuOTk0NCAyNTYuMDUsMCIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGgyNCIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSA0OTUxLjM1OTYsLTU3Ny4zODkxNyAwLC0zOTIuNDMxNyA3MzYuNzIzLDAgMCwtMjMzLjAzMTAzIC05OTIuODMsMCAwLDg1OC40OTY1MyAxMDk3LjczNCwwIDAsLTIzMy4wMzM4IC04NDEuNjI3LDAiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmUiCiAgICAgICAgIGlkPSJwYXRoMjYiIC8+CiAgICA8L2c+CiAgPC9nPgo8L3N2Zz4K"

defaultSmallLogoData : String
defaultSmallLogoData = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjwhLS0gQ3JlYXRlZCB3aXRoIElua3NjYXBlIChodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy8pIC0tPgoKPHN2ZwogICB4bWxuczpkYz0iaHR0cDovL3B1cmwub3JnL2RjL2VsZW1lbnRzLzEuMS8iCiAgIHhtbG5zOmNjPSJodHRwOi8vY3JlYXRpdmVjb21tb25zLm9yZy9ucyMiCiAgIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyIKICAgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIgogICB4bWxuczpzb2RpcG9kaT0iaHR0cDovL3NvZGlwb2RpLnNvdXJjZWZvcmdlLm5ldC9EVEQvc29kaXBvZGktMC5kdGQiCiAgIHhtbG5zOmlua3NjYXBlPSJodHRwOi8vd3d3Lmlua3NjYXBlLm9yZy9uYW1lc3BhY2VzL2lua3NjYXBlIgogICB3aWR0aD0iNTBtbSIKICAgaGVpZ2h0PSI1MG1tIgogICB2aWV3Qm94PSIwIDAgMTc3LjE2NTM1IDE3Ny4xNjUzNSIKICAgaWQ9InN2ZzgzMTQiCiAgIHZlcnNpb249IjEuMSIKICAgaW5rc2NhcGU6dmVyc2lvbj0iMC45MSByMTM3MjUiCiAgIHNvZGlwb2RpOmRvY25hbWU9ImxvZ28tcnVkZGVyLXNtLnN2ZyI+CiAgPGRlZnMKICAgICBpZD0iZGVmczgzMTYiIC8+CiAgPHNvZGlwb2RpOm5hbWVkdmlldwogICAgIGlkPSJiYXNlIgogICAgIHBhZ2Vjb2xvcj0iI2ZmZmZmZiIKICAgICBib3JkZXJjb2xvcj0iIzY2NjY2NiIKICAgICBib3JkZXJvcGFjaXR5PSIxLjAiCiAgICAgaW5rc2NhcGU6cGFnZW9wYWNpdHk9IjAuMCIKICAgICBpbmtzY2FwZTpwYWdlc2hhZG93PSIyIgogICAgIGlua3NjYXBlOnpvb209IjIuOCIKICAgICBpbmtzY2FwZTpjeD0iMC44MjUzNDUxOCIKICAgICBpbmtzY2FwZTpjeT0iNTkuMTkwMDM4IgogICAgIGlua3NjYXBlOmRvY3VtZW50LXVuaXRzPSJweCIKICAgICBpbmtzY2FwZTpjdXJyZW50LWxheWVyPSJsYXllcjEiCiAgICAgc2hvd2dyaWQ9ImZhbHNlIgogICAgIGlua3NjYXBlOnNob3dwYWdlc2hhZG93PSJmYWxzZSIKICAgICBzaG93Ym9yZGVyPSJ0cnVlIgogICAgIGlua3NjYXBlOndpbmRvdy13aWR0aD0iMTg2NSIKICAgICBpbmtzY2FwZTp3aW5kb3ctaGVpZ2h0PSIxMDU2IgogICAgIGlua3NjYXBlOndpbmRvdy14PSI1NSIKICAgICBpbmtzY2FwZTp3aW5kb3cteT0iMjQiCiAgICAgaW5rc2NhcGU6d2luZG93LW1heGltaXplZD0iMSIgLz4KICA8bWV0YWRhdGEKICAgICBpZD0ibWV0YWRhdGE4MzE5Ij4KICAgIDxyZGY6UkRGPgogICAgICA8Y2M6V29yawogICAgICAgICByZGY6YWJvdXQ9IiI+CiAgICAgICAgPGRjOmZvcm1hdD5pbWFnZS9zdmcreG1sPC9kYzpmb3JtYXQ+CiAgICAgICAgPGRjOnR5cGUKICAgICAgICAgICByZGY6cmVzb3VyY2U9Imh0dHA6Ly9wdXJsLm9yZy9kYy9kY21pdHlwZS9TdGlsbEltYWdlIiAvPgogICAgICAgIDxkYzp0aXRsZT48L2RjOnRpdGxlPgogICAgICA8L2NjOldvcms+CiAgICA8L3JkZjpSREY+CiAgPC9tZXRhZGF0YT4KICA8ZwogICAgIGlua3NjYXBlOmxhYmVsPSJDYWxxdWUgMSIKICAgICBpbmtzY2FwZTpncm91cG1vZGU9ImxheWVyIgogICAgIGlkPSJsYXllcjEiCiAgICAgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoMCwtODc1LjE5Njg1KSI+CiAgICA8ZwogICAgICAgdHJhbnNmb3JtPSJtYXRyaXgoMS40NzQzOTQyLDAsMCwxLjQ3NDM5NDIsLTczOS43MTkyNyw4NzguMjk5OTcpIgogICAgICAgaWQ9Imc4MjkzIgogICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MSI+CiAgICAgIDxwYXRoCiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiCiAgICAgICAgIGQ9Im0gNTcwLjk2MjY1LDkxLjY0MTM1MiBjIDMuMDYyNjcsLTAuODEyIDUuOTUzMzQsLTIuMDMyIDguNjI2NjcsLTMuNTg5MzMzIC0xLjMyOCwtMy40OTQ2NjcgLTAuNTg5MzMsLTcuNTg5MzMzIDIuMjI0LC0xMC40MDI2NjYgMi44MTIsLTIuODE0NjY3IDYuOTA4LC0zLjU0OTMzNCAxMC40MDEzMywtMi4yMjUzMzQgMS41NjEzNCwtMi42NzIgMi43ODEzNCwtNS41NjI2NjYgMy41ODgsLTguNjIzOTk5IC0zLjQxMzMzLC0xLjUzMDY2NyAtNS43OTQ2NiwtNC45NTMzMzQgLTUuNzk0NjYsLTguOTM3MzM0IDAsLTMuOTg2NjY2IDIuMzgxMzMsLTcuNDA1MzMzIDUuNzk0NjYsLTguOTM4NjY2IC0wLjgwNjY2LC0zLjA1ODY2NyAtMi4wMjY2NiwtNS45NDkzMzMgLTMuNTg4LC04LjYyMTMzMyAtMC45NzczMywwLjM3MiAtMi4wMDEzMywwLjU2MjY2NyAtMy4wMzQ2NiwwLjYwOTMzMyBsIC05Ljk2MjY3LDExLjQ1MDY2NyBjIDAuNTU0NjcsMS43MzczMzMgMC44NjI2NywzLjU4NCAwLjg2MjY3LDUuNDk5OTk5IDAsOS45NTQ2NjcgLTguMDk3MzQsMTguMDUzMzMzIC0xOC4wNTMzNCwxOC4wNTMzMzMgLTkuOTUzMzMsMCAtMTguMDUyLC04LjA5ODY2NiAtMTguMDUyLC0xOC4wNTMzMzMgMCwtOS45NTU5OTkgOC4wOTg2NywtMTguMDUxOTk5IDE4LjA1MiwtMTguMDUxOTk5IDEuODMyLDAgMy42LDAuMjc2IDUuMjY1MzQsMC43OTA2NjcgbCAxMS42ODgsLTEwLjE3NDY2NyBjIDAuMDcyLC0wLjkzNDY2NiAwLjI2OTMzLC0xLjg2MjY2NiAwLjYwOTMzLC0yLjc1MzMzMyAtMi42NzMzMywtMS41NjEzMzMgLTUuNTY0LC0yLjc4IC04LjYyNjY3LC0zLjU4OTMzMyAtMS41MjkzMywzLjQxNzMzMyAtNC45NTMzMyw1Ljc5NzMzMyAtOC45MzYsNS43OTczMzMgLTMuOTg0LDAgLTcuNDA2NjYsLTIuMzggLTguOTM0NjYsLTUuNzk3MzMzIC0zLjA2MTM0LDAuODA5MzMzIC01Ljk1MzM0LDIuMDI4IC04LjYyNCwzLjU4OTMzMyAxLjMyNCwzLjQ5MDY2NiAwLjU4OCw3LjU5MDY2NiAtMi4yMjY2NywxMC40MDUzMzMgLTIuODEyLDIuODEyIC02LjkwOCwzLjU0OTMzMyAtMTAuNDA0LDIuMjI0IC0xLjU2LDIuNjcyIC0yLjc3NDY3LDUuNTYyNjY2IC0zLjU4OCw4LjYyNCAzLjQxNiwxLjUzMDY2NiA1Ljc5NDY3LDQuOTQ5MzMzIDUuNzk0NjcsOC45MzU5OTkgMCwzLjk4NjY2NyAtMi4zNzg2Nyw3LjQwNjY2NyAtNS43OTQ2Nyw4LjkzNzMzNCAwLjgxMzMzLDMuMDYxMzMzIDIuMDI5MzMsNS45NTE5OTkgMy41ODgsOC42MjM5OTkgMy40OTYsLTEuMzI0IDcuNTk0NjcsLTAuNTg5MzMzIDEwLjQwNCwyLjIyNTMzNCAyLjgxNDY3LDIuODEzMzMzIDMuNTUzMzMsNi45MDc5OTkgMi4yMjY2NywxMC40MDI2NjYgMi42NzA2NiwxLjU1NzMzMyA1LjU2MjY2LDIuNzc3MzMzIDguNjI0LDMuNTg5MzMzIDEuNTI4LC0zLjQxNzMzMyA0Ljk1MDY2LC01Ljc5NiA4LjkzNDY2LC01Ljc5NiAzLjk4MjY3LDAgNy40MDY2NywyLjM3ODY2NyA4LjkzNiw1Ljc5NiIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGg1MiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSA1NTguODg2NjUsNTMuMTE3MzUzIGMgLTIuMTM4NjYsMi4xMzczMzQgLTIuMTM4NjYsNS42MDI2NjcgMCw3Ljc0IDEuMDY2NjcsMS4wNjggMi40NjUzNCwxLjYwMTMzMyAzLjg2OCwxLjYwMTMzMyAxLjM5ODY3LDAgMi44MDI2NywtMC41MzMzMzMgMy44NjgsLTEuNjAxMzMzIGwgMzkuMDczMzMsLTQ0LjY2Mzk5OSBjIDAuNTkzMzQsLTAuNTkzMzMzIDAuNTkzMzQsLTEuNTU0NjY2IDAsLTIuMTQ4IC0wLjU5MzMzLC0wLjU5MzMzMyAtMS41NTYsLTAuNTkzMzMzIC0yLjE0OCwwIGwgMCwwIC00NC42NjEzMywzOS4wNzE5OTkiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmUiCiAgICAgICAgIGlkPSJwYXRoNTQiIC8+CiAgICAgIDxwYXRoCiAgICAgICAgIGlua3NjYXBlOmNvbm5lY3Rvci1jdXJ2YXR1cmU9IjAiCiAgICAgICAgIGQ9Im0gNTEwLjM2OTMyLDQ5LjMyNDAyIDYuMDg4LDYuMjk2IGMgMS4yNDkzMywtMjQuMDg2NjY2IDIxLjE3MiwtNDMuMjMwNjY2IDQ1LjU2OCwtNDMuMjMwNjY2IDEwLjMzNzMzLDAgMTkuODY4LDMuNDQxMzM0IDI3LjUyLDkuMjM0NjY3IGwgOS41NzczMywtOC4yMTczMzMgQyA1ODkuMDY1MzIsNS4wMzYwMjEyIDU3Ni4xMzU5OSwtMC4wMDM5Nzg3MiA1NjIuMDI1MzIsLTAuMDAzOTc4NzIgYyAtMzEuMzE4NjcsMCAtNTYuODI1MzMsMjQuODE0NjY1NzIgLTU3Ljk2OCw1NS44NTQ2NjU3MiBsIDYuMzEyLC02LjUyNjY2NyIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGg1NiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaW5rc2NhcGU6Y29ubmVjdG9yLWN1cnZhdHVyZT0iMCIKICAgICAgICAgZD0ibSA1NTMuMzMwNjUsMTA5LjY3NjAyIDYuMjk0NjcsLTYuMDg4IGMgLTIxLjkyMjY3LC0xLjEzNiAtMzkuNzQyNjcsLTE3Ljc0NTMzNCAtNDIuNzY4LC0zOS4xMTYgbCAtNi40ODgsLTYuNzA4IC02LjA1ODY3LDYuMjY0IGMgMi45MzQ2NywyOC41MTQ2NjYgMjYuNTE4NjcsNTAuODkzMzMgNTUuNTQ4LDUxLjk2MTMzIGwgLTYuNTI4LC02LjMxMzMzIgogICAgICAgICBzdHlsZT0iZmlsbDojZmZmZmZmO2ZpbGwtb3BhY2l0eToxO2ZpbGwtcnVsZTpub256ZXJvO3N0cm9rZTpub25lIgogICAgICAgICBpZD0icGF0aDU4IiAvPgogICAgICA8cGF0aAogICAgICAgICBpbmtzY2FwZTpjb25uZWN0b3ItY3VydmF0dXJlPSIwIgogICAgICAgICBkPSJtIDYwNy41OTQ2NSw2MC40MjEzNTMgYyAtMS4xMzczMywyMS45MjEzMzMgLTE3Ljc0OCwzOS43NDEzMzcgLTM5LjExNzMzLDQyLjc2Nzk5NyBsIC02LjcwNjY3LDYuNDg2NjcgNi4yNjQsNi4wNTg2NyBjIDI4LjUxNiwtMi45MzMzNCA1MC44OTA2NywtMjYuNTE2MDA0IDUxLjk2LC01NS41NDUzMzcgbCAtMTIuNCwwLjIzMiIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZSIKICAgICAgICAgaWQ9InBhdGg2MCIgLz4KICAgIDwvZz4KICA8L2c+Cjwvc3ZnPgo="

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
