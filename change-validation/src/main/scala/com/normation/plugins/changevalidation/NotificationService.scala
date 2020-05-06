package com.normation.plugins.changevalidation

import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

import bootstrap.liftweb.FileSystemResource
import bootstrap.liftweb.RudderConfig
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import com.normation.NamedZioLogger
import com.normation.errors._
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Cancelled
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Deployed
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Deployment
import com.normation.plugins.changevalidation.TwoValidationStepsWorkflowServiceImpl.Validation
import com.normation.rudder.domain.workflows.ChangeRequest
import com.normation.rudder.domain.workflows.WorkflowNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import javax.mail.Session
import javax.mail._
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import zio.syntax._

import scala.jdk.CollectionConverters._

final case class Email(value: String)

final case class Username(value: String)

final case class SMTPConf(
    smtpHostServer : String
  , port           : Int
  , email          : Email
  , login          : Option[Username]
  , password       : Option[String]
)

// Used to aggregate data from config file
final case class EmailConf(
    to      : Set[Email]
  , replyTo : Set[Email]
  , cc      : Set[Email]
  , bcc     : Set[Email]
  , subject : String
  , template: String // path of the template to use
)

// Represents what we are really going to send after processing template from EmailConf object.
final case class EmailEnvelop(
    to      : Set[Email]
  , replyTo : Set[Email]
  , cc      : Set[Email]
  , bcc     : Set[Email]
  , subject : String
  , body    : String // actual content, HTML supported
)


/**
 * This service responsability is only to send emails using an SMTP config and an email envelop.
 */
class EmailNotificationService {

  def sendEmail(conf: SMTPConf, envelop: EmailEnvelop): IOResult[Unit] = {
    val prop = new Properties()
    prop.put("mail.smtp.host", conf.smtpHostServer)
    prop.put("mail.smtp.port", conf.port)
    prop.put("mail.smtp.starttls.enable", "true")

    IOResult.effect {
      val session = (conf.login, conf.password) match {
        case (Some(l), Some(p)) =>
          prop.put("mail.smtp.auth", "true");
          val auth = new Authenticator() {
            override protected def getPasswordAuthentication = new PasswordAuthentication(l.value, p)
          }
          Session.getInstance(prop, auth)
        case (_, None)          =>
          prop.put("mail.smtp.auth", "false");
          Session.getInstance(prop, null)
        case (None, _)          =>
          prop.put("mail.smtp.auth", "false");
          Session.getInstance(prop, null)
      }
      val message = new MimeMessage(session);
      message.setFrom(new InternetAddress(conf.email.value))
      message.setRecipients(
        Message.RecipientType.TO,
        envelop.to.map(_.value).mkString(",")
      )
      val reply: Array[Address] = envelop.replyTo.map(e => new InternetAddress(e.value)).toArray
      message.addRecipients(Message.RecipientType.BCC, envelop.bcc.map(_.value).mkString(","))
      message.addRecipients(Message.RecipientType.CC, envelop.cc.map(_.value).mkString(","))
      message.setReplyTo(reply)
      message.setSubject(envelop.subject);

      message.setContent(envelop.body, "text/html; charset=utf-8");
      Transport.send(message)
    }
  }
}

