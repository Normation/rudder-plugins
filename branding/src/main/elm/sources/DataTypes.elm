module DataTypes exposing (..)
import Ui.FileInput as FileInput
import Ui.ColorPicker as ColorPicker
import Http exposing(Error)
import Color exposing(Color)
import Toasty
import Toasty.Defaults

type alias Model =
  { contextPath      : String
  , bgColorPicker    : ColorPicker.Model
  , labelColorPicker : ColorPicker.Model
  , faviconFile      : FileInput.Model
  , wideLogoFile     : FileInput.Model
  , squareLogoFile   : FileInput.Model
  , loginLogoFile    : FileInput.Model
  , settings         : Settings
  , fileData         : FileData
  , toasties         : Toasty.Stack Toasty.Defaults.Toast
  }

type alias FileData = {
    faviconFileData    : String
  , wideLogoFileData   : String
  , squareLogoFileData : String
  , loginLogoFileData  : String
}

type alias Settings =
  --GENERAL
  { displayBar         : Bool
  , displayLabel       : Bool
  , labelTxt           : String
  , bgColorValue       : Color
  , labelColorValue    : Color
  --LOGOS
  , useCustomLogos     : Bool
  , useFavicon         : Bool
  , useWideLogo        : Bool
  , useSquareLogo      : Bool
  --LOGIN
  , displayBarLogin    : Bool
  , useLoginLogo       : Bool
  , displayMotd        : Bool
  , motd               : String
  }



--- UPDATE ---
type Msg =
  -- CUSTOM BAR
    ToggleCustomBar
  | ToggleLabel
  | EditLabelText String
  --LOGOS
  | ToggleCustomLogos
  | ToggleFavicon
  | FaviconFileInput    FileInput.Msg
  | ToggleWideLogo
  | WideLogoFileInput   FileInput.Msg
  | ToggleSquareLogo
  | SquareLogoFileInput FileInput.Msg
  -- LOGIN PAGE
  | ToggleCustomBarLogin
  | ToggleMotd
  | EditMotd String
  | ToggleLoginLogo
  | LoginLogoFileInput  FileInput.Msg
  | BgColorPicker       ColorPicker.Msg
  | TxtColorPicker      ColorPicker.Msg
  -- SAVE
  | GetSettings (Result Error Settings)
  | SaveSettings (Result Error Settings)
  | SendSave
  -- NOTIFICATIONS
  | ToastyMsg         (Toasty.Msg Toasty.Defaults.Toast)
  -- TEST