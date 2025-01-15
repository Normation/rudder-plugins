module Model exposing (..)

type alias Flags =
  { contextPath: String
  , hasWriteRights : Bool
  }


type ScheduleType = Scheduled | NotScheduled

type alias Schedule =
  { type_ : ScheduleType
  , duration : Int
  }

type OnMissing = Delete | Default String | NoChange

type alias UI =
  { newParam : Parameter
  , newHeader : Header
  , openParameter : Bool
  , openAdvanced : Bool
  , openTrigger : Bool
  , openNode : Bool
  , openHeader : Bool
  , deleteModal : Maybe DataSource
  }


type alias RunParameters =
    { onGeneration : Bool
    , onNewNode : Bool
    , schedule : Schedule
    }

type Mode = Init | ShowDatasource DataSource (Maybe DataSource)

type Type = HTTP HTTPType

type HTTPMethod = GET | POST

type RequestMode = ByNode | AllNodes String String

type alias Parameter =
    { name : String
    , value : String
    }

type alias Header =
    { name : String
    , value : String
    }

type alias HTTPType =
    { url : String
    , method : HTTPMethod
    , path: String
    , checkSsl : Bool
    , parameters : List Parameter
    , requestTimeout : Int
    , headers : List Header
    , requestMode : RequestMode
    , maxParallelRequest : Int
    , onMissing : OnMissing
    }

type alias DataSource =
    { id : String
    , name : String
    , description : String
    , enabled : Bool
    , updateTimeout : Int
    , runParameters : RunParameters
    , type_ : Type
    }


type alias Model =
    { dataSources : List DataSource
    , hasWriteRights : Bool
    , ui : UI
    , mode : Mode
    , contextPath : String
    }

