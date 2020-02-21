module DataTypes exposing (..)

import Color exposing (Color)
import Http exposing (Error)
import Toasty
import Toasty.Defaults
import ColorPicker
import File exposing (File)
import File.Select as Select

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
  , toasties         : Toasty.Stack Toasty.Defaults.Toast
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
  --, wideLogoEnable    : Bool
  --, wideLogoData      : String
  --, smallLogoEnable   : Bool
  --, smallLogoData     : String
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
      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)