module JsonEncoder exposing (..)

import Color exposing (Color)
import DataTypes exposing (..)
import Json.Encode exposing (..)



--- ENCODERS & DECODERS ---


color : Color -> Value
color c =
    let
        rgb =
            Color.toRgba c

        data =
            [ ( "red"  , float <| rgb.red )
            , ( "blue" , float <| rgb.blue )
            , ( "green", float <| rgb.green )
            , ( "alpha", float <| rgb.alpha )
            ]
    in
    object data


encodeSettings : Settings -> Value
encodeSettings settings =
    let
        data =
            [ ( "displayBar", bool <| settings.displayBar )
            , ( "displayLabel", bool <| settings.displayLabel )
            , ( "labelText", string <| settings.labelTxt )
            , ( "barColor", color <| settings.bgColorValue )
            , ( "labelColor", color <| settings.labelColorValue )
            , ( "enableLogo", bool <| settings.useCustomLogos )
            , ( "displayFavIcon", bool <| settings.useFavicon )
            , ( "displayBigLogo", bool <| settings.useWideLogo )
            , ( "displaySmallLogo", bool <| settings.useSquareLogo )
            , ( "displayBarLogin", bool <| settings.displayBarLogin )
            , ( "displayLoginLogo", bool <| settings.useLoginLogo )
            , ( "displayMotd", bool <| settings.displayMotd )
            , ( "motd", string <| settings.motd )
            ]
    in
    object data
