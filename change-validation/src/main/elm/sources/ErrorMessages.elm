module ErrorMessages exposing (decodeErrorDetails, getErrorMessage)

import Http
import Json.Decode exposing (decodeString, string)
import List exposing (drop, head)
import String exposing (join, split)


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


decodeErrorDetails : String -> ( String, String )
decodeErrorDetails json =
    let
        errorMsg =
            decodeString (Json.Decode.at [ "errorDetails" ] string) json

        msg =
            case errorMsg of
                Ok s ->
                    s

                Err e ->
                    "fail to process errorDetails"

        errors =
            split "<-" msg

        title =
            head errors
    in
    case title of
        Nothing ->
            ( "", "" )

        Just s ->
            ( s, join " \n " (drop 1 (List.map (\err -> "\t ‣ " ++ err) errors)) )
