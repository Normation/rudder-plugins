module DataSourceForm exposing (..)


import Model exposing (..)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onCheck, onClick, onInput)
import String.Extra
import List.Extra
import Messages exposing (..)
import Utils exposing (nameToId)


datasourceForm : Model -> DataSource -> Maybe DataSource -> Html Msg
datasourceForm model datasource origin =
  let

    baseForm = [
              div [ class "form-group" ] [
                label [ for "name" ] [ text "Name" ]
              , input [ type_ "text", class "form-control", id "name", value datasource.name, name "datasourceName", onInput (\s -> UpdateDataSource { datasource | name = s}) ] [] --, required ng-model "selectedDatasource.name", ng-change "updateKeyName(selectedDatasource.name)" ] [
              , nameError
              ]
            , div [ class "form-group" ] [
                label [ for "key"] [ text "Key name" ]
              , idWarning
              , input [ type_ "text", class "form-control has-example-help", id "key", placeholder (nameToId datasource.name), name "datasourceKey", value datasource.id, disabled (not isNew), onInput (\s -> UpdateDataSource { datasource | id = s}) ] []
              , div [ class "example-help" ] [
                  text "The key name will be used to access imported data, such as "
                , pre [] [ text ("${node.properties["++ (if String.isEmpty datasource.id then "\"key_name\"" else  datasource.id )++"]") ]
                ]
              , span [ class "text-danger" ] [ idError ]
              ]
            , div [ class "form-group" ] [
                label [ for "description", class "optional"] [ text "Description" ]
              , textarea [ class "form-control", id "description", value datasource.description , onInput (\s -> UpdateDataSource { datasource | description = s})] []
              ]
            , div [ class "form-group" ] [
                label [ class "bloc"] [ text "Type" ]
              , div [ class "btn-group" ] [
                  label [ class "btn btn-default active disabled" ] [ text "HTTP" ]
                ]
              ]
              ]
    enabled = datasource.enabled
    actionButtons =
        if model.hasWriteRights then
        [
                        div [ class "btn-group" ] [
                          if (isNew) then text ""
                          else
                          button [ type_ "button", class "btn btn-danger" ] [--, ng-click "deleteDatasource()", ng-if "!selectedDatasource.isNew",
                            text "Delete "
                          , i [ class "fa fa-times-circle" ] []
                          ]
                        ]
                      , div [ class "btn-group" ] [
                          if (isNew) then text ""
                          else
                          button [ type_ "button", class "btn btn-default", onClick (SaveCall { datasource | enabled = not enabled } ) ] [
                            text (if datasource.enabled then "Disable" else "Enable")
                          , i [ class ("fa " ++ (if datasource.enabled then "fa-ban" else "fa-circle-o")) ] []
                        ]
                      , div [ class "btn-group pull-right" ] [
                            case origin of
                              Nothing -> text ""
                              Just o ->

                                button [ type_ "button", class "btn btn-default", onClick (UpdateDataSource o) ] [
                                  text "Reset "
                                , i [class "fa fa-rotate-left"] []
                                ]
                          , button [ type_ "button", class "btn btn-success", onClick (SaveCall datasource) ] [
                              text "Save "
                            , i [ class "fa fa-download" ] []
                            ]
                        ]
                      ]
        ] else
          []

    headerDescription = if String.isEmpty datasource.description then text "" else p [] [ text datasource.description ]

    isNew = case origin of
              Nothing -> True
              Just o -> False
    (nameTitle, attributeTitle) = if isNew
                                    then ("New data source", [style "opacity"  "0.4"] )
                                  else
                                   if String.isEmpty datasource.name
                                     then ("Unnamed", [ style "font-style" "italic", style "color" "#777"])
                                     else (datasource.name, [])

    idWarning =
      if isNew then
        div [ class "group-warning" ] [
          span [ class "fa fa-info-circle"] []
          , text "Key name cannot be modified after creation."
        ]
      else
        text ""

    nameError =
      if (not isNew && String.isEmpty datasource.name)
      then
        span [ class "text-danger"] [
          text "Name is required"
        ]
      else
        text ""

    idError =
      if (not isNew && String.isEmpty datasource.id)
      then
        span [ class "text-danger"] [
          text "Key is required"
        ]
      else
        text ""

    forms = List.concat [ baseForm,  typeForm model.ui datasource ]
  in
        div [ class "main-container datasource-bloc" ] [
          div [ class "main-header" ] [
            div [ class "header-title" ] [
              h1 [] [
                span attributeTitle [ text nameTitle ]
              , if (datasource.enabled) then text "" else span [ class "badge-disabled" ] []
              ]

            , div [ class "header-buttons" ] actionButtons
            ]
          , div [ class "header-description" ] [ headerDescription ]
          ]

        , div [ class "main-navbar"] [
            ul [ class "ui-tabs-nav " ] []
          ]

        , div [ class "main-details" ] [
            Html.form [ class "bloc-body show-details", name "forms.datasourceForm" ]
              forms
          ]
        ]



