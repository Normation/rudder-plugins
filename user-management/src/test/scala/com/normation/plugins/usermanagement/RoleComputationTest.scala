package com.normation.plugins.usermanagement

import com.normation.plugins.usermanagement.UserManagementService.computeRoleCoverage
import com.normation.rudder.Role.Custom
import com.normation.rudder.RoleToRights.parseRole
import com.normation.rudder.{AuthorizationType, Role, RoleToRights}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RoleComputationTest extends Specification {
  "Computation of Role Coverage over Rights" should {

    "return 'None' when parameters are empty" in {
      val role = parseRole(Seq("user")).toSet
      computeRoleCoverage(role, Set()) must beNone
      computeRoleCoverage(Set(), Set(AuthorizationType.Compliance.Read)) must beNone
      computeRoleCoverage(Set(), Set()) must beNone
    }

    "return 'None' when authzs contains no_rights" in {
      val role = parseRole(Seq("user")).toSet
      computeRoleCoverage(role, Set(AuthorizationType.NoRights)) must beNone
      computeRoleCoverage(role, Set(AuthorizationType.NoRights) ++ AuthorizationType.allKind) must beNone
    }

    "return a 'Custom' role for empty intersection" in {
      val role = parseRole(Seq("user")).toSet
      computeRoleCoverage(role, Set(AuthorizationType.Compliance.Read)) match {
        case Some(r) =>
          r.head match {
            case Custom(_) => success
            case knowRole => failure(s"Unexpected role : ${knowRole.name}")
          }
        case None => success
      }
    }

    "contains 'Inventory' and 'Custom' roles" in {
      //      val role = parseRole(Seq("inventory", "user")).toSet
      val role = Role.values.map(_.name).toSeq

      computeRoleCoverage(
        RoleToRights.parseRole(role).toSet
        //        role
        , Set(AuthorizationType.Compliance.Read) ++ Role.Inventory.rights.authorizationTypes
      ) match {
        case Some(rs) =>
          val rolesName = rs.map(_.name.toLowerCase)
          if (rolesName.contains("inventory") && rolesName.contains("custom")) {
            rs.find {
              case Custom(_) => true
              case _ => false
            } match {
              case Some(r) =>
                if (r.rights.displayAuthorizations == "compliance_read")
                  success
                else
                  failure(s"wrong rights")
            }
          }
          else
            failure(s"Missing role : $rs")
        case None => failure("No roles found")
      }
    }

    "only detect 'Inventory' role" in {
      val role = parseRole(Seq("inventory")).toSet
      computeRoleCoverage(
        role
        , Role.Inventory.rights.authorizationTypes
      ) match {
        case Some(rs) =>
          val rolesName = rs.map(_.name.toLowerCase)
          if (rolesName.size == 1) {
            rs.head match {
              case r if r.name == "inventory" => success
              case r => failure(s"Wrong role : ${r.name}")
            }
          }
          else
            failure(s"Excepted 1 custom role, instead : ${rolesName.size} role(s)")
        case None => failure("No roles found")
      }
    }

    "only detect one custom role" in {
      val role = parseRole(Seq("inventory", "user")).toSet
      computeRoleCoverage(
        role
        , Set(
            AuthorizationType.UserAccount.Read
          , AuthorizationType.UserAccount.Write
          , AuthorizationType.UserAccount.Edit
        )
      ) match {
        case Some(rs) =>
          val rolesName = rs.map(_.name.toLowerCase)
          if (rolesName.size == 1) {
            rs.head match {
              case Custom(_) => success
              case r => failure(s"Unexpected role : ${r.name}")
            }
          }
          else
            failure(s"Excepted 1 custom role, instead : ${rolesName.size} role(s)")
        case None => failure("No roles found")
      }
    }

    "return administrator " in {
      val role = Role.values.map(_.name).toSeq
      computeRoleCoverage(
        RoleToRights.parseRole(role).toSet
        , AuthorizationType.allKind
      ) match {
        case Some(rs) =>
          if(rs.size ==  1 && rs.head.name == "administrator")
            success
          else
            failure("Excepted only administrator role")
        case _ => failure("Nothing returned")
      }
    }

    "allows intersection between know roles" in {
      val role = parseRole(Seq("inventory", "user")).toSet
      computeRoleCoverage(
        role
        , Role.User.rights.authorizationTypes ++ Role.Inventory.rights.authorizationTypes
      ) match {
        case Some(rs) =>
          val rolesName = rs.map(_.name.toLowerCase)
          if (rolesName.size == 2 && rs.forall(r => r.name == "inventory" || r.name == "user"))
            success
          else
            failure(s"Excepted inventory and user role, instead : ${rolesName.mkString(",")}")
        case None => failure("No roles found")
      }
    }

    "ignore NoRights role" in {
      val role = parseRole(Seq("no_rights", "user")).toSet
      computeRoleCoverage(
        role
        , Role.User.rights.authorizationTypes
      ) match {
        case Some(rs) =>
          val rolesName = rs.map(_.name.toLowerCase)
          if (rolesName.size == 1) {
            rs.head match {
              case r if r.name == "user" => success
              case r => failure(s"Wrong role : ${r.name}")
            }
          }
          else
            failure(s"Excepted 'User' role, instead : ${rolesName.mkString(",")}")
        case None => failure("No roles found")
      }
    }
  }
}
