module DataTypes exposing (..)

import Color exposing (Color)
import Http exposing (Error)
import ColorPicker
import File exposing (File)

type alias Rgba =
  { red   : Float
  , green : Float
  , blue  : Float
  , alpha : Float
  }

type alias Model =
  { contextPath      : String
  , bgColorPicker    : ColorPicker.State
  , labelColorPicker : ColorPicker.State
  , settings         : Settings
  , hover            : Bool
  }

type alias Logo =
  { enable  : Bool
  , name    : Maybe String
  , data    : Maybe String
  }

type alias Settings =
  --GENERAL
  { displayBar      : Bool
  , displayLabel    : Bool
  , labelTxt        : String
  , bgColorValue    : Color
  , labelColorValue : Color

  --LOGOS
  , wideLogo        : Logo
  , smallLogo       : Logo

  --LOGIN
  , displayBarLogin : Bool
  , displayMotd     : Bool
  , motd            : String
  }

type alias CssObj =
  { bgColor           : String
  , txtColor          : String
  , labelTxt          : String
  }

--- UPDATE ---

type Msg =
    -- LOGOS
    ToggleLogo String
    | UploadFile String
    | RemoveFile String
    | GotFile String File
    | GotPreviewWideLogo String
    | GotPreviewSmallLogo String
    -- CUSTOM BAR
    | ToggleCustomBar
    | ToggleLabel
    | EditLabelText String
      -- LOGIN PAGE
    | ToggleCustomBarLogin
    | ToggleMotd
    | EditMotd String
    | BgColorPicker ColorPicker.Msg
    | TxtColorPicker ColorPicker.Msg
      -- SAVE
    | GetSettings (Result Error Settings)
    | SaveSettings (Result Error Settings)
    | SendSave