port module ApiAuthorizations exposing (AccessControl, Api, ApiCategory, Model, Msg(..), Token, apiSelect, displayApi, displayCategory, giveAcl, init, main, setAcl, subscriptions, update, view)

import Debug exposing (toString)
import Html exposing (..)
import Browser
import Html.Attributes exposing (..)
import Html.Events exposing (..)
import List.Extra exposing (uniqueBy)
import String


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
    , apis : List Api
    }


type alias Model =
    { rudderApis : List ApiCategory
    , token : Token
    , newAcl : String -- the list of updated acl to use
    }


type Msg
    = SetAc AccessControl Bool -- set that AC to status (ie remove it if false, add it if true
    | SetAcl (List AccessControl) Bool -- set a list of access control to given status



-- SUBSCRIPTIONS

subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none



-- sending out new acl


port giveAcl : List AccessControl -> Cmd msg


init : { token : Token, rudderApis : List ApiCategory } -> ( Model, Cmd Msg )
init flags =
    ( Model flags.rudderApis flags.token "", Cmd.none )



-- UPDATE
-- utility method that update a model adding or removing a list of acl


setAcl : Model -> List AccessControl -> Bool -> ( Model, Cmd Msg )
setAcl model acl status =
    let
        newAcl =
            if status then
                -- add AC into list
                List.append acl model.token.acl
                    |> uniqueBy toString
                -- uniqueBy requires comparable and does work with structural equality, which it a pity. toString should be ok in all case for AccessControl, but it was not necessary.

            else
                List.filter (\a -> not (List.member a acl)) model.token.acl

        newToken =
            Token model.token.id newAcl
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



-- VIEW
-- the checkbox for an API which depends upon the presence
-- or not of the corresponding AccessControl


apiSelect : List AccessControl -> String -> String -> Html Msg
apiSelect acl path verb =
    let
        ac =
            AccessControl path verb

        status =
            List.member ac acl
    in
    label [ class "col-xs-1" ] [ input [ type_ "checkbox", checked status, onClick (SetAc ac (not status)) ] [] ]


displayApi : Api -> Html Msg
displayApi api =
    div [ class "col-xs-11" ]
        [ h4 []
            [ b [ class api.verb ] [ text api.verb ]
            , text (" " ++ api.path)
            ]
        , div [] [ text (api.name ++ ": " ++ api.description) ]
        ]


displayCategory : List AccessControl -> ApiCategory -> Html Msg
displayCategory acl cat =
    let
        catAcl =
            cat.apis |> List.map (\api -> AccessControl api.path api.verb)
    in
    div []
        [ h3 [] [ text cat.category ]
        , div []
            [ span [ onClick (SetAcl catAcl True), class "btn btn-success pull-right small", style "margin-left" "2px", style "color" "#fff" ] [ text "all" ]
            , span [ onClick (SetAcl catAcl False), class "btn btn-danger pull-right small", style "margin-right" "2px", style "color" "#fff" ] [ text "none" ]
            ]
        , div []
            (cat.apis
                |> List.map
                    (\api ->
                        label [ class "label-acl" ]
                            [ apiSelect acl api.path api.verb
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
