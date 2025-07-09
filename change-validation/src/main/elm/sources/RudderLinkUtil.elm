module RudderLinkUtil exposing (ContextPath, directiveLink, directiveLinkWithId, getApiUrl, getContextPath, groupLink, groupLinkWithId, nodeLinkWithId, paramLink, ruleLink, ruleLinkWithId, targetLinkWithId)

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
        ]


directiveLinkWithId : ContextPath -> String -> String -> Html msg
directiveLinkWithId (ContextPath contextPath) id name =
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
        ]


groupLinkWithId : ContextPath -> String -> String -> Html msg
groupLinkWithId (ContextPath contextPath) id name =
    span []
        [ a [ href (contextPath ++ "/secure/nodeManager/groups#{\"groupId\":\"" ++ id ++ "\"}") ]
            [ text name ]
        , text (" (Rudder ID : " ++ id ++ ")")
        ]


targetLinkWithId : ContextPath -> SimpleTarget -> Html msg
targetLinkWithId (ContextPath contextPath) target =
    let
        targetType =
            case target.targetType of
                RudderDataTypes.Group ->
                    "groupId"

                RudderDataTypes.NonGroup ->
                    "target"
    in
    span []
        [ a [ href (contextPath ++ "/secure/nodeManager/groups#{\"" ++ targetType ++ "\":\"" ++ target.id ++ "\"}") ]
            [ text target.name ]
        , text (" (Rudder ID : " ++ target.id ++ ")")
        ]


ruleLink : ContextPath -> String -> String -> Html msg
ruleLink (ContextPath contextPath) ruleId ruleName =
    span []
        [ a [ href (contextPath ++ "/secure/configurationManager/ruleManagement/rule/" ++ ruleId) ]
            [ text ruleName ]
        ]


ruleLinkWithId : ContextPath -> String -> String -> Html msg
ruleLinkWithId (ContextPath contextPath) ruleId ruleName =
    span []
        [ a [ href (contextPath ++ "/secure/configurationManager/ruleManagement/rule/" ++ ruleId) ]
            [ text ruleName ]
        , text (" (Rudder ID : " ++ ruleId ++ ")")
        ]


paramLink : ContextPath -> String -> Html msg
paramLink (ContextPath contextPath) paramName =
    a [ href (contextPath ++ "/secure/configurationManager/parameterManagement") ] [ text paramName ]


nodeLinkWithId : ContextPath -> String -> String -> Html msg
nodeLinkWithId (ContextPath contextPath) nodeId nodeName =
    span []
        [ a [ href (contextPath ++ "/secure/nodeManager/node/" ++ nodeId) ]
            [ text nodeName ]
        , text (" (Rudder ID : " ++ nodeId ++ ")")
        ]
