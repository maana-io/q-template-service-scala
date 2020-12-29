package io.maana

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, StatusCodes, _}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import sangria.validation.ValueCoercionViolation

import scala.concurrent.{ExecutionContext, Future}

//Graphql client imports
import com.github.jarlakxen.drunk._
import io.circe._
import sangria.macros._

// TODO report query errors out to client
// TODO move to Fanar Logic service endpoint
// TODO Fix overfetching in underlying services
// TODO Cache Ports
// TODO Cache Vessels

object Queries {
  val conf = ConfigFactory.load()
  // EC for queries
  implicit val executionContext = ExecutionContext.fromExecutor(new java.util.concurrent.ForkJoinPool(16))

  // TODO should all come out off the logic Endpoint
  val portsEndpoint        = conf.getString("app.portsEndpoint")
  val logicEndpoint        = conf.getString("app.fanarLogicEndpoint")
  val requirementsEndpoint = conf.getString("app.requirementsEndpoint")

  val clientEndpoint = "https://aatcfanar-test02.knowledge.maana.io:8443/service/ports/graphql"

  //println(s"portsEndpoint at $portsEndpoint")
  //println(s"logicEndpoint at $logicEndpoint")
  //println(s"requirementsEndpoint at $requirementsEndpoint")

  //this should be able to connect to the shipping model
  //val portsEndpoint = "https://aatcfanar-test03.knowledge.maana.io:8443/service/e1a8f4bf-9f7a-46a9-852c-66169c96cc76/graphql"
  //val authorization ="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Ik5ESkZSVFF5TlVRMFJETTFSRUpHTkRnek0wTTJNVEF5UWpVMk9EZ3dNamMzTVRBek5rRTFRZyJ9.eyJpc3MiOiJodHRwczovL21hYW5hLWFhdGMuZXUuYXV0aDAuY29tLyIsInN1YiI6ImF1dGgwfDVkZWZlZTJiZjc3ZTExMGU5ZGNlODAzYiIsImF1ZCI6WyJodHRwczovL2FhdGMubWFhbmEuaW8vIiwiaHR0cHM6Ly9tYWFuYS1hYXRjLmV1LmF1dGgwLmNvbS91c2VyaW5mbyJdLCJpYXQiOjE1NzkxMjYwMzQsImV4cCI6MTU3OTIxMjQzNCwiYXpwIjoidkhSOWdWbE42OVRWUm13N1BjUmh6amRtNGRmSVp4TlgiLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIn0.A8E8TFolVx1WvuMJh0qXQ8ayhmDuxx_B7XVBedQrb4kEyXRkJB_LXE4bbI2av15g19tDJo2kXEdWgAxM_94uxwebIh7YOUNKi5kkTRDcyRkC8i8-zrGdl2h5oqTrp2XZq2aomD3kY58DFXXQDCJYmny5ZuthSdeH8QGDAehpf6k4I-nA5WSFIglgWnHQVixoNjz0YxXn6wS9AUTRK6YOFApLG7LI376cTIq08L4JQm6W52UoSYmWvXZhKse-gSLPYymfftzx9eodAgVARRJxKyv_-XvXpo9c8RBaXmHequwvDedhNaVTKCaMRNuEAfsQFNp8LfRmT6_KHDXtY-8lHA"
  //val authHeader = headers.Authorization(OAuth2BearerToken(client.access_token))
  val acceptHeader = headers.Accept(`text/html`, `application/json`, `application/octet-stream`)

  implicit val system       = Server.system
  implicit val materializer = Server.materializer

  val productMappings = {
    val map0 = scala.io.Source
      .fromResource("mapping.csv")
      .getLines
      .map { line =>
        val split = line.split(",", 4)
        val from  = split(0).toLowerCase
        val to    = split(3).toLowerCase
        from -> to
      }
      .toVector
    // Unity unchanged mapping in case someone enters BP product types
    val out = (map0 ++ map0.map { a =>
      a._2 -> a._2
    }.toSet).toMap
    out
  }

  val cleaningTimeMultiplierTable: Map[String, Double] = Map(
    "mr"      -> 1.0,
    "lr1"     -> 1.5,
    "lr2"     -> 1.5,
    "panamax" -> 1.5,
    "aframax" -> 1.5
  )

  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")

  case class IdRef(id: String)
  case class Unit(id: String)
  // TODO figure out how to get Circe to do the right thing here for DateTime
  case class DateRange(startDate: String, endDate: String)
  case class DoubleValue(value: Double, unit: Unit = Unit(""))
  case class DVNoUnit(value: Double)
  case class ActionDetail(port: IdRef)
  case class VoyageDetail(end: IdRef)
  case class NextVesselState(date: String, fuelRemaining: DVNoUnit, lastKnownPort: IdRef)

