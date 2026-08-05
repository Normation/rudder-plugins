/*
 *************************************************************************************
 * Copyright 2026 Normation SAS
 *************************************************************************************
 *
 * This file is part of Rudder.
 *
 * Rudder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * In accordance with the terms of section 7 (7. Additional Terms.) of
 * the GNU General Public License version 3, the copyright holders add
 * the following Additional permissions:
 * Notwithstanding to the terms of section 5 (5. Conveying Modified Source
 * Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
 * Public License version 3, when you create a Related Module, this
 * Related Module is not considered as a part of the work and may be
 * distributed under the license agreement of your choice.
 * A "Related Module" means a set of sources files including their
 * documentation that, without modification of the Source Code, enables
 * supplementary functions or services in addition to those offered by
 * the Software.
 *
 * Rudder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

 *
 *************************************************************************************
 */

package com.normation.plugins.changevalidation

import com.normation.errors.*
import com.normation.plugins.AlwaysEnabledPluginStatus
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.*
import com.normation.plugins.changevalidation.api.ChangeRequestApiImpl
import com.normation.plugins.changevalidation.api.ChangeValidationSecurityError
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Rights
import com.normation.rudder.api.ApiAuthorization
import com.normation.rudder.batch.AsyncWorkflowInfo
import com.normation.rudder.domain.workflows.WorkflowNodeId
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.services.modification.DiffServiceImpl
import com.normation.rudder.services.workflows.ChangeRequestAuthorship
import com.normation.rudder.services.workflows.ChangeRequestAuthorship.*
import com.normation.rudder.tenants.DefaultTenantCheckLogic
import com.normation.rudder.tenants.TenantAccessGrant
import com.normation.rudder.users.AuthenticatedUser
import com.normation.rudder.users.RudderAccount
import com.normation.rudder.users.UserPassword
import org.junit.runner.RunWith
import zio.*
import zio.syntax.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

/*
 * Tests for the self-validation / self-deployment control after regression in https://issues.rudder.io/issues/29240
 *  We check both enforcement layers:
 * - the workflow service, which offers or omits transitions (`findNextSteps`/`findBackSteps`);
 * - the API guard `ChangeRequestApiImpl.checkSelfAction`, which denies them.
 *
 * The core rule: a user can only validate/deploy their OWN change request when the matching
 * setting (self-validation / self-deployment) is enabled; acting on someone else's change is
 * always allowed (subject to rights). It must fail closed if a setting cannot be read.
 * ┌─────┬────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────┐
 * │     │           Matrix           │                                                     What it locks in                  │
 * ├─────┼────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤
 * │ A   │ findNextSteps @ validation │ self-validation gates the validate transition — incl. the regressed case (author +    │
 * │     │                            │ disabled → no Deployment)                                                             │
 * ├─────┼────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤
 * │ B   │ findNextSteps @ deployment │ self-deployment gates the deploy transition                                           │
 * ├─────┼────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤
 * │ C   │ findBackSteps (cancel)     │ cancel follows the same canValidate/canDeploy control                                 │
 * ├─────┼────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤
 * │ D   │ RBAC not bypassed          │ self-* setting never grants a missing Validator.Edit/Deployer.Edit                    │
 * ├─────┼────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┤
 * │ E   │ checkSelfAction (API       │ denies forbidden self actions with ChangeValidationSecurityError, allows non-authors, │
 * │     │ guard)                     │ and fails closed when a setting can't be read                                         │
 * └─────┴────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────┘
 */
@RunWith(classOf[ZTestJUnitRunner])
class SelfValidationWorkflowTest extends ZIOSpecDefault {

  private val restTestSetUp = RestTestSetUp.newEnv
  private val mockServices  = new MockServices()

  // an authenticated user granted exactly `granted`; `name` is what a change request `owner` is matched against
  private def userWith(granted: Set[AuthorizationType], userLogin: String = "some-user"): AuthenticatedUser = {
    new AuthenticatedUser {
      override val account:     RudderAccount     = RudderAccount.User(userLogin, UserPassword.fromSecret("pwd"))
      override val authz:       Rights            = Rights.AnyRights
      override val apiAuthz:    ApiAuthorization  = ApiAuthorization.RW
      override def accessGrant: TenantAccessGrant = TenantAccessGrant.All
      override def actorIp:     Option[String]    = None

      override def checkRights(auth: AuthorizationType): Boolean = granted.contains(auth)
    }
  }

