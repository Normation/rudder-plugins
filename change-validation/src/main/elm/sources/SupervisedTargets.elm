module SupervisedTargets exposing (Category, Model, Msg(..), Subcategories(..), Target, alphanumericRegex, decodeApiCategory, decodeApiSave, decodeCategory, decodeSubcategories, decodeTarget, displayCategory, displaySubcategories, displayTarget, encodeTargets, getSupervisedIds, getTargets, init, isAlphanumeric, main, saveTargets, subscriptions, update, updateTarget, view)

import Browser
import ErrorMessages exposing (getErrorMessage)
import Html exposing (..)
import Html.Attributes exposing (checked, class, type_)
import Html.Events exposing (..)
import Http exposing (..)
import Json.Decode as D exposing (Decoder)
import Json.Decode.Pipeline exposing (..)
import Json.Encode as E
import Notifications exposing (errorNotification, successNotification)
import Regex
import String



------------------------------
-- SUBSCRIPTIONS
------------------------------


subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none



------------------------------
-- Init and main --
------------------------------


init : { contextPath : String } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model flags.contextPath (Category "waiting for server data..." (Subcategories []) [])
    in
    ( initModel
    , getTargets initModel
    )


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }



------------------------------
-- MODEL --
------------------------------


type alias Target =
    { id : String -- id
    , name : String -- display name of the rule target
    , description : String -- description
    , supervised : Bool -- do you want to validate CR targeting that rule target
    }


type alias Category =
    { name : String -- name of the category
    , categories : Subcategories -- sub-categories
    , targets : List Target -- targets in category
    }


type Subcategories
    = Subcategories (List Category) -- needed because no recursive type alias support


type alias Model =
    { contextPath : String
    , allTargets : Category -- from API
    }


type Msg
    = GetTargets (Result Error Category)
    | SaveTargets (Result Error String) -- here the string is just the status message
    | SendSave
    | UpdateTarget Target



-- NOTIFICATIONS
------------------------------
-- API --
------------------------------
-- API call to get the category tree


