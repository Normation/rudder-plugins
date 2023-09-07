module Utils exposing (..)


import Http exposing (Error)
import Model exposing (ScheduleType(..))
import Regex exposing (Regex)

nameToId : String -> String
nameToId name =
  Regex.replace (regexFromString "[^\\-_a-zA-Z0-9]") (always "_") name

regexFromString : String -> Regex
regexFromString =
      Regex.fromString >> Maybe.withDefault Regex.never

scheduleString : ScheduleType -> String
scheduleString schedule =
  case schedule of
    Scheduled -> "scheduled"
    NotScheduled -> "notscheduled"

errorMessage: Error -> String
errorMessage e =
    case e of
        Http.BadStatus x -> case x of
                            502 -> "Request timeout. It happens with long request, like CVE database update.\n You can check if it finished in webapp logs"
                            _   -> "Received a "++ (String.fromInt x) ++ " response"
        Http.BadBody body -> "Unexpected content received: "++ body
        Http.Timeout -> "Request timeout"
        Http.BadUrl url -> "Wrong url: " ++ url
        Http.NetworkError -> "Network error"