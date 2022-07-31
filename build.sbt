name := "reminder"

version := "1.0"

scalaVersion := "2.13.4"

lazy val akkaVersion     = "2.6.19"
lazy val akkaHttpVersion = "10.2.9"
lazy val akkaGrpcVersion = "2.1.4"

enablePlugins(AkkaGrpcPlugin)

fork := true

ThisBuild / libraryDependencies ++= Dependencies.serviceDependencies