httpMethodForm : HTTPType -> Html Msg
httpMethodForm httpData =
  div [ class "form-group" ] [
    label [ class "bloc"] [ text "Method" ]
  , div [ class "btn-group" ] [
      label [ class ("btn btn-default btn-radio " ++ if (httpData.method == GET) then "active" else "")  , for "get" ] [
        input [ type_ "radio", id "get", onClick (UpdateHTTPData {httpData | method = GET}), checked (httpData.method == GET) ] []
      , text " GET"
      ]
    , label [ class ("btn btn-default btn-radio " ++ if (httpData.method == POST) then "active" else ""), for "post" ] [
        input [ type_ "radio", id "post", onClick (UpdateHTTPData {httpData | method = POST}), checked (httpData.method == POST) ] []
      , text " POST"
      ]
    ]
  ]

urlForm: HTTPType -> Html Msg
urlForm httpData =

            div [ class "form-group clearfix" ] [
                label [ for "url"] [ text "URL" ]
              , input [ type_ "text", class "form-control has-example-help", id "url", value httpData.url, onInput (\s -> UpdateHTTPData { httpData | url = s}) ] []
              , div [ class "example-help" ] [
                  text "You can use Rudder variable expansion ("
                , pre [] [ text "${rudder.node.xxx}, ${rudder.param.xxx}, ${node.properties[xxx]}"]
                , text ") here. They will be replaced by their values for each node at the time the HTTP query is run."
                ]
              ]

postForm : UI -> HTTPType -> Html Msg
postForm ui httpData =
  let
    noParamWarning =
     if (List.isEmpty httpData.parameters) then
       div [ class "group-warning" ] [
         span [ class "fa fa-info-circle" ] []
       , text "No parameters defined for this data source."
       ]
     else text ""
    paramForm = (
      \index p ->
        div [ class "input-group group-header"] [
          input [ type_ "text", class "form-control", value p.name
                , onInput (\s -> UpdateHTTPData {httpData | parameters = List.Extra.updateAt index (\h -> {h | name = s }) httpData.parameters })
                ] []
        , span [ class "input-group-addon"] [ text ":" ]
        , input [ type_ "text", class "form-control", value p.value
                , onInput (\s -> UpdateHTTPData {httpData | parameters = List.Extra.updateAt index (\h -> {h | value = s }) httpData.parameters })
                ] []
        , span [ class "input-group-btn" ] [
            button [ class "btn btn-danger", onClick (UpdateHTTPData {httpData | parameters = List.Extra.removeAt index httpData.parameters }) ] [
              span [ class "fa fa-minus"]  []
            ]
          ]
        ]
      )

    param = ui.newParam
    newParam =
      div [ class "new-headers" ] [
        div [ class "bs-callout bs-callout-info text-info"] [
          text "Don't forget to click on the green plus to add the new parameter!"
        ]
      , div [ class "input-group group-header" ] [
          input [ type_ "text", class "form-control", value param.name, placeholder "name"
                , onInput (\s -> UpdateUI {ui | newParam = { param | name = s} })
                ] []
        , span [ class "input-group-addon"] [ text ":" ]
        , input [ type_ "text", class "form-control", value param.value, placeholder "value"
                , onInput (\s -> UpdateUI {ui | newParam = { param | value = s} })
                ] []
        , span [ class "input-group-btn" ] [
             button [ class "btn btn-success", onClick AddParam] [
               span [ class "fa fa-plus" ] []
             ]
           ]
         ]
       ]

    params = List.concat [ [noParamWarning], (List.indexedMap paramForm httpData.parameters), [newParam] ]
    opened = ui.openParameter

  in
    div [ class "form-group"] [
                label [] [
                  a [onClick (UpdateUI {ui | openParameter = not opened})] [
                    text "Parameters"
                  , span [ class "fa fa-chevron-right"] []
                  ]
                ]
              , if opened then div [ class "well"] params else text ""
              ]

