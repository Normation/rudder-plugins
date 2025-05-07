module WorkflowInformationTest exposing (..)

import Expect
import Fuzz exposing (int, maybe)
import Test exposing (..)
import WorkflowInformation exposing (PendingCountOpt(..), pendingCountOpt)


expectedTotalCount nonOptCount optCount =
    case optCount of
        Just res ->
            res + nonOptCount

        Nothing ->
            nonOptCount


suite =
    describe "pending count"
        [ -- A PendingCount Json that has two empty fields will always be decoded as a NotSet object
          test "NotSet" <|
            \_ ->
                pendingCountOpt Nothing Nothing
                    |> Expect.equal NotSet
        , -- A PendingCount Json whose pendingValidation field is present will always be decoded as a PendingCountWithTotal object
          fuzz2
            int
            (maybe int)
            "Non-empty pendingValidation field"
          <|
            \pendingValidation pendingDeploymentOpt ->
                pendingCountOpt (Just pendingValidation) pendingDeploymentOpt
                    |> Expect.equal
                        (PendingCountWithTotal
                            { pendingValidation = Just pendingValidation
                            , pendingDeployment = pendingDeploymentOpt
                            , totalCount = expectedTotalCount pendingValidation pendingDeploymentOpt
                            }
                        )
        , -- A PendingCount Json whose pendingDeployment field is present will always be decoded as a PendingCountWithTotal object
          fuzz2
            (maybe int)
            int
            "Non-empty pendingDeployment field"
          <|
            \pendingValidationOpt pendingDeployment ->
                pendingCountOpt pendingValidationOpt (Just pendingDeployment)
                    |> Expect.equal
                        (PendingCountWithTotal
                            { pendingValidation = pendingValidationOpt
                            , pendingDeployment = Just pendingDeployment
                            , totalCount = expectedTotalCount pendingDeployment pendingValidationOpt
                            }
                        )
        , -- A PendingCount Json whose pendingValidation and pendingDeployment fields are both present will always be decoded as a PendingCountWithTotal object
          fuzz2
            int
            int
            "Non-empty pendingDeployment and pendingValidation fields"
          <|
            \pendingValidation pendingDeployment ->
                pendingCountOpt (Just pendingValidation) (Just pendingDeployment)
                    |> Expect.equal
                        (PendingCountWithTotal
                            { pendingValidation = Just pendingValidation
                            , pendingDeployment = Just pendingDeployment
                            , totalCount = pendingValidation + pendingDeployment
                            }
                        )
        , -- After decoding, the obtained PendingCountOpt object must be a 'NotSet' if the two given fields are equal to 'Nothing', and be a 'PendingCountWithTotal' object whose fields are exactly equal to the given values.
          fuzz2
            (maybe int)
            (maybe int)
            "PendingCountWithTotal cannot have two empty fields"
          <|
            \pendingValidation pendingDeployment ->
                case pendingCountOpt pendingValidation pendingDeployment of
                    NotSet ->
                        ( pendingValidation, pendingDeployment ) |> Expect.equal ( Nothing, Nothing )

                    PendingCountWithTotal pendingCount ->
                        case ( pendingCount.pendingValidation, pendingCount.pendingDeployment ) of
                            ( Nothing, Nothing ) ->
                                Expect.fail "PendingCountWithTotal cannot have both of its pendingValidation and pendingDeployment fields be equal to 'Nothing'"

                            ( _, _ ) ->
                                ( pendingCount.pendingValidation, pendingCount.pendingDeployment ) |> Expect.equal ( pendingValidation, pendingDeployment )
        ]
