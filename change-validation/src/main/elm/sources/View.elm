module View exposing (..)

import ApiCalls exposing (getUsers, saveValidateAllSetting, saveWorkflow)
import DataTypes exposing (ColPos(..), EditMod(..), Model, Msg(..), User, UserList, Username, ViewState(..), getUsernames)
import Html exposing (..)
import Html.Attributes as Attr exposing (attribute, checked, class, disabled, id, style, type_, value)
import Html.Events exposing (onCheck, onClick)
import List exposing (isEmpty, length, member)
import String exposing (fromInt)


createInfoTootlip : String -> String -> Html msg
createInfoTootlip content placement =
    span
        [ class "fa fa-exclamation-triangle center-box-element input-icon bstool"
        , attribute "data-bs-toggle" "tooltip"
        , attribute "data-bs-placement" placement
        , attribute "title" content
        ]
        []


createRightInfoSection : List String -> Html msg
createRightInfoSection paragraphs =
    let
        paragraphElements =
            List.map (\paragraphContent -> p [] [ text paragraphContent ]) paragraphs
    in
    div [ Attr.class "section-right" ]
        [ div [ Attr.class "doc doc-info" ]
            ([ div [ Attr.class "marker" ] [ span [ Attr.class "fa fa-info-circle" ] [] ] ] ++ paragraphElements)
        ]



-- Displayed nb items and nb selected items when edition mod is On


displayFooter : Model -> ColPos -> Html Msg
displayFooter model pos =
    let
        isChecked =
            case pos of
                Left ->
                    (length model.leftChecked == length model.validatedUsers) && not (isEmpty model.leftChecked)

                Right ->
                    (length model.rightChecked == length model.unvalidatedUsers) && not (isEmpty model.rightChecked)
    in
    div [ class "box-footer" ]
        [ label [ class "all-items" ]
            [ input
                [ class ""
                , onCheck (\checkStatus -> CheckAll pos checkStatus)
                , type_ "checkbox"
                , value
                    (case pos of
                        Left ->
                            fromInt (length model.validatedUsers)

                        Right ->
                            fromInt (length model.unvalidatedUsers)
                    )
                , checked isChecked
                , disabled
                    (case pos of
                        Left ->
                            isEmpty model.validatedUsers

                        Right ->
                            isEmpty model.unvalidatedUsers
                    )
                ]
                []
            ]
        , div [ id "nb-items", class "footer-infos nb-items" ]
            [ case pos of
                Left ->
                    text (fromInt (length model.validatedUsers) ++ " Items")

                Right ->
                    text (fromInt (length model.unvalidatedUsers) ++ " Items")
            ]
        , div [ id "nb-selected", class "footer-infos" ]
            [ case pos of
                Left ->
                    if (length model.leftChecked == length model.validatedUsers) && not (isEmpty model.leftChecked) then
                        text "All Selected"

                    else
                        text (fromInt (length model.leftChecked) ++ " Selected")

                Right ->
                    if (length model.rightChecked == length model.unvalidatedUsers) && not (isEmpty model.rightChecked) then
                        text "All Selected"

                    else
                        text (fromInt (length model.rightChecked) ++ " Selected")
            ]
        ]



{--
  The box containing all users in the system who are not validated
  Displayed when edition mod is On
--}


displayRightCol : Model -> Html Msg
displayRightCol model =
    div [ class "box-users-container" ]
        [ h5 [ class "box-header" ] [ b [] [ text "Users" ] ]
        , div [ class "box-users-content" ]
            [ renderUsers model.unvalidatedUsers Right model
            ]
        , displayFooter model Right
        ]



{--
  Helper function to display individual user's row

  Implements checkbox and selectable logic according
  to which box the user should be in
    Left  -> validated
    Right -> unvalidated

  On edition mod "On" interactions are activated
--}


