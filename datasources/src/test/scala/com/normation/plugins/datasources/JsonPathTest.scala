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
import com.normation.rudder.domain.nodes.GenericProperty
import com.normation.rudder.domain.nodes.GenericProperty._
import net.liftweb.common._
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import com.normation.zio._

@RunWith(classOf[JUnitRunner])
class JsonPathTest extends Specification with BoxSpecMatcher with Loggable {

   implicit class ForceGet(json: String) {
     def forceParse = GenericProperty.parseValue(json) match {
       case Right(value) => value
       case Left(err)    => throw new IllegalArgumentException(s"Error in parsing value: ${err.fullMsg}")
     }
   }

  "These path are valid" should {

    "just an identifier" in  {
      JsonSelect.compilePath("foo").map( _.getPath ).either.runNow must beRight( "$['foo']" )
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
      val res = JsonSelect.fromPath("$.store.book", json).map( _.head ).either.runNow
      val expectedVal = """
            {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            }
        """.forceParse
      res must beRight (expectedVal)
    }
  }

  "get childrens" should {
    "retrieve JSON childrens forming an array" in {
      JsonSelect.fromPath("$.store.book[*]", json).either.runNow must beRight(List(
          """{
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            }""",
            """{
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            }""",
            """{
                "category": "\"quotehorror\"",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            }""",
            """{
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
            }""").map(_.forceParse))
    }
    "retrieve NUMBER childrens forming an array" in {
      JsonSelect.fromPath("$.store.book[*].price", json).either.runNow must beRight(List("8.95", "12.99", "8.99", "22.99").map(_.toConfigValue))
    }
    "retrieve STRING childrens forming an array" in {
      JsonSelect.fromPath("$.store.book[*].category", json).either.runNow must beRight(List("reference", "fiction", "\"quotehorror\"", "fiction").map(_.toConfigValue))
    }
    "retrieve JSON childrens (one)" in {
      JsonSelect.fromPath("$.store.bicycle", json).either.runNow must beRight(List("""{"color":"red","price":19.95}""").map(_.forceParse))
    }
    "retrieve NUMBER childrens (one)" in {
      JsonSelect.fromPath("$.store.bicycle.price", json).either.runNow must beRight(List("19.95").map(_.toConfigValue))
    }
    "retrieve STRING childrens (one)" in {
      JsonSelect.fromPath("$.store.bicycle.color", json).either.runNow must beRight(List("red").map(_.toConfigValue))
    }
    "retrieve ARRAY INT childrens (one)" in {
      JsonSelect.fromPath("$.intTable", json).either.runNow must beRight(List("1", "2", "3").map(_.toConfigValue))
    }
    "retrieve ARRAY STRING childrens (one)" in {
      JsonSelect.fromPath("$.stringTable", json).either.runNow must beRight(List("one", "two").map(_.toConfigValue))
    }
  }

  "from hostnames" should {

    "be able to compare with content with dot" in {
      JsonSelect.fromPath("$.nodes.[?(@.hostname =~ /abc123.some.host.com.*/i)]", hostnames).either.runNow must beRight(
        List("""{"environment":"DEV_INFRA","hostname":"abc123.some.host.com"}""".forceParse)
      )
    }

    "be able to compare with content with dot" in {
      JsonSelect.fromPath("""$.nodes['abc456.some.host.com']""", hostnames2).either.runNow must beRight(
        List("""{ "environment": "DEV_INFRA" }""".forceParse)
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


  lazy val json = """
  {
    "store": {
        "book": [
            {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            },
            {
                "category": "\"quotehorror\"",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            },
            {
                "category": "fiction",
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
    "stringTable": ["one", "two"]
  }
  """

}
