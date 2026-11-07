import Dependencies.servletProvided

// ============================================================================
// rudder-plugins build — sbt port of the Maven `plugins-parent` reactor.
// Shared logic lives in project/PluginBuild.scala (reused by rudder-plugins-private).
// Two build modes, driven exactly like Maven by `-Dlimited`:
//   - default  : `make` / `sbt "<plugin>/assembly"`            (unlicensed, OSS)
//   - licensed : `make licensed` / `sbt -Dlimited ... "<plugin>/assembly"`
// ============================================================================

PluginBuild.commonBuildSettings

// plugins-common-private: private code lib (LicensedPluginCheck); needs com.normation:license-lib
// from the private nexus. Only used in licensed mode; not part of the default aggregate.
lazy val pluginsCommonPrivate = Project("plugins-common-private", file("plugins-common-private"))
  .settings(PluginBuild.commonSettings)
  .settings(
    name    := "plugins-common-private",
    version := PluginBuild.privateLibVersion, // RUDDER_VERSION-PRIVATE_VERSION (main-build.conf)
    libraryDependencies ++= Dependencies.pluginBase,
    libraryDependencies += "com.normation" % "license-lib" % "2.4.0" // fixed private artifact (nexus)
  )

// ---- the 8 Scala plugins ------------------------------------------------------------------
lazy val apiAuthorizations   = PluginBuild.pluginProject("api-authorizations",   "apiauthorizations",   hasElm = true,  Seq(servletProvided))
lazy val authBackends        = PluginBuild.pluginProject("auth-backends",        "authbackends",        hasElm = true,  servletProvided +: Dependencies.authBackends)
lazy val branding            = PluginBuild.pluginProject("branding",             "branding",            hasElm = true,  Seq(servletProvided))
lazy val changeValidation    = PluginBuild.pluginProject("change-validation",    "changevalidation",    hasElm = true,  servletProvided +: Dependencies.changeValidation)
lazy val datasourcesPlugin   = PluginBuild.pluginProject("datasources",          "datasources",         hasElm = true,  servletProvided +: Dependencies.datasources)
lazy val nodeExternalReports = PluginBuild.pluginProject("node-external-reports","nodeexternalreports", hasElm = false)
lazy val openscapPlugin      = PluginBuild.pluginProject("openscap",             "openscappolicies",    hasElm = false, servletProvided +: Dependencies.openscap)
lazy val scaleOutRelay       = PluginBuild.pluginProject("scale-out-relay",      "scaleoutrelay",       hasElm = false, Seq(servletProvided))

// root aggregates only the OSS plugins (private lib excluded — it needs the private repo)
lazy val root = (project in file("."))
  .settings(name := "rudder-plugins", publish / skip := true)
  .aggregate(apiAuthorizations, authBackends, branding, changeValidation,
             datasourcesPlugin, nodeExternalReports, openscapPlugin, scaleOutRelay)
