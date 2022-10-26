/*
 *************************************************************************************
 * Copyright 2018 Normation SAS
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

package com.normation.plugins.branding.snippet

import bootstrap.rudder.plugin.BrandingPluginConf
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.plugins.PluginVersion
import com.normation.rudder.web.snippet.Login
import net.liftweb.common.Full
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers._
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class LoginBranding(val status: PluginStatus, version: PluginVersion)(implicit val ttag: ClassTag[Login])
    extends PluginExtensionPoint[Login] with Loggable {

  def pluginCompose(snippet: Login): Map[String, NodeSeq => NodeSeq] = Map(
    "display" -> guard(display(_))
  )

  private[this] val confRepo = BrandingPluginConf.brandingConfService
  def display(xml: NodeSeq)  = {
    val data                      = confRepo.getConf
    val bar                       = data match {
      case Full(d) if (d.displayBarLogin) =>
        <div id="headerBar">
          <div class="background">
            <span>{if (d.displayLabel) d.labelText}</span>
          </div>
        </div>
      case _                              => NodeSeq.Empty
    }
    var (customLogo, brandingCss) = data match {
      case Full(d) =>
        (
          d.wideLogo.loginLogo,
          <style>
          #headerBar {{background-color:#fff; float:left; height:30px; width:100%; position:relative; border-top-left-radius:20px; border-top-right-radius: 20px; overflow: hidden;}}
          #headerBar > .background {{background-color: {d.barColor.toRgba}; color: {
            d.labelColor.toRgba
          }; font-size:20px; text-align:center; font-weight: 700; position: absolute; ;top: 0;bottom: 0;left: 0;right: 0;}}
          #headerBar + form > .motd:not(.enabled) + .form-group{{margin-top: 30px;}}
          .motd.enabled{{margin-top: 15px;}}
          .rudder-branding-logo {{
          background-image: url(data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjxzdmcKICAgeG1sbnM6ZGM9Imh0dHA6Ly9wdXJsLm9yZy9kYy9lbGVtZW50cy8xLjEvIgogICB4bWxuczpjYz0iaHR0cDovL2NyZWF0aXZlY29tbW9ucy5vcmcvbnMjIgogICB4bWxuczpyZGY9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkvMDIvMjItcmRmLXN5bnRheC1ucyMiCiAgIHhtbG5zOnN2Zz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciCiAgIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIKICAgaWQ9InN2ZzkyOCIKICAgdmVyc2lvbj0iMS4xIgogICB2aWV3Qm94PSIwIDAgMTA3LjE1NjI1IDE5Ljg0Mzc1IgogICBoZWlnaHQ9Ijc1IgogICB3aWR0aD0iNDA1Ij4KICA8ZGVmcwogICAgIGlkPSJkZWZzOTIyIiAvPgogIDxtZXRhZGF0YQogICAgIGlkPSJtZXRhZGF0YTkyNSI+CiAgICA8cmRmOlJERj4KICAgICAgPGNjOldvcmsKICAgICAgICAgcmRmOmFib3V0PSIiPgogICAgICAgIDxkYzpmb3JtYXQ+aW1hZ2Uvc3ZnK3htbDwvZGM6Zm9ybWF0PgogICAgICAgIDxkYzp0eXBlCiAgICAgICAgICAgcmRmOnJlc291cmNlPSJodHRwOi8vcHVybC5vcmcvZGMvZGNtaXR5cGUvU3RpbGxJbWFnZSIgLz4KICAgICAgICA8ZGM6dGl0bGU+PC9kYzp0aXRsZT4KICAgICAgPC9jYzpXb3JrPgogICAgPC9yZGY6UkRGPgogIDwvbWV0YWRhdGE+CiAgPGcKICAgICB0cmFuc2Zvcm09InRyYW5zbGF0ZSgwLC0yNzcuMTU2MjQpIgogICAgIGlkPSJsYXllcjEiPgogICAgPGcKICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmYiCiAgICAgICB0cmFuc2Zvcm09Im1hdHJpeCgxLjQzMDAwMjUsMCwwLDEuNDMwMDAyNSwtNDUuODc1MTE4LC0xMjQuOTMwNykiCiAgICAgICBpZD0iZzE1MTMiPgogICAgICA8cGF0aAogICAgICAgICBpZD0icGF0aDE0IgogICAgICAgICBzdHlsZT0iZmlsbDojZmZmZmZmO2ZpbGwtb3BhY2l0eToxO2ZpbGwtcnVsZTpub256ZXJvO3N0cm9rZTpub25lO3N0cm9rZS13aWR0aDowLjAwNTM3NDU4IgogICAgICAgICBkPSJtIDU0LjU3NTk2NSwyODguMDk0NzUgYyAwLjQ5NzY3MiwwIDAuODkzNzgzLC0wLjExODQ3IDEuMTg4MDI1LC0wLjM1NjYxIDAuMjk0MjQ2LC0wLjIzNzkzIDAuNDQxNjY4LC0wLjU2MTA1IDAuNDQxNjY4LC0wLjk2ODUxIHYgLTAuMDIyOCBjIDAsLTAuNDMwODcgLTAuMTQzOTQxLC0wLjc1NzQgLTAuNDMwMTY1LC0wLjk4MDQyIC0wLjI4NjY3NCwtMC4yMjI1OCAtMC42OTA1MDYsLTAuMzMzNzkgLTEuMjEwODgsLTAuMzMzNzkgaCAtMi4wMjUzNSB2IDIuNjYyMTIgeiBtIC0zLjQxMzMyNSwtMy45MTQzNyBoIDMuNTE5ODgzIGMgMC40OTYwMDUsMCAwLjkzNjAwNywwLjA2OTggMS4zMTk4NjEsMC4yMDg0NiAwLjM4MzY5NSwwLjEzOTQ1IDAuNzAzMDYzLDAuMzMyOCAwLjk1ODg2MywwLjU4MDgyIDAuMjEwMzkzLDAuMjE4MzcgMC4zNzIwNDQsMC40NzA0MyAwLjQ4NDk1OCwwLjc1NjE5IDAuMTEyOTE5LDAuMjg1OTggMC4xNjk1MjcsMC42MDE4MSAwLjE2OTUyNywwLjk0NzUyIHYgMC4wMjI2IGMgMCwwLjMyMzcxIC0wLjA0NzIzLDAuNjE0NTMgLTAuMTQxMDY5LDAuODc0NDYgLTAuMDk0NDUsMC4yNTkxMyAtMC4yMjQzMTUsMC40ODg1OSAtMC4zODk2LDAuNjg3NzggLTAuMTY1NDM4LDAuMTk5OCAtMC4zNjI4MSwwLjM2ODkxIC0wLjU5MTgyMSwwLjUwNzk2IC0wLjIyOTQ2MSwwLjEzOTA2IC0wLjQ4MzQ0MiwwLjI0NjAxIC0wLjc2MTY0MywwLjMyMTMgbCAyLjEzMTkwOCwyLjk4OTY2IGggLTEuNjM1NDQ2IGwgLTEuOTQ2NDk0LC0yLjc1Mjk1IGggLTEuNzQyMzA0IHYgMi43NTI5NCBIIDUxLjE2MjY0IHYgLTcuODk2NzYiIC8+CiAgICAgIDxwYXRoCiAgICAgICAgIGlkPSJwYXRoMTYiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmU7c3Ryb2tlLXdpZHRoOjAuMDA1Mzc0NTgiCiAgICAgICAgIGQ9Im0gNjMuOTA5ODksMjkyLjIwMTA0IGMgLTAuNTEwOTkxLDAgLTAuOTczODQ5LC0wLjA3MyAtMS4zODczNjYsLTAuMjE5NzcgLTAuNDEzNjY4LC0wLjE0NjkxIC0wLjc2NzI0OCwtMC4zNjY2OSAtMS4wNjAyNzcsLTAuNjU5OTMgLTAuMjkzNDksLTAuMjkzNDMgLTAuNTE4ODY2LC0wLjY1NjMgLTAuNjc2ODg1LC0xLjA4ODc4IC0wLjE1Nzg3LC0wLjQzMjI5IC0wLjIzNjU3NiwtMC45MzQyIC0wLjIzNjU3NiwtMS41MDU5MyB2IC00LjU0NjI1IGggMS4zNzU4NjEgdiA0LjQ4OTU0IGMgMCwwLjczNzIzIDAuMTc3NywxLjI5NzQ1IDAuNTMzMzk1LDEuNjgxMTEgMC4zNTUwOTEsMC4zODM0NCAwLjg0NzAxMSwwLjU3NTM2IDEuNDc0ODU1LDAuNTc1MzYgMC42MjAxMjMsMCAxLjEwNzY1NiwtMC4xODQyNSAxLjQ2MzM0OSwtMC41NTI3NiAwLjM1NTU0NCwtMC4zNjgzMSAwLjUzMzM5NCwtMC45MTc2NiAwLjUzMzM5NCwtMS42NDcgdiAtNC41NDYyNSBoIDEuMzc2MzE4IHYgNC40Nzg0NCBjIDAsMC41ODY2NyAtMC4wODExMywxLjEwMTkgLTAuMjQyNzg0LDEuNTQ1NjkgLTAuMTYxODAxLDAuNDQzNzkgLTAuMzg5Mjk3LDAuODEzOTEgLTAuNjgyMzMxLDEuMTEwNzcgLTAuMjkzNDg2LDAuMjk3MjcgLTAuNjQ4NTc3LDAuNTE5MDggLTEuMDY2MTc5LDAuNjY1OTkgLTAuNDE3NjA0LDAuMTQ2NzMgLTAuODg1MzA1LDAuMjE5NzcgLTEuNDA0Nzc0LDAuMjE5NzciIC8+CiAgICAgIDxwYXRoCiAgICAgICAgIGlkPSJwYXRoMTgiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmU7c3Ryb2tlLXdpZHRoOjAuMDA1Mzc0NTgiCiAgICAgICAgIGQ9Im0gNzMuNDk5MDA5LDI5MC44MjQ2OCBjIDAuNDEzMzYzLDAgMC43ODk5NTEsLTAuMDY1OCAxLjEyNzkzOCwtMC4xOTgxOCAwLjMzODU4OSwtMC4xMzE5OCAwLjYyNjQ3OCwtMC4zMTcwNSAwLjg2MzM1NywtMC41NTQ5OSAwLjIzNjg3OCwtMC4yMzc5NCAwLjQyMDkzMywtMC41MjEyOCAwLjU1MjYxOSwtMC44NDk2MyAwLjEzMTUzLC0wLjMyODU2IDAuMTk3NTIyLC0wLjY4NTE2IDAuMTk3NTIyLC0xLjA3MDQyIHYgLTAuMDIzIGMgMCwtMC4zODQ2NiAtMC4wNjU5OSwtMC43NDM4OSAtMC4xOTc1MjIsLTEuMDc1ODcgLTAuMTMxNjg2LC0wLjMzMjM4IC0wLjMxNTc0MSwtMC42MTcxNSAtMC41NTI2MTksLTAuODU1MDkgLTAuMjM2ODc5LC0wLjIzODU0IC0wLjUyNDc2OCwtMC40MjUyMiAtMC44NjMzNTcsLTAuNTYwODMgLTAuMzM3OTg3LC0wLjEzNjIzIC0wLjcxNDU3NSwtMC4yMDQwNCAtMS4xMjc5MzgsLTAuMjA0MDQgSCA3MS45NDIyNyB2IDUuMzkyMDYgeiBtIC0yLjkzMjkwNSwtNi42NDQzIGggMi45NDQyNTcgYyAwLjYxNjY0MiwwIDEuMTgyNTc3LDAuMDk5NSAxLjY5Nzk2MSwwLjI5ODY4IDAuNTE0OTI2LDAuMTk5NzkgMC45NTg3MTMsMC40NzYwNyAxLjMzMDc1OCwwLjgyOTA0IDAuMzcyNjQ4LDAuMzUzNTggMC42NTk5MjksMC43NjkxMiAwLjg2MzIwNSwxLjI0NzIxIDAuMjAyOTc0LDAuNDc3NDkgMC4zMDQ1MzcsMC45OTQ1NCAwLjMwNDUzNywxLjU1MDc0IHYgMC4wMjI0IGMgMCwwLjU1Njc5IC0wLjEwMTU2MywxLjA3NTY2IC0wLjMwNDUzNywxLjU1NzE5IC0wLjIwMzI3NiwwLjQ4MDkyIC0wLjQ5MDU1NywwLjg5ODY3IC0wLjg2MzIwNSwxLjI1MTg0IC0wLjM3MjA0NSwwLjM1Mzc5IC0wLjgxNTgzMiwwLjYzMjA4IC0xLjMzMDc1OCwwLjgzNDkgLTAuNTE1Mzg0LDAuMjAzMDQgLTEuMDgxMzE5LDAuMzA0NzUgLTEuNjk3OTYxLDAuMzA0NzUgaCAtMi45NDQyNTcgdiAtNy44OTY3NiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaWQ9InBhdGgyMCIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4wMDUzNzQ1OCIKICAgICAgICAgZD0ibSA4My42NzQ2NDgsMjkwLjgyNDY4IGMgMC40MTM1MTYsMCAwLjc4OTY1LC0wLjA2NTggMS4xMjgwODgsLTAuMTk4MTggMC4zMzg0MzgsLTAuMTMxOTggMC42MjY0NzcsLTAuMzE3MDUgMC44NjMwNDksLTAuNTU0OTkgMC4yMzY3MzIsLTAuMjM3OTQgMC40MjA5NCwtMC41MjEyOCAwLjU1Mjc3MywtMC44NDk2MyAwLjEzMTUzNiwtMC4zMjg1NiAwLjE5NzUyOCwtMC42ODUxNiAwLjE5NzUyOCwtMS4wNzA0MiB2IC0wLjAyMyBjIDAsLTAuMzg0NjYgLTAuMDY1OTksLTAuNzQzODkgLTAuMTk3NTI4LC0xLjA3NTg3IC0wLjEzMTgzMywtMC4zMzIzOCAtMC4zMTYwNDEsLTAuNjE3MTUgLTAuNTUyNzczLC0wLjg1NTA5IC0wLjIzNjU3MiwtMC4yMzg1NCAtMC41MjQ2MTEsLTAuNDI1MjIgLTAuODYzMDQ5LC0wLjU2MDgzIC0wLjMzODQzOCwtMC4xMzYyMyAtMC43MTQ1NzIsLTAuMjA0MDQgLTEuMTI4MDg4LC0wLjIwNDA0IGggLTEuNTU2NTg2IHYgNS4zOTIwNiB6IG0gLTIuOTMzMjA0LC02LjY0NDMgaCAyLjk0NDI1MyBjIDAuNjE2OTQ3LDAgMS4xODI4OCwwLjA5OTUgMS42OTgxMTUsMC4yOTg2OCAwLjUxNDc3MSwwLjE5OTc5IDAuOTU5MDE3LDAuNDc2MDcgMS4zMzEzNTksMC44MjkwNCAwLjM3MTg5NSwwLjM1MzU4IDAuNjU5NDcsMC43NjkxMiAwLjg2MjU5OSwxLjI0NzIxIDAuMjAyOTg0LDAuNDc3NDkgMC4zMDQ1NDIsMC45OTQ1NCAwLjMwNDU0MiwxLjU1MDc0IHYgMC4wMjI0IGMgMCwwLjU1Njc5IC0wLjEwMTU1OCwxLjA3NTY2IC0wLjMwNDU0MiwxLjU1NzE5IC0wLjIwMzEyOSwwLjQ4MDkyIC0wLjQ5MDcwNCwwLjg5ODY3IC0wLjg2MjU5OSwxLjI1MTg0IC0wLjM3MjM0MiwwLjM1Mzc5IC0wLjgxNjU4OCwwLjYzMjA4IC0xLjMzMTM1OSwwLjgzNDkgLTAuNTE1MjM1LDAuMjAzMDQgLTEuMDgxMTY4LDAuMzA0NzUgLTEuNjk4MTE1LDAuMzA0NzUgaCAtMi45NDQyNTMgdiAtNy44OTY3NiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaWQ9InBhdGgyMiIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4wMDUzNzQ1OCIKICAgICAgICAgZD0ibSAxMDMuMjQyMzQsMjg4LjA5NDc1IGMgMC40OTc4MiwwIDAuODkzOTMsLTAuMTE4NDcgMS4xODgwMiwtMC4zNTY2MSAwLjI5NDM5LC0wLjIzNzkzIDAuNDQxNTIsLTAuNTYxMDUgMC40NDE1MiwtMC45Njg1MSB2IC0wLjAyMjggYyAwLC0wLjQzMDg3IC0wLjE0MzY0LC0wLjc1NzQgLTAuNDMwMTcsLTAuOTgwNDIgLTAuMjg2NjcsLTAuMjIyNTggLTAuNjkwMiwtMC4zMzM3OSAtMS4yMTA3MywtMC4zMzM3OSBoIC0yLjAyNTY1IHYgMi42NjIxMiB6IG0gLTMuNDEzNDgsLTMuOTE0MzcgaCAzLjUyMDE4IGMgMC40OTYwMSwwIDAuOTM2MDEsMC4wNjk4IDEuMzE5ODcsMC4yMDg0NiAwLjM4MzI0LDAuMTM5NDUgMC43MDI3NywwLjMzMjggMC45NTg1NiwwLjU4MDgyIDAuMjEwNTQsMC4yMTgzNyAwLjM3MjA1LDAuNDcwNDMgMC40ODUyNiwwLjc1NjE5IDAuMTEyNzYsMC4yODU5OCAwLjE2OTA3LDAuNjAxODEgMC4xNjkwNywwLjk0NzUyIHYgMC4wMjI2IGMgMCwwLjMyMzcxIC0wLjA0NzEsMC42MTQ1MyAtMC4xNDA5MiwwLjg3NDQ2IC0wLjA5NDEsMC4yNTkxMyAtMC4yMjQwMSwwLjQ4ODU5IC0wLjM4OTYsMC42ODc3OCAtMC4xNjUxNCwwLjE5OTggLTAuMzYyNjYsMC4zNjg5MSAtMC41OTE5NywwLjUwNzk2IC0wLjIyOTQ2LDAuMTM5MDYgLTAuNDgzMjksMC4yNDYwMSAtMC43NjExOSwwLjMyMTMgbCAyLjEzMTkxLDIuOTg5NjYgaCAtMS42MzU3NSBsIC0xLjk0NjY0LC0yLjc1Mjk0IGggLTEuNzQyMzEgdiAyLjc1Mjk0IGggLTEuMzc2NDcgdiAtNy44OTY3NiIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaWQ9InBhdGgyNCIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4wMDUzNzQ1OCIKICAgICAgICAgZD0ibSA5Mi4yOTMyNDIsMjg1LjQzMjYyIGggNC40NjczOTkgdiAtMS4yNTIyNCBoIC01Ljg0MzU2MiB2IDEuMjUyMjQgaCAxLjM3NjE2MyIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaWQ9InBhdGgyNiIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4wMDUzNzQ1OCIKICAgICAgICAgZD0ibSA5Mi4yOTM1NTIsMjkwLjgyNDY4IHYgLTIuMTA5MTYgaCAzLjk1OTU4NiB2IC0xLjI1MjQ1IGggLTUuMzM2MDU5IHYgNC42MTQwNyBoIDUuODk5ODY0IHYgLTEuMjUyNDYgaCAtNC41MjMzOTEiIC8+CiAgICAgIDxwYXRoCiAgICAgICAgIGlkPSJwYXRoMjgiCiAgICAgICAgIHN0eWxlPSJmaWxsOiNmZmZmZmY7ZmlsbC1vcGFjaXR5OjE7ZmlsbC1ydWxlOm5vbnplcm87c3Ryb2tlOm5vbmU7c3Ryb2tlLXdpZHRoOjAuMDA1Mzc0NTgiCiAgICAgICAgIGQ9Im0gMzkuOTE3NDkxLDI5MS44MTUxOCBjIDAuMzM2MzIxLC0wLjA4OTIgMC42NTQxNzgsLTAuMjIzNDEgMC45NDc2NjYsLTAuMzk0MzQgLTAuMTQ1NzYsLTAuMzg0MjYgLTAuMDY0NzgsLTAuODM0MSAwLjI0NDU5NywtMS4xNDMyOCAwLjMwOTIyOSwtMC4zMDkxNyAwLjc1OTA3MiwtMC4zODk5MSAxLjE0MjkyMSwtMC4yNDQ2IDAuMTcxNDkxLC0wLjI5MzQzIDAuMzA1NzQ4LC0wLjYxMTA5IDAuMzk0MjkyLC0wLjk0NzUyIC0wLjM3NDkxOSwtMC4xNjgzMSAtMC42MzY3NzEsLTAuNTQ0NDggLTAuNjM2NzcxLC0wLjk4MjAyIDAsLTAuNDM4MzQgMC4yNjE4NTIsLTAuODEzOTEgMC42MzY3NzEsLTAuOTgyNDIgLTAuMDg4NTQsLTAuMzM2MjMgLTAuMjIyODAxLC0wLjY1Mzg3IC0wLjM5NDI5MiwtMC45NDczMiAtMC4xMDc0NjYsMC4wNDA4IC0wLjIxOTc3NSwwLjA2MTggLTAuMzMzNDQ2LDAuMDY2OCBsIC0xLjA5NDc4OCwxLjI1ODMyIGMgMC4wNjA4NSwwLjE5MTEgMC4wOTQ2LDAuMzkzOTMgMC4wOTQ2LDAuNjA0NjIgMCwxLjA5MzgzIC0wLjg4OTg0NiwxLjk4MzY0IC0xLjk4MzU3NiwxLjk4MzY0IC0xLjA5Mzg3OCwwIC0xLjk4Mzg3NywtMC44ODk4MSAtMS45ODM4NzcsLTEuOTgzNjQgMCwtMS4wOTQ0MiAwLjg4OTk5OSwtMS45ODQwMyAxLjk4Mzg3NywtMS45ODQwMyAwLjIwMTE1OCwwIDAuMzk1MzUzLDAuMDMwNSAwLjU3ODgwMiwwLjA4NyBsIDEuMjgzODM2LC0xLjExODA1IGMgMC4wMDc2LC0wLjEwMjUyIDAuMDI5NjcsLTAuMjA0ODQgMC4wNjcwNSwtMC4zMDI1MSAtMC4yOTM0ODgsLTAuMTcxNTUgLTAuNjExMzQ1LC0wLjMwNTU1IC0wLjk0NzY2NiwtMC4zOTQzNiAtMC4xNjgxNiwwLjM3NTM4IC0wLjU0NDU5NCwwLjYzNjczIC0wLjk4MjAyNiwwLjYzNjczIC0wLjQzODAzNiwwIC0wLjgxNDAxNCwtMC4yNjEzNSAtMC45ODE4NzIsLTAuNjM2NzMgLTAuMzM2NDczLDAuMDg4OCAtMC42NTQzMywwLjIyMjgxIC0wLjk0NzY2NSwwLjM5NDM2IDAuMTQ1NDU2LDAuMzgzNjQgMC4wNjQ0OCwwLjgzNDA4IC0wLjI0NTA1NCwxLjE0MzI3IC0wLjMwODc3NCwwLjMwOTE4IC0wLjc1ODc2OCwwLjM5MDEgLTEuMTQyOTIsMC4yNDQ1OSAtMC4xNzE0OTEsMC4yOTM0NSAtMC4zMDQ2ODcsMC42MTEwOSAtMC4zOTQ0NDQsMC45NDc1MiAwLjM3NTA3MSwwLjE2ODMxIDAuNjM2OTIzLDAuNTQzODggMC42MzY5MjMsMC45ODIyMiAwLDAuNDM4MTQgLTAuMjYxODUyLDAuODEzNzEgLTAuNjM2OTIzLDAuOTgyMDIgMC4wODk3NiwwLjMzNjQzIDAuMjIzMTA1LDAuNjU0MDkgMC4zOTQ0NDQsMC45NDc1MiAwLjM4NDE1MiwtMC4xNDUzMSAwLjgzNDI5OCwtMC4wNjQ2IDEuMTQyOTIsMC4yNDQ2IDAuMzA5NTMyLDAuMzA5MTggMC4zOTA2NjMsMC43NTkwMiAwLjI0NTA1NCwxLjE0MzI4IDAuMjkzMzM1LDAuMTcwOTMgMC42MTExOTIsMC4zMDUxNCAwLjk0NzY2NSwwLjM5NDM0IDAuMTY3ODU4LC0wLjM3NTM3IDAuNTQzODM2LC0wLjYzNjkzIDAuOTgxODcyLC0wLjYzNjkzIDAuNDM3NDMyLDAgMC44MTM4NjYsMC4yNjE1NiAwLjk4MjAyNiwwLjYzNjkzIiAvPgogICAgICA8cGF0aAogICAgICAgICBpZD0icGF0aDMwIgogICAgICAgICBzdHlsZT0iZmlsbDojZmZmZmZmO2ZpbGwtb3BhY2l0eToxO2ZpbGwtcnVsZTpub256ZXJvO3N0cm9rZTpub25lO3N0cm9rZS13aWR0aDowLjAwNTM3NDU4IgogICAgICAgICBkPSJtIDM4LjU5MDA2MiwyODcuNTgxNzQgYyAtMC4yMzQ5MTEsMC4yMzQ5MSAtMC4yMzQ5MTEsMC42MTU3MyAwLDAuODUwNjUgMC4xMTczMDQsMC4xMTcyNCAwLjI3MTIzOSwwLjE3NTk3IDAuNDI1MzIyLDAuMTc1OTcgMC4xNTM3ODMsMCAwLjMwODE2OSwtMC4wNTg3IDAuNDI1MDIsLTAuMTc1OTcgbCA0LjI5Mzc4NCwtNC45MDgxMSBjIDAuMDY1MDksLTAuMDY1MiAwLjA2NTA5LC0wLjE3MDk0IDAsLTAuMjM2MTMgLTAuMDY1MzksLTAuMDY1MiAtMC4xNzA3MzQsLTAuMDY1MiAtMC4yMzYxMjMsMCB2IDAgbCAtNC45MDgwMDMsNC4yOTM1OSIgLz4KICAgICAgPHBhdGgKICAgICAgICAgaWQ9InBhdGgzMiIKICAgICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4wMDUzNzQ1OCIKICAgICAgICAgZD0ibSAzMy4yNTg1NTMsMjg3LjE2NDk5IDAuNjY5MDEyLDAuNjkxODIgYyAwLjEzNzQzNCwtMi42NDY3OSAyLjMyNjcwOCwtNC43NTA0OSA1LjAwNzU5OSwtNC43NTA0OSAxLjEzNjExLDAgMi4xODM1MjEsMC4zNzggMy4wMjQxNzUsMS4wMTQ3MiBsIDEuMDUyNTU5LC0wLjkwMzEyIGMgLTEuMTA1Mzg0LC0wLjkxOTg2IC0yLjUyNjIwMSwtMS40NzM2NCAtNC4wNzY3MzQsLTEuNDczNjQgLTMuNDQxNjI3LDAgLTYuMjQ0NTE1LDIuNzI3MSAtNi4zNjk5OTMsNi4xMzc5NiBsIDAuNjkzMzgyLC0wLjcxNzI1IiAvPgogICAgICA8cGF0aAogICAgICAgICBpZD0icGF0aDM0IgogICAgICAgICBzdHlsZT0iZmlsbDojZmZmZmZmO2ZpbGwtb3BhY2l0eToxO2ZpbGwtcnVsZTpub256ZXJvO3N0cm9rZTpub25lO3N0cm9rZS13aWR0aDowLjAwNTM3NDU4IgogICAgICAgICBkPSJtIDM3Ljk3OTYyNiwyOTMuNzk2OTggMC42OTE1NjYsLTAuNjY5MDEgYyAtMi40MDg3NDYsLTAuMTI0NzIgLTQuMzY3MDQzLC0xLjk1MDEyIC00LjY5OTU4MiwtNC4yOTg0MiBsIC0wLjcxMzA1NywtMC43MzcwMyAtMC42NjUzNzksMC42ODgxOSBjIDAuMzIyMDkzLDMuMTMzNTcgMi45MTM4MzMsNS41OTI2NSA2LjEwMzg5OSw1LjcxMDExIGwgLTAuNzE3NDQ3LC0wLjY5Mzg0IiAvPgogICAgICA8cGF0aAogICAgICAgICBpZD0icGF0aDM2IgogICAgICAgICBzdHlsZT0iZmlsbDojZmZmZmZmO2ZpbGwtb3BhY2l0eToxO2ZpbGwtcnVsZTpub256ZXJvO3N0cm9rZTpub25lO3N0cm9rZS13aWR0aDowLjAwNTM3NDU4IgogICAgICAgICBkPSJtIDQzLjk0MjYxMSwyODguMzg0MzUgYyAtMC4xMjQ4NzIsMi40MDkwNCAtMS45NDk5NzIsNC4zNjc0NSAtNC4yOTg0NzYsNC42OTk4MyBsIC0wLjczNjk3MywwLjcxMjggMC42ODgzODQsMC42NjU3OSBjIDMuMTMzNjExLC0wLjMyMjI5IDUuNTkyMzA1LC0yLjkxMzc5IDUuNzA5NzU5LC02LjEwMzg1IGwgLTEuMzYyNjk0LDAuMDI1NCIgLz4KICAgIDwvZz4KICA8L2c+Cjwvc3ZnPgo=);
          background-size: contain;
          background-repeat: no-repeat;
          position: absolute;
          height: 25px;
          width: calc(100% - 10px);
          bottom: 2px;
          left: 5px;
          }}
          </style>
        )
      case _       => (<img src="/images/logo-rudder-white.svg" data-lift="with-cached-resource" alt="Rudder"/>, NodeSeq.Empty)
    }
    val logoContainer             = {
      <div>
        {customLogo}
        <style>
          .custom-branding-logo {{
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center;
            height: 120px;
          }}
        </style>
      </div>
    }
    val motd                      = data match {
      case Full(data) if (data.displayMotd) =>
        <div class="motd enabled" style="margin-bottom: 20px; text-align: center;">{data.motd}</div>
      case _                                => <div class="motd"></div>
    }
    (".logo-container" #> logoContainer &
    ".plugin-info" #> bar &
    ".plugin-info *+" #> brandingCss &
    ".motd" #> motd)(xml)
  }
}
