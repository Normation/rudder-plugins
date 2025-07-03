module RudderLinkUtil exposing (ContextPath, directiveLink, getApiUrl, getContextPath, groupLink, nodeLink, paramLink, ruleLink, targetLink)

import Html exposing (Html, a, span, text)
import Html.Attributes exposing (href)
import RudderDataTypes exposing (SimpleTarget)


type ContextPath
    = ContextPath String


getContextPath : String -> ContextPath
getContextPath =
    ContextPath


getApiUrl : ContextPath -> String -> String
getApiUrl (ContextPath contextPath) url =
    contextPath ++ "/secure/api/" ++ url


directiveLink : ContextPath -> String -> String -> Html msg
directiveLink (ContextPath contextPath) id name =
    span []
        [ a [ href (contextPath ++ "/secure/configurationManager/directiveManagement#{\"directiveId\":\"" ++ id ++ "\"}") ]
            [ text name ]
        , text (" (Rudder ID : " ++ id ++ ")")
        ]


groupLink : ContextPath -> String -> String -> Html msg
groupLink (ContextPath contextPath) id name =
    span []
        [ a [ href (contextPath ++ "/secure/nodeManager/groups#{\"groupId\":\"" ++ id ++ "\"}") ]
            [ text name ]
        , text (" (Rudder ID : " ++ id ++ ")")
        ]


targetLink : ContextPath -> SimpleTarget -> Html msg
targetLink (ContextPath contextPath) target =
    span []
        [ a [ href (contextPath ++ "/secure/nodeManager/groups#{\"" ++ target.targetType ++ "\":\"" ++ target.id ++ "\"}") ]
            [ text target.name ]
        , text (" (Rudder ID : " ++ target.id ++ ")")
        ]


ruleLink : ContextPath -> String -> String -> Html msg
ruleLink (ContextPath contextPath) ruleId ruleName =
    span []
        [ a [ href (contextPath ++ "/secure/configurationManager/ruleManagement/rule/" ++ ruleId) ]
            [ text ruleName ]
        , text (" (Rudder ID : " ++ ruleId ++ ")")
        ]


paramLink : ContextPath -> String -> Html msg
paramLink (ContextPath contextPath) paramName =
    a [ href (contextPath ++ "/secure/configurationManager/parameterManagement") ] [ text paramName ]


nodeLink : ContextPath -> String -> String -> Html msg
nodeLink (ContextPath contextPath) nodeId nodeName =
    span []
        [ a [ href (contextPath ++ "/secure/nodeManager/node/" ++ nodeId) ]
            [ text nodeName ]
        , text (" (Rudder ID : " ++ nodeId ++ ")")
        ]
