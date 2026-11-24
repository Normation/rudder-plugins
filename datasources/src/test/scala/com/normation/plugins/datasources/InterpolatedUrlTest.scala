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

package com.normation.plugins.datasources

import com.normation.errors.*
import com.normation.rudder.domain.policies.GlobalPolicyMode
import com.normation.rudder.domain.policies.PolicyMode
import com.normation.rudder.domain.policies.PolicyModeOverrides
import com.normation.rudder.services.nodes.PropertyEngineServiceImpl
import com.normation.rudder.services.policies.InterpolatedValueCompilerImpl
import com.normation.rudder.services.policies.NodeConfigData
import com.normation.zio.*
import com.softwaremill.quicklens.*
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import zio.syntax.*

@RunWith(classOf[JUnitRunner])
class InterpolatedUrlTest extends Specification {

  val interpolation = new InterpolatedValueCompilerImpl(new PropertyEngineServiceImpl(List.empty))

  // replace ${name} by its value, enough to simulate interpolation
  def expandWith(values: Map[String, String])(s: String): IOResult[String] = {
    values.foldLeft(s) { case (acc, (k, v)) => acc.replace("${" + k + "}", v) }.succeed
  }

  def expandUrl(template: String, values: Map[String, String], escape: Boolean) = {
    new GetDataset(interpolation, escape).expandUrl(expandWith(values), template).either.runNow
  }

  "Escaping illegal chars" should {
    "keep a valid URL" in {
      val url = "https://user@my.cmdb.com:8443/a/b-c_d.e~f?x=1&y=a+b,c;d#frag"
      InterpolatedUrl.escapeIllegalChars(url) must beRight(url)
    }
    "encode spaces, quotes, pipes and non ascii chars" in {
      InterpolatedUrl.escapeIllegalChars("""https://h/a b"<>\^`|é😀""") must beRight(
        "https://h/a%20b%22%3C%3E%5C%5E%60%7C%C3%A9%F0%9F%98%80"
      )
    }
    "encode percent, even in already encoded values" in {
      InterpolatedUrl.escapeIllegalChars("https://h/a%20b") must beRight("https://h/a%2520b")
    }
  }

  "Same origin check" should {
    val ref     = "https://my.cmdb.com"
    val checked = InterpolatedUrl.checkSameOrigin(ref)

    "accept original reference" in {
      checked(ref) must beRight
    }
    "skip invalid interpolated URI in params" in {
      (checked("https://my.cmdb.com/x/${node.id}") must beRight) and
      (checked("https://my.cmdb.com?id=${node.id}") must beRight) and
      (checked("https://my.cmdb.com#${node.id}") must beRight)
    }
    "skip variables in the host part" in {
      (checked("https://${rudder.param.cmdb}/x") must beRight) and
      (checked("${rudder.param.cmdb}/x") must beRight) and
      (checked("http${s}://h/x") must beRight) and
      (checked("my.cmdb.com/x") must beRight)
    }

    "check scheme, host and port" in {
      val checkedA = InterpolatedUrl.checkSameOrigin("https://my.cmdb.com:8443")
      "accept ignoring case" in {
        checkedA("HTTPS://My.Cmdb.com:8443") must beRight
      }
      "refuse different scheme" in {
        checkedA("http://my.cmdb.com:8443") must beLeft
      }
      "refuse different port" in {
        checkedA("https://my.cmdb.com:8445") must beLeft
      }
      "refuse different host" in {
        (checked("https://evil.com") must beLeft) and
        (checked("https://my.cmdb.com@evil.com") must beLeft)
      }
    }

    "compare the authority when the host is not a valid DNS name" in {
      (InterpolatedUrl.checkSameOrigin("https://my_cmdb")("https://my_cmdb/a") must beRight) and
      (InterpolatedUrl.checkSameOrigin("https://my_cmdb")("https://evil_cmdb/a") must beLeft)
    }
  }

  "Interpolated URL" should {
    "be accepted when it stays on the base host" in {
      expandUrl("https://my.cmdb.com/${x}", Map("x" -> "../evil.com"), escape = true) must beRight(
        "https://my.cmdb.com/../evil.com"
      )
    }
    "be escaped" in {
      expandUrl("https://my.cmdb.com/${x}", Map("x" -> "a b"), escape = true) must beRight("https://my.cmdb.com/a%20b")
    }
    "not be escaped when disabled" in {
      expandUrl("https://my.cmdb.com/${x}", Map("x" -> "a%20b"), escape = false) must beRight("https://my.cmdb.com/a%20b")
    }
    "be accepted without escaping" in {
      expandUrl("https://my.cmdb.com/${x}", Map("x" -> "a b"), escape = false) must beRight("https://my.cmdb.com/a b")
    }
    "not be checked when the host starts with a variable" in {
      expandUrl("https://${x}/data", Map("x" -> "evil.com"), escape = true) must beRight("https://evil.com/data")
    }
    "default to https" in {
      expandUrl("my.cmdb.com/${x}", Map("x" -> "data"), escape = true) must beRight("https://my.cmdb.com/data")
    }
  }

  "Injected short hostname" should {
    "be escaped in JSON" in {
      val node = NodeConfigData.factRoot.modify(_.fqdn).setTo("""a"b\c}.rudder.local""")
      val res  = new InterpolateNode(interpolation)
        .compileInput(node, node, GlobalPolicyMode(PolicyMode.Enforce, PolicyModeOverrides.Always))(
          "${node.properties[datasources-injected][short-hostname]}"
        )
        .either
        .runNow
      res must beRight("""a"b\c}""")
    }
  }
}
