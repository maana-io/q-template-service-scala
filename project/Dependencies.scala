import sbt._

//TODO: move all dependencies into here
object Versions {
/*
  "com.github.jarlakxen" %% "drunk"           % "2.4.0",
  "org.sangria-graphql"  %% "sangria"         % "1.4.2",
  "org.sangria-graphql"  %% "sangria-slowlog" % "0.1.8",
  "org.sangria-graphql"  %% "sangria-circe"   % "1.2.1",
  "com.typesafe.akka"    %% "akka-http"       % "10.1.3",
  "de.heikoseeberger"    %% "akka-http-circe" % "1.21.0",
  "io.circe"             %% "circe-core"      % "0.9.3",
  "io.circe"             %% "circe-parser"    % "0.9.3",
  "io.circe"             %% "circe-optics"    % "0.9.3",
  "io.circe"             %% "circe-generic"   % "0.9.3",
  "joda-time"            % "joda-time"        % "2.9.9",
  "org.scalatest"        %% "scalatest"       % "3.0.5" % Test
* */
  lazy val drunk = "2.5.0"
  lazy val
}

object Dependencies {
  lazy val drunk = "com.github.jarlakxen" %% "drunk" % Versions.drunk

}
