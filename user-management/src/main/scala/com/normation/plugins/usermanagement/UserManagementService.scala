package com.normation.plugins.usermanagement

import better.files.Dsl.SymbolicOperations
import better.files.File
import bootstrap.liftweb.PasswordEncoder
import bootstrap.liftweb.UserFile
import bootstrap.liftweb.UserFileProcessing
import com.normation.errors.IOResult
import com.normation.errors.Unexpected
import com.normation.errors.effectUioUnit
import com.normation.plugins.usermanagement.UserManagementIO.getUserFilePath
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Rights
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import com.normation.rudder.domain.logger.ApplicationLoggerPure
import com.normation.rudder.repository.xml.RudderPrettyPrinter
import java.util.concurrent.TimeUnit
import org.springframework.core.io.{ClassPathResource => CPResource}
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.parsing.ConstructingParser
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer
import zio._
import zio.syntax._

case class UserFileInfo(userOrigin: List[UserOrigin], digest: String)
case class UserOrigin(user: User, hashValidHash: Boolean)

case class User(username: String, password: String, role: Set[String]) {
  def toNode: Node = <user name={username} password={password} role={role.mkString(",")} />
}

object UserOrigin {
  def verifyHash(hashType: String, hash: String) = {
    // $2[aby]$[cost]$[22 character salt][31 character hash]
    val bcryptReg = "^\\$2[aby]?\\$[\\d]+\\$[./A-Za-z0-9]{53}$".r
    hashType.toLowerCase match {
      case "sha" | "sha1"       => hash.matches("^[a-fA-F0-9]{40}$")
      case "sha256" | "sha-256" => hash.matches("^[a-fA-F0-9]{64}$")
      case "sha512" | "sha-512" => hash.matches("^[a-fA-F0-9]{128}$")
      case "md5"                => hash.matches("^[a-fA-F0-9]{32}$")
      case "bcrypt"             => hash.matches(bcryptReg.regex)
      case _                    => false
    }
  }
}

object UserManagementIO {

  def replaceXml(currentXml: NodeSeq, newXml: Node, file: File): IOResult[Unit] = {
    // create a backup of rudder-user.xml and roll it back in case of errors during update
    def withBackup(source: File)(mod: IOResult[Unit]) = {
      for {
        stamp  <- ZIO.clockWith(_.currentTime(TimeUnit.MILLISECONDS))
        backup <- IOResult.attempt(File(file.pathAsString + s"_backup_${stamp}"))
        _      <- IOResult.attempt(source.copyTo(backup)) // in case of error here, stop
        _      <- mod.foldZIO(
                    // failure: we try to restore the backup
                    err =>
                      ApplicationLoggerPure.Authz.error(
                        s"Error when trying to save updated rudder user authorizations, roll-backing to back-upped version. Error was: ${err.fullMsg}"
                      ) *>
                      IOResult
                        .attempt(backup.copyTo(source, overwrite = true))
                        .foldZIO(
                          err2 => {
                            // there, we are in big problem: error in rollback too, likely an FS problem, advice admin
                            val msg = s"Error when reverting rudder-users.xlm, you will likely need to have a manual action. Backup file is here and won't be deleted automatically: ${backup.pathAsString}. Error was: ${err2.fullMsg}"
                            ApplicationLoggerPure.Authz.error(msg) *> Unexpected(msg).fail
                          },
                          ok => {
                            effectUioUnit(backup.delete(swallowIOExceptions = true)) *> ApplicationLoggerPure.Authz
                              .info(s"User file correctly roll-backed") *> Unexpected(
                              s"And error happened when trying to save rudder-user.xml file, backup version was restore. Error was: ${err.fullMsg}"
                            ).fail
                     }),
                    // in case of update success, we just delete the backup file
                    ok => effectUioUnit(backup.delete(swallowIOExceptions = true))
                  )
      } yield ()
    }

    withBackup(file) {
      for {
        writable <- IOResult.attempt(file.isWritable)
        _        <- ZIO.when(!writable)(Unexpected(s"${file.path} is not writable").fail)
        readable <- IOResult.attempt(file.isReadable)
        _        <- ZIO.when(!readable)(Unexpected(s"${file.path} is not readable").fail)
        p         = new RudderPrettyPrinter(300, 4)
        _        <- IOResult.attempt(file.clear())
        _        <- IOResult.attempt {
                      currentXml.foreach { x =>
                        if (x.label == "authentication") {
                          file << p.format(newXml)
                        } else {
                          file << p.format(x)
                        }
                      }
                    }
      } yield ()
    }
  }

  def getUserFilePath: IOResult[File] = {
    val resources: IOResult[UserFile] = UserFileProcessing.getUserResourceFile()
    resources.map { r =>
      if (r.name.startsWith("classpath:"))
        File(new CPResource(UserFileProcessing.DEFAULT_AUTH_FILE_NAME).getPath)
      else
        File(r.name)
    }
  }
}

object UserManagementService {
  def getHash(s: String) = {
    s.toLowerCase match {
      case "sha" | "sha1"       => PasswordEncoder.SHA1
      case "sha256" | "sha-256" => PasswordEncoder.SHA256
      case "sha512" | "sha-512" => PasswordEncoder.SHA512
      case "md5"                => PasswordEncoder.MD5
      case "bcrypt"             => PasswordEncoder.BCRYPT
      case _                    => PasswordEncoder.PlainText
    }
  }

