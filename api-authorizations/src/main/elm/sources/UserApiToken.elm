module UserApiToken exposing (Model, Msg(..), Token, apiRequestTemplate, createUserToken, decodeApiUserToken, decodeDeleteToken, decodeToken, deleteUserToken, getUserToken, init, main, printError, response, subscriptions, tokenAbsent, tokenPresent, update, updateFromApiResult, updateFromDelete, view)

import Html exposing (..)
import Browser
import Html.Attributes exposing (class)
import Html.Events exposing (..)
import Http exposing (..)
import Json.Decode exposing (..)
import Json.Decode.Pipeline exposing (..)
import String


main =
    Browser.element
        { init = init
        , view = view
        , update = update
        , subscriptions = subscriptions
        }



-- MODEL


type alias Token =
    { id : String -- not interesting, user name
    , name : String -- not interesting, user name
    , token : String -- that's the part you want to get
    , creationDate : String
    , generationDate : String
    , kind : String
    , description : String
    , enabled : Bool
    }


type alias Model =
    { contextPath : String
    , token : Maybe Token
    , error : Maybe (Html Msg)
    , isNewToken : Bool
    }


type Msg
    = GetUserToken (Result Http.Error (Maybe Token))
    | CreateUserToken (Result Http.Error (Maybe Token))
    | DeleteUserToken (Result Http.Error (Maybe String))
    | CreateButton
    | DeleteButton



-- JSON decoder / encoder


decodeToken : Decoder Token
decodeToken =
    succeed Token
        |> required "id" string
        |> required "name" string
        |> required "token" string
        |> required "creationDate" string
        |> required "tokenGenerationDate" string
        |> required "kind" string
        |> required "description" string
        |> required "enabled" bool


decodeApiUserToken : Decoder (Maybe Token)
decodeApiUserToken =
    let
        -- Decoder (List Token)
        arrayToken =
            list decodeToken |> at [ "data", "accounts" ]
    in
    Json.Decode.map (\list -> List.head list) arrayToken


decodeDeleteToken : Decoder (Maybe String)
decodeDeleteToken =
    maybe string |> at [ "data", "accounts", "id" ]



-- SUBSCRIPTIONS


subscriptions : Model -> Sub Msg
subscriptions model =
    Sub.none


init : { contextPath : String } -> ( Model, Cmd Msg )
init flags =
    let
        initModel =
            Model flags.contextPath Nothing Nothing False
    in
    ( initModel
    , getUserToken initModel
    )



-- UPDATE


printError : Http.Error -> Html Msg
printError error =
    case error of
        BadUrl s ->
          text s

        Timeout ->
          text "Request timed out. Please try again"

        NetworkError ->
          text "There was a network error. Please try again"

        BadStatus code ->
          text ("Response error code " ++ String.fromInt code)

        _ ->
          text("There was an error during request")



response : Result Http.Error a -> Model -> (Model -> a -> Maybe Token) -> ( Model, Cmd Msg )
response resp model fmod =
    let
        newModel =
            case resp of
                Err err ->
                    { model | error = Just (printError err), token = Nothing }

                Ok maybeA ->
                    { model | error = Nothing, token = fmod model maybeA }
    in
    ( newModel, Cmd.none )


updateFromApiResult : Result Http.Error (Maybe Token) -> Model -> ( Model, Cmd Msg )
updateFromApiResult resp model =
    let
        id : Model -> Maybe Token -> Maybe Token
        id m maybeToken =
            maybeToken
    in
    response resp model id


updateFromDelete : Result Http.Error (Maybe String) -> Model -> ( Model, Cmd Msg )
updateFromDelete resp model =
    let
        updateDel : Model -> Maybe String -> Maybe Token
        updateDel m maybeRes =
            case maybeRes of
                Just id ->
                    Nothing

                Nothing ->
                    -- that means an error, let token as it was in model
                    m.token
    in
    response resp model updateDel


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        GetUserToken res ->
            updateFromApiResult res model

        CreateUserToken res ->
            updateFromApiResult res { model | isNewToken = True}

        DeleteUserToken res ->
            updateFromDelete res model

        CreateButton ->
            ( model, createUserToken model )

        DeleteButton ->
            ( model, deleteUserToken model )



