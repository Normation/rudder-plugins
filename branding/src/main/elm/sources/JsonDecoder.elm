module JsonDecoder exposing (..)

import Color exposing (Color)
import DataTypes exposing (Settings, Rgba, Logo)
import Json.Decode exposing (..)
import Json.Decode.Pipeline exposing (..)

decodeRgba : Decoder Rgba
decodeRgba =
    succeed Rgba
        |> required "red" float
        |> required "green" float
        |> required "blue" float
        |> required "alpha" float


decodeColor : Decoder Color
decodeColor =
    map Color.fromRgba decodeRgba


decodeApiSettings : Decoder Settings
decodeApiSettings =
    at [ "data", "branding" ] decodeSettings

decodeLogo : Decoder Logo
decodeLogo =
  succeed Logo
    |> required "enable" bool
    |> optional "name" (Json.Decode.map Just string) Nothing
    |> optional "data" (Json.Decode.map Just string) Nothing

decodeSettings : Decoder Settings
decodeSettings =
    succeed Settings
        |> required "displayBar"       bool
        |> required "displayLabel"     bool
        |> required "labelText"        string
        |> required "barColor"         decodeColor
        |> required "labelColor"       decodeColor
        |> required "wideLogo"         decodeLogo
        |> required "smallLogo"        decodeLogo
        |> required "displayBarLogin"  bool
        |> required "displayMotd"      bool
        |> required "motd"             string