  case class DischargeDetail(product: IdRef)
  case class VesselAction(dateRange: DateRange, nextVesselStatus: NextVesselState) {
    def destPort: String = nextVesselStatus.lastKnownPort.id
  }

  case class Schedule(vesselActions: Vector[VesselAction])
  case class ReqSchedule(requirement: IdRef, schedule: Schedule)
  case class BReq(rate: DVNoUnit)
  case class BunkerRequirements(
      laden_speed_11: List[BReq],
      laden_speed_12: List[BReq],
      laden_speed_12_5: List[BReq],
      laden_speed_13: List[BReq],
      laden_speed_13_5: List[BReq],
      laden_speed_14: List[BReq],
      laden_speed_14_5: List[BReq],
      laden_speed_15: List[BReq],
      ballast_speed_11: List[BReq],
      ballast_speed_12: List[BReq],
      ballast_speed_12_5: List[BReq],
      ballast_speed_13: List[BReq],
      ballast_speed_13_5: List[BReq],
      ballast_speed_14: List[BReq],
      ballast_speed_14_5: List[BReq],
      ballast_speed_15: List[BReq],
      no_eca_cold_cleaning: List[BReq],
      no_eca_hot_cleaning: List[BReq]
  )
  case class Vessel(id: String, ongoingActions: Vector[VesselAction], currentRequirementSchedule: Option[ReqSchedule])

  case class VesselDimensions(
      id: String,
      name: String,
      totalProductCapacity: DoubleValue,
      beam: DoubleValue,
      overallLength: DoubleValue,
      aftParallelBodyDistance: DoubleValue,
      forwardParallelBodyDistance: DoubleValue,
      ballastEconomicSpeed: DoubleValue,
      ballastMaxSpeed: DoubleValue,
      ladenEconomicSpeed: DoubleValue,
      ladenMaxSpeed: DoubleValue,
      bunkerRequirements: BunkerRequirements,
      fuelCapacity: DVNoUnit,
      sizeCategory: IdRef,
      cleanStatus: String,
      imoClass: String,
      scnt: DoubleValue,
      productDischargeRate: DoubleValue
  )

  // From the ports service the units aren't populated - everything is in Meters
  case class Location(latitude: Double, longitude: Double)

  case class Berth(
      id: String,
      maxBeam: Double,
      maxOverallLength: Double,
      minOverallLength: Double,
      minPmbAft: Double,
      minPmbForward: Double
  )

  case class Terminal(
      id: String,
      berths: Vector[Berth]
  )
  case class Port(
      id: String,
      location: Location,
      canRefuel: Boolean,
      loadingPumpRate: Double,
      averageFee: Double,
      terminals: Seq[Terminal]
  )

  case class PortDistance(value: Double, suezRoute: Boolean)

  //case class SimplePort(id: String)
  case class AllPortsFromCacheResult(allPortsFromCache: Seq[Port])

  // this is the Graphql Client response to be flattened
  type GraphQLResponse[Res] = Either[GraphQLResponseError, GraphQLResponseData[Res]]

  case class Contract(vessel: IdRef, dailyCharteringCosts: DVNoUnit, charterExpiration: String)

  //val bunkerRequirements = "{ no_eca_cold_cleaning { rate { value } } no_eca_hot_cleaning { rate { value } } laden_speed_11 { rate { value } } laden_speed_12 { rate { value } } laden_speed_12_5 { rate { value } } laden_speed_13 { rate { value } } laden_speed_13_5 { rate { value } } laden_speed_14 { rate { value } } laden_speed_14_5 { rate { value } } laden_speed_15 { rate { value } } ballast_speed_11 { rate { value } } ballast_speed_12 { rate { value } } ballast_speed_12_5 { rate { value } } ballast_speed_13 { rate { value } } ballast_speed_13_5 { rate { value } } ballast_speed_14 { rate { value } } ballast_speed_14_5 { rate { value } } ballast_speed_15 { rate { value } } }"
  val shortActionFields =
    "{ dateRange { startDate endDate } dischargePort { id } terminal { id } product { id cleanStatus} productQuantity { value unit { id } } }"

  val longActionFields =
    "{ dateRange { startDate endDate } loadingPort { id } terminal { id } product { id cleanStatus} productQuantity { value unit { id } } }"

  val actionFields =
    "{dateRange { startDate endDate } nextVesselStatus { date lastKnownPort { id } fuelRemaining { value } } }"

  val contracts = s"query { getAllVesselContracts { vessel { id } dailyCharteringCosts { value } charterExpiration } }"

