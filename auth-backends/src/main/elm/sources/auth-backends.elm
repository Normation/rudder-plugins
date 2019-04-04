port module AuthBackends exposing (AdminConfig, AuthConfig, ConfigOption, FileConfig, LdapConfig, Model, Msg(..), RadiusConfig, addTempToast, addToast, backendConfigOption, backendDescription, backendTitle, createDecodeErrorNotification, createErrorNotification, createSuccessNotification, decodeAdminConfig, decodeApiCurrentAuthConf, decodeConfigOption, decodeCurrentAuthConf, decodeFileConfig, decodeLdapConfig, decodeRadiusConfig, defaultConfig, displayAdminConfig, displayAuthConfig, displayBackendId, displayFileConfig, displayLdapConfig, displayProvidConfig, displayRadiusConfig, getErrorMessage, getTargets, init, main, subscriptions, tempConfig, update, view)

import Html exposing (..)
import Browser
import Html.Attributes exposing (checked, class, style, type_)
import Http exposing (..)
import Json.Decode as D exposing (Decoder, succeed)
import Json.Decode.Pipeline exposing (..)
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
        initModel =
            Model flags.contextPath Nothing Toasty.initialState
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


type alias AuthConfig =
    { declaredProviders : String
    , computedProviders : List String
    , adminConfig : AdminConfig
    , fileConfig : FileConfig
    , ldapConfig : LdapConfig
    , radiusConfig : RadiusConfig
    }


type alias ConfigOption =
    { description : String
    , key : String
    , value : String
    }


type alias AdminConfig =
    { description : String
    , login : ConfigOption
    , password : ConfigOption
    , enabled : Bool
    }


type alias FileConfig =
    { providerId : String
    , description : String
    , filePath : String
    }


type alias LdapConfig =
    { providerId : String
    , description : String
    , ldapUrl : ConfigOption
    , bindDn : ConfigOption
    , bindPassword : ConfigOption
    , searchBase : ConfigOption
    , ldapFilter : ConfigOption
    }


type alias RadiusConfig =
    { providerId : String
    , description : String
    , hostName : ConfigOption
    , hostPort : ConfigOption
    , secret : ConfigOption
    , timeout : ConfigOption
    , retries : ConfigOption
    , protocol : ConfigOption
    }


type alias Model =
    { contextPath : String
    , currentConfig : Maybe AuthConfig -- from API
    , toasties : Toasty.Stack Toasty.Defaults.Toast
    }


type Msg
    = GetCurrentAuthConfig (Result Error AuthConfig)
      -- NOTIFICATIONS
    | ToastyMsg (Toasty.Msg Toasty.Defaults.Toast)



------------------------------
-- API --
------------------------------
-- API call to get the category tree


getTargets : Model -> Cmd Msg
getTargets model =
    let
        url =
            model.contextPath ++ "/secure/api/authbackends/current-configuration"

        headers =
            []

        req =
            request
                { method = "GET"
                , headers = []
                , url = url
                , body = emptyBody
                , expect = expectJson decodeApiCurrentAuthConf
                , timeout = Nothing
                , withCredentials = False
                }
    in
    send GetCurrentAuthConfig req



-- encode / decode JSON
-- decode the JSON answer from a "get" API call - only "data" field content is interesting


decodeApiCurrentAuthConf : Decoder AuthConfig
decodeApiCurrentAuthConf =
    D.at [ "data" ] decodeCurrentAuthConf


decodeCurrentAuthConf : Decoder AuthConfig
decodeCurrentAuthConf =
    succeed AuthConfig
        |> required "declaredProviders" D.string
        |> required "computedProviders" (D.list <| D.string)
        |> required "adminConfig" decodeAdminConfig
        |> required "fileConfig" decodeFileConfig
        |> required "ldapConfig" decodeLdapConfig
        |> required "radiusConfig" decodeRadiusConfig


decodeConfigOption : Decoder ConfigOption
decodeConfigOption =
    succeed ConfigOption
        |> required "description" D.string
        |> required "key" D.string
        |> required "value" D.string


decodeAdminConfig : Decoder AdminConfig
decodeAdminConfig =
    succeed AdminConfig
        |> required "description" D.string
        |> required "login" decodeConfigOption
        |> required "password" decodeConfigOption
        |> required "enabled" D.bool


decodeFileConfig : Decoder FileConfig
decodeFileConfig =
    succeed FileConfig
        |> required "providerId" D.string
        |> required "description" D.string
        |> required "filePath" D.string


decodeLdapConfig : Decoder LdapConfig
decodeLdapConfig =
    succeed LdapConfig
        |> required "providerId" D.string
        |> required "description" D.string
        |> required "ldapUrl" decodeConfigOption
        |> required "bindDn" decodeConfigOption
        |> required "bindPassword" decodeConfigOption
        |> required "searchBase" decodeConfigOption
        |> required "ldapFilter" decodeConfigOption


decodeRadiusConfig : Decoder RadiusConfig
decodeRadiusConfig =
    succeed RadiusConfig
        |> required "providerId" D.string
        |> required "description" D.string
        |> required "hostName" decodeConfigOption
        |> required "hostPort" decodeConfigOption
        |> required "secret" decodeConfigOption
        |> required "timeout" decodeConfigOption
        |> required "retries" decodeConfigOption
        |> required "protocol" decodeConfigOption



------------------------------
-- UPDATE --
------------------------------


update : Msg -> Model -> ( Model, Cmd Msg )
update msg model =
    case msg of
        {--Api Calls message --}
        GetCurrentAuthConfig result ->
            case result of
                Ok currentConfig ->
                    let
                        newModel =
                            { model | currentConfig = Just currentConfig }
                    in
                    ( newModel, Cmd.none )

                Err err ->
                    let
                        newModel =
                            { model | currentConfig = Nothing }
                    in
                    ( newModel, Cmd.none ) |> createErrorNotification "Error while trying to fetch settings." err

        ToastyMsg subMsg ->
            Toasty.update defaultConfig ToastyMsg subMsg model



