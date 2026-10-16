// Fat-jar per plugin (replaces maven-shade-plugin / maven-assembly jar-with-dependencies).
// Provided-scope deps (all of rudder-web's closure) are excluded from the assembly, like Maven.
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")

// OSV (Open Source Vulnerabilities) dependency scan — replaces the Maven CI vulnerability check.
// 0.2.0 is published for sbt 2.x (sbt-osv_sbt2_3).
addSbtPlugin("net.nmoncho" % "sbt-osv" % "0.2.0")
