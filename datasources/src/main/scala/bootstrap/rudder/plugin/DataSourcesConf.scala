/*
*************************************************************************************
* Copyright 2016 Normation SAS
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

package bootstrap.rudder.plugin

import bootstrap.liftweb.RudderConfig
import com.normation.inventory.domain.NodeId
import com.normation.plugins.RudderPluginModule
import com.normation.plugins.datasources._
import com.normation.rudder.services.policies.PromiseGenerationHooks
import com.normation.rudder.services.servers.NewNodeManagerHooks
import org.joda.time.DateTime
import net.liftweb.common.Box
import com.normation.rudder.batch.AutomaticStartDeployment
import com.normation.rudder.domain.eventlog.RudderEventActor
import com.normation.plugins.datasources.api.DataSourceApiImpl
import com.normation.rudder.batch.AsyncDeploymentActor
import com.normation.zio.ZioRuntime
import com.normation.errors._
import com.normation.zio._
import com.normation.box._

/*
 * An update hook which triggers a configuration generation if needed
 */
class OnUpdatedNodeRegenerate(regenerate: AsyncDeploymentActor) {
  def hook(updatedNodeIds: Set[NodeId], cause: UpdateCause): IOResult[Unit] = {
    // we don't trigger a new update if the update cause was during a generation
    IOResult.effect(if(!cause.triggeredByGeneration && updatedNodeIds.nonEmpty) {
      regenerate ! AutomaticStartDeployment(cause.modId, RudderEventActor)
    })
  }
}

/*
 * Actual configuration of the data sources logic
 */
object DatasourcesConf extends RudderPluginModule {


  import bootstrap.liftweb.{RudderConfig => Cfg}

  // by build convention, we have only one of that on the classpath
  lazy val pluginStatusService =  new CheckRudderPluginEnableImpl(RudderConfig.nodeInfoService)

  lazy val regenerationHook = new OnUpdatedNodeRegenerate(RudderConfig.asyncDeploymentAgent)

  lazy val dataSourceRepository = new DataSourceRepoImpl(
      new DataSourceJdbcRepository(Cfg.doobie)
    , ZioRuntime.environment
    , new HttpQueryDataSourceService(
          Cfg.nodeInfoService
        , Cfg.roLDAPParameterRepository
        , Cfg.woNodeRepository
        , Cfg.interpolationCompiler
        , regenerationHook.hook _
        , () => Cfg.configService.rudder_global_policy_mode()
        , ZioRuntime.environment
      )
    , Cfg.stringUuidGenerator
    , pluginStatusService
  )

  // add data source pre-deployment update hook
  Cfg.deploymentService.appendPreGenCodeHook(new PromiseGenerationHooks() {
    def beforeDeploymentSync(generationTime: DateTime): Box[Unit] = {
      //that doesn't actually wait for the return. Not sure how to do it.
      dataSourceRepository.onGenerationStarted(generationTime).toBox
    }
  })

  Cfg.newNodeManager.appendPostAcceptCodeHook(new NewNodeManagerHooks() {
    def afterNodeAcceptedAsync(nodeId: NodeId): Unit = {
      dataSourceRepository.onNewNode(nodeId).runNow
    }
  })

  val dataSourceApi9 = new DataSourceApiImpl(
      Cfg.restExtractorService
    , Cfg.restDataSerializer
    , dataSourceRepository
    , Cfg.nodeInfoService
    , Cfg.woNodeRepository
    , Cfg.stringUuidGenerator
  )

  lazy val pluginDef = new DataSourcesPluginDef(DatasourcesConf.pluginStatusService)

}