  //will need to modify this
  //val portsQOld = s"""query {allPorts { id hasAtleastOneBunkeringBerth location { latitude longitude } averageFee { value }  loadingPumpRate { value unit { id } } berths { id maxBeam { value } maxOverallLength { value } minOverallLength { value } minPmbAft { value } minPmbForward { value }}}}""".stripMargin

  //new ports query
  val portsQuery =
    graphql"""
      query {allPortsFromCache { 
          id 
          canRefuel 
          location { latitude longitude } 
          averageFee 
          loadingPumpRate 
          terminals{
            id
            berths{
              id
              maxBeam
              maxOverallLength
              minOverallLength
              minPmbAft
              minPmbForward      
            }
          }
        }
    }
    """

  def distanceQ(from: String, to: String) =
    s"""query working2 {getPortToPortDistance(port1: \\"$from\\", port2: \\"$to\\"){ value suezRoute} }"""

  case class DistanceInput(originPort: String, destinationPort: String, antiPiracy: Boolean, eca: Boolean)
  case class DistanceResult(getPortToPortDistance: PortDistance)

  case class Pagination(offset: Int, size: Int)

  val distanceQuery =
    graphql"""
    query getPortToPortDistance($$originPort: String!, $$destinationPort:String!, $$eca: Boolean!, $$antiPiracy: Boolean! ){
    getPortToPortDistance(originPort: $$originPort, destinationPort: $$destinationPort, eca: $$eca, antiPiracy: $$antiPiracy){
          value
          suezRoute
    }
    }
    """

  def requestGQL(endpoint: String, gql: String): Future[JsonObject] = {
    val reqStr = s"""{"query":"$gql"}"""
    val request =
      HttpRequest(POST, uri = endpoint, headers = List(acceptHeader), entity = HttpEntity(`application/json`, reqStr))

    // We have to use a connection Flow otherwise the client floods the Server
    val uri = Uri(endpoint)
    val connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = if (uri.scheme == "https") {
      Http().outgoingConnectionHttps(uri.authority.host.toString(), uri.authority.port)
    } else {
      Http().outgoingConnection(uri.authority.host.toString(), uri.authority.port)
    }

    val foo = Source
      .single(request)
      .via(connection)
      .runWith(Sink.head)

    val bar = foo
      .map {
        case HttpResponse(StatusCodes.OK, _, res, _) =>
          res.httpEntity.dataBytes.map { b =>
            b.utf8String
          }
        case HttpResponse(status, _, res, _) =>
          res.httpEntity.dataBytes.map { b =>
            throw new Exception(s"Couldn't get data - $status - ${b.utf8String}")
          }
      }
      .flatMap { out =>
        out.runReduce((a, b) => a ++ b)
      }
      .map { str =>
        val json = parse(str).right.get.asObject.get
        json
      }

    bar
  }

  def getData(obj: JsonObject, name: String): Json =
    obj("data").get.asObject.get(name).get

  val MAX_SUPPORTED_SPEED = 15

  def toM(value: Double, unit: String): Double = unit.toLowerCase() match {
    case "m" => value
    case unit =>
      println(s"Bad unit for length $unit")
      ???
  }

  def toM3(value: Double, unit: String): Double = unit.toLowerCase() match {
    case "bbl" => value * 0.1589873
    case "m3"  => value
    case "m^3" => value
    case foo =>
      println(s"Unit = $foo")
      ???
  }

  def toM3PerSec(value: Double, unit: String): Double = unit.toLowerCase() match {
    case "m^3/h"  => value / 3600.0
    case "m^3/hr" => value / 3600.0
    case foo =>
      println(s"Unit = $foo")
      ???
  }

  def toUnixTimeSeconds(value: String): Long = {
    val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val d   = sdf.parse(value)
    d.getTime() / 1000
  }

  val PORT_DIMENSION_TOLERANCE = 0.1