headersForm : UI -> HTTPType -> Html Msg
headersForm ui httpData =
  let
    noHeadersWarning =
     if (List.isEmpty httpData.headers) then
       div [ class "group-warning" ] [
         span [ class "fa fa-info-circle" ] []
       , text "No Headers defined for this data source."
       ]
     else text ""
    headerForm = (
      \index param ->
        div [ class "input-group group-header"] [
          input [ type_ "text", class "form-control", value param.name
                , onInput (\s -> UpdateHTTPData {httpData | headers = List.Extra.updateAt index (\h -> {h | name = s }) httpData.headers })
                ] []
        , span [ class "input-group-addon"] [ text ":" ]
        , input [ type_ "text", class "form-control", value param.value
                , onInput (\s -> UpdateHTTPData {httpData | headers = List.Extra.updateAt index (\h -> {h | value = s }) httpData.headers })
                ] []
        , span [ class "input-group-btn" ] [
            button [ class "btn btn-danger", onClick (UpdateHTTPData {httpData | headers = List.Extra.removeAt index httpData.headers }) ] [
              span [ class "fa fa-minus"]  []
            ]
          ]
        ]
      )

    header = ui.newHeader
    newHeader =
      div [ class "new-headers" ] [
        div [ class "bs-callout bs-callout-info text-info"] [
          text "Don't forget to click on the green plus to add the new parameter!"
        ]
      , div [ class "input-group group-header" ] [
          input [ type_ "text", class "form-control", value header.name, placeholder "name"
                , onInput (\s -> UpdateUI {ui | newHeader = { header | name = s} })
                ] []
         , span [ class "input-group-addon"] [ text ":" ]
         , input [ type_ "text", class "form-control", value header.value, placeholder "value"
                , onInput (\s -> UpdateUI {ui | newHeader = { header | value = s} })
                ] []
         , span [ class "input-group-btn" ] [
             button [ class "btn btn-success", onClick AddHeader] [
               span [ class "fa fa-plus" ] []
             ]
           ]
         ]
       ]

    headers = List.concat [ [noHeadersWarning], (List.indexedMap headerForm httpData.headers), [newHeader] ]
    opened = ui.openHeader

  in
    div [ class "form-group" ] [
      label [ class "optional-link"] [
        a [onClick (UpdateUI {ui | openHeader = not opened})] [
          b [] [ text "Headers"]
        ]
      ]
    , if opened then div [ id "headers", class "well" ]  headers else text ""
    ]


