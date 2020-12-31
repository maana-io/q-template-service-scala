version := sys.env.getOrElse("BUILD_VERSION", "v1.0.0")

name := sys.env.getOrElse("NAME", "maana_scala_template_service")

packageName in Docker := sys.env.getOrElse("PACKAGE_NAME", "maana_scala_template_service")

description := "Maana Q Service Scala Tempalte"

scalaVersion := "2.12.6"
scalacOptions ++= Seq("-deprecation", "-feature")

resolvers += Resolver.bintrayRepo("jarlakxen", "maven")

libraryDependencies ++= Seq(
  Dependencies.drunk,
  Dependencies.sangria,
  Dependencies.sangriaSlowlog,
  Dependencies.sangriaCirce,
  Dependencies.akkaHttp,
  Dependencies.akkaHttpCirce,
  Dependencies.circeCore,
  Dependencies.circeParser,
  Dependencies.circeOptics,
  Dependencies.circeGeneric,
  Dependencies.jodaTime,
  Dependencies.scalatest
)

Revolver.settings
enablePlugins(JavaAppPackaging)

scalafmtOnCompile in ThisBuild := true