renderUserHelper : User -> ColPos -> Model -> Html Msg
renderUserHelper user pos model =
    let
        isEditActivate =
            if model.editMod == On then
                True

            else
                False

        sideChecked =
            case pos of
                Left ->
                    AddLeftChecked user (not (member user model.leftChecked))

                Right ->
                    AddRightChecked user (not (member user model.rightChecked))

        isChecked =
            case pos of
                Left ->
                    member user model.leftChecked

                Right ->
                    member user model.rightChecked

        content =
            li [ class "li-box-content-user" ]
                [ label [ style "vertical-align" "middle", style "display" "inline-block" ]
                    [ if isEditActivate then
                        input
                            [ class "box-input-element center-box-element"
                            , type_ "checkbox"
                            , value user.username
                            , checked isChecked
                            ]
                            []

                      else
                        div [ class "box-input-element center-box-element" ] []
                    ]
                , div [ class "center-box-element" ] [ text <| user.username ]
                , if not user.isInFile then
                    createInfoTootlip
                        """
            The user <b> doesn't exist anymore </b> but they are still a validated user, delete them by removing them from the validated users.
            """
                        "auto"

                  else
                    div [] []
                ]
    in
    if isEditActivate then
        if member user model.hasMoved then
            if isChecked then
                div [ class "users moved-checked", onClick sideChecked ] [ content ]

            else
                div [ id "moved-user", class "users-clickable", onClick sideChecked ] [ content ]

        else if user.isInFile then
            if isChecked then
                div [ class "users normal-checked", onClick sideChecked ] [ content ]

            else
                div [ id "normal-user", class "users-clickable", onClick sideChecked ] [ content ]

        else if isChecked then
            div [ class "users not-in-file-checked", onClick sideChecked ] [ content ]

        else
            div [ id "not-in-file-user", class "users-clickable", onClick sideChecked ] [ content ]
        -- deactivate the ability to select users in edition mod "Off"

    else if user.isInFile then
        div [ id "normal-user", class "users" ] [ content ]

    else
        div [ id "not-in-file-user", class "users" ] [ content ]



{--
  Display all users according to the box they belong
      Left  -> validated
      Right -> unvalidated
--}


renderUsers : UserList -> ColPos -> Model -> Html Msg
renderUsers users pos model =
    ul [] (List.map (\u -> renderUserHelper u pos model) users)


displayArrows : Model -> Html Msg
displayArrows model =
    let
        leftArrowBtnType =
            if isEmpty model.rightChecked then
                "btn btn-sm move-left btn-default"

            else
                "btn btn-sm move-left btn-primary"

        rightArrowBtnType =
            if isEmpty model.leftChecked then
                "btn btn-sm move-right btn-default"

            else
                "btn btn-sm move-right btn-primary"
    in
    div [ class "list-arrows arrows-validation" ]
        [ button [ onClick LeftToRight, class rightArrowBtnType, disabled (isEmpty model.leftChecked) ]
            [ span [ class "fa fa-chevron-right" ] []
            ]
        , br [] []
        , br [] []
        , button [ onClick RightToLeft, class leftArrowBtnType, disabled (isEmpty model.rightChecked) ]
            [ span [ class "fa fa-chevron-left" ] []
            ]
        ]



{--
  Display all user in the Left box (validated) according the edition mod

  "Save" and "Cancel" button is displayed only if modification have been done
  otherwise "Exit" button is displayed to exit edition mod

  The footer is displayed only in edition mod
--}


displayLeftCol : Model -> Html Msg
displayLeftCol model =
    let
        cancelType =
            if model.editMod == On && isEmpty model.hasMoved then
                ExitEditMod

            else
                CallApi getUsers

        actnBtnIfModif =
            if model.editMod == Off then
                div [] []

            else
                div []
                    [ button
                        [ id "cancel-workflow"
                        , class "btn btn-default btn-action-workflow"
                        , onClick cancelType
                        , type_ "button"
                        ]
                        [ text <|
                            if cancelType == ExitEditMod then
                                "Exit"

                            else
                                "Cancel"
                        ]
                    , if not (isEmpty model.hasMoved) then
                        button
                            [ id "save-workflow "
                            , class "btn btn-success btn-action-workflow"
                            , onClick (CallApi (saveWorkflow (getUsernames model.validatedUsers)))
                            , type_ "button"
                            ]
                            [ text <| "Save" ]

                      else
                        div [] []
                    ]
    in
    div []
        [ div [ class "box-users-container " ]
            [ h5 [ class "box-header" ] [ b [] [ text "Validated users" ] ]
            , div [ class "box-users-content" ]
                [ if isEmpty model.validatedUsers then
                    div [ style "text-align" "center" ]
                        [ if model.editMod == Off then
                            i [ class "fa fa-user-times empty-validated-user", style "margin-bottom" "10px" ]
                                [ br [] []
                                , p [ class "empty-box-msg" ] [ text "No validated users found" ]
                                ]

                          else
                            div [] []
                        ]

                  else
                    renderUsers model.validatedUsers Left model
                ]
            , case model.editMod of
                On ->
                    div [] []

                Off ->
                    div [ class "circle-edit", onClick SwitchMode ]
                        [ i [ class "edit-icon-validated-user fa fa-pencil", style "margin" "0" ] []
                        ]
            ]
        , case model.editMod of
            On ->
                displayFooter model Left

            Off ->
                div [] []
        , div [ class "action-button-container" ]
            [ actnBtnIfModif
            ]
        ]


