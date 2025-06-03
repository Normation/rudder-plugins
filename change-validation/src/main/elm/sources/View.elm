module View exposing (..)

import ApiCalls exposing (getUsers, saveValidateAllSetting, saveWorkflow)
import DataTypes exposing (ColPos(..), EditMod(..), Model, Msg(..), User, UserList, Username, ValidateAllView(..), WorkflowUsersForm, WorkflowUsersMsg(..), WorkflowUsersView(..), getUsernames)
import Html exposing (..)
import Html.Attributes exposing (attribute, checked, class, disabled, for, id, style, type_, value)
import Html.Events exposing (onCheck, onClick)
import List exposing (isEmpty, length, member)
import String exposing (fromInt)


type alias WorkflowUsersMsg =
    DataTypes.WorkflowUsersMsg


createInfoTootlip : String -> String -> Html Msg
createInfoTootlip content placement =
    span
        [ class "fa fa-exclamation-triangle center-box-element input-icon bstool"
        , attribute "data-bs-toggle" "tooltip"
        , attribute "data-bs-placement" placement
        , attribute "title" content
        ]
        []


createRightInfoSectionParagraphs : List String -> Html Msg
createRightInfoSectionParagraphs paragraphs =
    let
        paragraphElements =
            List.map (\paragraphContent -> p [] [ text paragraphContent ]) paragraphs
    in
    div [ class "section-right" ]
        [ div [ class "doc doc-info" ]
            ([ div [ class "marker" ] [ span [ class "fa fa-info-circle" ] [] ] ] ++ paragraphElements)
        ]



-- Displayed nb items and nb selected items when edition mod is On


displayFooter : WorkflowUsersForm -> ColPos -> Html Msg
displayFooter workflowUsersForm pos =
    let
        isChecked =
            case pos of
                Left ->
                    (length workflowUsersForm.leftChecked == length workflowUsersForm.validatedUsers) && not (isEmpty workflowUsersForm.leftChecked)

                Right ->
                    (length workflowUsersForm.rightChecked == length workflowUsersForm.unvalidatedUsers) && not (isEmpty workflowUsersForm.rightChecked)
    in
    div [ class "box-footer" ]
        [ label [ class "all-items" ]
            [ input
                [ class ""
                , onCheck (\checkStatus -> WorkflowUsersMsg (CheckAll pos checkStatus))
                , type_ "checkbox"
                , value
                    (case pos of
                        Left ->
                            fromInt (length workflowUsersForm.validatedUsers)

                        Right ->
                            fromInt (length workflowUsersForm.unvalidatedUsers)
                    )
                , checked isChecked
                , disabled
                    (case pos of
                        Left ->
                            isEmpty workflowUsersForm.validatedUsers

                        Right ->
                            isEmpty workflowUsersForm.unvalidatedUsers
                    )
                ]
                []
            ]
        , div [ id "nb-items", class "footer-infos nb-items" ]
            [ case pos of
                Left ->
                    text (fromInt (length workflowUsersForm.validatedUsers) ++ " Items")

                Right ->
                    text (fromInt (length workflowUsersForm.unvalidatedUsers) ++ " Items")
            ]
        , div [ id "nb-selected", class "footer-infos" ]
            [ case pos of
                Left ->
                    if (length workflowUsersForm.leftChecked == length workflowUsersForm.validatedUsers) && not (isEmpty workflowUsersForm.leftChecked) then
                        text "All Selected"

                    else
                        text (fromInt (length workflowUsersForm.leftChecked) ++ " Selected")

                Right ->
                    if (length workflowUsersForm.rightChecked == length workflowUsersForm.unvalidatedUsers) && not (isEmpty workflowUsersForm.rightChecked) then
                        text "All Selected"

                    else
                        text (fromInt (length workflowUsersForm.rightChecked) ++ " Selected")
            ]
        ]



{--
  The box containing all users in the system who are not validated
  Displayed when edition mod is On
--}


