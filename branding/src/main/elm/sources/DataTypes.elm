module DataTypes exposing (..)

import Color exposing (Color)
import Http exposing (Error)
import Toasty
import Toasty.Defaults
import ColorPicker

type alias Rgba =
  { red   : Float
  , green : Float
  , blue  : Float
  , alpha : Float
  }
type alias Model =
    { contextPath : String
    , bgColorPicker : ColorPicker.State
    , labelColorPicker : ColorPicker.State
    , settings : Settings
    , fileData : FileData
    , toasties : Toasty.Stack Toasty.Defaults.Toast
    }


type alias FileData =
    { faviconFileData : String
    , wideLogoFileData : String
    , squareLogoFileData : String
    , loginLogoFileData : String
    }


type alias Settings =
    --GENERAL
    { displayBar : Bool
    , displayLabel : Bool
    , labelTxt : String
    , bgColorValue : Color
    , labelColorValue : Color

    --LOGOS
    , useCustomLogos : Bool
    , useFavicon : Bool
    , useWideLogo : Bool
    , useSquareLogo : Bool

    --LOGIN
    , displayBarLogin : Bool
    , useLoginLogo : Bool
    , displayMotd : Bool
    , motd : String
    }



--- UPDATE ---


type Msg
    = -- CUSTOM BAR
      ToggleCustomBar
    | ToggleLabel
    | EditLabelText String
      -- LOGIN PAGE
    | ToggleCustomBarLogin
    | ToggleMotd
    | EditMotd String
    | ToggleLoginLogo
   -- | LoginLogoFileInput FileInput.Msg
    | BgColorPicker ColorPicker.Msg
    | TxtColorPicker ColorPicker.Msg
      -- SAVE
    | GetSettings (Result Error Settings)
    | SaveSettings (Result Error Settings)
    | SendSave
      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)



-- TEST
