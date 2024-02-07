port module Port exposing (..)

port errorNotification   : String -> Cmd msg
port successNotification : String -> Cmd msg
port initTooltips        : String -> Cmd msg