val Http4sVersion = "0.23.18"

val CirceVersion = "0.14.3"
val CirveFs2Version = "0.14.3"
val EnumeratumVersion = "1.7.2"
val LogbackVersion = "1.2.11"
val PureConfigVersion = "0.17.4"
val ScalaCheckVersion = "1.14.1"
val ScalaTestVersion = "3.2.16"
val MonocleVersion = "3.1.0"

ThisBuild / organization := "com.elisa"
ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

Global / scalacOptions ++= Seq(
  "-Ymacro-annotations",
  "-Ywarn-unused"
)

ThisBuild / scalafixDependencies ++= Seq(
  "com.github.liancheng" %% "organize-imports" % "0.6.0"
)

lazy val commonSettings = Seq(
  addCompilerPlugin(
    "org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full
  ),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

val serviceDeps =
  Seq(
    "org.http4s" %% "http4s-ember-server" % Http4sVersion,
    "org.http4s" %% "http4s-ember-client" % Http4sVersion,
    "org.http4s" %% "http4s-circe" % Http4sVersion,
    "org.http4s" %% "http4s-dsl" % Http4sVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-parser" % CirceVersion,
    "io.circe" %% "circe-literal" % CirceVersion,
    "ch.qos.logback" % "logback-classic" % LogbackVersion % Runtime,
    "com.github.pureconfig" %% "pureconfig" % PureConfigVersion,
    "dev.optics" %% "monocle-core" % MonocleVersion,
    "dev.optics" %% "monocle-macro" % MonocleVersion,
    "com.beachape" %% "enumeratum" % EnumeratumVersion,
    "org.scalactic" %% "scalactic" % ScalaTestVersion,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % "test"
  )

lazy val root = (project in file(".")).settings(
  commonSettings,
  libraryDependencies ++= serviceDeps
)
