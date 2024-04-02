package com.normation.plugins.usermanagement

import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL.*

object Serialization {
  def serializeRoleInfo(infos: Map[String, List[String]]): JValue = {
    infos.map {
      case (k, v) =>
        (("id"      -> k)
        ~ ("rights" -> v))
    }
  }

  def serializeUser(u: User): JValue = {
    (("username"     -> u.username)
    ~ ("password"    -> u.password)
    ~ ("permissions" -> u.permissions))
  }

  def serializeRole(rs: Set[Role]): JValue = {
    val (permissions, customs) = rs.partition {
      case Custom(_) => false
      case _         => true
    }
    (("permissions" -> permissions.map(_.name))
    ~ ("custom" -> customs.flatMap(_.rights.authorizationTypes.map(_.id)).toSeq.sorted))
  }
}
