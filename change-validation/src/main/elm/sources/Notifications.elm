port module Notifications exposing (..)

------------------------------
-- PORTS
------------------------------


port successNotification : String -> Cmd msg


port errorNotification : String -> Cmd msg
