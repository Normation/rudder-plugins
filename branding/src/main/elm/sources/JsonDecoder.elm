module JsonDecoder exposing (..)

import Color exposing (Color)
import DataTypes exposing (Settings, Rgba)
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


decodeSettings : Decoder Settings
decodeSettings =
    succeed Settings
        |> required "displayBar" bool
        |> required "displayLabel" bool
        |> required "labelText" string
        |> required "barColor" decodeColor
        |> required "labelColor" decodeColor
        |> required "enableLogo" bool
        |> required "displayFavIcon" bool
        |> required "displayBigLogo" bool
        |> required "displaySmallLogo" bool
        |> required "displayBarLogin" bool
        |> required "displayLoginLogo" bool
        |> required "displayMotd" bool
        |> required "motd" string
