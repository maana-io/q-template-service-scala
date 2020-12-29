package io.maana

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.OutgoingConnection
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import com.github.jarlakxen.drunk.{ClientOptions, GraphQLClient}
import sangria.execution.UserFacingError

import scala.collection.immutable
import scala.concurrent.Future

package object common {
  case class AppError(msg: String) extends Error(msg) with UserFacingError

  def clientForUri(
      uri: Uri,
      headers: immutable.Seq[HttpHeader]
  )(implicit system: ActorSystem, mat: ActorMaterializer): GraphQLClient = {
    val http: HttpExt = Http()
    val flow: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] =
      uri.scheme.toLowerCase match {
        case "http" => http.outgoingConnection(uri.authority.host.address(), uri.effectivePort)
        case "https" =>
          http.outgoingConnectionHttps(uri.authority.host.address(), uri.effectivePort)
        case value => throw AppError(s"Unknown URI scheme: $value")
      }

    GraphQLClient(uri, flow, clientOptions = ClientOptions.Default, headers = headers)
  }
}
