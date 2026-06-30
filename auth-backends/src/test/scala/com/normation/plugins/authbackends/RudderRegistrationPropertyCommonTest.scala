package com.normation.plugins.authbackends

import com.normation.plugins.authbackends.RudderRegistrationPropertyCommon.*
import org.junit.runner.RunWith
import zio.test.*
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class RudderRegistrationPropertyCommonTest extends ZIOSpecDefault {

  override def spec: Spec[Any, Any] = {
    suite("Parsing functions")(
      testParseScopes()
    )
  }

  private def testParseScopes() = {
    val genScope = Gen.listOf1(Gen.alphaNumericChar).map(_.mkString)
    suite("parseScope")(
      test("successfully parse a blank scope") {
        assertTrue(parseScope(" ") == Right(List.empty))
      },
      test("successfully parse scopes separated by ,") {
        checkAll(Gen.listOf1(genScope).map(_.mkString(",")))(s => assertTrue(parseScope(s) == Right(s.split(",").toList)))
      },
      test("successfully parse scopes separated by space") {
        checkAll(Gen.listOf1(genScope).map(_.mkString(" ")))(s => assertTrue(parseScope(s) == Right(s.split(" ").toList)))
      },
      test("fail to parse scope with other content") {
        assertTrue(parseScope("invalid&scope").isLeft == true)
      }
    )
  }
}
