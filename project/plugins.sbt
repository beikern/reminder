// Linting & Styling
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.22")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix" % "0.9.34")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage"       % "1.5.1")
addSbtPlugin("com.codacy"    % "sbt-codacy-coverage" % "1.3.17")

// GRPC
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.4")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.5")

// Publishing
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
