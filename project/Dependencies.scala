import sbt._

/**
 * Versions + ModuleIDs for the plugins build.
 *
 * The Maven `plugins-parent` POM lists ~150 transitive deps of rudder-web explicitly as
 * `provided` so the shade plugin keeps them out of the fat jar. In sbt we instead depend on
 * `rudder-web` (classes) as `Provided`: its whole transitive closure is then provided, and
 * sbt-assembly (which bundles the Runtime classpath) excludes it — same result, no big list.
 *
 * Only genuinely plugin-specific, non-rudder libraries are bundled (normal compile scope).
 */
object Dependencies {

  // version of rudder artifacts to build against — read from main-build.conf (single source of
  // truth), exactly what `make generate-pom` used to inject. See PluginBuild.rudderBuildVersion.
  val rudderVersion = PluginBuild.rudderBuildVersion

  object V {
    val scala          = "3.8.4"
    val springSecurity = "7.1.0"
    val servlet        = "6.1.0"
    val lift           = "4.0.0"
    val snakeyaml      = "2.6"
    val zioHttp        = "2.0.0-RC11"
    // plugin-specific bundled libs
    val jacksonDatabind = "2.20.1"
    val nimbusJoseJwt   = "10.5"
    val jakartaMail     = "2.0.2"
    val subethasmtp     = "7.2.0"
    val mustache        = "0.9.14"   // change-validation "compiler" (Mustache.java)
    val jsoup           = "1.21.2"
    // test stack (same as rudder-parent inherited <dependencies>)
    val specs2     = "4.23.0"
    val zio        = "2.1.26"
    val catsEffect = "3.7.0"
  }

  // ---- rudder provided (classes jar) + transitive closure (excluded from assembly) ----------
  val rudderWebProvided =
    ("com.normation.rudder" % "rudder-web" % rudderVersion).classifier("classes") % Provided

  // ---- test deps (rudder test-jars + common test stack) -------------------------------------
  val testDeps: Seq[ModuleID] = Seq(
    ("com.normation.rudder" % "rudder-core" % rudderVersion).classifier("tests") % Test,
    ("com.normation.rudder" % "rudder-rest" % rudderVersion).classifier("tests") % Test,
    ("com.normation.rudder" % "rudder-web"  % rudderVersion).classifier("tests") % Test,
    "org.typelevel"       %% "cats-effect-std" % V.catsEffect % Test,
    "net.liftweb"         %% "lift-testkit" % V.lift % Test, // _3 (the Maven parent's _2.13 is a bug)
    "org.specs2"          %% "specs2-core"          % V.specs2 % Test,
    "org.specs2"          %% "specs2-junit"         % V.specs2 % Test,
    "org.specs2"          %% "specs2-matcher-extra" % V.specs2 % Test,
    "dev.zio"             %% "zio-test"             % V.zio    % Test,
    "dev.zio"             %% "zio-test-junit"       % V.zio    % Test
  )

  // base set shared by every plugin
  val pluginBase: Seq[ModuleID] = rudderWebProvided +: testDeps

  // ---- plugin-specific bundled (non-provided) deps -----------------------------------------
  val servletProvided = "jakarta.servlet" % "jakarta.servlet-api" % V.servlet % Provided

  val authBackends: Seq[ModuleID] = Seq(
    "org.springframework.security" % "spring-security-oauth2-core"            % V.springSecurity,
    "org.springframework.security" % "spring-security-oauth2-client"          % V.springSecurity,
    "org.springframework.security" % "spring-security-oauth2-resource-server" % V.springSecurity,
    "org.springframework.security" % "spring-security-oauth2-jose"            % V.springSecurity,
    "com.fasterxml.jackson.core"   % "jackson-databind"                       % V.jacksonDatabind,
    "com.nimbusds"                 % "nimbus-jose-jwt"                        % V.nimbusJoseJwt
  )

  val changeValidation: Seq[ModuleID] = Seq(
    "com.github.spullara.mustache.java" % "compiler"     % V.mustache,
    "com.sun.mail"                      % "jakarta.mail" % V.jakartaMail,
    "com.github.davidmoten"             % "subethasmtp"  % V.subethasmtp
  )

  val datasources: Seq[ModuleID] = Seq(
    "org.yaml" % "snakeyaml" % V.snakeyaml,
    "io.d11"  %% "zhttp"     % V.zioHttp
  )

  val openscap: Seq[ModuleID] = Seq(
    "org.jsoup" % "jsoup" % V.jsoup
  )
}