displayRightCol : WorkflowUsersForm -> EditMod -> Html Msg
displayRightCol workflowUsersForm editMod =
    div [ class "box-users-container" ]
        [ h5 [ class "box-header" ] [ b [] [ text "Users" ] ]
        , div [ class "box-users-content" ]
            [ renderUsers workflowUsersForm.unvalidatedUsers Right editMod workflowUsersForm
            ]
        , displayFooter workflowUsersForm Right
        ]



{--
  Helper function to display individual user's row

  Implements checkbox and selectable logic according
  to which box the user should be in
    Left  -> validated
    Right -> unvalidated

  On edition mod "On" interactions are activated
--}


renderUserHelper : User -> ColPos -> EditMod -> WorkflowUsersForm -> Html Msg
renderUserHelper user pos editMod workflowUsersForm =
    let
        isEditActivate =
            if editMod == On then
                True

            else
                False

        sideChecked =
            case pos of
                Left ->
                    WorkflowUsersMsg (AddLeftChecked user (not (member user workflowUsersForm.leftChecked)))

                Right ->
                    WorkflowUsersMsg (AddRightChecked user (not (member user workflowUsersForm.rightChecked)))

        isChecked =
            case pos of
                Left ->
                    member user workflowUsersForm.leftChecked

                Right ->
                    member user workflowUsersForm.rightChecked

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
        if member user workflowUsersForm.hasMoved then
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


renderUsers : UserList -> ColPos -> EditMod -> WorkflowUsersForm -> Html Msg
renderUsers users pos editMod workflowUsersForm =
    ul [] (List.map (\u -> renderUserHelper u pos editMod workflowUsersForm) users)


displayArrows : WorkflowUsersForm -> Html Msg
displayArrows workflowUsersForm =
    let
        leftArrowBtnType =
            if isEmpty workflowUsersForm.rightChecked then
                "btn btn-sm move-left btn-default"

            else
                "btn btn-sm move-left btn-primary"

        rightArrowBtnType =
            if isEmpty workflowUsersForm.leftChecked then
                "btn btn-sm move-right btn-default"

            else
                "btn btn-sm move-right btn-primary"
    in
    div [ class "list-arrows arrows-validation" ]
        [ button [ onClick (LeftToRight |> WorkflowUsersMsg), class rightArrowBtnType, disabled (isEmpty workflowUsersForm.leftChecked) ]
            [ span [ class "fa fa-chevron-right" ] []
            ]
        , br [] []
        , br [] []
        , button [ onClick (RightToLeft |> WorkflowUsersMsg), class leftArrowBtnType, disabled (isEmpty workflowUsersForm.rightChecked) ]
            [ span [ class "fa fa-chevron-left" ] []
            ]
        ]



{--
  Display all user in the Left box (validated) according the edition mod

  "Save" and "Cancel" button is displayed only if modification have been done
  otherwise "Exit" button is displayed to exit edition mod

  The footer is displayed only in edition mod
--}


