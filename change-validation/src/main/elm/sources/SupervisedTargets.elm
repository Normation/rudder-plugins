module SupervisedTargets exposing (Category, Subcategories, SupervisedTargetsModel, SupervisedTargetsMsg, Target, getTargets, initModel, update, view)

import ErrorMessages exposing (getErrorMessage)
import Html exposing (..)
import Html.Attributes exposing (checked, class, id, type_)
import Html.Events exposing (..)
import Http exposing (..)
import Json.Decode as D exposing (Decoder)
import Json.Decode.Pipeline exposing (..)
import Json.Encode as E
import Ports exposing (errorNotification, successNotification)
import Regex
import String



------------------------------
-- Init and main --
------------------------------


initModel : String -> SupervisedTargetsModel
initModel contextPath =
    SupervisedTargetsModel contextPath (Category "waiting for server data..." (Subcategories []) [])



------------------------------
-- MODEL                    --
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


type alias SupervisedTargetsModel =
    { contextPath : String
    , allTargets : Category -- from API
    }


type SupervisedTargetsMsg
    = GetTargets (Result Error Category)
    | SaveTargets (Result Error String) -- here the string is just the status message
    | SendSave
    | UpdateTarget Target



-- NOTIFICATIONS
------------------------------
-- API --
------------------------------
-- API call to get the category tree


getTargets : SupervisedTargetsModel -> Cmd SupervisedTargetsMsg
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


saveTargets : SupervisedTargetsModel -> Cmd SupervisedTargetsMsg
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


update : SupervisedTargetsMsg -> SupervisedTargetsModel -> ( SupervisedTargetsModel, Cmd SupervisedTargetsMsg )
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


view : SupervisedTargetsModel -> Html SupervisedTargetsMsg
view model =
    div [ id "supervisedTargets" ]
        [ h3 [ class "page-subtitle" ]
            [ text "Configure groups with change validations" ]
        , div [ class "section-with-doc" ]
            [ div [ class "section-left" ]
                [ div [ id "supervised-targets-app" ]
                    [ div [ id "list-groups-change-validation" ]
                        [ div [ class "row" ]
                            [ div [ class "col-xs-12" ]
                                [ displayCategory model.allTargets
                                , div [ class "card-footer" ] [ button [ onClick SendSave, class "btn btn-success right" ] [ text "Save" ] ]
                                ]
                            ]
                        ]
                    ]
                ]
            , supervisedTargetsInfoSection
            ]
        ]


supervisedTargetsInfoSection : Html msg
supervisedTargetsInfoSection =
    createRightInfoSection
        [ p []
            [ text " Change validation are enable for "
            , b [] [ text "any" ]
            , text " change that would impact a node belonging to one of the chosen groups below. Be careful: a change on one another group "
            ]
        , p [] [ text " The supervised changes are: " ]
        , ul []
            [ li [] [ text "any change in a global parameter, as these changes can have side effects spreading technique code," ]
            , li [] [ text "any modification in one of the supervised groups," ]
            , li [] [ text "any change in a rule which targets a node which belong to a group marked as supervised, " ]
            , li [] [ text "any change in a directive used in one of the previous rules." ]
            ]
        , p [] []
        , p [] [ text " Changes in techniques are not subjected to change validation, nor are changes resulting from an archive import. " ]
        ]


createRightInfoSection : List (Html msg) -> Html msg
createRightInfoSection contents =
    div [ class "section-right" ]
        [ div [ class "doc doc-info" ]
            (div [ class "marker" ] [ span [ class "fa fa-info-circle" ] [] ] :: contents)
        ]


displaySubcategories : Subcategories -> List (Html SupervisedTargetsMsg)
displaySubcategories (Subcategories categories) =
    categories |> List.map (\cat -> displayCategory cat)


displayCategory : Category -> Html SupervisedTargetsMsg
displayCategory category =
    let
        subcats : List (Html SupervisedTargetsMsg)
        subcats =
            displaySubcategories category.categories

        targets : List (Html SupervisedTargetsMsg)
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


displayTarget : Target -> Html SupervisedTargetsMsg
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
                , span [ class "fa fa-check" ] []
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