  def computeRoleCoverage(roles: Set[Role], authzs: Set[AuthorizationType]): Option[Set[Role]] = {

    def parseAuthzIgnoreError(a: String) = AuthorizationType.parseAuthz(a).getOrElse(Set())

    def compareRights(r: Role): Option[Role] = {
      if (r.name == "no_rights") {
        None
      } else {
        val authzNames     = authzs.map(_.id)
        val roleAuthzNames = r.rights.authorizationTypes.map(_.id)
        val commonRights   = roleAuthzNames.intersect(authzNames)
        commonRights match {
          // Intersection is total
          case cr if cr == roleAuthzNames => Some(r)
          case cr if cr.nonEmpty          =>
            val test = cr.flatMap(parseAuthzIgnoreError)
            if (test.isEmpty) {
              None
            } else {
              val c = Custom(new Rights(test.toSeq: _*))
              Some(c)
            }
          case _                          => None
        }
      }
    }

    if (authzs.isEmpty || roles.isEmpty || authzs.exists(_.id == "no_rights")) {
      None
    } else {
      val (rs, custom)    = roles.flatMap(compareRights).partition {
        case Custom(_) => false
        case _         => true
      }
      val customAuthz     = custom.flatMap(_.rights.authorizationTypes.map(_.id))
      // remove authzs taken by a role in custom's rights
      val minCustomAuthz  = customAuthz.diff(rs.flatMap(_.rights.authorizationTypes.map(_.id)))
      val leftoversRights =
        authzs.diff(rs.flatMap(_.rights.authorizationTypes).union(minCustomAuthz.flatMap(parseAuthzIgnoreError)))
      val leftoversCustom: Option[Role]      = {
        if (leftoversRights.nonEmpty)
          Some(Custom(new Rights(leftoversRights.toSeq: _*)))
        else
          None
      }
      val data:            Option[Set[Role]] = {
        if (minCustomAuthz.nonEmpty) {
          Some(rs + Custom(new Rights(minCustomAuthz.flatMap(parseAuthzIgnoreError).toSeq: _*)))
        } else if (rs == Role.values.diff(Set(Role.NoRights))) {
          Some(Set(Role.Administrator))
        } else if (rs.nonEmpty)
          Some(rs)
        else
          None
      }
      leftoversCustom match {
        case Some(c) =>
          data match {
            case Some(r) => Some(r + c)
            case None    => Some(Set(c))
          }
        case None    => data
      }
    }
  }

  def add(newUser: User, isPreHashed: Boolean): IOResult[User] = {
    for {
      file       <- getUserFilePath
      parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
      userXML    <- IOResult.attempt(parsedFile.document().children)
      user       <- (userXML \\ "authentication").head match {
                      case e: Elem =>
                        val newXml = {
                          if (isPreHashed) {
                            e.copy(child = e.child ++ newUser.toNode)
                          } else {
                            e.copy(child = {
                              e.child ++ newUser
                                .copy(password = getHash((userXML \\ "authentication" \ "@hash").text).encode(newUser.password))
                                .toNode
                            })
                          }
                        }
                        UserManagementIO.replaceXml(userXML, newXml, file) *> newUser.succeed
                      case _ =>
                        Unexpected(s"Wrong formatting : ${file.path}").fail
                    }
    } yield user
  }

  def remove(toDelete: String): IOResult[Unit] = {
    for {
      file       <- getUserFilePath
      parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
      userXML    <- IOResult.attempt(parsedFile.document().children)
      toUpdate    = (userXML \\ "authentication").head
      newXml      = new RuleTransformer(new RewriteRule {
                      override def transform(n: Node): NodeSeq = n match {
                        case user: Elem if (user \ "@name").text == toDelete => NodeSeq.Empty
                        case other => other
                      }
                    }).transform(toUpdate).head
      _          <- UserManagementIO.replaceXml(userXML, newXml, file)
    } yield ()
  }

  def update(currentUser: String, newUser: User, isPreHashed: Boolean): IOResult[Unit] = {
    for {
      file       <- getUserFilePath
      parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
      userXML    <- IOResult.attempt(parsedFile.document().children)
      toUpdate    = (userXML \\ "authentication").head
      newXml      = new RuleTransformer(new RewriteRule {
                      override def transform(n: Node): NodeSeq = n match {
                        case user: Elem if (user \ "@name").text == currentUser =>
                          // for each user's parameters, if a new user's parameter is empty we decide to keep the original one
                          val newRoles    = if (newUser.role.isEmpty) (user \ "@role").text.split(",").toSet else newUser.role
                          val newUsername = if (newUser.username.isEmpty) currentUser else newUser.username
                          val newPassword = if (newUser.password.isEmpty) {
                            (user \ "@password").text
                          } else {
                            if (isPreHashed) newUser.password
                            else getHash((userXML \\ "authentication" \ "@hash").text).encode(newUser.password)
                          }

                          User(newUsername, newPassword, newRoles).toNode

                        case other => other
                      }
                    }).transform(toUpdate).head
      _          <- UserManagementIO.replaceXml(userXML, newXml, file)
    } yield ()
  }

  def getAll: IOResult[UserFileInfo] = {
    for {
      file       <- getUserFilePath
      parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
      userXML    <- IOResult.attempt(parsedFile.document().children)
      toUpdate    = (userXML \\ "authentication").head
      res        <- (userXML \\ "authentication").head match {
                      case e: Elem =>
                        val digest = (userXML \\ "authentication" \ "@hash").text.toUpperCase
                        val users  = e
                          .map(u => {
                            val password     = (u \ "@password").text
                            val user         = User((u \ "@name").text, (u \ "@password").text, (u \ "@role").map(_.text).toSet)
                            val hasValidHash = UserOrigin.verifyHash(digest, password)
                            UserOrigin(user, hasValidHash)
                          })
                          .toList
                        UserFileInfo(users, digest).succeed
                      case _ =>
                        Unexpected(s"Wrong formatting : ${file.path}").fail
                    }
    } yield res
  }
}
