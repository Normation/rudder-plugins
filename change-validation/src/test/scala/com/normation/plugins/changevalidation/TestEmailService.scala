/*
 *************************************************************************************
 * Copyright 2021 Normation SAS
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

import better.files.*
import com.normation.errors.IOResult
import com.normation.rudder.domain.nodes.NodeGroupId
import com.normation.rudder.domain.policies.DirectiveId
import com.normation.rudder.domain.policies.RuleId
import com.normation.rudder.domain.workflows.ChangeRequestId
import com.normation.rudder.domain.workflows.ChangeRequestInfo
import com.normation.rudder.domain.workflows.ConfigurationChangeRequest
import com.normation.rudder.domain.workflows.DirectiveChanges
import com.normation.rudder.domain.workflows.GlobalParameterChanges
import com.normation.rudder.domain.workflows.NodeGroupChanges
import com.normation.rudder.domain.workflows.RuleChanges
import com.normation.rudder.web.model.LinkUtil
import com.normation.zio.*
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.junit.runner.RunWith
import org.specs2.mutable.*
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterAll
import org.subethamail.wiser.Wiser
import scala.jdk.CollectionConverters.*

@RunWith(classOf[JUnitRunner])
class TestEmailService extends Specification with BeforeAfterAll {

  implicit class ForceGet[A](io: IOResult[A]) {
    def forceGet = io.either.runNow match {
      case Right(a)  => a
      case Left(err) => throw new IllegalArgumentException(s"Error when force-getting an IO in test: ${err.fullMsg}")
    }
  }

  val testDir = File(s"/tmp/rudder-test-email/${DateTime.now().toString(ISODateTimeFormat.dateTimeNoMillis())}")
  testDir.createDirectories()
  val conf    = testDir / "email.conf"

  // create a new smtp server
  val smtpServer = Wiser.port(2525)

  Resource
    .getAsString("emails/change-validation-email.conf")
    .replaceAll("TESTDIRPATH", testDir.pathAsString)
    .inputStream
    .pipeTo(conf.newOutputStream)
    .close()
  List("cancelled-mail.template", "deployed-mail.template", "deployment-mail.template", "validation-mail.template").foreach(f =>
    Resource.getAsStream(f).pipeTo((testDir / f).newOutputStream).close()
  )
  val notification =
    new NotificationService(new EmailNotificationService(), new LinkUtil(null, null, null, null), conf.pathAsString)

  override def beforeAll(): Unit = {
    smtpServer.start()
  }

  override def afterAll(): Unit = {
    smtpServer.stop()
    if (System.getProperty("tests.clean.tmp") != "false") {
      FileUtils.deleteDirectory(testDir.toJava)
    }
  }

  "The notification service" should {
    "be able to read configurations" in {

      val config = notification.getSMTPConf(conf.pathAsString).forceGet

      (config.smtpHostServer === "localhost") and (config.port === 2525)
      // todo more tests
    }

    "be able to read a template" in {

      val template = notification.getStepMailConf(TwoValidationStepsWorkflowServiceImpl.Validation, conf.pathAsString).forceGet

      (template.to === Set(Email("validator1@change.req"), Email("validator2@change.req")) and
      (template.template === (testDir / "validation-mail.template").pathAsString))
    }

    "be able to send a notification email" in {

      val cr = ConfigurationChangeRequest(
        ChangeRequestId(42),
        None,
        ChangeRequestInfo("A test CR", "this CR is for test"),
        Map[DirectiveId, DirectiveChanges](),
        Map[NodeGroupId, NodeGroupChanges](),
        Map[RuleId, RuleChanges](),
        Map[String, GlobalParameterChanges]()
      )

      val expectedMessage = {
        """Received: from localhost.localdomain (localhost.localdomain [127.0.0.1])
          |        by localhost.localdomain
          |        with SMTP (SubEthaSMTP null) id KPD9Z0ZH;
          |        Tue, 01 Jun 2021 02:00:51 +0200 (CEST)
          |Date: Tue, 1 Jun 2021 02:00:51 +0200 (CEST)
          |From: issuer@change.req
          |To: validator1@change.req, validator2@change.req
          |Message-ID: <213100527.0.1622505651379@localhost.localdomain>
          |Subject: Pending Validation CR #42:
          |MIME-Version: 1.0
          |Content-Type: text/html; charset=utf-8
          |Content-Transfer-Encoding: 7bit
          |
          |<h2>CR #42: A test CR</h2>
          |<h3>Pending Validation</h3>
          |<ul>
          |  <li>Author: No One</li>
          |  <li>Description: this CR is for test</li>
          |</ul>
          |<a href="https://my.rudder.server/rudder/secure/plugins/changes/changeRequest/42">Click here to review and validate</a></li>
          |
          |""".stripMargin
      }

      val isEmpty = smtpServer.getMessages.isEmpty
      notification.sendNotification(TwoValidationStepsWorkflowServiceImpl.Validation, cr).forceGet

      val messages = smtpServer.getMessages.asScala.toList
      val msg      = messages.headOption.toString

      // we need to delete date & id related fields
      def deleteDate(s: String): String = {
        s.split("\n").take(2).drop(3).take(2).drop(1).take(20).mkString("\n")
      }

      (isEmpty must beTrue) and
      (messages.isEmpty must beFalse) and
      (deleteDate(msg) === deleteDate(expectedMessage))
    }
  }
}
