/*
 *************************************************************************************
 * Copyright 2018 Normation SAS
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

package com.normation.plugins.scaleoutrelay

import com.normation.NamedZioLogger
import com.normation.errors.PureResult
import com.normation.plugins.PluginStatus
import com.normation.rudder.domain.Constants
import com.normation.rudder.services.policies.NodeRunHook
import com.normation.rudder.services.policies.write.AgentNodeProperties
import com.normation.rudder.services.policies.write.AgentNodeWritableConfiguration
import com.normation.rudder.services.policies.write.AgentSpecificFile
import com.normation.rudder.services.policies.write.AgentSpecificGeneration
import com.normation.rudder.services.policies.write.CFEngineAgentSpecificGeneration
import com.normation.rudder.services.policies.write.CfengineBundleVariables
import net.liftweb.common.Logger
import org.slf4j.LoggerFactory

/**
 * Applicative log of interest for Rudder ops.
 */
object ScaleOutRelayLogger extends Logger {
  override protected def _logger = LoggerFactory.getLogger("plugin.scale-out-relay")
}

object ScaleOutRelayLoggerPure extends NamedZioLogger {
  override def loggerName: String = "plugin.scale-out-relay"
}

/*
 * This will go in the plugin, and will be contributed somehow at config time.
 */
class ScaleOutRelayAgentSpecificGeneration(pluginInfo: PluginStatus) extends AgentSpecificGeneration {

  override def escape(value: String): String = CFEngineAgentSpecificGeneration.escape(value)

  /*
   * This plugin handle relay server, i.e non-root policy servers
   */
  override def handle(agentNodeProps: AgentNodeProperties): Boolean = {
    pluginInfo.isEnabled() &&
    agentNodeProps.isPolicyServer &&
    agentNodeProps.nodeId != Constants.ROOT_POLICY_SERVER_ID
  }

  override def write(cfg: AgentNodeWritableConfiguration): PureResult[List[AgentSpecificFile]] = {
    CFEngineAgentSpecificGeneration.write(cfg)
  }

  import com.normation.rudder.services.policies.write.BuildBundleSequence.BundleSequenceVariables
  import com.normation.rudder.services.policies.write.BuildBundleSequence.InputFile
  import com.normation.rudder.services.policies.write.BuildBundleSequence.TechniqueBundles
  override def getBundleVariables(
      inputs:   List[InputFile],
      bundles:  List[TechniqueBundles],
      runHooks: List[NodeRunHook]
  ): PureResult[BundleSequenceVariables] =
    Right(CfengineBundleVariables.getBundleVariables(escape, inputs, bundles, runHooks))
}
