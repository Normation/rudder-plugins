/*
 *************************************************************************************
 * Copyright 2016 Normation SAS
 *************************************************************************************
 *
 * This file is part of Rudder.
 *
 * Rudder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * In accordance with the terms of section 7 (7. Additional Terms.) of
 * the GNU General Public License version 3, the copyright holders add
 * the following Additional permissions:
 * Notwithstanding to the terms of section 5 (5. Conveying Modified Source
 * Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
 * Public License version 3, when you create a Related Module, this
 * Related Module is not considered as a part of the work and may be
 * distributed under the license agreement of your choice.
 * A "Related Module" means a set of sources files including their
 * documentation that, without modification of the Source Code, enables
 * supplementary functions or services in addition to those offered by
 * the Software.
 *
 * Rudder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

 *
 *************************************************************************************
 */

package com.normation.plugins.datasources

import com.normation.BoxSpecMatcher
import com.normation.rudder.domain.properties.GenericProperty
import com.normation.zio._
import net.liftweb.common._
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JsonPathTest extends Specification with BoxSpecMatcher with Loggable {

  implicit class ForceGet(json: String) {
    def forceParse = GenericProperty.parseValue(json) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalArgumentException(s"Error in parsing value: ${err.fullMsg}")
    }
  }

  sequential

  "These path are valid" should {

    "just an identifier" in {
      JsonSelect.compilePath("foo").map(_.getPath).either.runNow must beRight("$['foo']")
    }
  }

  "The selection" should {
    "fail if source is not a json" in {
      JsonSelect.fromPath("$", """{ not a json!} ,pde at all!""").either.runNow must beLeft
    }

    "fail if input path is not valid " in {
      JsonSelect.fromPath("$$$..$", """not a json! missing quotes!""").either.runNow must beLeft
    }

    "retrieve first" in {
      val res         = JsonSelect.fromPath("$.store.book[:1]", json).either.runNow
      val expectedVal = """
            {
                "category": "@reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            }
        """.forceParse
      res must beRight(expectedVal)
    }

    "retrieve all" in {
      val res         = JsonSelect.fromPath("$", json).either.runNow
      val expectedVal = json.forceParse
      res must beRight(expectedVal)
    }

    "works on empty top level array" in {
      val res         = JsonSelect.fromPath("$", arrayEmpty).either.runNow
      val expectedVal = "".forceParse
      res must beRight(expectedVal)
    }

    "works top level array of size 1 (but directly get the object)" in {
      val res         = JsonSelect.fromPath("$", arrayOfObjects1).either.runNow
      val expectedVal = """ {"id":"@one"} """.forceParse
      res must beRight(expectedVal)
    }
    "works top level array of size 1 when access the first child" in {
      val res         = JsonSelect.fromPath("$.[0]", arrayOfObjects1).either.runNow
      val expectedVal = """ {"id":"@one"} """.forceParse
      res must beRight(expectedVal)
    }

    "works on top level array of size n" in {
      val res         = JsonSelect.fromPath("$", arrayOfObjects2).either.runNow
      val expectedVal = arrayOfObjects2.forceParse
      res must beRight(expectedVal)
    }

    "works top level array of size n when access the first child" in {
      val res         = JsonSelect.fromPath("$.[0]", arrayOfObjects2).either.runNow
      val expectedVal = """ {"id":"@one"} """.forceParse
      res must beRight(expectedVal)
    }
  }

  // we need to add @ since it caused bugs like https://issues.rudder.io/issues/19863

  "get childrens" should {
    "retrieve JSON childrens forming an array" in {
      JsonSelect.fromPath("$.store.book[*]", json).either.runNow must beRight("""[
             {
                "category": "@reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "@fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            },
            {
                "category": "@\"quote\"horror\"",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            },
            {
                "category": "@fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
            }]""".forceParse)
    }
    "retrieve NUMBER childrens forming an array" in {
      JsonSelect.fromPath("$.store.book[*].price", json).either.runNow must beRight("""[8.95, 12.99, 8.99, 22.99]""".forceParse)
    }
    "retrieve STRING childrens forming an array" in {
      JsonSelect.fromPath("$.store.book[*].category", json).either.runNow must beRight(
        """["@reference", "@fiction", "@\"quote\"horror\"", "@fiction"]""".forceParse
      )
    }
    "retrieve JSON childrens (one)" in {
      JsonSelect.fromPath("$.store.bicycle", json).either.runNow must beRight("""{"color":"red","price":19.95}""".forceParse)
    }
    "retrieve NUMBER childrens (one)" in {
      JsonSelect.fromPath("$.store.bicycle.price", json).either.runNow must beRight("19.95".forceParse)
    }
    "retrieve STRING childrens (one)" in {
      JsonSelect.fromPath("$.store.bicycle.color", json).either.runNow must beRight("red".forceParse)
    }
    "retrieve ARRAY INT childrens (one)" in {
      JsonSelect.fromPath("$.intTable", json).either.runNow must beRight("[1, 2, 3]".forceParse)
    }
    "retrieve ARRAY STRING childrens (one)" in {
      JsonSelect.fromPath("$.stringTable", json).either.runNow must beRight("""["@one", "@two"]""".forceParse)
    }
  }

  "from hostnames" should {

    "be able to compare with content with dot" in {
      JsonSelect.fromPath("$.nodes.[?(@.hostname =~ /abc123.some.host.com.*/i)]", hostnames).either.runNow must beRight(
        """{"environment":"DEV_INFRA","hostname":"abc123.some.host.com"}""".forceParse
      )
    }

    "be able to compare with content with dot" in {
      JsonSelect.fromPath("""$.nodes['abc456.some.host.com']""", hostnames2).either.runNow must beRight(
        """{ "environment": "DEV_INFRA" }""".forceParse
      )
    }

  }

  lazy val hostnames = """
  { "nodes": [
    {
      "hostname": "abc123.some.host.com",
      "environment": "DEV_INFRA"
    },
    {
      "abc456.some.host.com": { "environment": "DEV_INFRA" }
    },
    {
      "hostname": "def123",
      "environment": "DEV_INFRA"
    },
    {
      "hostname": "ghi123.some.host.com",
      "environment": "DEV_INFRA"
    },
    {
      "hostname": "ghi456",
      "environment": "DEV_INFRA"
    }
  ] }
  """

  lazy val hostnames2 = """
  { "nodes": {
       "abc456.some.host.com": { "environment": "DEV_INFRA" }
  } }
  """

  lazy val arrayOfObjects1 =
    """[
     {"id":"@one"}
  ]"""

  lazy val arrayOfObjects2 =
    """[
     {"id":"@one"}
   , {"id":"@two"}
  ]"""

  lazy val arrayEmpty = """[]"""

  lazy val json = """
  {
    "store": {
        "book": [
            {
                "category": "@reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "@fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            },
            {
                "category": "@\"quote\"horror\"",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            },
            {
                "category": "@fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
            }
        ],
        "bicycle": {
            "color": "red",
            "price": 19.95
        }
    },
    "expensive": 10,
    "intTable": [1,2,3],
    "stringTable": ["@one", "@two"]
  }
  """

}
