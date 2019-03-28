port module UserManagement exposing (Model, Msg(..), User, UsersConf, addTempToast, addToast, createDecodeErrorNotification, createErrorNotification, createSuccessNotification, decodeApiCurrentUsersConf, decodeApiReloadResult, decodeCurrentUsersConf, decodeUser, defaultConfig, displayAuth, displayUser, displayUsersConf, getErrorMessage, getUsersConf, init, main, postReloadConf, processApiError, subscriptions, tempConfig, update, view)

import Html exposing (..)
import Html.Attributes exposing (checked, class, style, type_)
import Html.Events exposing (..)
import Http exposing (..)
import Json.Decode as D exposing (Decoder)
import Json.Decode.Pipeline exposing (..)
import Json.Encode as E
import Browser
import String
import Toasty
import Toasty.Defaults



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
        initModel = Model flags.contextPath Nothing Toasty.initialState
    in
    ( initModel
    , getUsersConf initModel
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
-- An user: Login, List of rights,


type alias User =
    { login : String
    , authz : String
    }



-- the full list of digest + list of users


type alias UsersConf =
    { digest : String
    , users : List User
    }


type alias Model =
    { contextPath : String
    , usersConf : Maybe UsersConf -- from API
    , toasties : Toasty.Stack Toasty.Defaults.Toast
    }


type Msg
    = GetUserInfo (Result Error UsersConf)
    | PostReloadUserInfo (Result Error String) -- also returns the updated list
    | SendReload -- ask for API call to reload user list
      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)



------------------------------
-- API --
------------------------------
-- API call to get the category tree


getUsersConf : Model -> Cmd Msg
getUsersConf model =
    let
        url =
            model.contextPath ++ "/secure/api/usermanagement/users"

        headers =
            []

        req =
            request
                { method = "GET"
                , headers = []
                , url = url
                , body = emptyBody
                , expect = expectJson decodeApiCurrentUsersConf
                , timeout = Nothing
                , withCredentials = False
                }
    in
    send GetUserInfo req


postReloadConf : Model -> Cmd Msg
postReloadConf model =
    let
        url =
            model.contextPath ++ "/secure/api/usermanagement/users/reload"

        headers =
            []

        req =
            request
                { method = "POST"
                , headers = []
                , url = url
                , body = emptyBody
                , expect = expectJson decodeApiReloadResult
                , timeout = Nothing
                , withCredentials = False
                }
    in
    send PostReloadUserInfo req



-- encode / decode JSON


decodeApiReloadResult : Decoder String
decodeApiReloadResult =
    D.at [ "data" ] D.string



-- decode the JSON answer from a "get" API call - only "data" field content is interesting


decodeApiCurrentUsersConf : Decoder UsersConf
decodeApiCurrentUsersConf =
    D.at [ "data" ] decodeCurrentUsersConf


decodeCurrentUsersConf : Decoder UsersConf
decodeCurrentUsersConf =
    D.succeed UsersConf
        |> required "digest" D.string
        |> required "users" (D.list <| decodeUser)


decodeUser : Decoder User
decodeUser =
    D.succeed User
        |> required "login" D.string
        |> required "authz" D.string



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        {--Api Calls message --}
        GetUserInfo result ->
            case result of
                Ok users ->
                    let
                        newModel =
                            { model | usersConf = Just users }
                    in
                    ( newModel, Cmd.none )

                Err err ->
                    processApiError err model

        PostReloadUserInfo result ->
            case result of
                Ok ok ->
                    ( model
                    , getUsersConf model
                    )

                Err err ->
                    processApiError err model

        SendReload ->
            ( model
            , postReloadConf model
            )

        ToastyMsg subMsg ->
            Toasty.update defaultConfig ToastyMsg subMsg model


