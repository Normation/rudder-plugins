/*
 *************************************************************************************
 * Copyright 2024 Normation SAS
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

package com.normation.plugins.openscappolicies.services

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

object ReportSanitizer {

  implicit class EnhancedSafelist(sf: Safelist) {
    def addAttrsOn(attrs: List[String], tags: List[String]): Safelist = {
      tags.foldLeft(sf) { case (sfT, tag) => sfT.addAttributes(tag, attrs*) }
    }
  }

  /*
   * Our Safe list.
   * We accept `html`, `head`, `title` tags because it's on an iframe, plus
   * some other styling elements.
   * `style` is accepted, and so are class, id, and style attr.
   */
  val safelist = Safelist
    .relaxed()
    // .addEnforcedAttribute("a", "rel", "nofollow")
    .addTags("html", "head", "title", "style", "nav", "footer", "abbr")
    // add style and class on all these attrs:
    .addAttrsOn(
      List("class", "style", "id"),
      List("nav", "div", "span", "footer", "ul", "li", "a", "table", "th", "td", "tr", "input")
    )
    .addAttributes("h3", "title")
    .addAttributes("abbr", "title")
    .addEnforcedAttribute("a", "rel", "nofollow")
    .preserveRelativeLinks(true)

  /*
   * Sanitize HTML using Jsoup "relax" safelist, which allows div and other structural elements.
   * See: https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer
   */
  def sanitizeHTMLReport(content: String): String = {
    // clean seems to be pure
    "<!DOCTYPE html>" + Jsoup.clean(content, "http://", safelist)
  }

}
