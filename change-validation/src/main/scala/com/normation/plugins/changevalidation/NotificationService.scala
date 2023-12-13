package com.normation.plugins.changevalidation

import bootstrap.liftweb.FileSystemResource
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
import com.normation.rudder.web.model.LinkUtil
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import jakarta.mail._
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties
import scala.jdk.CollectionConverters._
import zio.ZIO
import zio.syntax._

final case class Email(value: String)

final case class Username(value: String)

final case class SMTPConf(
    smtpHostServer: String,
    port:           Int,
    email:          Email,
    login:          Option[Username],
    password:       Option[String]
)

// Used to aggregate data from config file
final case class EmailConf(
    to:       Set[Email],
    replyTo:  Set[Email],
    cc:       Set[Email],
    bcc:      Set[Email],
    subject:  String,
    template: String // path of the template to use
)

// Represents what we are really going to send after processing template from EmailConf object.
final case class EmailEnvelop(
    to:      Set[Email],
    replyTo: Set[Email],
    cc:      Set[Email],
    bcc:     Set[Email],
    subject: String,
    body:    String // actual content, HTML supported
)

/**
 * This service responsibility is only to send emails using an SMTP config and an email envelop.
 */
class EmailNotificationService {

  def sendEmail(conf: SMTPConf, envelop: EmailEnvelop): IOResult[Unit] = {
    val prop = new Properties()
    prop.put("mail.smtp.host", conf.smtpHostServer)
    prop.put("mail.smtp.port", conf.port)
    prop.put("mail.smtp.starttls.enable", "true")

    for {
      session <- IOResult.attempt("Error when creating SMTP session")(
                   (conf.login, conf.password) match {
                     case (Some(l), Some(p)) =>
                       prop.put("mail.smtp.auth", "true");
                       val auth = new Authenticator() {
                         override protected def getPasswordAuthentication = new PasswordAuthentication(l.value, p)
                       }
                       Session.getInstance(prop, auth)
                     case _                  =>
                       prop.put("mail.smtp.auth", "false");
                       Session.getInstance(prop, null)
                   }
                 )
      message <- IOResult.attempt("Error when creating SMTP message envelop")(new MimeMessage(session))
      _       <- IOResult.attempt(s"Error with 'from' address [${conf.email.value}] of email")(
                   message.setFrom(new InternetAddress(conf.email.value))
                 )
      _       <- IOResult.attempt(s"Error when setting 'to' address(es) in email")(
                   message.setRecipients(
                     Message.RecipientType.TO,
                     envelop.to.map(_.value).mkString(",")
                   )
                 )
      _       <- IOResult.attempt(s"Error when setting 'replyTo' address(es) in email") {
                   val replyTo = envelop.replyTo.map(e => new InternetAddress(e.value)).toArray[Address]
                   if (replyTo.nonEmpty) {
                     message.setReplyTo(replyTo)
                   }
                 }
      _       <- IOResult.attempt(s"Error when setting 'bcc' address(es) in email")(
                   message.addRecipients(Message.RecipientType.BCC, envelop.bcc.map(_.value).mkString(","))
                 )
      _       <- IOResult.attempt(s"Error when setting 'cc' address(es) in email")(
                   message.addRecipients(Message.RecipientType.CC, envelop.cc.map(_.value).mkString(","))
                 )
      _       <- IOResult.attempt(s"Error when setting email subject")(message.setSubject(envelop.subject))
      _       <- IOResult.attempt(s"Error when setting email content")(message.setContent(envelop.body, "text/html; charset=utf-8"))
      _       <- IOResult.attempt(s"Error when sending email")(Transport.send(message))
    } yield ()
  }
}

trait NotificationService {
  def sendNotification(step: WorkflowNode, cr: ChangeRequest): IOResult[Unit]
}

