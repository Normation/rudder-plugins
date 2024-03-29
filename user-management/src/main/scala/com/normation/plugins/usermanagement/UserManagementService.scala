package com.normation.plugins.usermanagement

import better.files.Dsl.SymbolicOperations
import better.files.File
import bootstrap.liftweb.FileUserDetailListProvider
import bootstrap.liftweb.PasswordEncoder
import bootstrap.liftweb.UserFile
import bootstrap.liftweb.UserFileProcessing
import com.normation.errors.IOResult
import com.normation.errors.Unexpected
import com.normation.errors.effectUioUnit
import com.normation.eventlog.EventActor
import com.normation.plugins.usermanagement.UserManagementIO.getUserFilePath
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Rights
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import com.normation.rudder.RudderRoles
import com.normation.rudder.domain.logger.ApplicationLoggerPure
import com.normation.rudder.repository.xml.RudderPrettyPrinter
import com.normation.rudder.users.*
import com.normation.zio.*
import io.scalaland.chimney.dsl.*
import java.util.concurrent.TimeUnit
import org.springframework.core.io.ClassPathResource as CPResource
import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.parsing.ConstructingParser
import scala.xml.transform.RewriteRule
import scala.xml.transform.RuleTransformer
import zio.*
import zio.syntax.*

case class UserFileInfo(userOrigin: List[UserOrigin], digest: String)
case class UserOrigin(user: User, hashValidHash: Boolean)

