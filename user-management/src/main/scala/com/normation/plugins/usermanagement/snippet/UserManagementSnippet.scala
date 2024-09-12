package com.normation.plugins.usermanagement.snippet

import com.normation.rudder.users.CurrentUser
import com.normation.rudder.web.snippet.WithNonce
import net.liftweb.http.DispatchSnippet
import net.liftweb.http.js.JE.*
import net.liftweb.http.js.JsCmds.*
import org.apache.commons.text.StringEscapeUtils
import scala.xml.NodeSeq

class UserManagementSnippet extends DispatchSnippet {

  override def dispatch: DispatchIt = { case "render" => _ => scripts }

  private def scripts: NodeSeq = {
    val userId = StringEscapeUtils.escapeEcmaScript(CurrentUser.get.map(_.getUsername).getOrElse(""))
    WithNonce.scriptWithNonce(
      Script(
        OnLoad(
          JsRaw(
            s"""
               |var main = document.getElementById("user-management-content")
               |var initValues = {
               |  contextPath : contextPath,
               |  userId : "${userId}"
               |}
               |var app  = Elm.UserManagement.init({node: main, flags: initValues})
               |""".stripMargin
          )
        )
      )
    )
  }
}
