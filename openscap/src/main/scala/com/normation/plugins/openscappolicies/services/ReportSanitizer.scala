package com.normation.plugins.openscappolicies.services

import com.normation.errors.IOResult
import com.normation.plugins.openscappolicies.OpenScapReport
import org.owasp.validator.html.*
import scala.xml.NodeSeq

class ReportSanitizer(policyFile: String) {

  val policy = Policy.getInstance(policyFile)
  val as     = new AntiSamy

  def sanitizeReport(input: OpenScapReport): IOResult[NodeSeq] = {
    val cr = as.scan(input.content, policy)

    for {
      report <- IOResult.attempt(xml.XML.loadString(cr.getCleanHTML))
    } yield {
      report
    }
  }
}