getTargets : Model -> Cmd Msg
getTargets model =
    let
        url =
            model.contextPath ++ "/secure/api/changevalidation/supervised/targets"

        headers =
            []

        req =
            request
                { method = "GET"
                , headers = [ Http.header "X-Requested-With" "XMLHttpRequest" ]
                , url = url
                , body = emptyBody
                , expect = expectJson GetTargets decodeApiCategory
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



--


saveTargets : Model -> Cmd Msg
saveTargets model =
    let
        req =
            request
                { method = "POST"
                , headers = [ Http.header "X-Requested-With" "XMLHttpRequest" ]
                , url = model.contextPath ++ "/secure/api/changevalidation/supervised/targets"
                , body = jsonBody (encodeTargets (getSupervisedIds model.allTargets))
                , expect = expectJson SaveTargets decodeApiSave
                , timeout = Nothing
                , tracker = Nothing
                }
    in
    req



-- utility method to find all targets checked as "supervised"


getSupervisedIds : Category -> List String
getSupervisedIds cat =
    case cat.categories of
        Subcategories subcats ->
            let
                fromTargets =
                    cat.targets
                        |> List.filterMap
                            (\t ->
                                if t.supervised then
                                    Just t.id

                                else
                                    Nothing
                            )
            in
            List.concat (fromTargets :: (subcats |> List.map (\c -> getSupervisedIds c)))



-- encode / decode JSON
-- decode the JSON answer from a "save" API call. Just check status message.


decodeApiSave : Decoder String
decodeApiSave =
    D.at [ "result" ] D.string



-- decode the JSON answer from a "get" API call - only "data" field content is interesting


decodeApiCategory : Decoder Category
decodeApiCategory =
    D.at [ "data" ] decodeCategory


decodeCategory : Decoder Category
decodeCategory =
    D.succeed Category
        |> required "name" D.string
        |> required "categories" decodeSubcategories
        |> required "targets" (D.list decodeTarget)


decodeSubcategories : Decoder Subcategories
decodeSubcategories =
    D.map Subcategories (D.list (D.lazy (\_ -> decodeCategory)))


decodeTarget : Decoder Target
decodeTarget =
    D.succeed Target
        |> required "id" D.string
        |> required "name" D.string
        |> required "description" D.string
        |> required "supervised" D.bool



-- when we encode targets, we only care about the id of the target


encodeTargets : List String -> E.Value
encodeTargets targets =
    E.object [ ( "supervised", E.list (\s -> E.string s) targets ) ]



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        {--Api Calls message --}
        GetTargets result ->
            case result of
                Ok category ->
                    let
                        newModel =
                            { model | allTargets = category }
                    in
                    ( newModel, Cmd.none )

                Err err ->
                    ( model, errorNotification ("Error while trying to fetch settings: " ++ getErrorMessage err) )

        SaveTargets result ->
            case result of
                Ok m ->
                    ( model, successNotification "" )

                Err err ->
                    ( model, errorNotification ("Error while trying to save changes: " ++ getErrorMessage err) )

        SendSave ->
            ( model, saveTargets model )

        UpdateTarget target ->
            let
                newModel =
                    { model | allTargets = updateTarget target model.allTargets }
            in
            ( newModel, Cmd.none )



-- utility method the replace a target in Category (or its children) based on its id.
-- If several targets have the same id in the category tree, all are replaced.


updateTarget : Target -> Category -> Category
updateTarget target cat =
    let
        updatedCats =
            case cat.categories of
                Subcategories subcats ->
                    subcats |> List.map (\c -> updateTarget target c)

        updatedTargets =
            cat.targets
                |> List.map
                    (\t ->
                        if t.id == target.id then
                            target

                        else
                            t
                    )
    in
    Category cat.name (Subcategories updatedCats) updatedTargets



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    div []
        [ div [ class "row" ]
            [ div [ class "col-xs-12" ]
                [ displayCategory model.allTargets
                , div [ class "card-footer" ] [ button [ onClick SendSave, class "btn btn-success right" ] [ text "Save" ] ]
                ]
            ]
        ]


displaySubcategories : Subcategories -> List (Html Msg)
displaySubcategories (Subcategories categories) =
    categories |> List.map (\cat -> displayCategory cat)


displayCategory : Category -> Html Msg
displayCategory category =
    let
        subcats : List (Html Msg)
        subcats =
            displaySubcategories category.categories

        targets : List (Html Msg)
        targets =
            category.targets |> List.map (\target -> displayTarget target)

        htmlId =
            String.toLower category.name |> String.filter isAlphanumeric

        listGroupHeadingId =
            "list-group-heading-" ++ htmlId

        listGroupId =
            "list-group-" ++ htmlId
    in
    div [ class "card rounded-0" ]
        [ div [ class "card-body p-0" ]
            [ div [ class "card-title", Html.Attributes.id listGroupHeadingId ]
                [ div [ class "fs-5" ]
                    [ a [ class "show", Html.Attributes.href ("#" ++ listGroupId), Html.Attributes.attribute "role" "button", Html.Attributes.attribute "data-bs-toggle" "collapse", Html.Attributes.attribute "aria-expanded" "true", Html.Attributes.attribute "aria-controls" listGroupId ]
                        [ span [ class "fa fa-folder" ] []
                        , text category.name
                        ]
                    ]
                ]
            , div [ class "panel-collapse collapse show", Html.Attributes.id listGroupId, Html.Attributes.attribute "role" "tabpanel", Html.Attributes.attribute "aria-labelledby" listGroupHeadingId, Html.Attributes.attribute "aria-expanded" "true" ]
                (List.append subcats [ ul [ class "list-group list-group-flush" ] targets ])
            ]
        ]


displayTarget : Target -> Html Msg
displayTarget target =
    li [ class "list-group-item" ]
        [ label [ class "node" ]
            [ div [ class "node-name" ]
                [ span [ class "fa fa-sitemap" ] []
                , text target.name
                ]
            , div [ class "node-desc" ]
                [ if not (String.isEmpty target.description) then
                    text target.description

                  else
                    text ""
                ]
            , label [ class "node-check" ]
                [ input
                    [ type_ "checkbox"
                    , checked target.supervised
                    , onClick (UpdateTarget { target | supervised = not target.supervised })
                    ]
                    []
                , span [ class "ion ion-checkmark-round" ] []
                ]
            ]
        ]



------------------------------
-- HELPERS
------------------------------


alphanumericRegex =
    Maybe.withDefault Regex.never <| Regex.fromString "^[A-Za-z0-9]*$"


isAlphanumeric : Char -> Bool
isAlphanumeric c =
    let
        str =
            String.fromChar c
    in
    Regex.contains alphanumericRegex str