jsonPathForm : HTTPType -> Html Msg
jsonPathForm httpData =

             div [ class "form-group clearfix"] [
                label [ for "jsonpath", class "optional" ] [
                  text "JSON path"
                , span [ class "fa fa-question-circle icon-info", attribute "data-bs-toggle" "tooltip", attribute "data-bs-container" "body", title "You can also use Rudder variable expansion here. See help for URL above."] []
                ]
              , div [ class "input-group" ] [
                  label [ class "input-group-addon addon has-example-help", for "jsonpath"] [text "$."]
                , input [ type_ "text", class "form-control has-example-help", id "jsonpath", value httpData.path
                        , onInput ( \s -> UpdateHTTPData {httpData | path = s })
                        ] []
                ]
              , div [ class "example-help" ] [
                  text "When a JSON document is returned, you can define a JSON path expression (see "
                , a [ href "https://github.com/jayway/JsonPath/#getting-started", target "_blank" ] [ text "documentation" ]
                , text ") to select only a sub-part of the document as the actual data to use as a node property."
                ]
              ]

advancedOptions : DataSource -> UI -> HTTPType -> Html Msg
advancedOptions datasource ui httpData =
  let
    requestTimeoutMinutes = httpData.requestTimeout // 60
    requestTimeoutSeconds = modBy 60 httpData.requestTimeout
    updateTimeoutMinutes = datasource.updateTimeout // 60
    updateTimeoutSeconds = modBy 60 datasource.updateTimeout
    opened = ui.openAdvanced
  in

            div [ class "form-group" ] [
                label [] [
                  a [onClick (UpdateUI {ui | openAdvanced = not opened})] [
                    text "Advanced options "
                  , span [ class "fa fa-chevron-right"] []
                  ]
                ]
              , if opened then
                div [ class "well options"] [
                  ul [ class "form-group " ] [
                    li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "checkSsl" ] [
                          input [ type_ "checkbox", id "checkSsl", checked (not httpData.checkSsl)
                                , onCheck (\b -> UpdateHTTPData { httpData | checkSsl = not b} )
                                ] []
                        , label [ for "checkSsl", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon" ] []
                        ]
                      , label [ class "form-control", for "checkSsl" ] [
                          text "Ignore SSL certificate validation"
                        ]
                      ]
                    ]
                  ]
                , label [] [ text "HTTP request timeout" ]
                , div [ class "input-group group-update-frequency" ] [
                    input [ type_ "number", class "form-control ", id "requesttimeout-minutes", Html.Attributes.min "0", value (String.fromInt requestTimeoutMinutes)
                          , onInput (\s -> UpdateHTTPData { httpData | requestTimeout = (((Maybe.withDefault requestTimeoutMinutes (String.toInt s)) * 60)+ requestTimeoutSeconds)})
                          ] []
                  , label [ for "requesttimeout-minutes", class "input-group-addon" ] [
                      text (String.Extra.pluralize "minute" "minutes" requestTimeoutMinutes)
                    ]
                  , input [ type_ "number", class "form-control ", id "requesttimeout-seconds" , value (String.fromInt requestTimeoutSeconds), Html.Attributes.min "0", Html.Attributes.max "59"
                          , onInput (\s -> UpdateHTTPData { httpData| requestTimeout = ((Maybe.withDefault requestTimeoutSeconds (String.toInt s))+ (requestTimeoutMinutes*60))})
                          ] []
                  , label [ for "requesttimeout-seconds", class "input-group-addon"] [
                      text (String.Extra.pluralize "second" "seconds" requestTimeoutSeconds)
                    ]
                  ]
                , label [] [text "Data source update max duration"]
                , div [ class "input-group group-update-frequency" ] [
                    input [ type_ "number", class "form-control ", id "updatetimeout-minutes", Html.Attributes.min "0", value (String.fromInt updateTimeoutMinutes)
                          , onInput (\s -> UpdateDataSource { datasource | updateTimeout = (((Maybe.withDefault updateTimeoutMinutes (String.toInt s)) * 60)+ updateTimeoutSeconds)})
                          ] []
                  , label [ for "updatetimeout-minutes", class "input-group-addon" ] [
                      text (String.Extra.pluralize "minute" "minutes" updateTimeoutMinutes)
                    ]
                  , input [ type_ "number", class "form-control ", id "updatetimeout-seconds", value (String.fromInt updateTimeoutSeconds), Html.Attributes.min "0", Html.Attributes.max "59"
                          , onInput (\s -> UpdateDataSource { datasource| updateTimeout = ((Maybe.withDefault updateTimeoutSeconds (String.toInt s))+ (updateTimeoutMinutes*60))})
                          ] []
                  , label [ for "updatetimeout-seconds", class "input-group-addon"] [
                      text (String.Extra.pluralize "second" "seconds" updateTimeoutSeconds )
                    ]
                  ]
                , label [] [text "Maximum number of parallel requests"]
                , div [ class "input-group group-update-frequency" ] [
                    input [ type_ "number", class "form-control ", id "maxParallelReq", Html.Attributes.min "1", value (String.fromInt (httpData.maxParallelRequest))
                          , onInput (\s -> UpdateHTTPData { httpData | maxParallelRequest = (Maybe.withDefault httpData.maxParallelRequest (String.toInt s))})
                          ] []
                  ]
                ]

                else
                  text ""
              ]

