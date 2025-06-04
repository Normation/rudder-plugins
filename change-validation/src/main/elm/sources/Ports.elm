port module Ports exposing (..)

------------------------------
-- PORTS
------------------------------


port successNotification : String -> Cmd msg


port errorNotification : String -> Cmd msg


port readUrl : (String -> msg) -> Sub msg


port copyToClipboard : String -> Cmd msg
