module ErrorMessages exposing (..)

import Http


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

                Http.BadBody c ->
                    "Wrong content in request body" ++ c
    in
    errMessage
