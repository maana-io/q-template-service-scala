package io.maana.common

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, Authorization, OAuth2BearerToken}
import akka.stream.ActorMaterializer
import com.github.jarlakxen.drunk.GraphQLClient
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.parse
import sangria.execution.UserFacingError

import scala.concurrent.Await
import scala.concurrent.duration._

case class AuthResponse(access_token: String, token_type: String, scope: String, expires_in: Long)

class MaanaClient(implicit val system: ActorSystem, mat: ActorMaterializer) {
  import system.dispatcher
  case class AppError(msg: String) extends Error(msg) with UserFacingError

  private val logger = Logger(this.getClass)

  def maanaClient: GraphQLClient = {
    if (cachedMaanaClient.isEmpty && DateTime.now >= tokenExpirationTime) {
      val (client, auth) = rebuildMaanaClient
      //println(s"$auth")
      synchronized {
        cachedMaanaClient = Some(client)
        // Keep the credential for 50% of it's lifetime - about 12hours
        auth match {
          case Some(a) => tokenExpirationTime = DateTime.now + (a.expires_in * 500L)
          case None    => tokenExpirationTime = DateTime.MaxValue // No Auth never expires
        }
      }
      logger.info(s"Auth token refreshed - will expire $tokenExpirationTime UTC")
    }

    cachedMaanaClient.getOrElse(throw AppError("Can not obtain Maana GraphQL Client"))
  }

  def getAuthenticatedHeaders = {
    // This will ensure that we obtained fresh auth headers
    this.maanaClient
    buildHeadersForAuth(cachedAuth)
  }

  private def rebuildMaanaClient: (GraphQLClient, Option[AuthResponse]) = {
    val auth = reauthenticateAgainstMaana
    this.cachedAuth = auth

    val headers = buildHeadersForAuth(auth)

    // Auth code is not removed *for now* as we may need to have authentication against Maana later
    // but for now, with empty defaults and updated core.yml it will be unauthenticated access to maana-portal
    println(Configuration.MAANA_CLIENT_ENDPOINT)
    val client = clientForUri(Uri(Configuration.MAANA_CLIENT_ENDPOINT), headers)
    (client, auth)
  }
  private def reauthenticateAgainstMaana: Option[AuthResponse] =
    if (Configuration.AUTH_IDENTIFIER.isEmpty) {
      logger.warn("No auth info specified - requests will be made without authentication")
      None
    } else {
      val req: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"https://${Configuration.AUTH_DOMAIN}/oauth/token"),
        headers = List(
          Accept(MediaRanges.`*/*`)
        ),
        entity = FormData(
          Map(
            "grant_type"    -> "client_credentials",
            "audience"      -> Configuration.AUTH_IDENTIFIER,
            "client_id"     -> Configuration.AUTH_CLIENT_ID,
            "client_secret" -> Configuration.AUTH_CLIENT_SECRET
          )
        ).toEntity
      )

      val authResponseFuture = for {
        authResponse  <- Http().singleRequest(req)
        decodedEntity <- authResponse.entity.toStrict(60.seconds)
      } yield decodedEntity.data.utf8String

      val authResponse = Await.result(authResponseFuture, 60.seconds)

      implicit val authResponseDecoder: Decoder[AuthResponse] = deriveDecoder

      val authResponseEither = for {
        responseJson        <- parse(authResponse)
        decodedAuthResponse <- responseJson.as[AuthResponse]
      } yield decodedAuthResponse

      authResponseEither match {
        case Left(error)  => throw error
        case Right(value) => Some(value)
      }
    }

  private def buildHeadersForAuth(auth: Option[AuthResponse]) =
    auth match {
      case Some(a) =>
        List(
          Accept(MediaRanges.`*/*`),
          Authorization(OAuth2BearerToken(a.access_token))
        )
      case None =>
        List(
          Accept(MediaRanges.`*/*`),
        )
    }

  private var tokenExpirationTime: DateTime            = DateTime.now
  private var cachedAuth: Option[AuthResponse]         = None
  private var cachedMaanaClient: Option[GraphQLClient] = None
}

object MaanaClient {
  private var cachedClient: Option[MaanaClient] = None

  def apply()(implicit system: ActorSystem, mat: ActorMaterializer): MaanaClient =
    cachedClient match {
      case None =>
        val client = new MaanaClient()
        cachedClient = Some(client)
        client
      case Some(client) =>
        client
    }

  def get = cachedClient.get.maanaClient
}