  // a user holding both workflow rights, so tests can isolate the self-validation control from RBAC
  private val validatorAndDeployer =
    userWith(Set(AuthorizationType.Validator.Edit, AuthorizationType.Deployer.Edit))

  private def mkService(
      selfValidation: () => IOResult[Boolean],
      selfDeployment: () => IOResult[Boolean]
  ): TwoValidationStepsWorkflowServiceImpl = {
    new TwoValidationStepsWorkflowServiceImpl(
      mockServices.workflowEventLogService,
      mockServices.commitAndDeployChangeRequest,
      mockServices.workflowRepository,
      mockServices.workflowRepository,
      new AsyncWorkflowInfo,
      restTestSetUp.uuidGen,
      mockServices.changeRequestEventLogService,
      mockServices.changeRequestRepository,
      mockServices.changeRequestRepository,
      mockServices.notificationService,
      mockServices.userService,
      new DefaultTenantCheckLogic(),
      () => true.succeed,
      selfValidation,
      selfDeployment
    )
  }

  private def mkApi(
      selfValidation: () => IOResult[Boolean],
      selfDeployment: () => IOResult[Boolean]
  ): ChangeRequestApiImpl = {
    new ChangeRequestApiImpl(
      new DiffServiceImpl,
      restTestSetUp.mockTechniques.techniqueRepo,
      mockServices.changeRequestRepository,
      mockServices.changeRequestRepository,
      mockServices.workflowRepository,
      restTestSetUp.workflowLevelService,
      mockServices.commitAndDeployChangeRequest,
      mockServices.userPropertyService,
      selfValidation,
      selfDeployment,
      new DefaultTenantCheckLogic()
    )(using AlwaysEnabledPluginStatus)
  }

  private val selfValidateOK:     () => IOResult[Boolean] = () => true.succeed
  private val cannotSelfValidate: () => IOResult[Boolean] = () => false.succeed
  private val selfDeployOK:       () => IOResult[Boolean] = () => true.succeed
  private val cannotSelfDeploy:   () => IOResult[Boolean] = () => false.succeed
  private val failing:            () => IOResult[Boolean] = () => Inconsistency("cannot read setting").fail

  // the transitions offered from a step, by target id
  private def nextTargets(
      svc:        TwoValidationStepsWorkflowServiceImpl,
      step:       WorkflowNodeId,
      authorship: ChangeRequestAuthorship
  )(implicit user: AuthenticatedUser): Seq[WorkflowNodeId] =
    svc.findNextSteps(step, authorship).actions.map(_._1)

