port module UserManagement exposing (..)

import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import String
import List.Extra exposing (uniqueBy)

main = programWithFlags
    { init = init
    , view = view
    , update = update
    , subscriptions = subscriptions
    }


-- MODEL

-- An user: Login, List of rights,
type alias User =
  { login: String
  , authz: String
  }


-- the full list of digest + list of users
type alias UserList =
  { digest: String
  , users : List User
  }

type alias Model =
  { userList: UserList
  }

type Msg
  = Noop

-- SUBSCRIPTIONS

subscriptions : Model -> Sub Msg
subscriptions   model =  Sub.none


init : { authzConfig: UserList } -> (Model, Cmd Msg)
init flags =
  let
    initModel = Model flags.authzConfig
  in
    ( initModel, Cmd.none )

-- UPDATE

update : Msg -> Model -> (Model, Cmd Msg)
update msg model =
  case msg of
    Noop -> (model, Cmd.none)


-- VIEW

view: Model -> Html Msg
view model =
  div []
    [ div [] [(displayUserList model.userList)]
    ]

displayUserList: UserList -> Html Msg
displayUserList userList =
  let
    users = userList.users |> List.map (\user -> displayUser user)
  in
    div [] [
      h3 [] [ (text "Rudder Users") ]
    , div [] [
        (span [ style [("color", "#fff")] ] [text ("Password encoder: "++userList.digest) ])
      ]
    , div [] users
    ]

displayUser: User -> Html Msg
displayUser user =
  div [class "col-xs-11"] [
    h4 [] [
      (b [] [(text (user.login))])
    ]
  , div[][(text (user.authz))]
  ]


