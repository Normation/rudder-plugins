port module ApiAuthorizations exposing (..)

import Html exposing (..)
import Browser
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import List.Extra exposing (uniqueBy)
import String
import Json.Decode exposing (..)
import Json.Decode.Pipeline as D exposing (..)

main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }



-- MODEL
-- ACLs for a token
-- from Angularjs, we get acl: [ {"path": "string/to/api", "verb": "get" } ... }
type alias AccessControl =
    { path : String
    , verb : String
    }


-- for the token we only keep the id (for log) and ACL
type alias Token =
    { id : String
    , acl : List AccessControl
    }

-- Rudder APIs (the whole list)
-- [ {"category": "name", "apis": [ { "name": string, "description": string, "path": string, "verb": string }, ... ]}, ... ]

type alias Api =
    { name : String
    , description : String
    , path : String
    , verb : String
    }

type alias ApiCategory =
    { category : String
    , apis     : List Api
    }


type alias Model =
    { rudderApis : List ApiCategory
    , token      : Token
    , newAcl     : List AccessControl -- the list of updated acl to use
    }

type Msg
    = SetAc AccessControl Bool -- set that AC to status (ie remove it if false, add it if true
    | SetAcl (List AccessControl) Bool -- set a list of access control to given status
    | GetToken (Result Error (Token))



-- SUBSCRIPTIONS

subscriptions : Model -> Sub Msg
subscriptions model =
  getToken (GetToken << decodeValue (decodeToken))

-- sending out new acl
port giveAcl : List AccessControl -> Cmd msg
port getToken : (Json.Decode.Value -> msg) -> Sub msg


init : { token : Token, rudderApis : List ApiCategory } -> ( Model, Cmd Msg )
init flags =
  ( Model flags.rudderApis flags.token [], Cmd.none )



-- UPDATE
-- utility method that update a model adding or removing a list of acl

makeAcComparable :  AccessControl -> String
makeAcComparable ac =
  ac.path ++ ac.verb

setAcl : Model -> List AccessControl -> Bool -> ( Model, Cmd Msg )
setAcl model acl status =
  let
    newAcl =
      if status then
        -- add AC into list
        List.append acl model.token.acl
          |> uniqueBy makeAcComparable
          -- uniqueBy requires comparable and does work with structural equality, which it a pity. toString should be ok in all case for AccessControl, but it was not necessary.
      else
        List.filter (\a -> not (List.member a acl)) model.token.acl
    token = model.token
    newToken = {token | acl = newAcl}

  in
    ( { model | token = newToken }, giveAcl newAcl )


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
  case msg of
    -- set/unset a list of access control
    SetAcl acl status ->
      setAcl model acl status

    -- set/unset one access control
    SetAc ac status ->
      setAcl model [ ac ] status

    GetToken (Ok token) ->
      ({model | token = token}, Cmd.none)

    GetToken (Err e) ->
      (model, Cmd.none)


-- VIEW
-- the checkbox for an API which depends upon the presence
-- or not of the corresponding AccessControl


apiSelect : List AccessControl -> Api -> Html Msg
apiSelect listAcl api =
  let
    acl = AccessControl api.path api.verb
    status = List.member acl listAcl
  in
    label [  ] [ input [ type_ "checkbox", checked status, onClick (SetAc acl (not status)) ] [] ]


displayApi : Api -> Html Msg
displayApi api =
    div [  ]
        [ h5 []
          [ b [ class api.verb ] [ text api.verb ]
          , text (" " ++ api.path)
          ]
        , div [] [ text (api.name ++ ": " ++ api.description) ]
        ]


displayCategory : List AccessControl -> ApiCategory -> Html Msg
displayCategory acl cat =
    let
      catAcl   = cat.apis |> List.map (\api -> AccessControl api.path api.verb)
      toggleId = "toggle-" ++ cat.category
      nbSelected = catAcl
        |> List.Extra.count (\a -> List.member a acl)
    in
      div [class "acl-category"]
      [ input[type_ "checkbox", id toggleId, class "toggle-checkbox"][]
      , div[class "category-header"]
        [ label [for toggleId, class "category-toggle-caret"]
          [ i [class "fa fa-caret-down"][]
          ]
        , label [for toggleId]
          [ text cat.category
          , span [class ("badge badge-secondary " ++ if nbSelected <= 0 then "empty" else "")][text (String.fromInt nbSelected)]
          ]
        , div []
          [ button [type_ "button", onClick (SetAcl catAcl False), class "btn btn-default btn-sm" ] [ text "None" ]
          , button [type_ "button", onClick (SetAcl catAcl True ), class "btn btn-primary btn-sm" ] [ text "All"  ]
          ]
        ]
      , div [class "category-body"]
        (cat.apis
          |> List.map (\api ->
            label [ class "label-acl mb-2" ]
            [ apiSelect acl api
            , displayApi api
            ]
          )
        )
      ]


view : Model -> Html Msg
view model =
    div []
        [ div []
            -- list current apis for Rudder
            (model.rudderApis
                |> List.map
                    (\cat ->
                        displayCategory model.token.acl cat
                    )
            )
        ]


decodeToken : Decoder Token
decodeToken =
  succeed Token
    |> D.required "id"  string
    |> D.required "acl" (Json.Decode.list decodeAcl)

decodeAcl : Decoder AccessControl
decodeAcl =
  succeed AccessControl
    |> D.required "path" string
    |> D.required "verb" string