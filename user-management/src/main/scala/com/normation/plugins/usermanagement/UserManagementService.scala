package com.normation.plugins.usermanagement

import better.files.Dsl.SymbolicOperations
import better.files.File
import bootstrap.liftweb.{PasswordEncoder, UserConfigFileError, UserFile, UserFileProcessing}
import com.normation.plugins.usermanagement.UserManagementIO.getUserFilePath
import com.normation.rudder.Role.Custom
import com.normation.rudder.repository.xml.RudderPrettyPrinter
import com.normation.rudder.{AuthorizationType, Rights, Role, RoleToRights}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.util.Helpers.tryo
import org.springframework.core.io.{ClassPathResource => CPResource}
import scala.xml.parsing.ConstructingParser
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq}


case class User(username: String, password: String, role: Set[String]) {
  def toNode: Node = <user name={ username } password={ password } role={ role.mkString(",") } />
}

object UserManagementIO {

  def replaceXml(currentXml: NodeSeq, newXml: Node, file: File): Box[File] = {

    if (!file.isWritable)
      Failure(s"${file.path} is not writable")
    else if (!file.isReadable)
      Failure(s"${file.path} is not readable")
    else {
      val p = new RudderPrettyPrinter(300, 4)
      file.clear()
      currentXml.foreach { x =>
        if (x.label == "authentication")
          file << p.format(newXml)
        else
          file << p.format(x)
      }
      if (file.contentAsString contains newXml.text)
        Failure(s"'${file.path}' modification have failed")
      else
        Full(file)
    }
  }

  def getUserFilePath: Either[UserConfigFileError, File] = {
    val resources: Either[UserConfigFileError, UserFile] = UserFileProcessing.getUserResourceFile()
    resources match {
      case Left(err) =>
        Left(err)
      case Right(r)  =>
        val file: File = {
          if (r.name.startsWith("classpath:"))
            File(new CPResource(UserFileProcessing.DEFAULT_AUTH_FILE_NAME).getPath)
          else
            File(r.name)
        }
        Right(file)
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

    def compareRights(r: Role): Option[Role] = {
      if (r.name == "no_rights")
        None
      else {
        val authzNames     = authzs.map(_.id)
        val roleAuthzNames = r.rights.authorizationTypes.map(_.id)
        val commonRights   = roleAuthzNames.intersect(authzNames)
        commonRights match {
          // Intersection is total
          case cr if cr == roleAuthzNames => Some(r)
          case cr if cr.nonEmpty          =>
            val test = cr.flatMap(RoleToRights.parseAuthz)
            if (test.isEmpty)
              None
            else {
              val c = Custom(new Rights(test.toSeq: _*))
              Some(c)
            }
          case _                          => None
        }
      }
    }

    if(authzs.isEmpty || roles.isEmpty || authzs.exists(_.id == "no_rights"))
      None
    else {
      val (rs, custom) = roles.flatMap(compareRights).partition {
        case Custom(_) => false
        case _         => true
      }
      val customAuthz     = custom.flatMap(_.rights.authorizationTypes.map(_.id))
      // remove authzs taken by a role in custom's rights
      val minCustomAuthz  = customAuthz.diff(rs.flatMap(_.rights.authorizationTypes.map(_.id)))
      val leftoversRights = authzs.diff(rs.flatMap(_.rights.authorizationTypes).union(minCustomAuthz.flatMap(RoleToRights.parseAuthz)))
      val leftoversCustom: Option[Role] = {
        if (leftoversRights.nonEmpty)
          Some(Custom(new Rights(leftoversRights.toSeq: _*)))
        else
          None
      }
      val data = {
        if (minCustomAuthz.nonEmpty)
          Some(rs + Custom(new Rights(minCustomAuthz.flatMap(RoleToRights.parseAuthz).toSeq: _*)))
        else if (rs == Role.values.diff(Set(Role.NoRights)))
          Some(RoleToRights.parseRole(Seq("administrator")).toSet)
        else if(rs.nonEmpty)
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

  def add(newUser: User, isPreHashed: Boolean): Box[User] = {
    getUserFilePath match {
      case Left(err)   =>
        Failure(err.msg)
      case Right(file) =>
        tryo(ConstructingParser.fromFile(file.toJava, preserveWS = true)).flatMap { parsedFile =>
          val userXML = parsedFile.document.children
          (userXML \\ "authentication").head match {
            case e: Elem =>
              val newXml =
                if (isPreHashed)
                  e.copy(child = e.child ++ newUser.copy(password = newUser.password).toNode)
                else
                  e.copy(child = e.child ++ newUser.copy(password = getHash((userXML \\ "authentication" \ "@hash").text).encode(newUser.password)).toNode)
              UserManagementIO.replaceXml(userXML, newXml, file)
              Full(newUser)
            case _ =>
              Failure(s"Wrong formatting : ${file.path}")
          }
        }
    }
  }

  def remove(toDelete: String): Box[File] = {
    getUserFilePath match {
      case Left(err) =>
        Failure(err.msg)
      case Right(file) =>
        tryo(ConstructingParser.fromFile(file.toJava, preserveWS = true)).flatMap { parsedFile =>
          val userXML = parsedFile.document.children
          val toUpdate = (userXML \\ "authentication").head
          val newXml = new RuleTransformer(new RewriteRule {
            override def transform(n: Node): NodeSeq = n match {
              case user: Elem if (user \ "@name").text == toDelete => NodeSeq.Empty
              case other => other
            }
          }).transform(toUpdate).head
          UserManagementIO.replaceXml(userXML, newXml, file)
        }
    }
  }

  def update(currentUser: String, newUser: User, isPreHashed: Boolean): Box[File] = {
    getUserFilePath match {
      case Left(err) =>
        Failure(err.msg)
      case Right(file) =>
        tryo(ConstructingParser.fromFile(file.toJava, preserveWS = true)).flatMap{ parsedFile =>
          val userXML = parsedFile.document.children
          val toUpdate = (userXML \\ "authentication").head
          val newXml = new RuleTransformer(new RewriteRule {
            override def transform(n: Node): NodeSeq = n match {
              case user: Elem if (user \ "@name").text == currentUser =>
                if (isPreHashed) {
                  newUser.copy(
                      username = if (newUser.username.isEmpty) currentUser else newUser.username
                    , role = if (newUser.role.isEmpty) Set("no_rights") else newUser.role
                    , password = if (newUser.password.isEmpty) (user \ "@password").text else newUser.password
                  ).toNode
                }
                else {
                  newUser.copy(
                    username = if (newUser.username.isEmpty) currentUser else newUser.username
                    , password = if (newUser.password.isEmpty) (user \ "@password").text else getHash((userXML \\ "authentication" \ "@hash").text).encode(newUser.password)
                    , role = if (newUser.role.isEmpty) Set("no_rights") else newUser.role
                  ).toNode
                }
              case other => other
            }
          }).transform(toUpdate).head
          UserManagementIO.replaceXml(userXML, newXml, file)
        }
    }
  }
}