view : Model -> Html Msg
view model =
    let
        validateAllForm =
            if model.adminWrite then
                [ Html.br [] [], displayValidateAllForm model ]

            else
                [ text "" ]
    in
    let
        workflowUsers =
            div [ Attr.class "section-with-doc" ]
                [ div [ Attr.class "section-left" ]
                    [ div
                        []
                        [ case model.editMod of
                            On ->
                                div [ class "inner-portlet", style "display" "flex", style "justify-content" "center", id "workflowUsers" ]
                                    [ displayLeftCol model
                                    , displayArrows model
                                    , displayRightCol model
                                    ]

                            Off ->
                                div [ class "inner-portlet", style "display" "flex", style "justify-content" "center" ]
                                    [ displayLeftCol model
                                    ]
                        ]
                    ]
                , createRightInfoSection
                    [ " Any modification made by a validated user will be automatically deployed, "
                        ++ "without needing to be validated by another user first. "
                    ]
                ]
    in
    div
        [ Attr.id "workflowUsers" ]
        (List.append [ workflowUsers ] validateAllForm)


displayValidateAllForm : Model -> Html Msg
displayValidateAllForm model =
    case model.viewState of
        Form formState ->
            div
                [ Attr.class "section-with-doc" ]
                [ div [ Attr.class "section-left" ]
                    [ form []
                        [ ul []
                            [ li
                                [ Attr.class "rudder-form" ]
                                [ div [ Attr.class "input-group" ]
                                    [ label
                                        [ Attr.class "input-group-addon"
                                        , Attr.for "validationAutoValidatedUser"
                                        ]
                                        [ input
                                            [ Attr.type_ "checkbox"
                                            , Attr.id "validationAutoValidatedUser"
                                            , Attr.checked formState.formValues.validateAll
                                            , onClick (ChangeValidateAllSetting (not formState.formValues.validateAll))
                                            ]
                                            []
                                        , label
                                            [ Attr.for "validationAutoValidatedUser", Attr.class "label-radio" ]
                                            [ span [ Attr.class "ion ion-checkmark-round" ] [] ]
                                        , span [ Attr.class "ion ion-checkmark-round check-icon" ] []
                                        ]
                                    , label
                                        [ Attr.class "form-control", Attr.for "validationAutoValidatedUser" ]
                                        [ text " Validate all changes " ]
                                    ]
                                ]
                            ]
                        , input
                            [ Attr.type_ "submit"
                            , Attr.value "Save change"
                            , Attr.id "validationAutoSubmit"
                            , Attr.class "btn btn-default"
                            , Attr.disabled (formState.formValues.validateAll == formState.initValues.validateAll)
                            , onClick (CallApi (saveValidateAllSetting formState.formValues.validateAll))
                            ]
                            []
                        ]
                    ]
                , createRightInfoSection
                    [ " Any modification made by a validated user will be automatically approved no matter the nature of the change. "
                    , " Hence, configuring the groups below will have no effect on validated users (in the list above), but will apply"
                        ++ " to non-validated users, who will still need to create a change request in order to modify a node from a supervised group. "
                    ]
                ]

        _ ->
            text ""
