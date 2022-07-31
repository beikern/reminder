import sbt._

object Dependencies {

  val serviceDependencies: Seq[ModuleID] =
    Libraries.akka ++
      Libraries.akkaStream ++
      Libraries.akkaHttp ++
      Libraries.akkaPersistence ++
      Libraries.akkaProjections ++
      Libraries.akkaCluster ++
      Libraries.akkaDiscovery ++
      Libraries.akkaManagement ++
      Libraries.akkaSerialization ++
      Libraries.quill ++
      Libraries.logback ++
      Libraries.quartz ++
      Libraries.persistence ++
      Libraries.testing

  object V {
    // Logging
    val logback = "1.2.11"
    val log4s   = "1.10.0"

    // Akka
    val akkaVersion     = "2.6.18"
    val akkaHttpVersion = "10.2.9"
    val akkaHttpJsonVersion = "1.39.2"
    val akkaManagementVersion = "1.1.3"
    val QuillVersion = "4.1.0"
    val akkaGrpcVersion = "2.1.0"
    val akkaPersistenceJdbcVersion = "5.0.4"
    val akkaProjectionJdbcVersion = "1.2.4"
    // ZIO
    val zio        = "2.0.0-RC2"
    val zioConfig  = "3.0.0-RC3"
    val zioLogging = "2.0.0-RC5"

    // Quartz
    val quartz = "2.3.2"

    val akkaQuartz = "1.9.3-akka-2.6.x"

    // Persistence
    val testcontainersScala = "0.40.2"

    val mySQL = "8.0.28"

    val flyway = "8.5.2"

    val refined = "0.9.28"
    val newtype = "0.4.4"

    // Test
    val scalaTest = "3.2.11"

    // Scalafix
    val organizeImports = "0.6.0"

    // Compiler
    val betterMonadicFor = "0.3.1"
    val kindProjector    = "0.13.2"
    val semanticDB       = "4.5.0"
  }

  object Libraries {
    val akka = Seq(
      "com.typesafe.akka" %% "akka-actor-typed"         % V.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % V.akkaVersion % Test
    )

    val akkaStream = Seq(
      "com.typesafe.akka" %% "akka-stream"         % V.akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % V.akkaVersion % Test
    )

    val akkaHttp = Seq(
      "com.typesafe.akka" %% "akka-http"          % V.akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % V.akkaHttpVersion,
      "com.typesafe.akka" %% "akka-discovery"     % V.akkaVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % V.akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-jackson" % V.akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-jackson" % V.akkaHttpJsonVersion
    )

    val akkaPersistence = Seq(
      "com.typesafe.akka" %% "akka-persistence-typed" % V.akkaVersion,
      "com.lightbend.akka" %% "akka-persistence-jdbc" % V.akkaPersistenceJdbcVersion
    )

    val akkaProjections = Seq(
      "com.lightbend.akka" %% "akka-projection-jdbc" % V.akkaProjectionJdbcVersion,
      "com.lightbend.akka" %% "akka-projection-eventsourced" % V.akkaProjectionJdbcVersion
    )

    val akkaCluster = Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % V.akkaVersion
    )

    val akkaDiscovery = Seq(
      "com.typesafe.akka" %% "akka-discovery" % V.akkaVersion
    )

    val akkaManagement = Seq(
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % V.akkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % V.akkaManagementVersion
    )

    val akkaSerialization = Seq(
      "com.typesafe.akka" %% "akka-serialization-jackson" % V.akkaVersion
    )

    val quill = Seq(
      "io.getquill" %% "quill-jdbc" % V.QuillVersion
    )

    val testing: Seq[ModuleID] = Seq(
      "org.scalatest" %% "scalatest"                      % V.scalaTest,
      "com.dimafeng"  %% "testcontainers-scala-scalatest" % V.testcontainersScala,
      "com.dimafeng"  %% "testcontainers-scala-mysql"     % V.testcontainersScala
    ).map(_ % Test)

    val logback = Seq(
      "ch.qos.logback" % "logback-classic" % V.logback,
      "org.log4s"     %% "log4s"           % V.log4s
    )

    val quartz = Seq(
      "org.quartz-scheduler" % "quartz" % V.quartz
    )

    val persistence = Seq(
      "mysql"        % "mysql-connector-java" % V.mySQL,
      "org.flywaydb" % "flyway-mysql"         % V.flyway
    )
  }

  object Scalafix {
    val organizeImports = "com.github.liancheng" %% "organize-imports" % V.organizeImports
  }

  object CompilerPlugin {
    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % V.betterMonadicFor
    )
    val kindProjector = compilerPlugin(
      "org.typelevel" % "kind-projector" % V.kindProjector cross CrossVersion.full
    )
    val semanticDB = compilerPlugin(
      "org.scalameta" % "semanticdb-scalac" % V.semanticDB cross CrossVersion.full
    )
  }
}
