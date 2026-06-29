import sbt._
import sbt.Keys._
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport._
import scala.sys.process._

/**
 * Shared build glue for the Scala plugins, ported from the Maven `plugins-parent` POM +
 * `pom-template.xml`. This object lives in `rudder-plugins` and is REUSED by the downstream
 * `rudder-plugins-private` build, which symlinks this file from its `rudder-plugins` submodule
 * (the same way the repo already symlinks `makefiles`, `main-build.conf`, `plugins-common*`).
 * Centralising the logic here keeps the two repos' `build.sbt` to just a list of modules.
 *
 * The two build modes are driven exactly like Maven, by the *presence* of `-Dlimited`:
 *   - UNLICENSED (no `-Dlimited`, profile `internal-default`): compile
 *     `src/main/scala-templates/default` (OSS PluginEnableImpl, no license check).
 *   - LICENSED (`-Dlimited`, profile `internal-limited`): template-filter
 *     `src/main/scala-templates/limited` (LicensedPluginCheck), substituting the same
 *     placeholders Maven filled from `-Dplugin-resource-*` / `-Dplugin-declared-version`, and
 *     add the `plugins-common-private` dependency.
 */
object PluginBuild {

  /** licensed build iff `-Dlimited` is present (Maven activates by property presence). */
  val licensed: Boolean = sys.props.contains("limited")

  // ---- build.conf / main-build.conf parsing ------------------------------------------------
  private def parseConf(f: File): Map[String, String] =
    if (!f.exists) Map.empty
    else
      IO.readLines(f).iterator
        .map(_.trim)
        .filterNot(l => l.isEmpty || l.startsWith("#"))
        .flatMap(l => l.split("=", 2) match { case Array(k, v) => Some(k.trim -> v.trim); case _ => None })
        .toMap

  /** plugin-name from a plugin's build.conf. */
  def pluginName(base: File): String =
    parseConf(base / "build.conf").getOrElse("plugin-name", base.getName)

  /** full plugin id, e.g. rudder-plugin-api-authorizations. */
  def pluginFullName(base: File): String = s"rudder-plugin-${pluginName(base)}"

  /** plugin-version from build.conf (e.g. 2.2). */
  def pluginVersion(base: File): String =
    parseConf(base / "build.conf").getOrElse("plugin-version", "0.0")

  // ---- main-build.conf : the single source of truth for rudder + lib versions ---------------
  // sbt reads it directly (it is just Scala) — no `generate-pom` / pom-template needed, which only
  // existed because Maven resolves parent <version> before property interpolation. Resolved at the
  // build root (sbt cwd): in rudder-plugins-private this is a symlink to the submodule's file.
  private val mainBuild   = parseConf(file("main-build.conf"))
  val rudderVersion       = mainBuild.getOrElse("rudder-version", "0.0.0") // e.g. 9.2.0~alpha1
  val branchType          = mainBuild.getOrElse("branch-type", "")
  val commonVersion       = mainBuild.getOrElse("common-version", "")
  val privateVersion      = mainBuild.getOrElse("private-version", "")

  /** version of the rudder artifacts plugins build against (global-vars.mk RUDDER_BUILD_VERSION). */
  val rudderBuildVersion: String = branchType match {
    case "next" => s"$rudderVersion-SNAPSHOT"
    case _      => rudderVersion
  }

  /** a plugin's published version (global-vars.mk PLUGIN_POM_VERSION). */
  def pluginPomVersion(base: File): String = branchType match {
    case "next" | "nightly" => s"$rudderVersion-${pluginVersion(base)}-SNAPSHOT"
    case _                  => s"$rudderVersion-${pluginVersion(base)}"
  }

  /** plugins-common-private published version (RUDDER_VERSION-PRIVATE_VERSION, SNAPSHOT on next/nightly). */
  def privateLibVersion: String = branchType match {
    case "next" | "nightly" => s"$rudderVersion-$privateVersion-SNAPSHOT"
    case _                  => s"$rudderVersion-$privateVersion"
  }

