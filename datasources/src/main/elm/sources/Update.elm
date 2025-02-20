module Update exposing (..)

import APICalls exposing (deleteDataSource, saveDataSource)
import Model exposing (..)
import List.Extra
import Messages exposing (..)
import Port exposing (..)
import Utils exposing (errorMessage, nameToId)


newDataSource = DataSource "" "" "" True (5*60) (RunParameters False False (Schedule Scheduled 30)) (HTTP (HTTPType "" GET "" True [] 30 [] ByNode 10 Delete))

update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  let
    baseUi = model.ui
  in
  case msg of
    UpdateUI ui ->
      ({ model | ui = ui}, initTooltips "")

    SelectDataSource dataSource ->
      ({ model | mode = ShowDatasource dataSource (Just dataSource)}, initTooltips "")
    NewDataSource ->
      ({ model | mode = ShowDatasource newDataSource Nothing}, initTooltips "")

    UpdateDataSource dataSource ->
      case model.mode of
        ShowDatasource _ o ->
          ({ model | mode = ShowDatasource dataSource o}, Cmd.none)
        _ ->
          (model,Cmd.none)


    UpdateHTTPData data ->
      case model.mode of
        ShowDatasource d o ->
          ({ model | mode = ShowDatasource {d | type_ = HTTP data} o}, Cmd.none)
        _ ->
          (model,Cmd.none)

    AddHeader ->
      case model.mode of
        ShowDatasource d o ->
          case d.type_ of
            HTTP data ->
              ({ model | ui = { baseUi | newHeader = Header "" "" },  mode = ShowDatasource {d | type_ = HTTP { data | headers = List.append data.headers [ model.ui.newHeader] } } o}, Cmd.none)
        _ ->
          (model,Cmd.none)

    AddParam ->
      case model.mode of
        ShowDatasource d o ->
          case d.type_ of
            HTTP data ->
              ({ model | ui = { baseUi | newParam = Parameter "" "" },  mode = ShowDatasource {d | type_ = HTTP { data | parameters = List.append data.parameters [ model.ui.newParam ] } } o}, Cmd.none)
        _ ->
          (model,Cmd.none)

    SaveCall d ->
      let
         listDatasources = model.dataSources
         id = if(String.isEmpty d.id) then (nameToId d.name) else d.id
         hasError =
            case d.type_ of
              HTTP tpe -> (String.isEmpty d.name) || (String.isEmpty id) || (String.isEmpty tpe.url)
         updated = { d | id = id }
      in
        if(hasError)
        then
          ( model
          , Cmd.batch [ errorNotification ("Please fill missing values") ]
          )
        else

          case model.mode of
            -- Creation scenario
            ShowDatasource _ Nothing ->
                ( { model | dataSources = updated :: listDatasources }
                , Cmd.batch [saveDataSource model True updated, successNotification ("Datasource '"++ updated.name ++"' successfully created") ]
                )
            -- Update scenario
            ShowDatasource _ (Just _) ->
              ( { model | dataSources = if(List.any (\dts -> dts.id == updated.id) listDatasources) then listDatasources else updated :: listDatasources}
              , Cmd.batch [saveDataSource model False updated, successNotification ("Datasource '"++ updated.name ++"' successfully updated") ]
              )
            _ ->
              (model, Cmd.none)


    OpenDeleteModal datasource ->
      let
        shouldDeleteModalOpened =
          case baseUi.deleteModal of
            Just d  -> Nothing
            Nothing -> Just datasource
      in
        ({ model | ui = { baseUi | deleteModal = shouldDeleteModalOpened}}, Cmd.none)

    DeleteCall d ->
      (model, deleteDataSource model d)

    GetDataSources result ->
      case result of
          Ok dataSources ->
            ({ model | dataSources = dataSources}, initTooltips "")
          Err e ->
            (model, errorNotification (errorMessage e))


    SaveDataSource result ->
      case result of
          Ok dataSource ->
            let
              testMode = ShowDatasource dataSource (Just dataSource)
              newMode =
                case model.mode of
                    ShowDatasource d o ->
                      if (d.id == dataSource.id) then
                        ShowDatasource dataSource (Just dataSource)
                      else
                        ShowDatasource d o
                    other -> other
            in
              ({ model | mode = testMode, dataSources = List.Extra.updateIf (.id >> (==) dataSource.id) (always dataSource) model.dataSources}, Cmd.none)
          Err e ->
            (model, errorNotification (errorMessage e))

    DeleteDataSource result ->
      case result of
          Ok id ->
            ( { model
              | mode = Init
              , dataSources = List.filter (.id >> (/=) id)  model.dataSources
              , ui = { baseUi | deleteModal = Nothing}
              }
            , successNotification ("Successfully deleted datasource '" ++ id ++ "'")
            )
          Err e ->
            (model, errorNotification (errorMessage e))


