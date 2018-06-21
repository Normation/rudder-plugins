module JsonDecoder exposing (..)
import Json.Decode exposing (..)
import Json.Decode.Pipeline exposing(..)
import DataTypes exposing (Settings)
import Color exposing (rgba, Color)
import Ext.Color exposing (Rgba)


rgbaToColor : Rgba -> Color
rgbaToColor color =
  rgba color.red color.green color.blue color.alpha

decodeRgba : Decoder Rgba
decodeRgba =
  decode Rgba
    |> required "alpha" float
    |> required "green" int
    |> required "blue"  int
    |> required "red"   int


decodeColor : Decoder Color
decodeColor =
  map rgbaToColor decodeRgba

decodeApiSettings : Decoder Settings
decodeApiSettings =
  at ["data", "branding" ] decodeSettings

decodeSettings : Decoder Settings
decodeSettings =
  decode Settings
    |> required "displayBar"       bool
    |> required "displayLabel"     bool
    |> required "labelText"        string
    |> required "barColor"         decodeColor
    |> required "labelColor"       decodeColor
    |> required "enableLogo"       bool
    |> required "displayFavIcon"   bool
    |> required "displayBigLogo"   bool
    |> required "displaySmallLogo" bool
    |> required "displayBarLogin"  bool
    |> required "displayLoginLogo" bool
    |> required "displayMotd"      bool
    |> required "motd"             string
