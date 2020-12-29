package io.maana

import io.circe.generic.auto._
import io.circe.syntax._
import sangria.execution.UserFacingError
import sangria.macros.derive._
import sangria.schema._
import sangria.execution.UserFacingError
import sangria.marshalling.{DateSupport, FromInput}
import java.util.concurrent.{ConcurrentHashMap, Executors}

import scala.language.implicitConversions

//need to figure out what this is for.  looks like to do with cleaning time but we don't use that right now
import io.maana.Queries.productMappings
import io.maana.ScheduleResults.DetailedSchedule
import sangria.marshalling.circe._

// These really are required -- Intellij just can't establish that
import com.typesafe.config.ConfigFactory
import io.maana.QueryInputs._
import io.maana.Scalars._
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Schema {
  case class SchemaError(msg: String) extends Error(msg) with UserFacingError

  val conf                   = ConfigFactory.load()
  val executionContext       = ExecutionContext.fromExecutor(new java.util.concurrent.ForkJoinPool(50))
  val useGlobalDistanceCache = conf.getBoolean("app.globalDistanceCache")

  val globalDistanceCache =
    new java.util.concurrent.ConcurrentHashMap[(String, String), Future[Queries.PortDistance]].asScala

  val dateFormatString              = "MM/dd/yy"
  val vesselVolumeCapacityTolerance = 1.1;
  val maxBeamTolerance              = 1.01
  val cleanStatusAny                = "d/c"

  val DryRate  = 0
  val ColdRate = 1
  val HotRate  = 2

  //types (these are used by QueryInput.scala at the moment)
  type VesselId   = String
  type PortId     = String
  type TerminalId = String
  // Probably shouldn't be a String
  type CleanStatus = String

  //product Type -> it is some kind of mapping
  case class Product(id: String, originalId: String)
  //case class Product(id: String)

  case class VesselDimensions(
      id: VesselId,
      name: String,
      sizeCategory: String,
      fuelCapacity: Double,
      ballastEconomicSpeed: Double,
      ballastMaxSpeed: Double,
      ladenEconomicSpeed: Double,
      ladenMaxSpeed: Double,
      totalProductCapacityM3: Double,
      beam: Double,
      overallLength: Double,
      aftParallelBodyDistance: Double,
      forwardParallelBodyDistance: Double,
      ladenBunkerRequirementsMtPerDay: Map[Int, Double],
      ballastBunkerRequirementsMtPerDay: Map[Int, Double],
      cleaningTimeMultiplier: Double,
      cleaningRates: Vector[Double],
      cleanStatus: CleanStatus,
      imoClass: String,
      scnt: Double,
      cargoPumpingRateM3PerS: Double
  )

  // Vessel data needed for simulation
  //not sure what this does exactly
  case class OldVessel(
      id: VesselId,
      ongoingActionsEnd: Option[ActionEndState],
      currentRequirement: Option[CurrentRequirement]
  )

  sealed trait PortAction {
    def product: Product
    def productQuantityM3: Double
    def valid: DateRange
    def portId: String
    def terminalId: Option[String]
  }

  // requirement types
  case class ShortAction(
      product: Product,
      productQuantityM3: Double,
      valid: DateRange,
      ogDateRange: DateRange,
      portId: String,
      terminalId: Option[String]
  ) extends PortAction
  case class LongAction(
      product: Product,
      productQuantityM3: Double,
      valid: DateRange,
      ogDateRange: DateRange,
      portId: String,
      terminalId: Option[String]
  ) extends PortAction

  case class ActionEndState(port: PortId, date: Long, fuelRemaining: Double)
  case class CurrentRequirement(id: String, end: Long, port: PortId, fuelRemaining: Double)

  case class Requirement(
      id: String,
      shorts: Vector[ShortAction],
      longs: Vector[LongAction],
      cleanStatus: CleanStatus,
      locked: Option[VesselId]
  ) {
    def firstAction = longs.head
    def lastAction  = shorts.last
  }
  case class RequirementLock(requirement: String, vessel: String)

  //classes / functions

  //this is also imported by QueryInput.scala although I don't think its used
  case class DateRange(startDate: Long, endDate: Long) {
    def inside(d: DateRange): Boolean   = (d.startDate <= this.startDate) && (d.endDate >= this.endDate)
    def contains(d: Long): Boolean      = (this.startDate <= d) && (this.endDate >= d)
    def overlaps(d: DateRange): Boolean = this.contains(d.startDate) || this.contains(d.endDate) || this.inside(d)

    override def toString: String = {
      def startDate =
        new DateTime(this.startDate * 1000, org.joda.time.DateTimeZone.UTC).toDateTime.toString(dateFormatString)
      def endDate =
        new DateTime(this.endDate * 1000, org.joda.time.DateTimeZone.UTC).toDateTime.toString(dateFormatString)
      s"($startDate, $endDate)"
    }
  }

  case class PortRestriction(dateRange: DateRange)
  type PortIncompatibilityMap = Map[PortId, Seq[PortRestriction]]

  //not sure we need this either
  case class TerminalRestriction(dateRange: DateRange)
  type TerminalIncompatibilityMap = Map[TerminalId, Seq[TerminalRestriction]]

  case class Berth(
      id: String,
      maxBeam: Double,
      maxOverallLength: Double,
      minOverallLength: Double,
      minPmbAft: Double,
      minPmbForward: Double
  )
  case class Port(
      id: PortId,
      feeDollars: Double,
      loadingPumpRateM3PerS: Double,
      berths: Vector[Berth],
      canRefuel: Boolean,
      latitude: Double,
      longitude: Double,
      neighbors: Vector[Port]
  )
  type PortMap = Map[PortId, Port]

  case class SimplePort(id: String)

  case class UnavailableTime(dateRange: DateRange, startPort: PortId, endPort: PortId)

  case class VesselWithDimensions(
      id: VesselId,
      startDate: Long,
      startFuel: Double,
      lastProduct: Schema.Product,
      startLocation: PortId,
      portRestrictions: PortIncompatibilityMap,
      terminalRestrictions: TerminalIncompatibilityMap,
      unavailableTimes: Seq[UnavailableTime],
      dimensions: VesselDimensions,
      contract: VesselContract
  )
  case class VesselContract(vesselId: VesselId, dailyCharterCost: Double, expiration: Long)
  type VesselDimensionMap = Map[VesselId, VesselDimensions]
  type VesselContractMap  = Map[VesselId, VesselContract]

  //functions
  def toPortRestrictions(portRestrictions: Seq[QueryInputs.PortRestriction]): PortIncompatibilityMap =
    portRestrictions
      .filter(
        in =>
          in.portId match {
            case Some(y) => true
            case None    => false
          }
      )
      .map { in =>
        in.portId.getOrElse("") -> PortRestriction(
          dateRange = toDateRange(in.dateRange)
        )
      }
      .groupBy(_._1)
      .map(a => a._1 -> a._2.map { _._2 })
      .toMap

  def toTerminalRestrictions(portRestrictions: Seq[QueryInputs.PortRestriction]): TerminalIncompatibilityMap =
    portRestrictions
      .filter(
        in =>
          in.terminalId match {
            case Some(y) => true
            case None    => false
          }
      )
      .map { in =>
        in.terminalId.getOrElse("") -> TerminalRestriction(
          dateRange = toDateRange(in.dateRange)
        )
      }
      .groupBy(_._1)
      .map(a => a._1 -> a._2.map { _._2 })
      .toMap

  // result is a set of ordered discontinuous Date Ranges
  def combineRanges(in: Seq[UnavailableTime]): Seq[UnavailableTime] = in match {
    case Nil      => Seq.empty
    case _ +: Nil => in
    case h +: t =>
      val rhs = combineRanges(t)
      val r   = rhs.head
      if (!h.dateRange.overlaps(rhs.head.dateRange)) {
        h +: rhs
      } else {
        // Have to combine r and h - we know were sorted by startDate
        combineRanges(
          UnavailableTime(
            dateRange = DateRange(h.dateRange.startDate, Math.max(h.dateRange.endDate, r.dateRange.endDate)),
            startPort = h.startPort,
            endPort = r.endPort // TODO may not be correct
          ) +: rhs.tail
        )
      }
  }

  def toM3PerSec(value: Double, unit: String): Double = unit.toLowerCase() match {
    case "m^3/h"  => value / 3600.0
    case "m^3/hr" => value / 3600.0
    case foo =>
      println(s"Unit = ${foo}")
      ???
  }

  def toDateRange(in: DateRangeInput): DateRange =
    DateRange(in.startDate.toDate.getTime / 1000, in.endDate.toDate.getTime / 1000)

  def toSchemaRequirements(
      requirements: Seq[RequirementInput],
      now: Long,
      vesselsWithDimensions: Seq[VesselWithDimensions]
  ): Seq[Requirement] = {
    val requirementsIn = requirements.map { in =>
      Requirement(
        id = in.id,
        shorts = in.shorts.map { s =>
          val prod = productMappings.getOrElse(s.product.toLowerCase, {
            println(s"WARNING: no entry in mapping for product ${s.product}, no cleaning time calculated"); ""
          })
          val range = toDateRange(s.valid)
          ShortAction(Product(prod, s.product), s.quantity, range, range, s.location, s.terminal)
        }.toVector,
        longs = in.longs.map { l =>
          val prod = productMappings.getOrElse(l.product.toLowerCase, {
            println(s"WARNING: no entry in mapping for product ${l.product}, no cleaning time calculated"); ""
          })
          val range = toDateRange(l.valid)
          LongAction(Product(prod, l.product), l.quantity, range, range, l.location, l.terminal)
        }.toVector,
        locked = in.locked,
        cleanStatus = in.cleanStatus.toLowerCase()
      )
    }
    val vesselStartMap = vesselsWithDimensions.map(v => v.id -> v.startDate).toMap
    // Adjust the requirements so that those that are overdue - can't be started have shifted end constraints
    // Also given them some additional leeway since they are already late
    requirementsIn.map { r =>
      val scheduleStartDate = r.locked match {
        case Some(n) => vesselStartMap.getOrElse(n, throw SchemaError(s"ERROR: Start date for vessel $n not found"))
        case None    => now
      }
      // if we can't meet the first constraint
      val startIsLate = r.longs.headOption.map(_.valid.startDate < scheduleStartDate).getOrElse(false)
      val endIsLate   = r.longs.headOption.map(_.valid.endDate < scheduleStartDate).getOrElse(false)
      if (endIsLate) {
        val leeway = 5 * Schedule.secondsPerDay.toLong
        println(s"Adjusting requirement to start after start $r")
        val delta = scheduleStartDate - r.longs.head.valid.endDate + leeway
        // Adjust all the constraints
        r.copy(
          longs = r.longs.map(l => l.copy(valid = DateRange(l.valid.startDate, l.valid.endDate + delta))),
          shorts = r.shorts.map(s => s.copy(valid = DateRange(s.valid.startDate, s.valid.endDate + delta)))
        )
      } else if (startIsLate) {
        val leeway = 1 * Schedule.secondsPerDay.toLong
        println(s"Adjusting requirement to start after start $r")
        val delta = scheduleStartDate - r.longs.head.valid.startDate + leeway
        // Adjust all the constraints
        r.copy(
          longs = r.longs.map(l => l.copy(valid = DateRange(l.valid.startDate, l.valid.endDate + delta))),
          shorts = r.shorts.map(s => s.copy(valid = DateRange(s.valid.startDate, s.valid.endDate + delta)))
        )
      } else {
        r
      }
    }

  }

  //maps the input (VesselWithq88AndStatus to the vessel used in this service)
  def toVesselsWithDimensions(vessels: Seq[VesselWithQ88AndStatusInput]): Seq[VesselWithDimensions] = {
    vessels.map { in =>
      VesselWithDimensions(
        id = in.id,
        startDate = in.vessel.currentStatus.availableFrom.toDate.getTime / 1000,
        startFuel = in.vessel.currentStatus.startingFuel,
        startLocation = in.vessel.currentStatus.lastKnownPort,
        lastProduct = {
          val prod = productMappings.getOrElse(
            in.vessel.currentStatus.lastProduct.toLowerCase, {
              println(
                s"WARNING: no entry in mapping for product ${in.vessel.currentStatus.lastProduct}, no cleaning time calculated"
              ); ""
            }
          )
          Product(prod, in.vessel.currentStatus.lastProduct)
        },
        portRestrictions = toPortRestrictions(in.vessel.portRestrictions.getOrElse(Seq.empty)),
        //what is terminalRestrictions used for?
        terminalRestrictions = toTerminalRestrictions(in.vessel.portRestrictions.getOrElse(Seq.empty)),
        unavailableTimes = combineRanges(
          in.vessel.unavailableTimes
            .getOrElse(Seq.empty)
            .map(
              a =>
                UnavailableTime(
                  dateRange = toDateRange(a.dateRange),
                  startPort = a.startPort.getOrElse(""),
                  endPort = a.endPort.getOrElse(a.startPort.getOrElse(""))
                )
            )
            .sortBy(_.dateRange.startDate)
        ),
        //dimensions = Shared.vesselDimensionMap.getOrElse(in.id, throw SchemaError(s"ERROR: Dimensions for vessel ${in.id} not found")),
        //build dimensions type from input
        /*
          no_eca_cold_cleaning
          no_eca_hot_cleaning
          laden_speed_11
          laden_speed_12
          laden_speed_12_5
          laden_speed_13
          laden_speed_13_5
          laden_speed_14
          laden_speed_14_5
          laden_speed_15
          ballast_speed_11
          ballast_speed_12
          ballast_speed_12_5
          ballast_speed_13
          ballast_speed_13_5
          ballast_speed_14
          ballast_speed_14_5
          ballast_speed_15


         */
        dimensions = {
          VesselDimensions(
            id = in.id,
            name = in.vessel.name,
            sizeCategory = in.vessel.details.sizeCategory,
            fuelCapacity = in.q88Vessel.carryingCapacity.fuelCapacity,
            ballastEconomicSpeed = in.q88Vessel.speedCapabilities.ballastEconomicSpeed,
            ballastMaxSpeed = in.q88Vessel.speedCapabilities.ballastMaxSpeed,
            ladenEconomicSpeed = in.q88Vessel.speedCapabilities.ladenEconomicSpeed,
            ladenMaxSpeed = in.q88Vessel.speedCapabilities.ladenMaxSpeed,
            totalProductCapacityM3 = in.q88Vessel.totalProductCapacity,
            beam = in.q88Vessel.dimensions.beam,
            overallLength = in.q88Vessel.dimensions.overallLength,
            aftParallelBodyDistance = in.q88Vessel.dimensions.aftParallelBodyDistance,
            forwardParallelBodyDistance = in.q88Vessel.dimensions.forwardParallelBodyDistance,
            ladenBunkerRequirementsMtPerDay = Map(
              (10 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_11, // No 10
              (10.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_11, // No 10.5
              (11 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_11,
              (11.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_11, // No 11.5
              (12 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_12,
              (12.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_12_5,
              (13 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_13,
              (13.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_13_5,
              (14 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_14,
              (14.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_14_5,
              (15 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_15,
              (15.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_15, // No 15.5
            ),
            ballastBunkerRequirementsMtPerDay = Map(
              (10 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_11, // No 10
              (10.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_11, // No 10.5
              (11 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_11,
              (11.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_11, // No 11.5
              (12 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_12,
              (12.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_12_5,
              (13 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_13,
              (13.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_13_5,
              (14 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_14,
              (14.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_14_5,
              (15 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_15,
              (15.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_15, // No 15.5
            ),
            cleaningTimeMultiplier = in.vessel.details.cleaningTimeMultiplier,
            cleaningRates = Vector(
              0,
              in.vessel.bunkerRequirements.no_eca_cold_cleaning.foldLeft(0.0) { case (a, b) => a + b },
              in.vessel.bunkerRequirements.no_eca_hot_cleaning.foldLeft(0.0) { case (a, b)  => a + b }
            ),
            cleanStatus = in.vessel.clean,
            //not used or at least in the vessel model its null
            imoClass = in.q88Vessel.imoClass,
            scnt = in.q88Vessel.scnt,
            cargoPumpingRateM3PerS = in.q88Vessel.cargoPumpingRateM3PerS,
          )

        },
        contract = {
          VesselContract(
            vesselId = in.id,
            dailyCharterCost = in.vessel.details.charteringCost,
            expiration = Scalars
              .parseDate(in.vessel.details.contractExpiration)
              .toOption
              .map { d =>
                d.toDate.getTime / 1000
              }
              .getOrElse {
                println(s"WARNING - Could not parse contract Date ${in.vessel.details.contractExpiration}");
                Long.MaxValue
              }
          )

        }
        //contract = Shared.vesselContractMap.getOrElse(in.id, {println(s"WARNING: no contract for vessel ${in.id}"); Schema.VesselContract("", 0, 0)})
      )
    }

  }

  //maps the input (VesselWithq88AndStatus to the vessel used in this service)
  def toVesselsWithDimensionsAndRequirements(
      vessels: Seq[VesselWithQ88AndStatusAndRequirementsInput]
  ): Seq[VesselWithDimensions] =
    vessels.map { in =>
      VesselWithDimensions(
        id = in.id,
        startDate = in.vessel.currentStatus.availableFrom.toDate.getTime / 1000,
        startFuel = in.vessel.currentStatus.startingFuel,
        startLocation = in.vessel.currentStatus.lastKnownPort,
        lastProduct = {
          val prod = productMappings.getOrElse(
            in.vessel.currentStatus.lastProduct.toLowerCase, {
              println(
                s"WARNING: no entry in mapping for product ${in.vessel.currentStatus.lastProduct}, no cleaning time calculated"
              ); ""
            }
          )
          Product(prod, in.vessel.currentStatus.lastProduct)
        },
        portRestrictions = toPortRestrictions(in.vessel.portRestrictions.getOrElse(Seq.empty)),
        //what is terminalRestrictions used for?
        terminalRestrictions = toTerminalRestrictions(in.vessel.portRestrictions.getOrElse(Seq.empty)),
        unavailableTimes = combineRanges(
          in.vessel.unavailableTimes
            .getOrElse(Seq.empty)
            .map(
              a =>
                UnavailableTime(
                  dateRange = toDateRange(a.dateRange),
                  startPort = a.startPort.getOrElse(""),
                  endPort = a.endPort.getOrElse(a.startPort.getOrElse(""))
                )
            )
            .sortBy(_.dateRange.startDate)
        ),
        dimensions = {
          VesselDimensions(
            id = in.id,
            name = in.vessel.name,
            sizeCategory = in.vessel.details.sizeCategory,
            fuelCapacity = in.q88Vessel.carryingCapacity.fuelCapacity,
            ballastEconomicSpeed = in.q88Vessel.speedCapabilities.ballastEconomicSpeed,
            ballastMaxSpeed = in.q88Vessel.speedCapabilities.ballastMaxSpeed,
            ladenEconomicSpeed = in.q88Vessel.speedCapabilities.ladenEconomicSpeed,
            ladenMaxSpeed = in.q88Vessel.speedCapabilities.ladenMaxSpeed,
            totalProductCapacityM3 = in.q88Vessel.totalProductCapacity,
            beam = in.q88Vessel.dimensions.beam,
            overallLength = in.q88Vessel.dimensions.overallLength,
            aftParallelBodyDistance = in.q88Vessel.dimensions.aftParallelBodyDistance,
            forwardParallelBodyDistance = in.q88Vessel.dimensions.forwardParallelBodyDistance,
            ladenBunkerRequirementsMtPerDay = Map(
              (10 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_11, // No 10
              (10.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_11, // No 10.5
              (11 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_11,
              (11.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_11, // No 11.5
              (12 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_12,
              (12.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_12_5,
              (13 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_13,
              (13.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_13_5,
              (14 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_14,
              (14.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_14_5,
              (15 * 2).toInt   -> in.vessel.bunkerRequirements.ballast_speed_15,
              (15.5 * 2).toInt -> in.vessel.bunkerRequirements.ballast_speed_15, // No 15.5
            ),
            ballastBunkerRequirementsMtPerDay = Map(
              (10 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_11, // No 10
              (10.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_11, // No 10.5
              (11 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_11,
              (11.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_11, // No 11.5
              (12 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_12,
              (12.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_12_5,
              (13 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_13,
              (13.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_13_5,
              (14 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_14,
              (14.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_14_5,
              (15 * 2).toInt   -> in.vessel.bunkerRequirements.laden_speed_15,
              (15.5 * 2).toInt -> in.vessel.bunkerRequirements.laden_speed_15, // No 15.5
            ),
            cleaningTimeMultiplier = in.vessel.details.cleaningTimeMultiplier,
            cleaningRates = Vector(
              0,
              in.vessel.bunkerRequirements.no_eca_cold_cleaning.foldLeft(0.0) { case (a, b) => a + b },
              in.vessel.bunkerRequirements.no_eca_hot_cleaning.foldLeft(0.0) { case (a, b)  => a + b }
            ),
            cleanStatus = in.vessel.clean,
            //not used or at least in the vessel model its null
            imoClass = in.q88Vessel.imoClass,
            scnt = in.q88Vessel.scnt,
            cargoPumpingRateM3PerS = in.q88Vessel.cargoPumpingRateM3PerS,
          )
        },
        contract = {
          VesselContract(
            vesselId = in.id,
            dailyCharterCost = in.vessel.details.charteringCost,
            expiration = Scalars
              .parseDate(in.vessel.details.contractExpiration)
              .toOption
              .map { d =>
                d.toDate.getTime / 1000
              }
              .getOrElse {
                println(s"WARNING - Could not parse contract Date ${in.vessel.details.contractExpiration}");
                Long.MaxValue
              }
          )
        }
        //contract = Shared.vesselContractMap.getOrElse(in.id, {println(s"WARNING: no contract for vessel ${in.id}"); Schema.VesselContract("", 0, 0)})
      )
    }

  def capacityCheck(vessel: VesselDimensions, requirement: Requirement): Either[String, Unit] = {
    val requirementQty = Math.max(requirement.shorts.map { _.productQuantityM3 }.sum, requirement.longs.map {
      _.productQuantityM3
    }.sum)
    if ((vessel.totalProductCapacityM3 * vesselVolumeCapacityTolerance) >= requirementQty) {
      println("capacity check valid")
      Right(())
    } else {
      Left(s"Vessel does not have capacity to complete requirement ${vessel.totalProductCapacityM3} vs $requirementQty")
    }
  }

  // Only vessels with an IMOClass can carry MTBE
  def mtbeCheck(vessel: VesselDimensions, requirement: Requirement): Either[String, Unit] = {
    println("checking MTBE")
    val requirementContainsMTBE = !requirement.longs.forall { l =>
      l.product.id.toLowerCase() != "mtbe"
    }
    if (requirementContainsMTBE && vessel.imoClass.isEmpty) {
      Left("Vessel cannot carry MTBE product")
    } else {
      Right(())
    }
  }

  def contractNotExpired(contract: VesselContract, requirement: Requirement): Either[String, Unit] = {
    println("checking contract")
    if (requirement.shorts.last.valid.startDate > contract.expiration) {
      Left(s"Requirement completes after contract expiration on ${dateStr(contract.expiration)}")
    } else {
      Right(())
    }
  }

  def dockingFeasible(vessel: VesselDimensions, port: Port): Boolean =
    port.berths.exists { b =>
      (b.maxBeam == 0 || b.maxBeam >= vessel.beam) &&
      (b.maxOverallLength == 0 || b.maxOverallLength >= vessel.overallLength) &&
      (b.minOverallLength == 0 || vessel.overallLength >= b.minOverallLength) &&
      (b.minPmbAft == 0 || vessel.aftParallelBodyDistance == 0.0 || b.minPmbAft <= vessel.aftParallelBodyDistance) &&
      (b.minPmbForward == 0 || vessel.forwardParallelBodyDistance == 0.0 || b.minPmbForward <= vessel.forwardParallelBodyDistance)
    }

  def portCheck(
      vessel: VesselDimensions,
      requirement: Requirement,
      portIncompatibilities: PortIncompatibilityMap,
      terminalIncompatibilities: TerminalIncompatibilityMap,
      portMap: PortMap
  ): Either[String, Unit] = {
    // requirement can not require stopping at a port that is incompatible with the vessel within this date range

    def checkActions(actions: Seq[PortAction]): Boolean = actions.forall { action =>
      val port = portMap(action.portId)
      println(port)
      dockingFeasible(vessel, port) && {
        val invalidRanges = portIncompatibilities.getOrElse(action.portId, Vector.empty)
        // TODO this is too aggressive but safe - should only remove on inclusion and adjust arrival times for loading/unloading
        invalidRanges.forall(r => !action.valid.overlaps(r.dateRange))
      } && {
        action.terminalId match {
          case Some(a) => {
            val invalidTerminalRanges = terminalIncompatibilities.getOrElse(a, Vector.empty)
            invalidTerminalRanges.forall(r => !action.valid.overlaps(r.dateRange))
          }
          case None => true
        }
      }
    }

    if (checkActions(requirement.shorts) && checkActions(requirement.longs)) {
      Right(())
    } else {
      Left(s"Vessel size is not compatible with port within requirement")
    }
  }

  def checkClean(vessel: VesselDimensions, requirement: Requirement): Either[String, Unit] =
    if (vessel.cleanStatus == cleanStatusAny || {
          val requirementStatus = requirement.cleanStatus
          requirementStatus == cleanStatusAny ||
          vessel.cleanStatus == requirementStatus
        }) {
      Right(())
    } else {
      Left("Vessel can't perform requirement because of Clean/Dirty Status")
    }

  // TODO shouldn't we reject based on unavailable times
  def checkRequirement(
      vessel: VesselDimensions,
      contract: VesselContract,
      requirement: Requirement,
      portIncompatibilities: PortIncompatibilityMap,
      terminalIncompatibilities: TerminalIncompatibilityMap,
      portMap: PortMap
  ): Either[String, Unit] = {
    def valid(): Either[String, Unit] =
      for {
        _ <- mtbeCheck(vessel, requirement)
        _ <- contractNotExpired(contract, requirement)
        _ <- checkClean(vessel, requirement)
        _ <- capacityCheck(vessel, requirement)
        _ <- portCheck(vessel, requirement, portIncompatibilities, terminalIncompatibilities, portMap)
      } yield ()

    val res = if (requirement.locked.isDefined) {
      if (requirement.locked.get == vessel.id) {
        // locked Requirements MUST be valid to solve
        println("requirement is locked")
        valid()
      } else {
        Left("Requirement locked to another vessel")
      }
    } else {
      print("requirement not locked so checking validity")
      valid()
    }
    res
  }

  def filteredRequirements(
      vessel: VesselDimensions,
      contract: VesselContract,
      requirements: Seq[Requirement],
      portIncompatibilities: PortIncompatibilityMap,
      terminalIncompatibilities: TerminalIncompatibilityMap,
      portMap: PortMap
  ): Seq[Requirement] =
    requirements.filter(
      r => checkRequirement(vessel, contract, r, portIncompatibilities, terminalIncompatibilities, portMap).isRight
    )

  //resolvers the service exposes to Q
  //Queries
  trait Query {

    @GraphQLDescription("""
        Main Entrypoint for schedule creation, returns a JSON String.
                          | date: Date at which scheduling is being performed ISO-8601 format.
                          | vessels: Initial state of vessels to be scheduled.
                          | requirements: Requirements to be scheduled.

        """.stripMargin)
    @GraphQLField
    def schedules(
        date: DateTime,
        vessels: Seq[VesselWithQ88AndStatusInput],
        requirements: Seq[RequirementInput],
        constants: Constants
    ): String = Profile.prof("Query: schedules") {
      implicit val executionContext0 = executionContext

      val d                     = date.toDate.getTime / 1000
      val vesselsWithDimensions = toVesselsWithDimensions(vessels)
      //println(vesselsWithDimensions)
      val requirementsToSchedule = toSchemaRequirements(requirements, d, vesselsWithDimensions)
      //println(requirementsToSchedule)

      Schedule.ConstantValues.createConstants(constants)

      println("created data")

      val context = if (useGlobalDistanceCache) {
        println("context")
        Schedule.Context(portMap = Shared.portMap, distanceCache = globalDistanceCache)

      } else {
        Schedule.Context(portMap = Shared.portMap)
      }

      println("got context")

      // Easier to debug none parallel version
      //      val res0 = Profile.prof("Simulation.schedule") {
      //        vesselsWithDimensions.map { vessel =>
      //          val vesselCandidates = filteredRequirements(vessel.dimensions, vessel.contract, requirementsToSchedule, vessel.portRestrictions, vessel.terminalRestrictions, Shared.portMap)
      //          Schedule.schedule(d, vessel, vesselCandidates, context)
      //        }
      //      }

      // Faster Parallel version
      val res0 = Profile.prof("Simulation.schedule") {
        val futures = vesselsWithDimensions.map { vessel =>
          Future {
            //these are candidate requirements to schedule on the vessel after a bunch of checks
            val vesselCandidates = filteredRequirements(
              vessel.dimensions,
              vessel.contract,
              requirementsToSchedule,
              vessel.portRestrictions,
              vessel.terminalRestrictions,
              Shared.portMap
            )

            //schedules it by itself assuming no filtering
            //val vesselCandidates = requirementsToSchedule
            println(vesselCandidates)
            Schedule.schedule(d, vessel, vesselCandidates, context)
          }
        }
        val fin = Future.sequence(futures)
        Await.result(fin, 200.seconds)
      }

      Profile.prof("Result To JSon") {
        res0.asJson.noSpaces
      }
    }

    //TODO: add detailed schedule and validate order resolvers in here
    @GraphQLDescription(
      """Expand a given set of ordered requirements to include actions performed by those requirements and the resulting state
        |""".stripMargin
    )
    @GraphQLField
    def detailedSchedules(
        date: DateTime,
        vessels: Seq[VesselWithQ88AndStatusAndRequirementsInput],
        requirements: Seq[RequirementInput]
    ): Seq[DetailedSchedule] = Profile.prof("Query: detailedSchedules") {
      val d = date.toDate.getTime / 1000

      //this is a different input so we will have to change the input to this function also.
      val vesselsWithDimensions = toVesselsWithDimensionsAndRequirements(vessels)
      val requirementMap        = toSchemaRequirements(requirements, d, vesselsWithDimensions).map(r => r.id -> r).toMap
      val vesselSchedules = vessels.map(in => {
        in.requirements.map(
          r => requirementMap.getOrElse(r, throw SchemaError(s"Requirement $r for vessel ${in.id} not found"))
        )
      })
      val vesselsWithDimensionsAndSchedules = vesselsWithDimensions.zip(vesselSchedules)

      val context = if (useGlobalDistanceCache) {
        Schedule.Context(portMap = Shared.portMap, distanceCache = globalDistanceCache)
      } else {
        Schedule.Context(portMap = Shared.portMap)
      }

      val res = vesselsWithDimensionsAndSchedules.map {
        case (v, rs) =>
          Schedule.expandSchedule(d, v, rs, context)
      }
      res

    }

  }

  case object QueryImpl extends Query
  //case object MutationImpl extends Mutation

  //
  val QueryType: ObjectType[Any, Unit] = deriveContextObjectType[Any, Query, Unit](_ => QueryImpl)
  //val MutationType: ObjectType[Any, Unit] = deriveContextObjectType[Any, Mutation, Unit](_ => MutationImpl)

  val schema = sangria.schema.Schema[Any, Unit](QueryType) // Some(MutationType))
}