displayLeftCol : WorkflowUsersForm -> EditMod -> Html Msg
displayLeftCol workflowUsersForm editMod =
    let
        cancelType =
            if editMod == On && isEmpty workflowUsersForm.hasMoved then
                ExitEditMod |> WorkflowUsersMsg

            else
                CallApi getUsers |> WorkflowUsersMsg

        actnBtnIfModif =
            if editMod == Off then
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
                            if cancelType == (ExitEditMod |> WorkflowUsersMsg) then
                                "Exit"

                            else
                                "Cancel"
                        ]
                    , if not (isEmpty workflowUsersForm.hasMoved) then
                        button
                            [ id "save-workflow "
                            , class "btn btn-success btn-action-workflow"
                            , onClick (CallApi (saveWorkflow (getUsernames workflowUsersForm.validatedUsers)) |> WorkflowUsersMsg)
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
                [ if isEmpty workflowUsersForm.validatedUsers then
                    div [ style "text-align" "center" ]
                        [ if editMod == Off then
                            i [ class "fa fa-user-times empty-validated-user", style "margin-bottom" "10px" ]
                                [ br [] []
                                , p [ class "empty-box-msg" ] [ text "No validated users found" ]
                                ]

                          else
                            div [] []
                        ]

                  else
                    renderUsers workflowUsersForm.validatedUsers Left editMod workflowUsersForm
                ]
            , case editMod of
                On ->
                    div [] []

                Off ->
                    div [ class "circle-edit", onClick (SwitchMode |> WorkflowUsersMsg) ]
                        [ i [ class "edit-icon-validated-user fa fa-pencil", style "margin" "0" ] []
                        ]
            ]
        , case editMod of
            On ->
                displayFooter workflowUsersForm Left

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
            if model.hasWriteRights then
                [ Html.br [] [], displayValidateAllForm model ]

            else
                []
    in
    let
        workflowUsers =
            case model.workflowUsersView of
                WorkflowUsersInitView ->
                    text ""

                WorkflowUsers workflowUsersForm ->
                    div [ class "section-with-doc" ]
                        [ div [ class "section-left" ]
                            [ div
                                []
                                [ case model.editMod of
                                    On ->
                                        div [ class "inner-portlet", style "display" "flex", style "justify-content" "center", id "workflowUsers" ]
                                            [ displayLeftCol workflowUsersForm On
                                            , displayArrows workflowUsersForm
                                            , displayRightCol workflowUsersForm On
                                            ]

                                    Off ->
                                        div [ class "inner-portlet", style "display" "flex", style "justify-content" "center" ]
                                            [ displayLeftCol workflowUsersForm Off
                                            ]
                                ]
                            ]
                        , createRightInfoSectionParagraphs
                            [ " Any modification made by a validated user will be automatically deployed, "
                                ++ "without needing to be validated by another user first. "
                            ]
                        ]
    in
    div
        [ id "workflowUsers" ]
        (workflowUsers :: validateAllForm)


displayValidateAllForm : Model -> Html Msg
displayValidateAllForm model =
    case model.validateAllView of
        ValidateAll formState ->
            div
                [ class "section-with-doc" ]
                [ div [ class "section-left" ]
                    [ form []
                        [ ul []
                            [ li
                                [ class "rudder-form" ]
                                [ div [ class "input-group" ]
                                    [ label
                                        [ class "input-group-addon"
                                        , for "validationAutoValidatedUser"
                                        ]
                                        [ input
                                            [ type_ "checkbox"
                                            , id "validationAutoValidatedUser"
                                            , checked formState.formValues.validateAll
                                            , onClick (WorkflowUsersMsg (ChangeValidateAllSetting (not formState.formValues.validateAll)))
                                            ]
                                            []
                                        , label
                                            [ for "validationAutoValidatedUser", class "label-radio" ]
                                            [ span [ class "fa fa-check" ] [] ]
                                        , span [ class "fa fa-check check-icon" ] []
                                        ]
                                    , label
                                        [ class "form-control", for "validationAutoValidatedUser" ]
                                        [ text " Validate all changes " ]
                                    ]
                                ]
                            ]
                        , input
                            [ type_ "button"
                            , value "Save change"
                            , id "validationAutoSubmit"
                            , class "btn btn-default"
                            , disabled (formState.formValues.validateAll == formState.initValues.validateAll)
                            , onClick (WorkflowUsersMsg (CallApi (saveValidateAllSetting formState.formValues.validateAll)))
                            ]
                            []
                        ]
                    ]
                , createRightInfoSectionParagraphs
                    [ " Any modification made by a validated user will be automatically approved no matter the nature of the change. "
                    , " Hence, configuring the groups below will have no effect on validated users (in the list above), but will apply"
                        ++ " to non-validated users, who will still need to create a change request in order to modify a node from a supervised group. "
                    ]
                ]

        _ ->
            text ""