  given user: AuthenticatedUser = validatorAndDeployer

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("self-validation / self-deployment control")(
    // ---------------------------------------------------------------------------------------
    // Matrix A: findNextSteps at "Pending validation" — the "validate" transition to
    // "Pending deployment" is gated by self-validation when the user is the author.
    // ---------------------------------------------------------------------------------------
    suite("A - findNextSteps: self-validation gates the validate transition")(
      test("author CAN validate when self-validation is enabled") {
        val targets = nextTargets(mkService(selfValidateOK, selfDeployOK), Validation.id, Author)
        assertTrue(targets.contains(Deployment.id))
      },
      test("author CANNOT validate their own change when self-validation is disabled") {
        val targets = nextTargets(mkService(cannotSelfValidate, selfDeployOK), Validation.id, Author)
        assertTrue(!targets.contains(Deployment.id))
      },
      test("a non-author CAN validate even when self-validation is disabled") {
        val targets = nextTargets(mkService(cannotSelfValidate, selfDeployOK), Validation.id, NotAuthor)
        assertTrue(targets.contains(Deployment.id))
      }
    ),

    // ---------------------------------------------------------------------------------------
    // Matrix B: findNextSteps deploy transition ("Pending deployment" -> "Deployed") is gated
    // by self-deployment when the user is the author.
    // ---------------------------------------------------------------------------------------
    suite("B - findNextSteps: self-deployment gates the deploy transition")(
      test("author CAN deploy when self-deployment is enabled") {
        val targets = nextTargets(mkService(selfValidateOK, selfDeployOK), Deployment.id, Author)
        assertTrue(targets.contains(Deployed.id))
      },
      test("author CANNOT deploy their own change when self-deployment is disabled") {
        val targets = nextTargets(mkService(selfValidateOK, cannotSelfDeploy), Deployment.id, Author)
        assertTrue(!targets.contains(Deployed.id))
      },
      test("a non-author CAN deploy even when self-deployment is disabled") {
        val targets = nextTargets(mkService(selfValidateOK, cannotSelfDeploy), Deployment.id, NotAuthor)
        assertTrue(targets.contains(Deployed.id))
      }
    ),

    // ---------------------------------------------------------------------------------------
    // Matrix C: findBackSteps (cancel) uses the same control — canValidate at "Pending
    // validation", canDeploy at "Pending deployment".
    // ---------------------------------------------------------------------------------------
    suite("C - findBackSteps: cancel follows the same self-* control")(
      test("author CANNOT cancel from validation when self-validation is disabled") {
        val backs = mkService(cannotSelfValidate, selfDeployOK).findBackSteps(Validation.id, Author).map(_._1)
        assertTrue(!backs.contains(Cancelled.id))
      },
      test("author CAN cancel from validation when self-validation is enabled") {
        val backs = mkService(selfValidateOK, selfDeployOK).findBackSteps(Validation.id, Author).map(_._1)
        assertTrue(backs.contains(Cancelled.id))
      },
      test("author CANNOT cancel from deployment when self-deployment is disabled") {
        val backs = mkService(selfValidateOK, cannotSelfDeploy).findBackSteps(Deployment.id, Author).map(_._1)
        assertTrue(!backs.contains(Cancelled.id))
      }
    ),

    // ---------------------------------------------------------------------------------------
    // Matrix D: the self-* setting NEVER grants a missing right. A user without Validator.Edit
    // gets no validate transition even for someone else's change with self-validation enabled.
    // ---------------------------------------------------------------------------------------
    suite("D - RBAC is still required (self-* never bypasses rights)")(
      test("no validate transition without Validator.Edit, even for a non-author with self-validation enabled") {
        implicit val user: AuthenticatedUser = userWith(Set(AuthorizationType.Deployer.Edit))
        val targets = nextTargets(mkService(selfValidateOK, selfDeployOK), Validation.id, NotAuthor)
        assertTrue(!targets.contains(Deployment.id))
      },
      test("no deploy transition without Deployer.Edit, even for a non-author with self-deployment enabled") {
        implicit val user: AuthenticatedUser = userWith(Set(AuthorizationType.Validator.Edit))
        val targets = nextTargets(mkService(selfValidateOK, selfDeployOK), Deployment.id, NotAuthor)
        assertTrue(!targets.contains(Deployed.id))
      }
    ),

    // ---------------------------------------------------------------------------------------
    // Matrix E: checkSelfAction (API defense-in-depth) denies the forbidden self actions.
    // ---------------------------------------------------------------------------------------
    suite("E - checkSelfAction: API guard denies forbidden self actions")(
      test("author validating their own change is denied (SecurityError) when self-validation is disabled") {
        mkApi(cannotSelfValidate, selfDeployOK)
          .checkSelfAction(Validation.id, Deployment.id, Author)
          .either
          .map(res => assertTrue(res.left.toOption.exists(_.isInstanceOf[ChangeValidationSecurityError])))
      },
      test("author validating their own change is allowed when self-validation is enabled") {
        mkApi(selfValidateOK, selfDeployOK)
          .checkSelfAction(Validation.id, Deployment.id, Author)
          .either
          .map(res => assertTrue(res.isRight))
      },
      test("a non-author is allowed even when self-validation is disabled") {
        mkApi(cannotSelfValidate, cannotSelfDeploy)
          .checkSelfAction(Validation.id, Deployment.id, NotAuthor)
          .either
          .map(res => assertTrue(res.isRight))
      },
      test("author deploying their own change is denied when self-deployment is disabled") {
        mkApi(selfValidateOK, cannotSelfDeploy)
          .checkSelfAction(Deployment.id, Deployed.id, Author)
          .either
          .map(res => assertTrue(res.left.toOption.exists(_.isInstanceOf[ChangeValidationSecurityError])))
      },
      test("validate-and-deploy in one step is a deployment: denied for the author when self-deployment is disabled") {
        mkApi(selfValidateOK, cannotSelfDeploy)
          .checkSelfAction(Validation.id, Deployed.id, Author)
          .either
          .map(res => assertTrue(res.left.toOption.exists(_.isInstanceOf[ChangeValidationSecurityError])))
      },
      test("fails closed: a setting that cannot be read denies the author's self action") {
        mkApi(failing, selfDeployOK)
          .checkSelfAction(Validation.id, Deployment.id, Author)
          .either
          .map(res => assertTrue(res.left.toOption.exists(_.isInstanceOf[ChangeValidationSecurityError])))
      }
    )
  )
}