updateTrigger : UI -> DataSource -> Html Msg
updateTrigger ui datasource =
  let
    params = datasource.runParameters
    schedule = params.schedule
    frequency =
      case   schedule.type_ of
        Scheduled ->
          let
            hours = (schedule.duration // 60)
            minutes = (modBy 60 schedule.duration )

          in
                div [ class "input-group group-update-frequency" ] [
                    input [ type_ "number", class "form-control ", id "updateFrequency-hours", Html.Attributes.min "0", value (String.fromInt hours)
                          , onInput (\s -> UpdateDataSource { datasource| runParameters = { params | schedule = {schedule | duration = ((Maybe.withDefault hours (String.toInt s)) * 60)+ minutes}}})
                          ] []
                  , label [ for "updateFrequency-hours", class "input-group-addon"] [
                      text (String.Extra.pluralize "hour" "hours" hours)
                    ]
                  , input [ type_ "number", class "form-control ", id "updateFrequency-minutes",  Html.Attributes.min "0", value (String.fromInt minutes)
                          , onInput (\s -> UpdateDataSource { datasource| runParameters = { params | schedule = {schedule | duration = (Maybe.withDefault minutes (String.toInt s))+ (hours*60)}}})
                          ] []
                  , label [ for "updateFrequency-minutes", class "input-group-addon"] [ --
                      text (String.Extra.pluralize "minute" "minutes" minutes)
                    ]
                  ]
        NotScheduled ->
          text ""
    opened = ui.openTrigger
  in
            div [ class "form-group" ] [
                label [ class "bloc"] [
                  a [onClick (UpdateUI {ui | openTrigger = not opened})] [
                    text "Update triggers"
                  , span [ class "fa fa-chevron-right"] []
                  ]
                ]
              , if opened then
                div [ class "well"] [
                  ul [ class "form-group " ] [
                    li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "neverUpdate" ] [
                          input [ type_ "checkbox", id "neverUpdate", checked (schedule.type_ /= NotScheduled)
                                , onCheck (\b -> UpdateDataSource { datasource| runParameters = { params | schedule = {schedule | type_ = if b then Scheduled else NotScheduled } } } )
                                ] []
                        , label [ for "neverUpdate", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon"] []
                        ]
                      , label [ class "form-control", for "neverUpdate"] [
                          text ("Update periodically - " ++ if schedule.type_ == NotScheduled then "Not scheduled" else "Scheduled")
                        ]
                      ]
                    ]
                  ]
                , frequency
                , ul [ class "form-group " ] [
                    li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "onGeneration" ] [
                          input [ type_ "checkbox", id "onGeneration", checked params.onGeneration
                                , onCheck (\b -> UpdateDataSource { datasource| runParameters = { params | onGeneration = b } } )
                                ] []
                        , label [ for "onGeneration", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon" ] [ ]
                        ]
                      , label [ class "form-control", for "onGeneration" ] [
                          text "Update when a policy generation starts"
                        ]
                      ]
                    ]
                  , li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "onNewNode" ] [
                          input [ type_ "checkbox", id "onNewNode", checked params.onNewNode
                                , onCheck (\b -> UpdateDataSource { datasource| runParameters = { params | onNewNode = b } } )
                                ] []
                        , label [ for "onNewNode", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon" ] []
                        ]
                      , label [ class "form-control", for "onNewNode" ] [
                          text "Update when a new node is accepted"
                        ]
                      ]
                    ]
                  ]
                ]
                else text ""
              ]


