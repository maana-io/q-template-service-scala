package io.maana

import io.circe.CursorOp.MoveRight
import io.circe.{Decoder, DecodingFailure, HCursor}
import org.joda.time._
import org.joda.time.format.ISODateTimeFormat
import sangria.marshalling.DateSupport
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation

import scala.util.{Failure, Success, Try}

object Scalars {
  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")

  def parseDate(s: String) = Try(new DateTime(s, DateTimeZone.UTC)) match {
    case Success(date) ⇒ Right(date)
    case Failure(_)    ⇒ Left(DateCoercionViolation)
  }

  // Need explicit Json decoder for input objects -- Thanks Sangria
  implicit val decodeDateTime: Decoder[DateTime] = (c: HCursor) => {
    val out = for {
      str <- c.value.as[String]
      date <- parseDate(str).left
               .map(_ => DecodingFailure(s"invalid Date - $str", List(MoveRight))): Decoder.Result[DateTime]
    } yield date
    out
  }

  implicit val DateTimeType = ScalarType[DateTime](
    "DateTime",
    coerceOutput = (d, caps) ⇒
      if (caps.contains(DateSupport)) d.toDate
      else ISODateTimeFormat.dateTime().print(d),
    coerceUserInput = {
      case s: String ⇒ parseDate(s)
      case _         ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case sangria.ast.StringValue(s, _, _, _, _) ⇒ parseDate(s)
      case _                                      ⇒ Left(DateCoercionViolation)
    }
  )

  def dateStr(in: Long): String =
    new DateTime(in * 1000).toDateTime(org.joda.time.DateTimeZone.UTC).toString()
}
