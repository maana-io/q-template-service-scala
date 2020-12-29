import sbt._

object Versions {
  lazy val drunk          = "2.5.0"
  lazy val sangria        = "1.4.2"
  lazy val sangriaSlowLog = "0.1.8"
  lazy val sangriaCirce   = "1.2.1"
  lazy val akkaHttp       = "10.1.3"
  lazy val akkaHttpCirce  = "1.21.0"
  lazy val circe          = "0.9.3"
  lazy val jodaTime       = "2.9.9"
  lazy val scalatest      = "3.0.5"
}

object Dependencies {
  lazy val drunk          = "com.github.jarlakxen" %% "drunk"           % Versions.drunk
  lazy val sangria        = "org.sangria-graphql"  %% "sangria"         % Versions.sangria
  lazy val sangriaSlowlog = "org.sangria-graphql"  %% "sangria-slowlog" % Versions.sangriaSlowLog
  lazy val sangriaCirce   = "org.sangria-graphql"  %% "sangria-circe"   % Versions.sangriaCirce
  lazy val akkaHttp       = "com.typesafe.akka"    %% "akka-http"       % Versions.akkaHttp
  lazy val akkaHttpCirce  = "de.heikoseeberger"    %% "akka-http-circe" % Versions.akkaHttpCirce
  lazy val circeCore      = "io.circe"             %% "circe-core"      % Versions.circe
  lazy val circeParser    = "io.circe"             %% "circe-parser"    % Versions.circe
  lazy val circeOptics    = "io.circe"             %% "circe-optics"    % Versions.circe
  lazy val circeGeneric   = "io.circe"             %% "circe-generic"   % Versions.circe
  lazy val jodaTime       = "joda-time"            % "joda-time"        % Versions.jodaTime
  lazy val scalatest      = "org.scalatest"        %% "scalatest"       % Versions.scalatest % Test

}
