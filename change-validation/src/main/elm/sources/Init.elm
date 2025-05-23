module Init exposing (..)

import DataTypes exposing (EditMod(..), Model, Msg(..), ViewState(..))



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions _ =
    Sub.none


initModel : String -> Bool -> Model
initModel contextPath adminWrite =
    Model contextPath [] [] [] [] [] [] Off adminWrite False NoView