  // ---- build-wide settings (was the ThisBuild block of build.sbt) ---------------------------
  def commonBuildSettings: Seq[Setting[?]] = Seq(
    ThisBuild / scalaVersion     := Dependencies.V.scala,
    ThisBuild / organization     := "com.normation.plugins",
    // keep unsuffixed artifactIds (api-authorizations, not api-authorizations_3), Maven parity.
    ThisBuild / crossPaths       := false,
    ThisBuild / autoScalaLibrary := false, // scala lib comes (provided) via rudder-web
    ThisBuild / resolvers ++= Seq(
      Resolver.mavenLocal, // rudder-web/core/rest published by the webapp build (honors -Dmaven.repo.local)
      "rudder-release"  at "https://repository.rudder.io/maven/releases/",
      "rudder-snapshot" at "https://repository.rudder.io/maven/snapshots/"
    ),
    // scalacOptions inherited from rudder-parent (same strict set as the webapp build)
    ThisBuild / scalacOptions ++= Seq(
      "-release:17", "-deprecation", "-explain-types", "-feature", "-unchecked",
      "-language:existentials", "-language:higherKinds", "-language:implicitConversions",
      "-Xmax-inlines", "100",
      "-Wconf:msg=An existential type that came from a Scala-2 classfile for trait BoxTrait:s",
      "-Werror", "-Wunused:imports", "-Wunused:locals", "-Wunused:implicits", "-Wunused:privates",
      "-Ycheck-all-patmat", "-Ysemanticdb"
    ),
    ThisBuild / javacOptions ++= Seq("--release", "17", "-encoding", "UTF-8")
  )

  // ---- per-module common settings (the Maven `plugins-parent`) ------------------------------
  def commonSettings: Seq[Setting[?]] = Seq(
    // version = RUDDER_VERSION (main-build.conf) - plugin-version (build.conf), like PLUGIN_POM_VERSION.
    version := pluginPomVersion(baseDirectory.value),
    // run tests against class dirs (sbt 2.0 exportJars=true otherwise breaks logback/resources)
    exportJars := false,
    Test / fork := true,
    Test / parallelExecution := false,
    Test / javaOptions += "-Dspecs2.commandline=console",
    Compile / packageDoc / publishArtifact := false,
    // local publish -> ~/.m2 (honors -Dmaven.repo.local), like the webapp build
    publishMavenStyle := true,
    publishLocal := publishM2.value
  )

