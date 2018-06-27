module JsonEncoder exposing (..)
import Json.Encode exposing (..)
import DataTypes exposing (..)
import Color exposing (Color, toRgb)

--- ENCODERS & DECODERS ---

color : Color -> Value
color color =
  let
    rgb = toRgb color
    data = [
        ( "red"  , int   <| rgb.red   )
      , ( "blue" , int   <| rgb.blue  )
      , ( "green", int   <| rgb.green )
      , ( "alpha", float <| rgb.alpha )
    ]
  in
    object data


encodeSettings : Settings -> Value
encodeSettings settings =
  let
    data =
    [ ("displayBar"       , bool   <| settings.displayBar      )
    , ("displayLabel"     , bool   <| settings.displayLabel    )
    , ("labelText"        , string <| settings.labelTxt        )
    , ("barColor"         , color  <| settings.bgColorValue    )
    , ("labelColor"       , color  <| settings.labelColorValue )
    , ("enableLogo"       , bool   <| settings.useCustomLogos  )
    , ("displayFavIcon"   , bool   <| settings.useFavicon      )
    , ("displayBigLogo"   , bool   <| settings.useWideLogo     )
    , ("displaySmallLogo" , bool   <| settings.useSquareLogo   )
    , ("displayBarLogin"  , bool   <| settings.displayBarLogin )
    , ("displayLoginLogo" , bool   <| settings.useLoginLogo    )
    , ("displayMotd"      , bool   <| settings.displayMotd     )
    , ("motd"             , string <| settings.motd            )
    ]
  in
    object data



