------------------------------
-- VIEW --
------------------------------


view : Model -> Html Msg
view model =
    let
        content =
            case model.currentConfig of
                Nothing ->
                    text "Waiting for data from server..."

                Just config ->
                    displayAuthConfig config
    in
    div [ class "row" ]
        [ content
        , div [ class "toasties" ] [ Toasty.view defaultConfig Toasty.Defaults.view ToastyMsg model.toasties ]
        ]



-- an utility that display a config option


backendConfigOption : ConfigOption -> Html Msg
backendConfigOption config =
    div [ class "row form-group" ]
        [ div [ class "text-info col-xs-12" ] [ text config.description ]
        , div [ class "col-xs-12" ]
            [ span [ class "key" ] [ text config.key ]
            , span [ class "sep" ] [ text ":" ]
            , span [ class "val" ] [ text config.value ]
            ]
        ]


displayBackendId : String -> Html Msg
displayBackendId id =
    span [ class "backend-id" ]
        [ span [] [ text " - " ]
        , span [] [ b [] [ text "Backend ID: " ] ]
        , span [] [ text id ]
        ]


backendDescription : String -> Html Msg
backendDescription desc =
    p [ class "col-xs-12 callout-fade callout-info" ]
        [ div [ class "marker" ] [ span [ class "glyphicon glyphicon-info-sign" ] [] ]
        , text desc
        ]


backendTitle : String -> String -> Html Msg
backendTitle title providerId =
    h4 []
        [ text title
        , displayBackendId providerId
        ]


displayAuthConfig : AuthConfig -> Html Msg
displayAuthConfig config =
    div []
        [ displayProvidConfig config
        , displayAdminConfig config.adminConfig
        , displayFileConfig config.fileConfig
        , displayLdapConfig config.ldapConfig
        , displayRadiusConfig config.radiusConfig
        ]


displayProvidConfig : AuthConfig -> Html Msg
displayProvidConfig config =
    div [ class "col-xs-12" ]
        [ h4 [] [ text "Currently configured provider sequence" ]
        , div [ class "col-xs-12 callout-fade callout-info" ]
            [ div [ class "marker" ] [ span [ class "glyphicon glyphicon-info-sign" ] [] ]
            , p []
                [ text """Rudder relies on authentication providers to decide if an user can log in
                   into the application.
                   When an user tries to log in, all of the configured providers are tried in
                   sequence to find if any allows the user log in.
                   You can configure the sequence of tested provider with the configuration
                   key 'rudder.auth.provider' which accept a comma-separated list of
                   provider ID."""
                ]
            , p []
                [ text """By default, the 'file' provider is used, which correspond to the authentication configured in file"""
                , b [] [ text """ /opt/rudder/etc/rudder-users.xml""" ]
                , text "."
                ]
            , p [] [ text """A special provider can be added for a root admin. It's an hook to
                     alway let the possibility to have at least one access in the application.
                     That provider does not have to be declared in the list, and is always
                     tested first the root admin is enabled.
                  """ ]
            ]
        , div [ class "row form-group" ]
            [ label [ class "col-xs-12" ] [ text "Configured list of providers" ]
            , div [ class "col-xs-12 text-info" ] [ text "This is value of key 'rudder.auth.provider' as configured in 'rudder-web.properties'." ]
            , div [ class "col-xs-12 tag-list" ] [ span [ class "tag" ] [ text config.declaredProviders ] ]
            ]
        , div [ class "row form-group" ]
            [ label [ class "col-xs-12" ] [ text "Computed list of providers" ]
            , div [ class "col-xs-12 text-info" ] [ text "This is the list of providers actually used by Rudder, once hooks and plugin status are resolbed." ]
            , div [ class "col-xs-12 tag-list" ] (config.computedProviders |> List.map (\x -> span [ class "tag" ] [ text x ]))
            ]
        ]


displayAdminConfig : AdminConfig -> Html Msg
displayAdminConfig admin =
    div [ class "col-xs-12" ]
        [ h4 [] [ text "Root Admin account" ]
        , backendDescription admin.description
        , backendConfigOption admin.login
        , backendConfigOption admin.password
        ]


displayFileConfig : FileConfig -> Html Msg
displayFileConfig file =
    div [ class "col-xs-12" ]
        [ backendTitle "File backend" file.providerId
        , backendDescription file.description
        , p []
            [ span [ class "key" ] [ text "Path towards authentication file" ]
            , span [ class "sep" ] [ text ":" ]
            , span [ class "val" ] [ text file.filePath ]
            ]
        ]


displayLdapConfig : LdapConfig -> Html Msg
displayLdapConfig ldap =
    div [ class "col-xs-12" ]
        [ backendTitle "LDAP or AD backend configuration" ldap.providerId
        , backendDescription ldap.description
        , backendConfigOption ldap.ldapUrl
        , backendConfigOption ldap.bindDn
        , backendConfigOption ldap.bindPassword
        , backendConfigOption ldap.searchBase
        , backendConfigOption ldap.ldapFilter
        ]


displayRadiusConfig : RadiusConfig -> Html Msg
displayRadiusConfig radius =
    div [ class "col-xs-12" ]
        [ backendTitle "Radius configuration" radius.providerId
        , backendDescription radius.description
        , backendConfigOption radius.hostName
        , backendConfigOption radius.hostPort
        , backendConfigOption radius.secret
        , backendConfigOption radius.timeout
        , backendConfigOption radius.retries
        , backendConfigOption radius.protocol
        ]



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
