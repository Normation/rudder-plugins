port module ApiAuthorizations exposing (..)

import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import String


main = programWithFlags
    { init = init
    , view = view
    , update = update
    , subscriptions = subscriptions
    }


-- MODEL

-- ACLs for a token
-- from Angularjs, we get acl: [ {"path": "string/to/api", "verb": "get" } ... }

type alias AccessControl =
  { path: String
  , verb: String
  }

-- for the token we only keep the id (for log) and ACL
type alias Token =
  { id : String
  , acl: List AccessControl
  }

-- Rudder APIs (the whole list)
-- [ {"category": "name", "apis": [ { "name": string, "description": string, "path": string, "verb": string }, ... ]}, ... ]

type alias Api =
  { name       : String
  , description: String
  , path       : String
  , verb       : String
  }

type alias ApiCategory =
  { category: String
  , apis    : List Api
  }

type alias Model =
  { rudderApis: List ApiCategory
  , token     : Token
  , newAcl    : String -- the list of updated acl to use
  }

type Msg
  = SetAc AccessControl Bool -- set that AC to status (ie remove it if false, add it if true

-- SUBSCRIPTIONS

subscriptions : Model -> Sub Msg
subscriptions   model =  Sub.none

-- sending out new acl
port giveAcls : List AccessControl -> Cmd msg

init : { token: Token, rudderApis: List ApiCategory } -> (Model, Cmd Msg)
init flags =
  ((Model flags.rudderApis flags.token ""), Cmd.none)

-- UPDATE

update : Msg -> Model -> (Model, Cmd Msg)
update msg model =
  case msg of
    SetAc ac status ->
      let
        newAcl   = if status then -- add AC into list
                     ac :: model.token.acl
                   else
                     List.filter (\a -> a /= ac) (model.token.acl)
        newToken = Token model.token.id newAcl
      in
        ( {model | token = newToken }, giveAcls newToken.acl )


-- VIEW

-- the checkbox for an API which depends upon the presence
-- or not of the corresponding AccessControl
apiSelect: List AccessControl -> String -> String -> Html Msg
apiSelect acl path verb =
  let
    ac     = AccessControl path verb
    status = List.member ac acl
  in
    label[class "col-xs-1"][input [ type_ "checkbox", checked status, onClick (SetAc ac (not status)) ] []]

displayApi: Api -> Html Msg
displayApi api =
  div [class "col-xs-11"] [
    h4 [] [
      (b [class api.verb] [(text (api.verb))])
    , (text (" " ++ api.path))
    ]
  , div[][(text (api.name ++ ": " ++ api.description))]
  ]

displayCategory: List AccessControl -> ApiCategory -> Html Msg
displayCategory acl cat =
  div [] [
    h3 [] [(text cat.category)]
  , div [] (cat.apis |> List.map (\api ->
      label [class "label-acl" ] [
        (apiSelect acl api.path api.verb)
      , (displayApi api)
      ]
    ))
  ]

view: Model -> Html Msg
view model =
  div []
    [ div [] ( -- list current apis for Rudder
          model.rudderApis |> List.map( \cat ->
            displayCategory model.token.acl cat
          )
        )
    ]
