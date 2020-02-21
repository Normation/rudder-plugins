module JsonEncoder exposing (..)

import Color exposing (Color)
import DataTypes exposing (..)
import Json.Encode exposing (..)

--- ENCODERS & DECODERS ---


color : Color -> Value
color c =
  let
    rgb = Color.toRgba c
    data =
      [ ( "red"  , float <| rgb.red   )
      , ( "blue" , float <| rgb.blue  )
      , ( "green", float <| rgb.green )
      , ( "alpha", float <| rgb.alpha )
      ]
  in
    object data

logo : Logo -> Value
logo l =
  let
    logoName =
      case l.name of
        Nothing -> []
        Just n -> [ ("name", string <| n ) ]

    logoData =
      case l.data of
        Nothing -> []
        Just d -> [ ("data", string <| d ) ]

    data = ( "enable", bool   <| l.enable ) :: (List.append logoName logoData)

  in
    object data

encodeSettings : Settings -> Value
encodeSettings settings =
  let
    data =
      [ ( "displayBar"      , bool   <| settings.displayBar      )
      , ( "displayLabel"    , bool   <| settings.displayLabel    )
      , ( "labelText"       , string <| settings.labelTxt        )
      , ( "barColor"        , color  <| settings.bgColorValue    )
      , ( "labelColor"      , color  <| settings.labelColorValue )
      , ( "wideLogo"        , logo   <| settings.wideLogo        )
      , ( "smallLogo"       , logo   <| settings.smallLogo       )
      , ( "displayBarLogin" , bool   <| settings.displayBarLogin )
      , ( "displayMotd"     , bool   <| settings.displayMotd     )
      , ( "motd"            , string <| settings.motd            )
      ]
  in
    object data