-- API


apiRequestTemplate : (Result Http.Error (Maybe Token) -> Msg) -> String -> String -> Model -> Cmd Msg
apiRequestTemplate msg method path model =
    let
        url =
            model.contextPath ++ path

        req =
            request
                { method = method
                , headers = []
                , url = url
                , body = emptyBody
                , expect = expectJson msg decodeApiUserToken
                , timeout = Nothing
                , tracker = Nothing
                }
    in
     req


getUserToken : Model -> Cmd Msg
getUserToken model =
    apiRequestTemplate GetUserToken "GET" "/secure/api/user/api/token" model


createUserToken : Model -> Cmd Msg
createUserToken model =
    apiRequestTemplate CreateUserToken "PUT" "/secure/api/user/api/token" model


deleteUserToken : Model -> Cmd Msg
deleteUserToken model =
    let
        url =
            model.contextPath ++ "/secure/api/user/api/token"

        req =
            request
                { method = "DELETE"
                , headers = []
                , url = url
                , body = emptyBody
                , expect = expectJson DeleteUserToken decodeDeleteToken
                , timeout = Nothing
                , tracker = Nothing
                }
    in
      req



-- VIEW
-- view when the token exists


tokenPresent : Token -> Bool -> List (Html Msg)
tokenPresent token isNewToken =
    let
        ( statusIcon, statusTxt, statusClass ) =
            case token.enabled of
                True ->
                    ( "fa fa-check", "enabled", "text-success" )

                False ->
                    ( "fa-ban", "disabled", "text-info" )
        hasClearTextToken = not (String.isEmpty token.token)
    in
    [ if hasClearTextToken then
      li []
      [ a [ class "no-click" ]
        [ span [ class "fa fa-key" ] []
        , text "Your personal token: "
        , div [ class "help-block" ] [ b [] [ text token.token ] ]
        ]
      ]
    else
      li []
      [ a [ class "no-click" ]
        [ span [ class "fa fa-key" ] []
        , text "You have a personal token."
        ]
      ]
    , if isNewToken then
      li []
      [ a [ class "no-click" ]
        [ span [ class "fa fa-exclamation-triangle" ] []
        , text "Copy it now as it will not be re-displayed"
        ]
      ]
    else
      if hasClearTextToken then
        li []
        [ a [ class "no-click" ]
          [ span [ class "fa fa-exclamation-triangle" ] []
          , text "Deprecated token format, please re-create"
          ]
        ]
      else
        text ""
    , li []
        [ a [ class "no-click" ]
          [ span [ class "fa fa-calendar" ] []
          , text "Generated on "
          , b [] [ text token.generationDate ]
          ]
        ]
    , li []
        [ a [ class "no-click" ]
          [ span [ class ("fa " ++ statusIcon) ] []
          , text "Status: "
          , b [ class ("text-capitalize " ++ statusClass) ] [ text statusTxt ]
          ]
        ]
    , li [ class "footer" ] [ a [ class "deleteToken", onClick DeleteButton ] [ text "Delete API token" ] ]
    ]


tokenAbsent : Model -> List (Html Msg)
tokenAbsent model =
    [ li [] [ a [ class "no-click no-token" ] [ text "You don't have an API token yet." ] ]
    , li [ class "footer" ] [ a [ class "createToken", onClick CreateButton ] [ text "Create an API token" ] ]
    ]


view : Model -> Html Msg
view model =
    ul [ class "menu" ]
        ([ case model.error of
            Just error ->
                li [ class "error" ] [ a [ class "no-click" ] [ error ] ]

            Nothing ->
                text ""
         ]
            ++ (case model.token of
                    Just token ->
                        tokenPresent token model.isNewToken

                    Nothing ->
                        tokenAbsent model
               )
        )