  def ports(client: GraphQLClient): Future[Seq[Schema.Port]] = {
    def withTol(v: Double, tol: Double): Double =
      if (v == 0) { // 0 is s sentinel value and should not be adjusted
        0
      } else {
        v + tol
      }

    println("querying Q for ports")
    //println(portsQuery)

    val q: GraphQLCursor[AllPortsFromCacheResult, Nothing]     = client.query[AllPortsFromCacheResult](portsQuery)
    val data: Future[GraphQLResponse[AllPortsFromCacheResult]] = q.result
    data.map {
      case Left(error) =>
        throw new Exception(s"Couldn't get data, error: $error")
      case Right(value) =>
        //println(value)
        value.data.allPortsFromCache.map(
          p =>
            Schema.Port(
              id = p.id,
              feeDollars = p.averageFee,
              loadingPumpRateM3PerS = p.loadingPumpRate / 3600.0, //toM3PerSec
              canRefuel = p.canRefuel,
              latitude = p.location.latitude,
              longitude = p.location.longitude,
              //berths needs mapping
              //terminals
              berths = p.terminals.flatMap(t => t.berths)
                .map { b =>
                  Schema.Berth(
                    id = b.id,
                    maxBeam = withTol(b.maxBeam, PORT_DIMENSION_TOLERANCE),
                    maxOverallLength = withTol(b.maxOverallLength, PORT_DIMENSION_TOLERANCE), // Unspecified always passes
                    minOverallLength = withTol(b.minOverallLength, -PORT_DIMENSION_TOLERANCE), // Unspecified always passes
                    minPmbAft = b.minPmbAft, // Unspecified always passes
                    minPmbForward = b.minPmbForward
                  )
                }
                .toVector,
              neighbors = Vector.empty
            )
        )
    }
    /*val req = requestGQL(logicEndpoint, portsQ).map { res =>
      getData(res, "allPorts").as[Vector[Port]].right.get.map { p =>

        Schema.Port(
          id = p.id,
          feeDollars = p.averageFee.value,
          loadingPumpRateM3PerS = toM3PerSec(p.loadingPumpRate.value,p.loadingPumpRate.unit.id),
          berths = p.berths.map {b =>
            Schema.Berth(
              id = b.id,
              maxBeam = withTol(b.maxBeam.map(_.value).getOrElse(0), PORT_DIMENSION_TOLERANCE),                                  // Unspecified always passes
              // maxOverallLength = withTol(b.maxOverallLength.map(_.value).getOrElse(1), PORT_DIMENSION_TOLERANCE),                // If not specified use a small number that will always fail
              // minOverallLength = withTol(b.minOverallLength.map(_.value).getOrElse(Double.MaxValue), -PORT_DIMENSION_TOLERANCE),  // If not specified use a big number that will always fail
              maxOverallLength = withTol(b.maxOverallLength.map(_.value).getOrElse(0), PORT_DIMENSION_TOLERANCE),                // Unspecified always passes
              minOverallLength = withTol(b.minOverallLength.map(_.value).getOrElse(0), -PORT_DIMENSION_TOLERANCE),  // Unspecified always passes
              minPmbAft = b.minPmbAft.map(_.value).getOrElse(0),                              // Unspecified always passes
              minPmbForward = b.minPmbForward.map(_.value).getOrElse(0)                       // Unspecified always passes
            )
          },
          canRefuel = p.hasAtleastOneBunkeringBerth,
          latitude = p.location.latitude,
          longitude = p.location.longitude,
          neighbors = Vector.empty
        )
      }
    }*/

  }

  def distance(client: GraphQLClient, from: String, to: String): Future[PortDistance] =
    if (from == to) {
      Future.successful(PortDistance(value = 0.0, suezRoute = false))
    } else {
      val eca        = true
      val antiPiracy = true
      println(from)
      println(to)

      //had to use the cursor to make it compile
      val q: GraphQLCursor[DistanceResult, DistanceInput] =
        client.query(
          distanceQuery,
          DistanceInput(originPort = from, destinationPort = to, antiPiracy = antiPiracy, eca = eca)
        )
      val data: Future[GraphQLResponse[DistanceResult]] = q.result

      //val q = client.query[DistanceResult][DistanceInput](distanceQuery, DistanceInput(originPort = from, destinationPort = to, antiPiracy = antiPiracy, eca = eca))
      //val data: Future[GraphQLResponse[DistanceResult][DistanceInput]] = q.result
      data.map {
        case Left(error) =>
          throw new Exception(s"Couldn't get data, error: $error")
        case Right(value) =>
          value.data.getPortToPortDistance
      }
      //use the same ports endpoint to requests portToPort Distance
      /*val req = requestGQL(portsEndpoint, distanceQ(from, to)).map { res =>
        getData(res, "getPortToPortDistance").as[PortDistance].right.get
      }
      req*/
    }

  def allContracts: Future[Seq[Schema.VesselContract]] = {
    val req = requestGQL(logicEndpoint, contracts).map { res =>
      getData(res, "getAllVesselContracts").as[Vector[Contract]].right.get.map { l =>
        Schema.VesselContract(
          vesselId = l.vessel.id,
          dailyCharterCost = l.dailyCharteringCosts.value,
          expiration = Scalars
            .parseDate(l.charterExpiration)
            .toOption
            .map { d =>
              d.toDate.getTime / 1000
            }
            .getOrElse { println(s"WARNING - Could not parse contract Date ${l.charterExpiration}"); Long.MaxValue }
        )
      }
    }
    req
  }

}
