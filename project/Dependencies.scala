import sbt._



//TODO: move all dependencies into here
object Versions {

    lazy val drunk              = "2.5.0"
}

object Dependencies {
    lazy val drunk                = "com.github.jarlakxen" %% "drunk"                    % Versions.drunk
}

