package io.maana.common

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

object Configuration {
  private val typesafeConfig = ConfigFactory.load()

  val MAANA_CLIENT_ENDPOINT = typesafeConfig.getString("app.maanaClientEndpoint")

  // Auth
  val AUTH_IDENTIFIER    = typesafeConfig.getString("app.authIdentifier")
  val AUTH_DOMAIN        = typesafeConfig.getString("app.authDomain")
  val AUTH_CLIENT_ID     = typesafeConfig.getString("app.authClientId")
  val AUTH_CLIENT_SECRET = typesafeConfig.getString("app.authClientSecret")
}
