package com

import com.normation.plugins.usermanagement.JsonProviderInfo
import com.normation.plugins.usermanagement.JsonRights
import com.normation.plugins.usermanagement.JsonRoles
import com.normation.plugins.usermanagement.JsonUser
import com.normation.rudder.users.UserStatus
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SerializationTest extends Specification {

  "JsonUser" should {
    "use providers info" in {
      val providersInfo: Map[String, JsonProviderInfo] = Map(
        "provider1" -> JsonProviderInfo(
          "provider1",
          JsonRights(Set("read")),
          JsonRoles(Set("read_role")),
          JsonRights.empty
        ),
        "provider2" -> JsonProviderInfo(
          "provider2",
          JsonRights(Set("read", "write")),
          JsonRoles(Set("read_role", "write_role")),
          JsonRights(Set("custom_read"))
        )
      )

      val expected = JsonUser(
        "user",
        UserStatus.Active,
        JsonRights(Set("read", "write")),
        JsonRoles(Set("read_role", "write_role")),
        JsonRoles(Set("read_role", "write_role")),
        JsonRights(Set("custom_read")),
        List("provider1", "provider2"),
        providersInfo
      )

      JsonUser("user", UserStatus.Active, providersInfo) must beEqualTo(expected)
    }
  }
}
