version := sys.env.getOrElse("BUILD_VERSION", "v1.0.3")

name := sys.env.getOrElse("NAME", "maana_fast_scheduler")

packageName in Docker := sys.env.getOrElse("PACKAGE_NAME", "fanarcr.azurecr.io/maana_fast_scheduler")

description := "Maana's Shipping Fast Scheduler Service"

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
