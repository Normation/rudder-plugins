module Messages exposing (..)


import Model exposing (DataSource, HTTPType, UI)
import Http exposing (Error)
type Msg =
    SelectDataSource DataSource
  | UpdateDataSource DataSource
  | UpdateHTTPData HTTPType
  | UpdateUI UI
  | AddParam
  | AddHeader
  | NewDataSource

  | SaveCall DataSource
  | DeleteCall DataSource

  | GetDataSources (Result Error (List DataSource))
  | SaveDataSource (Result Error DataSource)
  | OpenDeleteModal DataSource
  | DeleteDataSource (Result Error String)