processApiError : Error -> Model -> ( Model, Cmd Msg )
processApiError err model =
    let
        newModel =
            { model | usersConf = Nothing }
    in
    ( newModel, Cmd.none ) |> createErrorNotification "Error while trying to fetch settings." err



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    let
        content =
            case model.usersConf of
                Nothing ->
                    text "Waiting for data from server..."

                Just config ->
                    displayUsersConf config
    in
    div []
        [ content
        , div [ class "toasties" ] [ Toasty.view defaultConfig Toasty.Defaults.view ToastyMsg model.toasties ]
        ]


displayUsersConf : UsersConf -> Html Msg
displayUsersConf usersConf =
    let
        users =
            usersConf.users |> List.map (\user -> displayUser user)
    in
    div [ class "row" ]
        [ div [ class "col-xs-12" ]
            [ h3 []
                [ text "Rudder Users"
                , button [ class "btn btn-sm btn-primary", onClick SendReload ]
                    [ text "Reload users from file"
                    , span [ class "fa fa-refresh" ] []
                    ]
                ]
            ]
        , div [ class "col-xs-12" ]
            [ p [ class "callout-fade callout-info" ]
                [ div [ class "marker" ] [ span [ class "glyphicon glyphicon-info-sign" ] [] ]
                , text ("Password encoder: " ++ usersConf.digest)
                ]
            ]
        , div [ class "col-xs-12 user-list" ]
            [ div [ class "row " ] users ]
        ]


displayUser : User -> Html Msg
displayUser user =
    div [ class "col-xs-12 col-sm-6 col-md-4" ]
        [ div [ class "user" ]
            [ div [ class "row" ]
                [ h4 [ class "col-xs-12" ]
                    [ span [ class "fa fa-user user-icon" ] []
                    , text user.login
                    ]
                ]
            , div []
                [ h6 [] [ span [] [ text "RIGHTS" ] ]
                , div [ class "list-auths" ] (displayAuth user.authz)
                ]
            ]
        ]


displayAuth : String -> List (Html Msg)
displayAuth auth =
    let
        auths = String.split "," auth
                |> List.map (\x -> span [ class "auth" ] [ text x ])
    in
    auths



------------------------------
-- NOTIFICATIONS --
------------------------------


getErrorMessage : Http.Error -> String
getErrorMessage e =
    let
        errMessage =
            case e of
                Http.BadStatus b ->
                    let
                        status =
                            b.status

                        message =
                            status.message
                    in
                    "Code " ++ String.fromInt status.code ++ " : " ++ message

                Http.BadUrl str ->
                    "Invalid API url"

                Http.Timeout ->
                    "It took too long to get a response"

                Http.NetworkError ->
                    "Network error"

                Http.BadPayload str rstr ->
                    str
    in
    errMessage


defaultConfig : Toasty.Config Msg
defaultConfig =
    Toasty.Defaults.config
        |> Toasty.delay 999999999
        |> Toasty.containerAttrs
            [ style "position" "fixed"
            , style "top" "50px"
            , style "right" "30px"
            , style "width" "100%"
            , style "max-width" "500px"
            , style "list-style-type" "none"
            , style "padding" "0"
            , style "margin" "0"
            ]


tempConfig : Toasty.Config Msg
tempConfig =
    defaultConfig |> Toasty.delay 3000


addTempToast : Toasty.Defaults.Toast -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
addTempToast toast ( model, cmd ) =
    Toasty.addToast tempConfig ToastyMsg toast ( model, cmd )


addToast : Toasty.Defaults.Toast -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
addToast toast ( model, cmd ) =
    Toasty.addToast defaultConfig ToastyMsg toast ( model, cmd )


createSuccessNotification : String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createSuccessNotification message =
    addTempToast (Toasty.Defaults.Success "Success!" message)


createErrorNotification : String -> Http.Error -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createErrorNotification message e =
    addToast (Toasty.Defaults.Error "Error..." (message ++ "  (" ++ getErrorMessage e ++ ")"))


createDecodeErrorNotification : String -> String -> ( Model, Cmd Msg ) -> ( Model, Cmd Msg )
createDecodeErrorNotification message e =
    addToast (Toasty.Defaults.Error "Error..." (message ++ "  (" ++ e ++ ")"))
