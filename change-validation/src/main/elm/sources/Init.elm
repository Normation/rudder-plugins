port module Init exposing (..)

import DataTypes exposing (EditMod(..), Model, Msg(..))
import Http

------------------------------
-- PORTS
------------------------------


port successNotification : String -> Cmd msg
port errorNotification : String -> Cmd msg



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
  Sub.none

initModel : String -> Model
initModel contextPath  =
  Model contextPath [] [] [] [] [] [] Off

getErrorMessage : Http.Error -> String
getErrorMessage e =
    let
        errMessage =
            case e of
                Http.BadStatus status ->
                    "Code " ++ String.fromInt status

                Http.BadUrl str ->
                    "Invalid API url"

                Http.Timeout ->
                    "It took too long to get a response"

                Http.NetworkError ->
                    "Network error"
                Http.BadBody c->
                  "Wrong content in request body" ++ c

    in
    errMessage