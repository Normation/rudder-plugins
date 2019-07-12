package com.normation.plugins.usermanagement

import java.net.URI

import better.files
import better.files.Dsl.SymbolicOperations
import better.files.File
import bootstrap.liftweb.{PasswordEncoder, UserConfigFileError, UserFile, UserFileProcessing}
import com.normation.plugins.usermanagement.UserManagementIO.getUserFilePath
import com.normation.rudder.Role.Custom
import com.normation.rudder.{AuthorizationType, Rights, Role, RoleToRights}
import com.normation.rudder.repository.xml.RudderPrettyPrinter
import net.liftweb.common.{Box, Failure, Full}
import org.springframework.core.io.{ClassPathResource => CPResource}

import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.parsing.ConstructingParser
import scala.xml.transform.{RewriteRule, RuleTransformer}

case class User(username: String, password: String, role: Set[String]) {
  def toNode: Node = <user name={ username } password={ password } role={ role.mkString(",") } />
}

object UserManagementIO {

  def replaceXml(currentXml: NodeSeq, newXml: Node, path: URI): Box[File] = {
    val file: files.File = files.File(path)

    if (!file.isWriteable)
      Failure(s"$path is not writable")
    else if (!file.isReadable)
      Failure(s"$path is not readable")
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
        Failure(s"'$path' modification have failed")
      else
        Full(file)
    }
  }

  def getUserFilePath: Either[UserConfigFileError, URI] = {
    val resources: Either[UserConfigFileError, UserFile] = UserFileProcessing.getUserResourceFile()
    resources match {
      case Left(err) =>
        Left(err)
      case Right(r)  =>
        val path = {
          if (r.name.startsWith("classpath:"))
            new CPResource(UserFileProcessing.DEFAULT_AUTH_FILE_NAME).getURI
          else
            new URI(r.name)
        }
        Right(path)
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

  def add(newUser: User): Box[User] = {
    getUserFilePath match {
      case Left(err)   =>
        Failure(err.msg)
      case Right(path) =>
        val srcXML = ConstructingParser.fromFile(File(path).toJava, preserveWS = true).document.children
        val digest =  new PasswordEncoder.DigestEncoder((srcXML \\ "authentication" \ "@hash").text)
        (srcXML \\ "authentication").head match {
          case e: Elem =>
            val newXml = e.copy(child = e.child ++ newUser.copy(password = getHash((srcXML \\ "authentication" \ "@hash").text).encode(newUser.password)).toNode)
            UserManagementIO.replaceXml(srcXML, newXml, path)
            Full(newUser)
          case _ =>
            Failure(s"Wrong formatting : $path")
        }
    }
  }

  def remove(toDelete: String): Box[File] = {
    getUserFilePath match {
      case Left(err)   =>
        Failure(err.msg)
      case Right(path) =>
        val srcXML = ConstructingParser.fromFile(File(path).toJava, preserveWS = true).document.children
        val toUpdate = (srcXML \\ "authentication").head
        val newXml = new RuleTransformer(new RewriteRule {
          override def transform(n: Node): NodeSeq = n match {
            case user: Elem if (user \ "@name").text == toDelete => NodeSeq.Empty
            case other => other
          }
        }).transform(toUpdate).head
        UserManagementIO.replaceXml(srcXML, newXml, path)
    }
  }

  def update(currentUser: String, newUser: User): Box[File] = {
    getUserFilePath match {
      case Left(err)   =>
        Failure(err.msg)
      case Right(path) =>
        val srcXML = ConstructingParser.fromFile(File(path).toJava, preserveWS = true).document.children
        val digest =  new PasswordEncoder.DigestEncoder((srcXML \\ "authentication" \ "@hash").text)
        val toUpdate = (srcXML \\ "authentication").head
        val newXml = new RuleTransformer(new RewriteRule {
          override def transform(n: Node): NodeSeq = n match {
            case user: Elem if (user \ "@name").text == currentUser =>
              newUser.copy(
                  username = if(newUser.username.isEmpty) currentUser else newUser.username
                , password = if(newUser.password.isEmpty) (user \ "@password").text  else getHash((srcXML \\ "authentication" \ "@hash").text).encode(newUser.password)
                , role     = if(newUser.role.isEmpty) Set("no_rights") else newUser.role
              ).toNode
            case other => other
          }
        }).transform(toUpdate).head
        UserManagementIO.replaceXml(srcXML, newXml, path)
    }
  }
}