  // ---- the fat-jar (Maven maven-shade-plugin): own classes + non-provided deps --------------
  def assemblySettings(jarBase: String): Seq[Setting[?]] = Seq(
    assembly / assemblyJarName    := s"$jarBase-${version.value}-jar-with-dependencies.jar",
    // land it in <plugin>/target/ so the Makefile's `mv target/<name>-*-jar-with-dependencies.jar` works
    assembly / assemblyOutputPath := Def.uncached { baseDirectory.value / "target" / (assembly / assemblyJarName).value },
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case "module-info.class"      => MergeStrategy.discard
      case _                        => MergeStrategy.first
    }
  )

  // ---- license source-template toggle ------------------------------------------------------
  private val placeholders = Seq("plugin-resource-publickey", "plugin-resource-license", "plugin-declared-version")

  /** Generate the filtered `limited` sources into sourceManaged, substituting the 4 placeholders. */
  private def filterLimitedSources = Def.task {
    val base    = baseDirectory.value
    val srcDir  = base / "src" / "main" / "scala-templates" / "limited"
    val outDir  = (Compile / sourceManaged).value / "scala-templates-limited"
    val subst: Map[String, String] =
      placeholders.map(k => k -> sys.props.getOrElse(k, "")).toMap +
        ("plugin-fullname" -> pluginFullName(base))
    IO.delete(outDir)
    if (!srcDir.exists) Seq.empty[File]
    else
      (srcDir ** "*.scala").get().map { src =>
        val rel     = srcDir.toPath.relativize(src.toPath).toString
        val out     = outDir / rel
        val content = subst.foldLeft(IO.read(src)) { case (c, (k, v)) => c.replace("${" + k + "}", v) }
        IO.write(out, content)
        out
      }
  }

  /** Settings selecting default vs limited sources, like the two Maven profiles. */
  def licenseSettings: Seq[Setting[?]] =
    if (licensed)
      Seq(
        Compile / sourceGenerators += filterLimitedSources.taskValue,
        libraryDependencies += "com.normation.plugins" % "plugins-common-private" % privateLibVersion
      )
    else
      Seq(
        Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "main" / "scala-templates" / "default"
      )

  // ---- build.conf copied into the jar (Maven copy-build-conf), for runtime plugin id -------
  def buildConfResource(destDirectory: String) = Def.task {
    val base    = baseDirectory.value
    val outDir  = (Compile / resourceManaged).value / "com" / "normation" / "plugins" / destDirectory
    val pn      = pluginName(base)
    def filter(s: String) = s.replace("${plugin-name}", pn)
    val gen = Seq(base / "build.conf" -> "build.conf", base / ".." / "main-build.conf" -> "main-build.conf")
    gen.collect { case (src, name) if src.exists =>
      val out = outDir / name
      IO.write(out, filter(IO.read(src)))
      out
    }
  }

  // ---- Elm frontend (build.sh + elm-review), like the webapp ------------------------------
  val elmBuild  = taskKey[Seq[File]]("Build the plugin Elm frontend and stage css/js under toserve/")
  val elmReview = taskKey[Unit]("Run elm-review for this plugin")

  def elmSettings(destDirectory: String): Seq[Setting[?]] = Seq(
    elmBuild := Def.uncached {
      val mainDir = baseDirectory.value / "src" / "main"
      streams.value.log.info(s"Building Elm frontend (build.sh --release) for ${name.value}")
      val rc = Process(Seq("./build.sh", "--release"), mainDir).!
      if (rc != 0) sys.error(s"Elm build.sh failed with exit code $rc")
      val gen     = mainDir / "elm" / "generated"
      val outBase = (Compile / resourceManaged).value / "toserve" / destDirectory
      (gen * ("*.css" | "*.js")).get().map { src =>
        val target = outBase / src.getName
        IO.copyFile(src, target)
        target
      }
    },
    Compile / resourceGenerators += elmBuild.taskValue,
    elmReview := {
      val rc = Process(Seq("npx", "elm-review"), baseDirectory.value / "src" / "main" / "elm").!
      if (rc != 0) sys.error(s"elm-review failed with exit code $rc")
    },
    Test / test := (Test / test).dependsOn(elmReview).evaluated
  )

  /**
   * One Scala plugin module. `dir` is also the Maven artifactId and the sbt project id (so
   * `sbt "<dir>/assembly"` works from the Makefile). `destDirectory` is the plugin POM's
   * `destDirectory` (base package / toserve dir). `hasElm` toggles the frontend build.
   */
  def pluginProject(dir: String, destDirectory: String, hasElm: Boolean, extra: Seq[ModuleID] = Nil): Project = {
    val p = Project(dir, file(dir))
      .enablePlugins(AssemblyPlugin)
      .settings(commonSettings)
      .settings(assemblySettings(dir))
      .settings(
        name := dir,
        libraryDependencies ++= Dependencies.pluginBase ++ extra,
        // copy build.conf into the jar for runtime plugin identification (Maven copy-build-conf)
        Compile / resourceGenerators += buildConfResource(destDirectory).taskValue
      )
      .settings(licenseSettings*)
    if (hasElm) p.settings(elmSettings(destDirectory)*) else p
  }
}
