package com.normation.plugins.usermanagement

import com.normation.plugins.usermanagement.UserManagementService.computeRoleCoverage
import com.normation.rudder.AuthorizationType
import com.normation.rudder.Role
import com.normation.rudder.Role.Custom
import com.normation.rudder.RudderRoles
import com.normation.zio._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RoleComputationTest extends Specification {
  def parseRoles(roles: List[String]) = RudderRoles.parseRoles(roles).runNow.toSet

  "Computation of Role Coverage over Rights" should {

    "return 'None' when parameters are empty" in {
      (computeRoleCoverage(Set(Role.User), Set()) must beNone) and
      (computeRoleCoverage(Set(), Set(AuthorizationType.Compliance.Read)) must beNone) and
      (computeRoleCoverage(Set(), Set()) must beNone)
    }

    "return 'None' when authzs contains no_rights" in {
      (computeRoleCoverage(Set(Role.User), Set(AuthorizationType.NoRights)) must beNone) and
      (computeRoleCoverage(Set(Role.User), Set(AuthorizationType.NoRights) ++ AuthorizationType.allKind) must beNone)
    }

    "return a 'Custom' role for empty intersection" in {
      computeRoleCoverage(Set(Role.User), Set(AuthorizationType.Compliance.Read)) must beEqualTo(
        Some(Set(Role.forAuthz(AuthorizationType.Compliance.Read)))
      )
    }

    "contains 'Inventory' and 'Custom' roles" in {
      computeRoleCoverage(
        Role.values,
        Set(AuthorizationType.Compliance.Read) ++ Role.Inventory.rights.authorizationTypes
      ) must beEqualTo(Some(Set(Role.Inventory, Role.forAuthz(AuthorizationType.Compliance.Read))))
    }

    "only detect 'Inventory' role" in {
      computeRoleCoverage(
        Set(Role.Inventory),
        Role.Inventory.rights.authorizationTypes
      ) must beEqualTo(Some(Set(Role.Inventory)))
    }

    "only detect one custom role" in { // why ?
      val a: Set[AuthorizationType] = Set(
        AuthorizationType.UserAccount.Read,
        AuthorizationType.UserAccount.Write,
        AuthorizationType.UserAccount.Edit
      )
      computeRoleCoverage(
        Set(Role.User, Role.Inventory),
        a
      ) must beEqualTo(Some(Set(Role.forAuthz(a))))
    }

    "return administrator " in {
      computeRoleCoverage(
        Role.values,
        AuthorizationType.allKind
      ) must beEqualTo(Some(Set(Role.Administrator)))
    }

    "allows intersection between know roles" in {
      computeRoleCoverage(
        Set(Role.Inventory, Role.User),
        Role.User.rights.authorizationTypes ++ Role.Inventory.rights.authorizationTypes
      ) must beEqualTo(Some(Set(Role.User, Role.Inventory)))
    }

    "ignore NoRights role" in {
      computeRoleCoverage(
        Set(Role.NoRights, Role.User),
        Role.User.rights.authorizationTypes
      ) must beEqualTo(Some(Set(Role.User)))
    }
  }
}