class NotificationServiceImpl(
    emailService:   EmailNotificationService,
    linkUtil:       LinkUtil,
    configMailPath: String
) extends NotificationService {

  // we want all our string to be trimmed
  implicit class ConfigExtension(config: Config) {
    def getTrimmedString(path: String) = config.getString(path).trim

    // shortcut to get email from a comma-separated string
    // We don't want any empty value, it causes smtp client to throw Illegal address.
    def getEmails(path: String) = {
      config
        .getString(path)
        .split(",")
        .flatMap { s =>
          s.trim match {
            case "" => None
            case x  => Some(Email(x))
          }
        }
        .toSet
    }
  }

  implicit class ToEnvelop(val param: EmailConf) {
    def toEnvelop(body: String) = {
      EmailEnvelop(
        param.to,
        param.replyTo,
        param.cc,
        param.bcc,
        param.subject,
        body
      )
    }
  }

  val logger = NamedZioLogger("plugin.change-validation")

  override def sendNotification(step: WorkflowNode, cr: ChangeRequest): IOResult[Unit] = {
    for {
      serverConfig <- getSMTPConf(configMailPath)
      _            <- ZIO.when(serverConfig.smtpHostServer.nonEmpty) {
                        for {
                          emailConf     <- getStepMailConf(step, configMailPath)
                          rudderBaseUrl <- getRudderBaseUrl(configMailPath)
                          params         = extractChangeRequestInfo(rudderBaseUrl, cr)
                          mf             = new DefaultMustacheFactory()
                          emailBody     <- getContentFromTemplate(mf, emailConf, params)
                          emailSubject  <- getSubjectFromTemplate(mf, emailConf.subject, params)
                          _             <- emailService.sendEmail(serverConfig, emailConf.toEnvelop(emailBody).copy(subject = emailSubject))
                        } yield ()
                      }
    } yield ()
  }

  protected[changevalidation] def getConfig(path: String): IOResult[Config] = {
    val file = new File(path)
    IOResult.attemptZIO {
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

  protected[changevalidation] def getRudderBaseUrl(path: String): IOResult[String] = {
    for {
      config        <- getConfig(path)
      rudderBaseUrl <- IOResult.attempt(s"An error occurs while parsing RUDDER base url in ${path}") {
                         config.getTrimmedString("rudder.base.url")
                       }
    } yield rudderBaseUrl
  }

  protected[changevalidation] def getSMTPConf(path: String): IOResult[SMTPConf] = {
    for {
      config <- getConfig(path)
      smtp   <- IOResult.attempt(s"An error occurs while parsing SMTP conf in ${path}") {
                  val hostServer = config.getTrimmedString("smtp.hostServer")
                  val port       = config.getInt("smtp.port")
                  val email      = config.getTrimmedString("smtp.email")
                  val login      = {
                    val l = config.getTrimmedString("smtp.login")
                    if (l.isEmpty) None else Some(Username(l))
                  }
                  val password   = {
                    val p = config.getTrimmedString("smtp.password")
                    if (p.isEmpty) None else Some(p)
                  }
                  SMTPConf(
                    hostServer,
                    port,
                    Email(email),
                    login,
                    password
                  )
                }
    } yield smtp
  }

  protected[changevalidation] def getStepMailConf(step: WorkflowNode, path: String): IOResult[EmailConf] = {
    for {
      config   <- getConfig(path)
      s        <- step match {
                    case Validation => "validation".succeed
                    case Deployment => "deployment".succeed
                    case Cancelled  => "cancelled".succeed
                    case Deployed   => "deployed".succeed
                    case e          => Inconsistency(s"Step ${e} is not part of workflow validation").fail
                  }
      envelope <- IOResult.attempt {
                    val to       = config.getEmails(s"${s}.to")
                    val replyTo  = config.getEmails(s"${s}.replyTo")
                    val cc       = config.getEmails(s"${s}.cc")
                    val bcc      = config.getEmails(s"${s}.bcc")
                    val subject  = config.getTrimmedString(s"${s}.subject")
                    val template = config.getTrimmedString(s"${s}.template")
                    EmailConf(
                      to,
                      replyTo,
                      cc,
                      bcc,
                      subject,
                      template
                    )
                  }
    } yield envelope
  }

  protected[changevalidation] def getContentFromTemplate(
      mf:        MustacheFactory,
      emailConf: EmailConf,
      param:     Map[String, String]
  ): IOResult[String] = {
    IOResult.attempt(s"Error when getting `${emailConf.template}` template configuration") {
      val mustache = mf.compile(new FileReader(emailConf.template), emailConf.template)
      mustache.execute(new StringWriter(), param.asJava).toString
    }
  }

  protected[changevalidation] def getSubjectFromTemplate(
      mf:      MustacheFactory,
      subject: String,
      param:   Map[String, String]
  ): IOResult[String] = {
    IOResult.attempt(s"Error when expanding variables in email Subject") {
      val mustache = mf.compile(new StringReader(subject), subject)
      mustache.execute(new StringWriter(), param.asJava).toString
    }
  }

  protected[changevalidation] def extractChangeRequestInfo(rudderBaseUrl: String, cr: ChangeRequest): Map[String, String] = {
    // we could get a lot more information and mustache parameters by pattern matching on ChangeRequest and
    // extracting information for ConfigurationChangeRequest like: object updates (mod/creation/deletion),
    // change request creation date, long messages, etc
    Map(
      "id"          -> cr.id.value.toString,
      "name"        -> cr.info.name,
      "description" -> cr.info.description,
      "author"      -> cr.owner
      // rudderBaseUrl should not contains "/" at the end
      ,
      "link"        -> (rudderBaseUrl + linkUtil.baseChangeRequestLink(cr.id))
    )
  }
}
