module Init exposing (..)

import DataTypes exposing (EditMod(..), Model, Msg(..))



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none


initModel : String -> Model
initModel contextPath =
    Model contextPath [] [] [] [] [] [] Off
