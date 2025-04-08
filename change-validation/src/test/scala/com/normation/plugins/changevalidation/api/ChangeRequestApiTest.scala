/*
 *************************************************************************************
 * Copyright 2023 Normation SAS
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

package com.normation.plugins.changevalidation.api

import better.files.File
import bootstrap.rudder.plugin.ChangeValidationWorkflowLevelService
import com.normation.cfclerk.domain.InputVariableSpec
import com.normation.cfclerk.domain.SectionSpec
import com.normation.cfclerk.domain.TechniqueName
import com.normation.errors.IOResult
import com.normation.errors.effectUioUnit
import com.normation.eventlog.EventActor
import com.normation.plugins.AlwaysEnabledPluginStatus
import com.normation.plugins.changevalidation.MockServices
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.*
import com.normation.rudder.MockGlobalParam
import com.normation.rudder.MockNodes
import com.normation.rudder.api.ApiVersion
import com.normation.rudder.batch.AsyncWorkflowInfo
import com.normation.rudder.domain.nodes.AddNodeGroupDiff
import com.normation.rudder.domain.nodes.DeleteNodeGroupDiff
import com.normation.rudder.domain.nodes.ModifyToNodeGroupDiff
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.nodes.NodeGroupUid
import com.normation.rudder.domain.policies.AddDirectiveDiff
import com.normation.rudder.domain.policies.AddRuleDiff
import com.normation.rudder.domain.policies.AllTargetExceptPolicyServers
import com.normation.rudder.domain.policies.DeleteDirectiveDiff
import com.normation.rudder.domain.policies.DeleteRuleDiff
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.DirectiveUid
import com.normation.rudder.domain.policies.ModifyToDirectiveDiff
import com.normation.rudder.domain.policies.ModifyToRuleDiff
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.policies.RuleUid
import com.normation.rudder.domain.policies.Tags
import com.normation.rudder.domain.properties.AddGlobalParameterDiff
import com.normation.rudder.domain.properties.DeleteGlobalParameterDiff
import com.normation.rudder.domain.properties.ModifyToGlobalParameterDiff
import com.normation.rudder.domain.queries.CriterionComposition.*
import com.normation.rudder.domain.queries.Query
import com.normation.rudder.domain.queries.QueryReturnType.*
import com.normation.rudder.domain.queries.ResultTransformation
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ChangeRequestInfo
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.DirectiveChange
import com.normation.rudder.domain.workflows.DirectiveChangeItem
import com.normation.rudder.domain.workflows.DirectiveChanges
import com.normation.rudder.domain.workflows.GlobalParameterChange
import com.normation.rudder.domain.workflows.GlobalParameterChangeItem
import com.normation.rudder.domain.workflows.GlobalParameterChanges
import com.normation.rudder.domain.workflows.NodeGroupChange
import com.normation.rudder.domain.workflows.NodeGroupChangeItem
import com.normation.rudder.domain.workflows.NodeGroupChanges
import com.normation.rudder.domain.workflows.RuleChange
import com.normation.rudder.domain.workflows.RuleChangeItem
import com.normation.rudder.domain.workflows.RuleChanges
import com.normation.rudder.rest.RestTestSetUp
import com.normation.rudder.rest.TraitTestApiFromYamlFiles
import com.normation.rudder.services.modification.DiffServiceImpl
import java.nio.file.Files
import org.joda.time.DateTime
import org.junit.runner.RunWith
import zio.*
import zio.syntax.*
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class ChangeRequestApiTest extends ZIOSpecDefault {
  val restTestSetUp = RestTestSetUp.newEnv

  val tmpDir: File = File(Files.createTempDirectory("rudder-test-"))
  val yamlSourceDirectory  = "changevalidation_api"
  val yamlDestTmpDirectory = tmpDir / "templates"

  val actor = EventActor("test-user")

  val directiveSectionSpec = {
    SectionSpec(
      name = "sections",
      children = Seq(
        InputVariableSpec("PACKAGE_LIST", "", None, "", id = None),
        InputVariableSpec("PACKAGE_STATE", "", None, "", id = None),
        SectionSpec(
          name = "Package",
          children = Seq(
            SectionSpec(
              name = "Package architecture",
              children = Seq(
                SectionSpec(
                  name = "Package architecture",
                  children = Seq(
                    SectionSpec(
                      name = "PACKAGE_ARCHITECTURE",
                      children = Seq(
                        SectionSpec(
                          name = "PACKAGE_ARCHITECTURE_SPECIFIC"
                        )
                      )
                    )
                  )
                )
              )
            ),
            SectionSpec(
              name = "Package manager",
              children = Seq(
                SectionSpec(
                  name = "PACKAGE_MANAGER"
                )
              )
            ),
            SectionSpec(
              name = "Package version",
              children = Seq(
                SectionSpec(
                  name = "PACKAGE_VERSION",
                  children = Seq(
                    SectionSpec(
                      name = "PACKAGE_VERSION_SPECIFIC"
                    )
                  )
                )
              )
            ),
            SectionSpec(
              name = "Post-modification script",
              children = Seq(
                SectionSpec(
                  name = "PACKAGE_POST_HOOK_COMMAND"
                )
              )
            )
          )
        ),
        SectionSpec(
          name = "Package",
          children = Seq(
            SectionSpec(
              name = "Package architecture",
              children = Seq(
                SectionSpec(
                  name = "Package architecture",
                  children = Seq(
                    SectionSpec(
                      name = "PACKAGE_ARCHITECTURE",
                      children = Seq(
                        SectionSpec(
                          name = "PACKAGE_ARCHITECTURE_SPECIFIC"
                        )
                      )
                    )
                  )
                )
              )
            ),
            SectionSpec(
              name = "Package manager",
              children = Seq(
                SectionSpec(
                  name = "PACKAGE_MANAGER"
                )
              )
            ),
            SectionSpec(
              name = "Package version",
              children = Seq(
                SectionSpec(
                  name = "PACKAGE_VERSION",
                  children = Seq(
                    SectionSpec(
                      name = "PACKAGE_VERSION_SPECIFIC"
                    )
                  )
                )
              )
            ),
            SectionSpec(
              name = "Post-modification script",
              children = Seq(
                SectionSpec(
                  name = "PACKAGE_POST_HOOK_COMMAND"
                )
              )
            )
          )
        )
      )
    )
  }

  val mockDirectives  = restTestSetUp.mockDirectives
  val mockNodeGroups  = restTestSetUp.mockNodeGroups
  val mockRules       = restTestSetUp.mockRules
  val mockGlobalParam = new MockGlobalParam()

  val mockServices = new MockServices(
    Map(
      Validation.id -> List(
        ConfigurationChangeRequest(
          ChangeRequestId(1),
          None,
          ChangeRequestInfo("first cr directive", "My directive first change"),
          Map(
            DirectiveId(DirectiveUid("directive_001")) -> DirectiveChanges(
              DirectiveChange(
                None,
                DirectiveChangeItem(
                  actor,
                  DateTime.parse("2023-01-01T00:00:00.000Z"),
                  Some("directive_001 change reason"),
                  AddDirectiveDiff(TechniqueName("packageManagement"), mockDirectives.directives.pkgDirective)
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty,
          Map.empty,
          Map.empty
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(2),
          None,
          ChangeRequestInfo("second cr directive", "My directive second change"),
          Map(
            DirectiveId(DirectiveUid("directive_001")) -> DirectiveChanges(
              DirectiveChange(
                Some(
                  (
                    TechniqueName("packageManagement"),
                    mockDirectives.directives.pkgDirective,
                    Some(directiveSectionSpec)
                  )
                ),
                DirectiveChangeItem(
                  actor,
                  DateTime.parse("2023-02-02T00:00:00.000Z"),
                  Some("directive_001 change reason"),
                  ModifyToDirectiveDiff(
                    TechniqueName("packageManagement"),
                    mockDirectives.directives.pkgDirective,
                    Some(directiveSectionSpec)
                  )
                ),
                List(
                  DirectiveChangeItem(
                    actor,
                    DateTime.parse("2023-02-02T00:00:00.000Z"),
                    Some("directive_001 another change reason"),
                    ModifyToDirectiveDiff(
                      TechniqueName("packageManagement"),
                      mockDirectives.directives.pkgDirective.copy(
                        name = "pkg_directive_001",
                        shortDescription = "testing directive change",
                        longDescription = "testing directive change",
                        priority = 1,
                        isSystem = true,
                        parameters = mockDirectives.directives.pkgDirective.parameters ++ Map(("PACKAGE_LIST", Seq("curl")))
                        // ignored changes :
                        // policyMode = Some(PolicyMode.Audit),
                        // tags = Tags.fromMaps(List(Map("key" -> "value")))
                      ),
                      Some(directiveSectionSpec)
                    )
                  )
                )
              ),
              List.empty
            )
          ),
          Map.empty,
          Map.empty,
          Map.empty
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(3),
          None,
          ChangeRequestInfo("third cr directive", "My directive third change"),
          Map(
            DirectiveId(DirectiveUid("directive_001")) -> DirectiveChanges(
              DirectiveChange(
                Some(
                  (
                    TechniqueName("packageManagement"),
                    mockDirectives.directives.pkgDirective,
                    Some(directiveSectionSpec)
                  )
                ),
                DirectiveChangeItem(
                  actor,
                  DateTime.parse("2023-03-03T00:00:00.000Z"),
                  Some("directive_001 delete change reason"),
                  DeleteDirectiveDiff(
                    TechniqueName("packageManagement"),
                    mockDirectives.directives.pkgDirective
                  )
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty,
          Map.empty,
          Map.empty
        )
      ),
      Deployment.id -> List(
        ConfigurationChangeRequest(
          ChangeRequestId(4),
          None,
          ChangeRequestInfo("first cr group", "My group first change"),
          Map.empty,
          Map(
            NodeGroupId(NodeGroupUid("group_001")) -> NodeGroupChanges(
              NodeGroupChange(
                None,
                NodeGroupChangeItem(
                  actor,
                  DateTime.parse("2023-04-04T00:00:00.000Z"),
                  Some("group_001 change reason"),
                  AddNodeGroupDiff(mockNodeGroups.g0)
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty,
          Map.empty
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(5),
          None,
          ChangeRequestInfo("second cr group", "My group second change"),
          Map.empty,
          Map(
            NodeGroupId(NodeGroupUid("group_001")) -> NodeGroupChanges(
              NodeGroupChange(
                Some(mockNodeGroups.g0),
                NodeGroupChangeItem(
                  actor,
                  DateTime.parse("2023-05-05T00:00:00.000Z"),
                  Some("group_002 change reason"),
                  ModifyToNodeGroupDiff(mockNodeGroups.g0)
                ),
                List(
                  NodeGroupChangeItem(
                    actor,
                    DateTime.parse("2023-05-05T00:00:00.000Z"),
                    Some("group_001 another change reason"),
                    ModifyToNodeGroupDiff(
                      mockNodeGroups.g0.copy(
                        name = "group_002",
                        description = "testing node group change",
                        query = Some(Query(NodeReturnType, And, ResultTransformation.Identity, List.empty)),
                        serverList = Set(MockNodes.rootId),
                        isDynamic = true,
                        properties = mockNodeGroups.g0props.take(1),
                        _isEnabled = false,
                        isSystem = false
                      )
                    )
                  )
                )
              ),
              List.empty
            )
          ),
          Map.empty,
          Map.empty
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(6),
          None,
          ChangeRequestInfo("third cr group", "My group third change"),
          Map.empty,
          Map(
            NodeGroupId(NodeGroupUid("group_001")) -> NodeGroupChanges(
              NodeGroupChange(
                Some(mockNodeGroups.g0),
                NodeGroupChangeItem(
                  actor,
                  DateTime.parse("2023-06-06T00:00:00.000Z"),
                  Some("group_001 delete change reason"),
                  DeleteNodeGroupDiff(mockNodeGroups.g0)
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty,
          Map.empty
        )
      ),
      Deployed.id   -> List(
        ConfigurationChangeRequest(
          ChangeRequestId(7),
          None,
          ChangeRequestInfo("first cr rule", "My rule first change"),
          Map.empty,
          Map.empty,
          Map(
            RuleId(RuleUid("rule_001")) -> RuleChanges(
              RuleChange(
                None,
                RuleChangeItem(
                  actor,
                  DateTime.parse("2023-07-07T00:00:00.000Z"),
                  Some("rule_001 change reason"),
                  AddRuleDiff(mockRules.rules.rpmRule.copy(tags = Tags.fromMaps(List(Map("key" -> "value")))))
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(8),
          None,
          ChangeRequestInfo("second cr rule", "My rule second change"),
          Map.empty,
          Map.empty,
          Map(
            // To this point, RuleChange has a different 'change' implementation than others : it only takes 'firstChange'
            RuleId(RuleUid("rule_001")) -> RuleChanges(
              RuleChange(
                Some(mockRules.rules.rpmRule),
                RuleChangeItem(
                  actor,
                  DateTime.parse("2023-08-08T00:00:00.000Z"),
                  Some("rule_001 change reason"),
                  ModifyToRuleDiff(
                    mockRules.rules.rpmRule.copy(
                      name = "rule_002",
                      shortDescription = "testing rule change",
                      longDescription = "testing rule change",
                      directiveIds = Set(DirectiveId(DirectiveUid("directive1"))),
                      targets = Set(AllTargetExceptPolicyServers),
                      isEnabledStatus = false,
                      isSystem = false
                      // ignored changes :
                      // categoryId = RuleCategoryId("rule_category_001"),
                      // policyMode = Some(PolicyMode.Audit),
                      // tags = Tags.fromMaps(List(Map("key" -> "value")))
                    )
                  )
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(9),
          None,
          ChangeRequestInfo("third cr rule", "My rule third change"),
          Map.empty,
          Map.empty,
          Map(
            RuleId(RuleUid("rule_001")) -> RuleChanges(
              RuleChange(
                Some(mockRules.rules.rpmRule),
                RuleChangeItem(
                  actor,
                  DateTime.parse("2023-09-09T00:00:00.000Z"),
                  Some("rule_001 delete change reason"),
                  DeleteRuleDiff(mockRules.rules.rpmRule)
                ),
                List.empty
              ),
              List.empty
            )
          ),
          Map.empty
        )
      ),
      Cancelled.id  -> List(
        ConfigurationChangeRequest(
          ChangeRequestId(10),
          None,
          ChangeRequestInfo("first cr global param", "My global param first change"),
          Map.empty,
          Map.empty,
          Map.empty,
          Map(
            "my_global_param" -> GlobalParameterChanges(
              GlobalParameterChange(
                None,
                GlobalParameterChangeItem(
                  actor,
                  DateTime.parse("2023-10-10T00:00:00.000Z"),
                  Some("my_global_param change reason"),
                  AddGlobalParameterDiff(mockGlobalParam.jsonParam)
                ),
                List.empty
              ),
              List.empty
            )
          )
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(11),
          None,
          ChangeRequestInfo("second cr global param", "My global param second change"),
          Map.empty,
          Map.empty,
          Map.empty,
          Map(
            "my_global_param" -> GlobalParameterChanges(
              GlobalParameterChange(
                Some(mockGlobalParam.jsonParam),
                GlobalParameterChangeItem(
                  actor,
                  DateTime.parse("2023-11-11T00:00:00.000Z"),
                  Some("my_global_param change reason"),
                  ModifyToGlobalParameterDiff(
                    mockGlobalParam.jsonParam
                      .withDescription("testing global param change")
                      .withValue(mockGlobalParam.stringParam.value)
                  )
                ),
                List.empty
              ),
              List.empty
            )
          )
        ),
        ConfigurationChangeRequest(
          ChangeRequestId(12),
          None,
          ChangeRequestInfo("third cr global param", "My global param third change"),
          Map.empty,
          Map.empty,
          Map.empty,
          Map(
            "my_global_param" -> GlobalParameterChanges(
              GlobalParameterChange(
                Some(mockGlobalParam.jsonParam),
                GlobalParameterChangeItem(
                  actor,
                  DateTime.parse("2023-12-12T00:00:00.000Z"),
                  Some("my_global_param delete change reason"),
                  DeleteGlobalParameterDiff(mockGlobalParam.jsonParam)
                ),
                List.empty
              ),
              List.empty
            )
          )
        )
      )
    )
  )

  val modules = List(
    new ChangeRequestApiImpl(
      new DiffServiceImpl,
      restTestSetUp.mockTechniques.techniqueRepo,
      mockServices.changeRequestRepository,
      mockServices.changeRequestRepository,
      mockServices.workflowRepository,
      restTestSetUp.workflowLevelService,
      mockServices.commitAndDeployChangeRequest,
      mockServices.userPropertyService,
      mockServices.userService
    )
  )

  val validationWorkflowService = new TwoValidationStepsWorkflowServiceImpl(
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
    () => true.succeed,
    () => true.succeed,
    () => true.succeed
  )

  restTestSetUp.workflowLevelService.overrideLevel(
    new ChangeValidationWorkflowLevelService(
      AlwaysEnabledPluginStatus,
      restTestSetUp.workflowLevelService.defaultWorkflowService,
      validationWorkflowService,
      List.empty,
      () => true.succeed,
      () => false.succeed,
      null
    )
  )

  val apiVersions            = ApiVersion(13, true) :: ApiVersion(14, false) :: Nil
  val (rudderApi, liftRules) = TraitTestApiFromYamlFiles.buildLiftRules(modules, apiVersions, Some(mockServices.userService))

  val transformations: Map[String, String => String] = Map()

  // we are testing error cases, so we don't want to output error log for them
  org.slf4j.LoggerFactory
    .getLogger("com.normation.rudder.rest.RestUtils")
    .asInstanceOf[ch.qos.logback.classic.Logger]
    .setLevel(ch.qos.logback.classic.Level.OFF)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    (suite("All REST tests defined in files") {

      for {
        s <- TraitTestApiFromYamlFiles.doTest(
               yamlSourceDirectory,
               yamlDestTmpDirectory,
               liftRules,
               List("api_changerequest.yml"),
               transformations
             )
        _ <- effectUioUnit(
               if (java.lang.System.getProperty("tests.clean.tmp") != "false") IOResult.attempt(restTestSetUp.cleanup())
               else ZIO.unit
             )
      } yield s
    })
  }

}