nodeBehaviour : UI -> HTTPType -> Html Msg
nodeBehaviour ui data =
  let
    opened = ui.openNode
    (isDefault, currentDefault) =
      case data.onMissing of
        Default v -> (True,v)
        _ -> (False, "")
  in
            div [ class "form-group" ] [
                label [ class "bloc" ] [
                  a [onClick (UpdateUI {ui | openNode = not opened})] [

                    text "What to do when a query for a Node returns a 404 error?"
                  , span [ class "fa fa-chevron-right" ] []
                  ]
                ]
              , if opened then
                div [ class "well" ] [
                  ul [ class "form-group " ] [
                    li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "onMissingDelete" ] [
                          input [ type_ "radio", id "onMissingDelete", checked (data.onMissing == Delete)
                                , onClick (UpdateHTTPData { data| onMissing = Delete } )
                                ] []
                        , label [ for "onMissingDelete", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon" ] []
                        ]
                      , label [ class "form-control", for "onMissingDelete" ] [
                          text "Delete the node property corresponding to that data source (default behavior)"
                        ]
                      ]
                    ]
                  ]
                , ul [ class "form-group " ] [
                    li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "onMissingNoChange" ] [
                          input [ type_ "radio", id "onMissingNoChange", checked (data.onMissing == NoChange)
                                , onClick (UpdateHTTPData { data| onMissing = NoChange } )
                                ] []
                        , label [ for "onMissingNoChange", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon" ] []
                        ]
                      , label [ class "form-control", for "onMissingNoChange" ] [
                          text "Do not change the node property corresponding to that data source"
                        ]
                      ]
                    ]
                  , li [ class "rudder-form" ] [
                      div [ class "input-group" ] [
                        label [ class "input-group-addon", for "onMissingDefaultValue" ] [
                          input [ type_ "radio", id "onMissingDefaultValue", checked isDefault
                                , onClick (UpdateHTTPData { data| onMissing = Default currentDefault } )
                                ] []
                        , label [ for "onMissingDefaultValue", class "label-radio" ] [
                            span [ class "ion ion-checkmark-round" ] []
                          ]
                        , span [ class "ion ion-checkmark-round check-icon" ] []
                        ]
                      , label [ class "form-control", for "onMissingDefaultValue" ] [
                          text "Set the node property corresponding to the data source to the following value:"
                        ]
                      , ( case data.onMissing of
                          Default default ->
                            div [ class "input-group group-onmissing-value"] [
                              label [ for "onMissingDefaultValueValue", class "form-control"] [
                                text "(string or JSON allowed, empty is equivalent to deleting the property)"
                              ]
                            , textarea [ class "form-control ", id "onMissingDefaultValueValue", value default
                                       , onInput (\s -> UpdateHTTPData {data | onMissing = Default s})
                                       ] []
                            ]
                          _ -> text ""
                      )
                      ]
                    ]
                  ]
                ]
                else text ""
              ]

typeForm : UI -> DataSource -> List (Html Msg)
typeForm ui  datasource =
  case datasource.type_ of
    HTTP data ->
        [ httpMethodForm data
        , if (data.method == POST) then postForm ui data else  text ""
        , urlForm data
        , headersForm ui data
        , jsonPathForm data
        , advancedOptions datasource ui data
        , updateTrigger ui datasource
        , nodeBehaviour ui data
        ]
