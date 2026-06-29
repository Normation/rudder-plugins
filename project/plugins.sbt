// Fat-jar per plugin (replaces maven-shade-plugin / maven-assembly jar-with-dependencies).
// Provided-scope deps (all of rudder-web's closure) are excluded from the assembly, like Maven.
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