class NotificationService(
    emailService: EmailNotificationService
  , configMailPath: String
) {

  implicit class ToEnvelop(val param: EmailConf)  {
    def toEnvelop(body: String) = {
      EmailEnvelop(
          param.to
        , param.replyTo
        , param.cc
        , param.bcc
        , param.subject
        , body
      )
    }
  }

  val logger         = NamedZioLogger("plugin.change-validation")

  def sendNotification(step: WorkflowNode, cr: ChangeRequest): IOResult[Unit] = {
    for {
      serverConfig  <- getSMTPConf(configMailPath)
      emailConf     <- getStepMailConf(step, configMailPath)
      rudderBaseUrl <- getRudderBaseUrl(configMailPath)
      params        =  extractChangeRequestInfo(rudderBaseUrl, cr)
      mf            =  new DefaultMustacheFactory()
      emailBody     <- getContentFromTemplate(mf, emailConf, params)
      emailSubject  <- getSubjectFromTemplate(mf, emailConf.subject, params)
      _             <- emailService.sendEmail(serverConfig, emailConf.toEnvelop(emailBody).copy(subject = emailSubject))
    } yield ()
  }

  private[this] def getConfig(path: String): IOResult[Config] = {
    val file           = new File(path)
    IOResult.effectM {
      for {
        configResource <- if (file.exists && file.canRead) {
                            FileSystemResource(file).succeed
                          } else {
                            Inconsistency(s"Configuration file not found: ${file.getPath}").fail
                          }
      } yield {
        ConfigFactory.load(ConfigFactory.parseFile(configResource.file))
      }
    }
  }

  private[this] def getRudderBaseUrl(path: String): IOResult[String] = {
    for {
      config        <- getConfig(path)
      rudderBaseUrl <- IOResult.effect(s"An error occurs while parsing RUDDER base url in ${path}"){
                         config.getString("rudder.base.url")
                       }
    } yield rudderBaseUrl
  }

  private[this] def getSMTPConf(path: String): IOResult[SMTPConf] = {
    for {
      config <- getConfig(path)
      smtp   <- IOResult.effect(s"An error occurs while parsing SMTP conf in ${path}") {
                  val hostServer = config.getString("smtp.hostServer")
                  val port       = config.getInt("smtp.port")
                  val email      = config.getString("smtp.email")
                  val login      = {
                    val l = config.getString("smtp.login")
                    if (l.isEmpty) None else Some(Username(l))
                  }
                  val password   = {
                    val p = config.getString("smtp.password")
                    if (p.isEmpty) None else Some(p)
                  }
                  SMTPConf(
                    hostServer
                    , port
                    , Email(email)
                    , login
                    , password
                  )
                }
    } yield smtp
  }

  private[this] def getStepMailConf(step: WorkflowNode, path: String): IOResult[EmailConf] = {
    for {
      config   <- getConfig(path)
      s        <- step match {
                    case Validation => "validation".succeed
                    case Deployment => "deployment".succeed
                    case Cancelled  => "cancelled".succeed
                    case Deployed   => "deployed".succeed
                    case e          => Inconsistency(s"Step ${e} is not part of workflow validation").fail
                 }
      envelope <- IOResult.effect{
                    val to       = config.getString(s"${s}.to").split(",").map(Email).toSet
                    val replyTo  = config.getString(s"${s}.replyTo").split(",").map(Email).toSet
                    val cc       = config.getString(s"${s}.cc").split(",").map(Email).toSet
                    val bcc      = config.getString(s"${s}.bcc").split(",").map(Email).toSet
                    val subject  = config.getString(s"${s}.subject")
                    val template = config.getString(s"${s}.template")
                    EmailConf(
                        to
                      , replyTo
                      , cc
                      , bcc
                      , subject
                      , template
                    )
                 }
    } yield envelope
  }

  private[this] def getContentFromTemplate(mf: MustacheFactory, emailConf: EmailConf, param: Map[String, String]): IOResult[String] = {
    IOResult.effect(s"Error when getting `${emailConf.template}` template configuration"){
      val mustache = mf.compile(new FileReader(emailConf.template), emailConf.template)
      mustache.execute(new StringWriter(), param.asJava).toString
    }
  }

  private[this] def getSubjectFromTemplate(mf: MustacheFactory, subject: String, param: Map[String, String]): IOResult[String] = {
    IOResult.effect(s"Error when expanding variables in email Subject"){
      val mustache = mf.compile(new StringReader(subject), subject)
      mustache.execute(new StringWriter(), param.asJava).toString
    }
  }

  private[this] def extractChangeRequestInfo(rudderBaseUrl: String, cr: ChangeRequest): Map[String, String] = {
    // we could get a lot more information and mustache parameters by pattern matching on ChangeRequest and
    // extracting information for ConfigurationChangeRequest like: object updates (mod/creation/deletion),
    // change request creation date, long messages, etc
    Map(
        "id"          -> cr.id.value.toString
      , "name"        -> cr.info.name
      , "description" -> cr.info.description
      , "author"      -> cr.owner
      // rudderBaseUrl should not contains "/" at the end
      , "link"        -> (rudderBaseUrl + RudderConfig.linkUtil.changeRequestLink(cr.id))
    )
  }
}