case class User(username: String, password: String, permissions: Set[String]) {
  def toNode: Node = <user name={username} password={password} permissions={permissions.mkString(",")} />
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
                            val msg =
                              s"Error when reverting rudder-users.xlm, you will likely need to have a manual action. Backup file is here and won't be deleted automatically: ${backup.pathAsString}. Error was: ${err2.fullMsg}"
                            ApplicationLoggerPure.Authz.error(msg) *> Unexpected(msg).fail
                          },
                          ok => {
                            effectUioUnit(backup.delete(swallowIOExceptions = true)) *> ApplicationLoggerPure.Authz
                              .info(s"User file correctly roll-backed") *> Unexpected(
                              s"And error happened when trying to save rudder-user.xml file, backup version was restore. Error was: ${err.fullMsg}"
                            ).fail
                          }
                        ),
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

  def getUserFilePath(resourceFile: UserFile): File = {
    if (resourceFile.name.startsWith("classpath:"))
      File(new CPResource(UserFileProcessing.DEFAULT_AUTH_FILE_NAME).getPath)
    else
      File(resourceFile.name)
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

  /**
   * Parse a list of permissions and split it so that we have a representation of users permissions as :
   * - a set of roles that could be parsed from the permissions
   * - a minimal set of authorization types that are complementary to roles and not present yet in the set of roles
   * - a set of unknown permissions that could neither parsed as roles nor authorization types
   */
  def parsePermissions(
      permissions: Set[String]
  )(implicit allRoles: Set[Role]): (Set[Role], Set[AuthorizationType], Set[String]) = {
    // Everything that is not a role is an authz and remaining authz are put into custom role
    val allRolesByName     = allRoles.map(r => r.name -> r).toMap
    val (remaining, roles) = permissions.partitionMap(r => allRolesByName.get(r).toRight(r))
    val allRoleAuthz       = roles.flatMap(_.rights.authorizationTypes)
    val (unknowns, authzs) = remaining.partitionMap(a => {
      AuthorizationType
        .parseRight(a)
        .map(_.filter(!allRoleAuthz.contains(_)))
        .left
        .map(_ => a)
    })

    (roles, authzs.flatten, unknowns)
  }

  def computeRoleCoverage(roles: Set[Role], authzs: Set[AuthorizationType]): Option[Set[Role]] = {

    def compareRights(r: Role, as: Set[AuthorizationType]): Option[Role] = {
      if (r == Role.NoRights) {
        None
      } else {
        val commonRights = r.rights.authorizationTypes.intersect(as)
        commonRights match {
          // Intersection is total
          case cr if cr == r.rights.authorizationTypes => Some(r)
          case cr if cr.nonEmpty                       =>
            Some(Custom(Rights(cr)))
          case _                                       => None
        }
      }
    }

    if (authzs.isEmpty || roles.isEmpty || authzs.contains(AuthorizationType.NoRights)) {
      None
    } else if (authzs.contains(AuthorizationType.AnyRights)) {
      // only administrator can have that right, and it encompasses every other ones
      Some(Set(Role.Administrator))
    } else {
      val (rs, custom) = roles.flatMap(r => compareRights(r, authzs)).partition {
        case Custom(_) => false
        case _         => true
      }

      val customAuthz     = custom.flatMap(_.rights.authorizationTypes)
      // remove authzs taken by a role in custom's rights
      val minCustomAuthz  = customAuthz.diff(rs.flatMap(_.rights.authorizationTypes))
      val rsRights        = rs.flatMap(_.rights.authorizationTypes)
      val leftoversRights = authzs.diff(rsRights.union(minCustomAuthz))
      val leftoversCustom: Option[Role]      = {
        if (leftoversRights.nonEmpty)
          Some(Custom(Rights(leftoversRights)))
        else
          None
      }
      val data:            Option[Set[Role]] = {
        if (minCustomAuthz.nonEmpty) {
          Some(rs + Custom(Rights(minCustomAuthz)))
        } else if (rs == RudderRoles.getAllRoles.runNow.values.toSet.diff(Set(Role.NoRights: Role))) {
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

}

class UserManagementService(
    userRepository:      UserRepository,
    userService:         FileUserDetailListProvider,
    getUserResourceFile: IOResult[UserFile]
) {
  import UserManagementService.*

  /*
   * For now, when we add an user, we always add it in the XML file (and not only in database).
   * So we let the callback on file reload does what it needs.
   */
  def add(newUser: User, isPreHashed: Boolean): IOResult[User] = {
    for {
      file       <- getUserResourceFile.map(getUserFilePath(_))
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
      _          <- userService.reloadPure()
    } yield user
  }

  /*
   * When we delete an user, it can be from file or auto-added by OIDC or other backend supporting that.
   * So we let the callback on file reload do the deletion for file users
   */
  def remove(toDelete: String, actor: EventActor, reason: String): IOResult[Unit] = {
    for {
      file       <- getUserResourceFile.map(getUserFilePath(_))
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
      _          <- userService.reloadPure()
    } yield ()
  }

  /*
   * This method contains all logic of updating an user, taking into account :
   * - the providers list for the user and their ability to extend roles from user file
   * - the password definition and hashing
   */
  def update(id: String, updateUser: UpdateUserFile, isPreHashed: Boolean)(
      allRoles: Map[String, Role]
  ): IOResult[Unit] = {
    implicit val currentRoles: Set[Role] = allRoles.values.toSet

    // Unknown permissions are trusted and put in file
    val (roles, authz, unknown) = UserManagementService.parsePermissions(updateUser.permissions.toSet)

    val newFileUserPermissions = roles.map(_.name) ++ authz.map(_.id) ++ unknown
    for {
      userInfo <- userRepository
                    .get(id)
                    .notOptional(s"User '${id}' does not exist therefore cannot be updated")

      // the user to update in the file with the resolved permissions
      fileUser  = updateUser.transformInto[User].copy(permissions = newFileUserPermissions)

      // we may have users that where added by other providers, and still want to add them in file
      // This block initializes the user in the file, for permissions and password to be updated below
      _ <- ZIO.when(!(userService.authConfig.users.keySet.contains(id))) {
             for {
               // Apparently we need to do the whole thing to get the file and add it, before doing it again to update
               file       <- getUserResourceFile.map(getUserFilePath(_))
               parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
               userXML    <- IOResult.attempt(parsedFile.document().children)
               toUpdate    = (userXML \\ "authentication").head

               _ <- (userXML \\ "authentication").head match {
                      case e: Elem =>
                        val newXml = e.copy(child = e.child ++ User(id, "", Set.empty).toNode)
                        UserManagementIO.replaceXml(userXML, newXml, file)
                      case _ =>
                        Unexpected(s"Wrong formatting : ${file.path}").fail
                    }
             } yield ()
           }

      file       <- getUserResourceFile.map(getUserFilePath(_))
      parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
      userXML    <- IOResult.attempt(parsedFile.document().children)
      toUpdate    = (userXML \\ "authentication").head

      newXml = new RuleTransformer(new RewriteRule {
                 override def transform(n: Node): NodeSeq = n match {
                   case user: Elem if (user \ "@name").text == id =>
                     // for each user's parameters, if a new user's parameter is empty we decide to keep the original one
                     val newRoles    = {
                       if (fileUser.permissions.isEmpty) ((user \ "@role") ++ (user \ "@permissions")).text.split(",").toSet
                       else fileUser.permissions
                     }
                     val newUsername = if (fileUser.username.isEmpty) id else fileUser.username
                     val newPassword = if (fileUser.password.isEmpty) {
                       (user \ "@password").text
                     } else {
                       if (isPreHashed) fileUser.password
                       else getHash((userXML \\ "authentication" \ "@hash").text).encode(fileUser.password)
                     }

                     User(newUsername, newPassword, newRoles).toNode

                   case other => other
                 }
               }).transform(toUpdate).head
      _     <- UserManagementIO.replaceXml(userXML, newXml, file)
      _     <- userService.reloadPure()
    } yield ()
  }

  /**
   * User information fields in the database
   */
  def updateInfo(id: String, updateUser: UpdateUserInfo): IOResult[Unit] = {
    // always update fields, at worst they will be updated with an empty value
    userRepository.updateInfo(
      id,
      Some(updateUser.name),
      Some(updateUser.email),
      updateUser.otherInfo
    )
  }

  def getAll: IOResult[UserFileInfo] = {
    for {
      file       <- getUserResourceFile.map(getUserFilePath(_))
      parsedFile <- IOResult.attempt(ConstructingParser.fromFile(file.toJava, preserveWS = true))
      userXML    <- IOResult.attempt(parsedFile.document().children)
      res        <- (userXML \\ "authentication").head match {
                      case e: Elem =>
                        val digest = (userXML \\ "authentication" \ "@hash").text.toUpperCase
                        val users  = e
                          .map(u => {
                            val password     = (u \ "@password").text
                            val user         =
                              User((u \ "@name").text, (u \ "@password").text, ((u \ "@role") ++ (u \ "@permissions")).map(_.text).toSet)
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
