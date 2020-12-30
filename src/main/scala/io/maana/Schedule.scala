package io.maana

import io.maana.QueryInputs.Constants
import io.maana.Schema.{DateRange, UnavailableTime}
import io.maana.Server.Client.client
import io.maana.common.Logger
import sangria.execution.UserFacingError

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.language.implicitConversions
import scala.util.Try

// TODO vessel unavailable time.
// TODO past due locked requirements

// TODO Account for contract costs when computing actual Schedule
// TODO deal with average port wait time

// TODO port incompatibilities - can push arrival dates if at end

// TODO check if we end a requirement stranded - should then abort
// TODO Change time granularity to hours

object Schedule {
  val logger = Logger(this.getClass)

  case class SchedulingError(msg: String) extends Error(msg) with UserFacingError

  // Must be doubles so time calculations up cast
  val secondsPerHour: Double = 3600.0
  val hoursPerDay: Double    = 24.0
  val secondsPerDay: Double  = secondsPerHour * hoursPerDay

  // approximate cost of fuel as used in python

  //val defaultFuelCost = 400
  //val defaultDieselCost = 650
  //val refuelThresholdAmount = 400
  //val criticalFuelThresholdAmount = 300
  val operationalOverhead = 9 * secondsPerHour.toLong

  object ConstantValues {
    var defaultFuelCost             = 0
    var defaultDieselCost           = 0
    var refuelThresholdAmount       = 0
    var criticalFuelThresholdAmount = 0
    var operationalOverhead         = secondsPerHour.toLong

    def createConstants(constants: Constants) {
      //id = "Optimization Constants"
      defaultFuelCost = constants.defaultFuelPrice
      defaultDieselCost = constants.defaultDieselPrice
      refuelThresholdAmount = constants.refuelThreshold
      criticalFuelThresholdAmount = constants.criticalRefuelThreshold
      operationalOverhead = constants.operationalOverhead * secondsPerHour.toLong

    }
  }

  // Load up the cleaning times data
  val cleaningTimesSeconds = Try {
    Source
      .fromResource("data/BP_times.csv")
      .getLines
      .map { line =>
        val split = line.split(",", 5)
        val from = split(1).toLowerCase
        val to = split(2).toLowerCase
        val time = split(3).toInt
        val cold = split(4).toLowerCase.contains("cold")
        val hot = split(4).toLowerCase.contains("hot")
        val tpe = if (cold) Schema.ColdRate else if (hot) Schema.HotRate else Schema.DryRate
        (from, to) -> (time * secondsPerHour, tpe)
      }
      .toMap
  }.getOrElse {
    logger.error("File data/BP_times.csv is not found")
    Map.empty[(String, String), (Double, Int)]
  }

  // Load up the bunker consumption for cleaning data
  val cleaningBunkerConsumptionsMr = Try {
    Source
      .fromResource("data/mr_cleaning_bunker_consumption.csv")
      .getLines
      .drop(1)
      .map { line =>
        val split = line.split(",", 4)
        val from = split(0).toLowerCase
        val to = split(1).toLowerCase
        val fuel = split(2).toDouble
        val diesel = split(3).toDouble
        (from, to) -> (fuel, diesel)
      }
      .toMap
  }.getOrElse {
    logger.error("File data/mr_cleaning_bunker_consumption.csv is not found")
    Map.empty[(String, String), (Double, Double)]
  }

  val cleaningBunkerConsumptionsLr = Try {
    Source
      .fromResource("data/lr_cleaning_bunker_consumption.csv")
      .getLines
      .drop(1)
      .map { line =>
        val split = line.split(",", 4)
        val from = split(0).toLowerCase
        val to = split(1).toLowerCase
        val fuel = split(2).toDouble
        val diesel = split(3).toDouble
        (from, to) -> (fuel, diesel)
      }
      .toMap
  }.getOrElse {
    logger.error("File data/lr_cleaning_bunker_consumption.csv is not found")
    Map.empty[(String, String), (Double, Double)]
  }

  // Vessel Actions provide a mechanism to precompute costs in a vessel agnostic way
  // Vessels need to be provided to get an actual cost
  //  trait Costs
  //not an graphql type
  sealed trait Cost {
    def cost: Double
    def startDate: Long
    def duration: Long
    def distanceTravelled: Double
    def fuelConsumed: Double
    def locationAfter: Schema.Port
    def constraintsSatisfied: Boolean
  }

  case class ActionCost(
      cost: Double,
      startDate: Long,
      duration: Long,
      distanceTravelled: Double,
      fuelConsumed: Double,
      locationAfter: Schema.Port,
      constraintsSatisfied: Boolean
  ) extends Cost

  case class PrepareAndTravelCost(
      cost: Double,
      startDate: Long,
      duration: Long,
      travelSpeed: Double,
      travelDuration: Long,
      distanceTravelled: Double,
      fuelConsumed: Double,
      locationAfter: Schema.Port,
      constraintsSatisfied: Boolean,
      cleaningAction: CleaningAction,
      refuelingAction: RefuelingAction,
      suezCost: Double
  ) extends Cost

  sealed trait VesselAction

  //TODO really just a nested set of VesselActions
  case class AnnotatedRequirement(requirement: Requirement, cost: Cost) extends VesselAction

  object AnnotatedRequirement {

    def apply(
        requirement: Requirement,
        vessel: Schema.VesselDimensions,
        contractExpiry: Long,
        portRestrictions: Schema.PortIncompatibilityMap,
        unavailableTimes: Seq[UnavailableTime],
        location: Schema.Port,
        startDate: Long,
        remainingFuel: Double,
        currentProduct: Schema.Product
    )(implicit context: Context): AnnotatedRequirement =
      new AnnotatedRequirement(
        requirement = requirement,
        requirement.cost(
          vessel,
          contractExpiry,
          portRestrictions,
          unavailableTimes,
          startDate,
          remainingFuel,
          location,
          currentProduct
        )
      )
  }

  // Captures additional cost of a refueling action if one must take place.
  case class RefuelingAction(
      duration: Long,
      cost: Double,
      fuel: Double,
      port: Schema.Port,
      travel1Duration: Long,
      travel2Duration: Long,
      fuel1: Double,
      fuel2: Double,
      refuelingDuration: Long
  )

  object RefuelingAction {

    def refuel(
        vessel: Schema.VesselDimensions,
        portRestrictions: Schema.PortIncompatibilityMap,
        now: Long,
        speed: Double,
        fuelConsumedPerDay: Double,
        distanceToTarget: Double,
        durationToTarget: Long,
        currentFuel: Double,
        fuelToTarget: Double,
        at: Schema.Port,
        target: Schema.Port
    )(implicit context: Context): Option[RefuelingAction] = {
      // Determine port to refuel at
      val fuelAtTarget = currentFuel - fuelToTarget
      if (distanceToTarget == 0 || !fuelLow(fuelAtTarget)) {
        Some(RefuelingAction(0, 0, 0, at, 0, 0, 0, 0, 0)) // Don't refuel
      } else if (target.canRefuel && !fuelCritical(fuelAtTarget)) {
        // Refuel at the target if it's not dangerous - next move will trigger actual refueling - we know we can refuel there so it's OK
        //        println(s"Refueling: at target - remaining fuel will be at ${fuelAtTarget/vessel.fuelCapacity}%")
        Some(RefuelingAction(0, 0, 0, target, 0, 0, 0, 0, 0))
      } else if (at.canRefuel) {
        // Refuel here if possible - fill her up -- Negative value since it adds fuel
        //        println(s"Refueling: at start point - current fuel = ${currentFuel/vessel.fuelCapacity}% remaining fuel would have been ${fuelAtTarget/vessel.fuelCapacity}%")
        val fuel = vessel.fuelCapacity - currentFuel
        //TODO Port wait time
        val pumpRate =
          if (at.loadingPumpRateM3PerS < vessel.cargoPumpingRateM3PerS) at.loadingPumpRateM3PerS
          else vessel.cargoPumpingRateM3PerS
        val duration = ConstantValues.operationalOverhead + pumpingTime(pumpRate, fuel)
        Some(RefuelingAction(duration, 0, -fuel, at, 0, 0, 0, 0, duration)) // Fuel cost is payed when using it
      } else {
        // TODO need a test case that triggers this
        // Find optimal intermediate port - could be cached - but infrequent
        println(s"Refueling: looking for refueling port between ${at.id} and ${target.id}")
        // val maxRange = ((currentFuel - (criticalFuelThresholdAmount)) / fuelConsumedPerDay) * (speed * hoursPerDay)
        val maxRange = (currentFuel / fuelConsumedPerDay) * (speed * hoursPerDay)
        // Requires 2 * N distance lookups where N is the number of candidates
        // TODO make requests in parallel
        val unsortedPorts = at.neighbors
          .filter { p =>
            Schema.dockingFeasible(vessel, p)
          } // Must be able to enter the port
          .map { n =>
            n -> (distanceNm(at.id, n.id).value, distanceNm(n.id, target.id).value)
          }
          .filter(_._2._1 < maxRange) // Must be within range
          .filter { // Port incompatibilities - this isn't quite right, vessel could wait for port incompatibility to expire
            case (p, (d1, d2)) =>
              val invalidRanges = portRestrictions.getOrElse(p.id, Seq.empty)
              val arrival       = (now + d1).toLong
              val notRestricted =
                invalidRanges.forall(r => !DateRange(arrival, arrival + secondsPerDay.toLong).overlaps(r.dateRange))
              notRestricted
          }

        if (unsortedPorts.isEmpty) {
          // TODO check if we end a requirement stranded - should then abort
          println("Vessel stranded before move")
          // throw SchedulingError(s"Vessel ${vessel.name} is stranded. Not enough bunker to get to any refueling port")
          None
        } else {
          val sortedPorts          = unsortedPorts.sortBy { case (n, d) => d._1 + d._2 }
          val (bestPort, (d1, d2)) = sortedPorts.head

          //for the best port to refuel..if this is the destination return none
          if (bestPort.id == target.id) {
            println("refuel option is destination so refuel in the next move")
            Some(RefuelingAction(0, 0, 0, target, 0, 0, 0, 0, 0))
          } else {
            println(
              s"Refueling Vessel: ${vessel.id} - Using port ${bestPort.id}, distance from source to dest = ${distanceNm(at.id, target.id)}, broken to $d1 -> $d2"
            )
            val durationToRefuelingPoint = Math.ceil(d1 * secondsPerHour / speed).toLong
            val fuelToRefuelingPoint     = (durationToRefuelingPoint / secondsPerDay) * fuelConsumedPerDay

            val fuelAdded = vessel.fuelCapacity - (currentFuel - fuelToRefuelingPoint)
            val pumpRate =
              if (at.loadingPumpRateM3PerS < vessel.cargoPumpingRateM3PerS) at.loadingPumpRateM3PerS
              else vessel.cargoPumpingRateM3PerS
            val fuelingDuration     = ConstantValues.operationalOverhead + pumpingTime(pumpRate, fuelAdded)
            val durationToDest      = Math.ceil(d2 * secondsPerHour / speed).toLong
            val fuelToDest          = (durationToDest / secondsPerDay) * fuelConsumedPerDay
            val newFuelAtTarget     = vessel.fuelCapacity - fuelToDest
            val newDurationToTarget = durationToRefuelingPoint + fuelingDuration + durationToDest

            // Have to enter the new port and travel the additional distance, consuming additional fuel
            val additionalFuelCost = (fuelToRefuelingPoint + fuelToDest - fuelToTarget) * ConstantValues.defaultFuelCost
            val cost               = bestPort.feeDollars + additionalFuelCost

            Some(
              RefuelingAction(
                newDurationToTarget - durationToTarget,
                cost,
                fuelAtTarget - newFuelAtTarget,
                bestPort,
                durationToRefuelingPoint,
                durationToDest,
                fuelToRefuelingPoint,
                fuelToDest,
                fuelingDuration
              )
            )

          }

        }
      }
    }
  }

  case class CleaningAction(
      totalCost: Double,
      durationCost: Double,
      fuelCost: Double,
      dieselCost: Double,
      fuelConsumed: Double,
      duration: Long,
      nextProduct: Schema.Product
  )

  object CleaningAction {

    def clean(
        vessel: Schema.VesselDimensions,
        currentProduct: Schema.Product,
        requirementProduct: Schema.Product,
        productQuantityM3: Double
    ): CleaningAction = {

      val M3_2_bbl   = 6.28981
      val MR_default = 300000
      val LR_default = 500000

      if (currentProduct.id.isEmpty || requirementProduct.id.isEmpty) {
        CleaningAction(0, 0, 0, 0, 0, 0, requirementProduct) // No cleaning
      } else {
        // Need to clean
        // TODO take into account gasOil fuel cost
        val (time, rate) = cleaningTimesSeconds((currentProduct.id, requirementProduct.id))

        val (fuel, diesel) = vessel.sizeCategory match {
          case "MR"  => cleaningBunkerConsumptionsMr.getOrElse((currentProduct.id, requirementProduct.id), (0.0, 0.0))
          case "LR1" => cleaningBunkerConsumptionsLr.getOrElse((currentProduct.id, requirementProduct.id), (0.0, 0.0))
          case "LR2" => cleaningBunkerConsumptionsLr.getOrElse((currentProduct.id, requirementProduct.id), (0.0, 0.0))
          case _     => (0.0, 0.0)
        }

        val timeModified = Math.ceil(time * vessel.cleaningTimeMultiplier).toLong
        val cleaningTimeSeconds = vessel.sizeCategory match {
          case "MR"  => Math.ceil(M3_2_bbl * productQuantityM3 * timeModified / MR_default).toLong
          case "LR"  => Math.ceil(M3_2_bbl * productQuantityM3 * timeModified / LR_default).toLong
          case "LR2" => Math.ceil(M3_2_bbl * productQuantityM3 * timeModified / LR_default).toLong
          case _     => timeModified
        }
        // println(s"The time to clean ${vessel.name} is ${cleaningTimeSeconds/3600} hours, originally this was ${timeModified/3600}.")

        val durationCost = (cleaningTimeSeconds / secondsPerDay) * vessel.cleaningRates(rate) * ConstantValues.defaultFuelCost
        val fuelCost     = fuel * ConstantValues.defaultFuelCost
        val dieselCost   = diesel * ConstantValues.defaultDieselCost
        val totalCost    = durationCost + fuelCost + dieselCost

        CleaningAction(totalCost, durationCost, fuelCost, dieselCost, fuel, cleaningTimeSeconds, requirementProduct)
      }
    }
  }

  // Gets the cost  of traveling through the Suez canal based on:
  // - Vessel's SCNT (Suez Canal Net Tonnage) - a static value based on the physical properties of the vessel (from q88)
  // - If vessel is ballast or laden
  // - If the product is clean/dirty
  def calculateSuezCost(scnt: Double, ballast: Boolean, productType: String): Double = {
    println(s"calculating suez cost for scnt: $scnt, clean status: $productType, ballast?: $ballast")
    // Price (in SDR) per SCNT for the first 5k, next 5k, next 10k, ... 20k, 30k, 50k, remaining.
    // SDR is special drawing rights, and is what the suez canal calculates rates in.
    val suezRatesPerSCNT =
      if (ballast) List(6.70, 4.74, 3.59, 1.78, 1.53, 1.32, 1.29)
      else {
        productType match {
          case "d" => List(7.88, 5.58, 4.22, 2.09, 1.80, 1.55, 1.52)
          case "c" => List(7.88, 5.58, 4.22, 2.80, 2.74, 2.47, 2.38)
        }
      }
    // TODO: Need to find some way to get this live. Constant for now to get functionality working.
    val sdrToUsdRate = 1.37
    val quantities   = List(5000, 5000, 10000, 20000, 30000, 50000, 1000000)
    // Calculates the cost by stepping through and applying the rate for each SCNT amount that the SCNT is fully within
    // Subtracts from the SCNT total until the total remaining is less than the amount for  the price range
    // Adds price for remaining and then the rest are 0.
    val suezCost = quantities
      .zip(suezRatesPerSCNT)
      .foldLeft((0.0, scnt))({
        case ((totalCost, scntRemaining), (curScnt, curCost)) =>
          if (scntRemaining < curScnt) {
            (totalCost + (curCost * scntRemaining * sdrToUsdRate), 0.0)
          } else {
            (totalCost + (curCost * curScnt * sdrToUsdRate), scntRemaining - curScnt)
          }
      })
    suezCost._1
  }

  // Common code for prepare and Travel Actions
  def prepareAndTravelCost(
      vessel: Schema.VesselDimensions,
      portRestrictions: Schema.PortIncompatibilityMap,
      startDate: Long,
      startFuel: Double,
      currentPort: Schema.Port,
      distance: Queries.PortDistance,
      economicSpeed: Double,
      maxSpeed: Double,
      bunkerRequirements: Map[Int, Double],
      completeAfter: Long,
      startBefore: Long,
      dest: Schema.Port,
      cleaningAction: CleaningAction,
      ballast: Boolean
  )(implicit context: Context): PrepareAndTravelCost = {

    // Figure out speed we need to travel to make the requirement - it's either the max speed or the economic speed, so we don't unnecessarily remove possible schedules
    val (speed, travelDuration) = {
      // This isn't quite right, it's possible for a badly scheduled Requirement to be such that you can't complete it if you arrive at the end of the pickup window, but can if you arrive earlier
      // It's difficult to address that case simply, because this action now depends on subsequent calculations, it requires a 2 pass process, where max speed is used then costs relaxed
      // For now we'll assume that is not the case, and this will cover almost all useful cases anyway
      val travelDurationAtEconomic = Math.ceil((secondsPerHour * distance.value) / economicSpeed).toLong
      if (startDate + travelDurationAtEconomic < startBefore) {
        (economicSpeed, travelDurationAtEconomic)
      } else {
        // Note we don't check here, if the time is after the pickup the load unload will be marked as bad
        val travelDurationAtMax = Math.ceil((secondsPerHour * distance.value) / maxSpeed).toLong
        (maxSpeed, travelDurationAtMax)
      }
    }

    val suezCost = if (distance.suezRoute) {
      println(s"calculating suez cost for vessel: ${vessel.name}");
      calculateSuezCost(vessel.scnt, ballast, vessel.cleanStatus)
    } else {
      0.0
    }

    val fuelConsumedPerDay = bunkerRequirements(Math.ceil(speed * 2).toInt)
    val fuelConsumed       = fuelConsumedPerDay * (travelDuration / secondsPerDay)

    val refuelingActionO = RefuelingAction.refuel(
      vessel,
      portRestrictions,
      startDate,
      speed,
      fuelConsumedPerDay,
      distance.value,
      travelDuration,
      startFuel,
      fuelConsumed,
      currentPort,
      dest
    )
    refuelingActionO
      .map { refuelingAction =>
        val refuelDuration = refuelingAction.duration
        // wait duration is time we have to spend - (travelTime + refuelingTime)
        val waitDuration = Math.max(0, (completeAfter - startDate) - (travelDuration + refuelDuration))
        // Cleaning can take place while travelling or waiting but not refueling
        val cleaningDuration = Math.max(0, cleaningAction.duration - (travelDuration + waitDuration))
        val totalCost        = fuelConsumed * ConstantValues.defaultFuelCost + refuelingAction.cost + cleaningAction.totalCost + suezCost // based on unrefueled consumption cost Delta included in refueling action
        // waiting is not done at ports, so assumed cost is just charter cost
        // Note we don't actually need to account for the charter cost, it's being paid no matter what the ship is doing
        val totalDuration     = travelDuration + waitDuration + refuelDuration + cleaningDuration
        val totalFuelConsumed = fuelConsumed + refuelingAction.fuel // refueling cost will be negative adding fuel
        PrepareAndTravelCost(
          totalCost,
          startDate,
          totalDuration,
          speed,
          travelDuration,
          distance.value,
          totalFuelConsumed,
          dest,
          true,
          cleaningAction,
          refuelingAction,
          suezCost
        )
      }
      .getOrElse {
        println("ERROR: Shouldn't get here")
        // throw SchedulingError(s"Vessel ${vessel.name} does not have enough bunker to get to any refueling port")
        PrepareAndTravelCost(
          0,
          startDate,
          0,
          0,
          10000000,
          0,
          0,
          dest,
          false,
          cleaningAction,
          RefuelingAction(0, 0, 0, dest, 0, 0, 0, 0, 0),
          suezCost
        )
      } // Constraints failed because refueling can't be completed
  }

  // PrepareAndTravel encompass the combination of  refueling, cleaning, traveling to a location and waiting there until at least the
  // time the next action can take place. That could be Wait then Travel or Travel then Wait or just Wait or Just Travel
  // always at start of a requirement, requires distance calculation
  // travel can incorporate a refueling operation, since they use fuel, will be inserted before travel begins
  // Note identical to Prepare and Travel distance, but we can't precompute the initial distance
  // Note: Cleaning can only take place along with to the initial move PrepareAndTravelTo to match original over simplified Python logic
  case class PrepareAndTravelTo(dest: Schema.Port, completeAfter: Long, startBefore: Long) extends VesselAction {

    def cost(
        vessel: Schema.VesselDimensions,
        portRestrictions: Schema.PortIncompatibilityMap,
        startDate: Long,
        startFuel: Double,
        currentPort: Schema.Port,
        currentProduct: Schema.Product,
        startProduct: Schema.Product,
        productQuantityM3: Double
    )(implicit context: Context): PrepareAndTravelCost = {
      val distance       = distanceNm(currentPort.id, dest.id)
      val cleaningAction = CleaningAction.clean(vessel, currentProduct, startProduct, productQuantityM3)
      // First voyage always empty
      prepareAndTravelCost(
        vessel,
        portRestrictions,
        startDate,
        startFuel,
        currentPort,
        distance,
        vessel.ballastEconomicSpeed,
        vessel.ballastMaxSpeed,
        vessel.ballastBunkerRequirementsMtPerDay,
        completeAfter,
        startBefore,
        dest,
        cleaningAction,
        true
      )
    }
  }

  // Distance to travel between the Ports - when known as a part of a requirements - cached distance in effect
  case class PrepareAndTravelDist(
      dest: Schema.Port,
      distance: Queries.PortDistance,
      completeAfter: Long,
      startBefore: Long
  ) extends VesselAction {

    // Subsequent Voyages always laden
    def cost(
        vessel: Schema.VesselDimensions,
        portRestrictions: Schema.PortIncompatibilityMap,
        startDate: Long,
        startFuel: Double,
        currentPort: Schema.Port
    )(implicit context: Context): PrepareAndTravelCost =
      // only clean at start of Requirement - other case
      prepareAndTravelCost(
        vessel,
        portRestrictions,
        startDate,
        startFuel,
        currentPort,
        distance,
        vessel.ladenEconomicSpeed,
        vessel.ladenMaxSpeed,
        vessel.ladenBunkerRequirementsMtPerDay,
        completeAfter,
        startBefore,
        dest,
        CleaningAction(0, 0, 0, 0, 0, 0, Schema.Product("", "")),
        false
      )
  }

  // Corresponds to a Long
  case class Load(duration: Long, acost: Double, product: Schema.Product, port: Schema.Port, valid: DateRange)
      extends VesselAction {

    def cost(vessel: Schema.VesselDimensions, startDate: Long): ActionCost = {
      val constraint = valid.contains(startDate)
      ActionCost(acost, startDate, duration, 0, 0, port, constraint)
    }
  }

  object Load {

    def apply(port: Schema.Port, action: Schema.PortAction, vessel: Schema.VesselDimensions): Load = {
      val pumpRate =
        if (port.loadingPumpRateM3PerS < vessel.cargoPumpingRateM3PerS) port.loadingPumpRateM3PerS
        else vessel.cargoPumpingRateM3PerS
      val duration = ConstantValues.operationalOverhead + pumpingTime(pumpRate, action.productQuantityM3)
      // Note we don't actually need to account for the charter cost, it's being paid no matter what the ship is doing
      // Poet costs are just the one time berthing fees.
      val cost = port.feeDollars
      new Load(duration, cost, action.product, port, action.valid)
    }
  }

  // Corresponds to a Short
  case class Unload(duration: Long, acost: Double, product: Schema.Product, port: Schema.Port, valid: DateRange)
      extends VesselAction {

    def cost(vessel: Schema.VesselDimensions, startDate: Long): ActionCost = {
      val constraint = valid.contains(startDate)
      ActionCost(acost, startDate, duration, 0, 0, port, constraint)
    }
  }

  object Unload {

    def apply(port: Schema.Port, action: Schema.PortAction, vessel: Schema.VesselDimensions): Unload = {
      val pumpRate =
        if (port.loadingPumpRateM3PerS < vessel.cargoPumpingRateM3PerS) port.loadingPumpRateM3PerS
        else vessel.cargoPumpingRateM3PerS
      val duration = ConstantValues.operationalOverhead + pumpingTime(pumpRate, action.productQuantityM3)
      val cost     = port.feeDollars
      new Unload(duration, cost, action.product, port, action.valid)
    }
  }

  case class Requirement(
      id: Int,
      initialProduct: Schema.Product,
      finalProduct: Schema.Product,
      actions: Seq[VesselAction],
      originalRequirement: Schema.Requirement
  ) extends VesselAction {

    def costInner(
        vessel: Schema.VesselDimensions,
        contractExpiry: Long,
        portRestrictions: Schema.PortIncompatibilityMap,
        unavailableTimes: Seq[UnavailableTime],
        startDate: Long,
        startFuel: Double,
        currentPort: Schema.Port,
        currentProduct: Schema.Product
    )(implicit context: Context): ActionCost = {
      // cost is sum of costs of actions
      case class VState(now: Long, currentFuel: Double)

      val initial: (VState, ActionCost) =
        (VState(startDate, startFuel), ActionCost(0, startDate, 0, 0, 0, currentPort, true))
      val timeWithCost = actions.foldLeft(initial) {
        case ((vs, c), a) =>
          val cost = a match {
            case l: Load                  => l.cost(vessel, vs.now)
            case u: Unload                => u.cost(vessel, vs.now)
            case wt: PrepareAndTravelDist => wt.cost(vessel, portRestrictions, vs.now, vs.currentFuel, c.locationAfter)
            case wt: PrepareAndTravelTo =>
              wt.cost(
                vessel,
                portRestrictions,
                vs.now,
                vs.currentFuel,
                c.locationAfter,
                currentProduct,
                this.initialProduct,
                this.originalRequirement.firstAction.productQuantityM3
              ) // Only deal with currently deal with cleaning at the start of a Requirement
            case r: Requirement =>
              r.cost(
                vessel,
                contractExpiry,
                portRestrictions,
                unavailableTimes,
                vs.now,
                vs.currentFuel,
                c.locationAfter,
                this.initialProduct
              ) // shouldn't happen but if it's nested the product is assumed to be the same as we clean for
            case ar: AnnotatedRequirement => ar.cost
          }

          (
            VState(cost.startDate + cost.duration, vs.currentFuel - cost.fuelConsumed),
            ActionCost(
              c.cost + cost.cost,
              startDate,
              c.duration + cost.duration,
              c.distanceTravelled + cost.distanceTravelled,
              c.fuelConsumed + cost.fuelConsumed,
              cost.locationAfter,
              c.constraintsSatisfied && cost.constraintsSatisfied
            )
          )
      }

      timeWithCost._2
    }

    // Generates the costing/validity, adjusts for unavailable Times
    def cost(
        vessel: Schema.VesselDimensions,
        contractExpiry: Long,
        portRestrictions: Schema.PortIncompatibilityMap,
        unavailableTimes: Seq[UnavailableTime],
        startDate: Long,
        startFuel: Double,
        currentPort: Schema.Port,
        currentProduct: Schema.Product
    )(implicit context: Context): ActionCost = {
      val possibleCost = costInner(
        vessel,
        contractExpiry,
        portRestrictions,
        unavailableTimes,
        startDate,
        startFuel,
        currentPort,
        currentProduct
      )
      if (!possibleCost.constraintsSatisfied || possibleCost.startDate + possibleCost.duration > contractExpiry) {
        // Contract expires while scheduling
        possibleCost.copy(constraintsSatisfied = false)
      } else {
        unavailableTimes match {
          case Nil    => possibleCost
          case h +: t =>
            // Account for travel time to Unavailable time location
            val distanceToUnavailable = if (h.startPort.isEmpty) {
              Queries.PortDistance(value = 0, suezRoute = false)
            } else {
              distanceNm(possibleCost.locationAfter.id, h.startPort)
            }
            // Will always be unladen after requirement
            val speed             = vessel.ballastEconomicSpeed
            val timeToUnavailable = Math.ceil((secondsPerHour * distanceToUnavailable.value) / speed).toLong

            if (possibleCost.startDate + possibleCost.duration < h.dateRange.startDate - timeToUnavailable) {
              possibleCost
            } else {
              // account for change in location after unavailable time
              val newPort = if (h.endPort.isEmpty) currentPort else context.portMap(h.endPort)
              cost(vessel, contractExpiry, portRestrictions, t, h.dateRange.endDate, startFuel, newPort, currentProduct)
            }
        }
      }
    }

    // TODO probably better copy data out
    def firstAction = originalRequirement.firstAction

    def lastAction = originalRequirement.lastAction
  }

  object Requirement {

    def apply(id: Int, requirement: Schema.Requirement, vessel: Schema.VesselDimensions)(
        implicit context: Context
    ): Requirement = {
      // currently all Longs must complete before all Shorts
      val portActions: Seq[Schema.PortAction] = requirement.longs.sortBy(_.valid.startDate) ++ requirement.shorts
        .sortBy(_.valid.startDate)
      val withPorts = portActions.map { a =>
        a -> context.portMap(a.portId)
      }
      // convert to a set of actual actions performed
      val initialActions: List[VesselAction] = List(
        // the first action is different - and always a long
        PrepareAndTravelTo(withPorts.head._2, portActions.head.valid.startDate, portActions.head.valid.endDate),
        Load(withPorts.head._2, withPorts.head._1, vessel)
      )

      // Get the remaining actions with the previous port
      val remainingPortActions = withPorts.tail.zip(withPorts).map { case ((a, p), (pa, pp)) => (a, p, pp) }
      val otherActions: Seq[VesselAction] = remainingPortActions.flatMap {
        case (action, port, prevPort) =>
          val distance = distanceNm(port.id, prevPort.id)
          List(
            PrepareAndTravelDist(port, distance, action.valid.startDate, action.valid.endDate),
            // Then it's either a Load or an Unload
            action match {
              case a @ Schema.LongAction(product, quantity, valid, ogDateRange, portId, terminalId) =>
                Load(port, a, vessel)
              case a @ Schema.ShortAction(product, quantity, valid, ogDateRange, portId, terminalId) =>
                Unload(port, a, vessel)
            }
          )
      }

      val initialProduct = portActions.head.product
      val finalProduct   = portActions.last.product

      new Requirement(id, initialProduct, finalProduct, initialActions ++ otherActions, requirement)
    }
  }

  case class State(
      vessel: Schema.VesselDimensions,
      contract: Schema.VesselContract,
      portRestrictions: Schema.PortIncompatibilityMap,
      totalCost: Double, // The thing we're minimizing
      port: Schema.Port,
      now: Long,
      currentFuel: Double,            // Can it do the voyage
      currentProduct: Schema.Product, // Does vessel need to be cleaned
      // TODO time List vs Vector
      unavailableTimes: Seq[UnavailableTime], // Pre booked time windows that prevent scheduling - Ordered by start date
      candidateRequirements: List[Requirement], // Ordered by Start Date
      depth: Int = 0
  )

  // Assumptions
  // Requirements started must be finished
  // Requirement actions can not be re-ordered - could be done up front if necessary
  // Sunk cost id lost - no point costing what's currently being performed
  // The only variation in a ships requirement cost is any initial voyage/refueling/cleaning, so the bulk of the cost/time can be computed once per vessel
  // Can refueling occur in the middle of a requirement? Yes
  // If cleaning occur in the middle of a requirement, it should be included in the requirement - included in requirement
  // All pick up must occur before any drop offs
  // Unavailable time - must be at location at beginning, one place changes end loc and potentially fuel.

  def pumpingTime(pumpRate: Double, quantity: Double): Long = Math.ceil(quantity / pumpRate).toLong

  def fuelLow(fuel: Double): Boolean = fuel < ConstantValues.refuelThresholdAmount

  def fuelCritical(fuel: Double): Boolean = fuel < ConstantValues.criticalFuelThresholdAmount

  case class Context(
      portMap: Map[PortId, Schema.Port],
      // Cache the future so blocking on external requests is minimized
      distanceCache: scala.collection.mutable.Map[(String, String), Future[Queries.PortDistance]] =
        new java.util.concurrent.ConcurrentHashMap[(String, String), Future[Queries.PortDistance]].asScala
  )

  type ActionList = List[VesselAction]
  type PortId     = String

  // context just for distance queries
  def distanceNm(from: PortId, to: PortId)(implicit context: Context): Queries.PortDistance =
    if (from == to) {
      Queries.PortDistance(value = 0, suezRoute = false)
    } else {
      // first fetch is not synchronized for performance in case of trivial cache hit
      val future = context.distanceCache.getOrElse(
        (from, to),
        // Could have been populate by parallel call after failure so fetch again synchronized this time to prevent duplicate external requests
        synchronized(
          context.distanceCache.getOrElse(
            (from, to), {
              println(s"Fetching distance from service $from -> $to")
              val d = Queries.distance(client, from, to)
              // Note service does not return symmetric distances A=>B != B=>A
              context.distanceCache.put((from, to), d)
              d
            }
          )
        )
      )
      // If distance requests take more than 30s we have an issue
      Await.result(future, 30.seconds)
    }

  case class Schedule(action: AnnotatedRequirement, children: List[Schedule])

  // returns all possible schedules given the current State

  /*
      val state = State(
          vessel = vessel.dimensions,
          contract = vessel.contract,
          portRestrictions = vessel.portRestrictions,
          totalCost = 0,
          port = context.portMap(startingPort),
          now = scheduleStartTime,
          currentFuel = startingFuel,
          currentProduct = vessel.lastProduct,
          candidateRequirements = reqs,
          unavailableTimes = vessel.unavailableTimes
        )

   */
  def possibleSchedules(state: State)(implicit context: Context): List[Schedule] = {
    // This filtering is an optimization, could just compute the actual Requirement actions for the vessel
    // Requirements valid from here? i.e. can the vessel reach the port before the last possible loading date
    val distances = state.candidateRequirements.map { r =>
      r -> distanceNm(state.port.id, r.firstAction.portId).value
    }

    // Assume traveling at maximum unladen speed
    val travelTimes = distances.map { case (r, d) => (r, d, Math.ceil(d / state.vessel.ballastMaxSpeed).toLong) }

    // Have to be able to reach the requirement before the end of it's first long
    val canReach = travelTimes.filter { case (r, d, t) => r.firstAction.valid.endDate > t + state.now }

    // Compute the actual requirement actions for the vessel and filter to just those that can complete all legs in the specified time windows and aren't dangerously low on fuel
    val requirements = canReach
      .map {
        case (r, _, _) =>
          AnnotatedRequirement(
            r,
            state.vessel,
            state.contract.expiration,
            state.portRestrictions,
            state.unavailableTimes,
            state.port,
            state.now,
            state.currentFuel,
            state.currentProduct
          )
      }
      .filter(r => r.cost.constraintsSatisfied)
      .filterNot(r => fuelCritical(state.currentFuel - r.cost.fuelConsumed))

    // Recurse given the resulting state
    val vesselActions = requirements.map { r =>
      // compute the child schedules - might be a stack issue - may have to reconfigure JVM
      val cost           = r.cost
      val completionTime = cost.startDate + cost.duration

      val completionState = state.copy(
        depth = state.depth + 1,
        totalCost = state.totalCost + cost.cost,
        port = r.cost.locationAfter,
        now = completionTime,
        currentFuel = state.currentFuel - cost.fuelConsumed,
        currentProduct = r.requirement.finalProduct, // Ends having transferred product the requirement ends with
        // no point in considering requirements we can't meet now
        candidateRequirements = canReach.map(_._1).filter { a =>
          a.id != r.requirement.id &&                     // Already scheduled
          a.firstAction.valid.endDate > completionTime && // Needs to start too early
          (
            a.firstAction.ogDateRange.endDate == a.firstAction.valid.endDate || // Check if the requirement was late and pushed to the future and if it was:
            a.firstAction.ogDateRange.startDate > r.requirement.firstAction.ogDateRange.startDate // Ensure it is not scheduled after non-late reqs
          )
        },
        // Remove unavailable times we've passed
        unavailableTimes = state.unavailableTimes.dropWhile(ut => completionTime > ut.dateRange.startDate)
      )
      val childSchedules = possibleSchedules(completionState)
      Schedule(r, childSchedules)
    }

    vesselActions
  }

  def schedule(
      now: Long,
      vessel: Schema.VesselWithDimensions,
      requirementsToSchedule: Seq[Schema.Requirement],
      context: Context
  ) = {
    // TODO take into account current ongoing action/current ongoing Requirement
    val startingPort = vessel.startLocation
    val startingFuel = vessel.startFuel
    // Schedule start occurs after latest of current requirement, ongoing Action or now
    val scheduleStartTime = Math.max(vessel.startDate, now)

    //requirements that are already locked to requirements
    val lockedRequirements = requirementsToSchedule.filter(_.locked.isDefined)

    // Order all the locked requirements by start date of first long
    val sortedLocked = lockedRequirements.filter(_.longs.nonEmpty).sortBy(_.longs.head.valid.startDate)
    // layout the schedule for those requirements
    val lockedSchedule = expandSchedule(now, vessel, sortedLocked, context)
    // Isolated locked requirements can be scheduled Normally
    // Late requirements in this list must have their Start/End time adjusted to allow construction of at least this schedule
    // This gives maximal leeway in scheduling other requirements around the locked ones
    val patchedRequirements = requirementsToSchedule.map { r =>
      if (r.locked.isDefined) {
        // Compute the updated Requirements
        // Find the schedule for the locked requirement
        val schedule = lockedSchedule.requirements.find(_.id == r.id).get
        // These are always the earliest points a long/short can start, and are always after or at the original start
        // have to convert back to internal date format - possible loss of precision
        val longStarts  = schedule.actions.filter(_.`type` == "load").map { _.startsAt.toDate.getTime / 1000 }
        val shortStarts = schedule.actions.filter(_.`type` == "unload").map { _.startsAt.toDate.getTime / 1000 }
        val newLongs = r.longs.zip(longStarts).map {
          case (l, newStart) =>
            l.copy(
              valid = DateRange(
                newStart - 1,
                Math.max(newStart + 1 * secondsPerHour.toLong, l.valid.endDate + (secondsPerDay * 1.5).toLong)
              )
            )
        }
        val newShorts = r.shorts.zip(shortStarts).map {
          case (s, newStart) =>
            s.copy(
              valid = DateRange(
                newStart - 1,
                Math.max(newStart + 1 * secondsPerHour.toLong, s.valid.endDate + (secondsPerDay * 1.5).toLong)
              )
            )
        }
        r.copy(longs = newLongs, shorts = newShorts)
      } else {
        r
      }
    }

    val reqs = patchedRequirements.zipWithIndex.map { case (r, i) => Requirement(i, r, vessel.dimensions)(context) }.toList

    val state = State(
      vessel = vessel.dimensions,
      contract = vessel.contract,
      portRestrictions = vessel.portRestrictions,
      totalCost = 0,
      port = context.portMap(startingPort),
      now = scheduleStartTime,
      currentFuel = startingFuel,
      currentProduct = vessel.lastProduct,
      candidateRequirements = reqs,
      unavailableTimes = vessel.unavailableTimes
    )

    val res0 = possibleSchedules(state)(context)

    // remove schedules without all of the locked requirements
    val requiredReqs = lockedRequirements.map { _.id }
    val res = res0.flatMap { s =>
      filterByRequired(s, requiredReqs)
    }

    // TODO - remove - this is more expensive than computing the schedules
    println(s"Created ${res.length} Schedules for Vessel: ${vessel.id}")
    val totalCombinations = res.map(countSchedules).sum
    println(s"Created $totalCombinations possible schedules")
    val maxdepth = res.map(scheduleMaxDepth).fold(0L) { (a, b) =>
      Math.max(a, b)
    }
    println(s"Maximum Schedule depth = $maxdepth")
    println()

    //    res.map{s => walkSchedules(printScheduleRequirementIds, s) }
    //    res.map{s => walkSchedules(printScheduleRequirementEnds, s) }
    //    res.map{s => walkSchedules(printSchedule(startingFuel, scheduleStartTime), s) }
    //    println
    // TODO - remove - End

    val out = ScheduleResults.toResultSchedule(vessel.dimensions, res)
    out
  }

  def filterByRequired(schedule: Schedule, reqIds: Seq[String]): Option[Schedule] = {
    val childReqs = reqIds.filter(_ != schedule.action.requirement.originalRequirement.id)
    if (childReqs.isEmpty) {
      Some(schedule) // All requirements met - return all subtrees
    } else {
      val newChildren = schedule.children.flatMap { s =>
        filterByRequired(s, childReqs)
      }
      if (newChildren.nonEmpty) {
        Some(schedule.copy(children = newChildren))
      } else {
        None
      }
    }
  }

  def countSchedules(schedule: Schedule): Long =
    1 + schedule.children.map(s => countSchedules(s)).sum

  def scheduleMaxDepth(schedule: Schedule): Long = schedule.children match {
    case Nil => 1
    case x   => 1 + x.map(scheduleMaxDepth).max
  }

  def walkSchedules[T](
      fn: Seq[AnnotatedRequirement] => T,
      schedule: Schedule,
      acc: Seq[AnnotatedRequirement] = Seq.empty
  ): Seq[T] = schedule.children match {
    case Nil => Seq(fn(acc :+ schedule.action))
    case x =>
      val newAcc = acc :+ schedule.action
      // Note that we must include the version of the schedule that terminates here
      fn(newAcc) +: x.flatMap(s => walkSchedules(fn, s, newAcc))
  }

  val dateFormatString = "MM/dd/yy"

  import org.joda.time.DateTime

  def arToString(ar: AnnotatedRequirement): String = {
    def startDate0 =
      new DateTime(ar.requirement.firstAction.valid.startDate * 1000).toDateTime.toString(dateFormatString)

    def startDate1 = new DateTime(ar.requirement.firstAction.valid.endDate * 1000).toDateTime.toString(dateFormatString)

    def endDate0 = new DateTime(ar.requirement.lastAction.valid.startDate * 1000).toDateTime.toString(dateFormatString)

    def endDate1 = new DateTime(ar.requirement.lastAction.valid.endDate * 1000).toDateTime.toString(dateFormatString)

    // Compute Cost breakdown
    // Compute Distance travelled

    s"${ar.requirement.originalRequirement.id}($startDate0|$startDate1-$endDate0|$endDate1)"

  }

  def getScheduleRequirementIds(in: Seq[AnnotatedRequirement]): Seq[String] =
    in.map(r => r.requirement.originalRequirement.id)

  def printScheduleRequirementIds(in: Seq[AnnotatedRequirement]): Unit = {
    val strs = in.map(r => s""""${r.requirement.originalRequirement.id}"""").mkString(",")
    println(s"[$strs]")
  }

  def printScheduleRequirementEnds(in: Seq[AnnotatedRequirement]): Unit = {
    val strs =
      in.map(r => s""""${toDate(r.cost.startDate)} - ${toDate(r.cost.startDate + r.cost.duration)}"""").mkString(",")
    println(s"[$strs]")
  }

  def printSchedule(fuel: Double)(in: Seq[AnnotatedRequirement]): Unit = {

    // Start by just printing ID and time to complete + required completion duration
    // Compute the start times of the legs
    val endDates    = in.map(r => r.cost.startDate + r.cost.duration)
    val initialFuel = (fuel, Seq.empty[Double])
    val endFuels = in
      .foldLeft(initialFuel) {
        case ((fuel, endFuels), ar) =>
          val endFuel = fuel - ar.cost.fuelConsumed
          (endFuel, endFuels :+ endFuel)
      }
      ._2

    val outStr = in
      .zip(endDates.zip(endFuels))
      .map {
        case (ar, (ed, ef)) =>
          val dateRange = arToString(ar)
          val endDate   = new DateTime(ed * 1000).toDateTime.toString(dateFormatString)
          val d         = ar.cost.distanceTravelled
          val f         = ar.cost.fuelConsumed
          s"$dateRange:$endDate (${ef.toLong}:${f.toLong}:${d.toLong}nm)"
      }
      .mkString(" -> ")
    val cost = in.map {
      _.cost.cost
    }.sum
    val distance = in.map {
      _.cost.distanceTravelled
    }.sum

    println(f"[$$$cost%.0f, $distance%.0fnm, $fuel%.0f] $outStr")
  }

  def toDate(in: Long): org.joda.time.DateTime = new org.joda.time.DateTime(in * 1000, org.joda.time.DateTimeZone.UTC)

  // TODO add charter costs
  def actualCost(cost: Double, duration: Long) = cost

  def expandPrepareAndWait(vs: State, cost: PrepareAndTravelCost, dest: PortId): Seq[ScheduleResults.Action] = {
    // TODO contract costs
    println(s"expanding prepare and wait actions")

    val vessel = vs.vessel
    val vsNow  = cost.startDate

    val (refuelingAction0, refueling0End) = {
      val refuelEnds = vsNow + cost.refuelingAction.refuelingDuration

      if (cost.refuelingAction.duration == 0 || cost.refuelingAction.port.id != vs.port.id)
        (None, vsNow)
      else
        (
          Some(
            ScheduleResults.Action(
              id = vessel.id + " refuel",
              `type` = "refuel",
              cost = actualCost(0, cost.refuelingAction.duration), // Fuel is paid for by use so it's just time -- // TODO  is that what we want to report here?
              speed = 0.0,
              startsAt = toDate(vsNow),
              // Note refueling Action costs are actually deltas over the base Travel and Wait cost
              endState = ScheduleResults.State(
                id = vessel.id + " refuel end state",
                endsAt = toDate(refuelEnds),
                fuelRemaining = vessel.fuelCapacity,
                lastProduct = vs.currentProduct.originalId,
                location = vs.port.id
              )
            )
          ),
          refuelEnds
        )
    }

    // cleaning actions overlap Wait and travel times
    val cleaningEnd = refueling0End + cost.cleaningAction.duration
    val cleaningAction =
      if (cost.cleaningAction.duration == 0)
        None
      else
        Some(
          ScheduleResults.Action(
            id = vessel.id + " clean",
            `type` = "clean",
            cost = cost.cleaningAction.fuelCost + cost.cleaningAction.dieselCost,
            speed = 0.0,
            startsAt = toDate(refueling0End),
            endState = ScheduleResults.State(
              id = vessel.id + " clean end state",
              endsAt = toDate(cleaningEnd),
              fuelRemaining = vs.currentFuel - cost.cleaningAction.fuelConsumed, // cleaning doesn't consume fuel, just time for now - fuel is accounted for by travel
              lastProduct = "", // not correct
              location = cost.locationAfter.id // not really valid could be on route
            )
          )
        )

    // refueling at intermediate point
    val (travelToRefuelingLocation, refuelingAction1, travelToTarget, travelEnd, fuelAfterTravel) = {

      /*if(cost.refuelingAction.duration != 0 && cost.refuelingAction.port.id == dest){
       println("refuels at destination")
       //println(s"${cost}")
       val voyageEnd = vsNow + cost.refuelingAction.travel1Duration
       val refuelEnds = voyageEnd + cost.refuelingAction.refuelingDuration
       (
         Some(ScheduleResults.Action(
            `type` = "voyage",
            cost = actualCost(cost.refuelingAction.fuel1 * defaultFuelCost, cost.refuelingAction.travel1Duration),
            speed = cost.travelSpeed,
            startsAt = toDate(refueling0End),
            endState = ScheduleResults.State(
              endsAt = toDate(voyageEnd),
              fuelRemaining = vs.currentFuel - cost.refuelingAction.fuel1,
              lastProduct = vs.currentProduct.originalId, // Not necessarily correct
              location = cost.refuelingAction.port.id
            )
          )),
           Some(ScheduleResults.Action(
            `type` = "refuel",
            cost = actualCost(0, cost.refuelingAction.refuelingDuration),       // Fuel is paid for on use // TODO is this the right cost to return?
            speed = 0.0,
            startsAt = toDate(voyageEnd),
            endState = ScheduleResults.State(
              endsAt = toDate(refuelEnds),
              fuelRemaining = vessel.fuelCapacity,
              lastProduct = vs.currentProduct.originalId, // Not necesarilly correct
              location = cost.refuelingAction.port.id
            )
          )),
          None,
          // cost.refuelingAction.travel2Duration -> should be zero anyway
          refueling0End + cost.refuelingAction.refuelingDuration + cost.refuelingAction.travel1Duration + cost.refuelingAction.travel2Duration,
          vessel.fuelCapacity - cost.refuelingAction.fuel2

       )
     }*/
      if (cost.refuelingAction.duration != 0 && cost.refuelingAction.port.id != dest && cost.refuelingAction.port.id != vs.port.id) {
        val voyageEnd  = vsNow + cost.refuelingAction.travel1Duration
        val refuelEnds = voyageEnd + cost.refuelingAction.refuelingDuration
        (
          Some(
            ScheduleResults.Action(
              id = vessel.id + " voyage",
              `type` = "voyage",
              cost = actualCost(
                cost.refuelingAction.fuel1 * ConstantValues.defaultFuelCost,
                cost.refuelingAction.travel1Duration
              ),
              speed = cost.travelSpeed,
              startsAt = toDate(refueling0End),
              endState = ScheduleResults.State(
                id = vessel.id + " voyage end state",
                endsAt = toDate(voyageEnd),
                fuelRemaining = vs.currentFuel - cost.refuelingAction.fuel1,
                lastProduct = vs.currentProduct.originalId, // Not necessarily correct
                location = cost.refuelingAction.port.id
              )
            )
          ),
          Some(
            ScheduleResults.Action(
              id = vessel.id + " refuel",
              `type` = "refuel",
              cost = actualCost(0, cost.refuelingAction.refuelingDuration), // Fuel is paid for on use // TODO is this the right cost to return?
              speed = 0.0,
              startsAt = toDate(voyageEnd),
              endState = ScheduleResults.State(
                id = vessel.id + " refuel end state",
                endsAt = toDate(refuelEnds),
                fuelRemaining = vessel.fuelCapacity,
                lastProduct = vs.currentProduct.originalId, // Not necessarily correct
                location = cost.refuelingAction.port.id
              )
            )
          ),
          Some(
            ScheduleResults.Action(
              id = vessel.id + " voyage",
              `type` = "voyage",
              cost = actualCost(
                (cost.refuelingAction.fuel2 * ConstantValues.defaultFuelCost) + cost.suezCost,
                cost.refuelingAction.travel2Duration
              ),
              speed = cost.travelSpeed,
              startsAt = toDate(refuelEnds),
              endState = ScheduleResults.State(
                id = vessel.id + " voyage end state",
                endsAt = toDate(refuelEnds + cost.refuelingAction.travel2Duration),
                fuelRemaining = vessel.fuelCapacity - cost.refuelingAction.fuel2,
                lastProduct = vs.currentProduct.originalId,
                location = dest
              )
            )
          ),
          refueling0End + cost.refuelingAction.refuelingDuration + cost.refuelingAction.travel1Duration + cost.refuelingAction.travel2Duration,
          vessel.fuelCapacity - cost.refuelingAction.fuel2
        )
      } else {
        (
          None,
          None,
          if (cost.travelDuration == 0) {
            None
          } else {
            Some(
              ScheduleResults.Action(
                id = vessel.id + " voyage",
                `type` = "voyage",
                cost = actualCost(
                  ((cost.fuelConsumed - cost.refuelingAction.fuel) * ConstantValues.defaultFuelCost) + cost.suezCost,
                  cost.travelDuration
                ), // Account for refueling action at start
                speed = cost.travelSpeed,
                startsAt = toDate(refueling0End),
                endState = ScheduleResults.State(
                  id = vessel.id + " voyage end state",
                  endsAt = toDate(refueling0End + cost.travelDuration),
                  fuelRemaining = vs.currentFuel - cost.fuelConsumed,
                  lastProduct = vs.currentProduct.originalId,
                  location = dest
                ),
              )
            )
          },
          refueling0End + cost.travelDuration,
          vs.currentFuel - cost.fuelConsumed
        )
      }
    }

    val waitTime = cost.duration - cost.travelDuration - cost.refuelingAction.duration // This one is the delta over the other two
    val waitForLoad = if (waitTime == 0) {
      None
    } else {
      Some(
        ScheduleResults.Action(
          id = vessel.id + " wait",
          `type` = "wait",
          cost = actualCost(0, cost.duration),
          speed = 0.0,
          startsAt = toDate(travelEnd),
          endState = ScheduleResults.State(
            id = vessel.id + " wait end state",
            endsAt = toDate(travelEnd + waitTime),
            fuelRemaining = fuelAfterTravel,
            lastProduct = vs.currentProduct.originalId, // Not necessarily correct
            location = dest
          )
        )
      )
    }

    val res = Seq(
      refuelingAction0,
      cleaningAction,
      travelToRefuelingLocation,
      refuelingAction1,
      travelToTarget,
      waitForLoad,
    ).flatten

    res
  }

  def expandScheduleI(state: State, requirements: Seq[Requirement])(
      implicit context: Context
  ): Seq[ScheduleResults.Requirement] = {

    val vessel = state.vessel
    // Cost the requirements
    val (_, reqs) = requirements.foldLeft((state, Seq.empty[ScheduleResults.Requirement])) {
      case ((vs1, rs), r) =>
        // cost the overall requirement and the contained action
        // TODO contract costs

        // Have to schedule the requirement to account for deferred startTime - unavailable times, and adjust incoming state
        val rcost = r.cost(
          vessel,
          state.contract.expiration,
          vs1.portRestrictions,
          vs1.unavailableTimes,
          vs1.now,
          vs1.currentFuel,
          vs1.port,
          vs1.currentProduct
        )

        // Start port can change because of unavailable time
        val newPort = vs1.unavailableTimes
          .takeWhile(u => u.dateRange.startDate < rcost.startDate)
          .lastOption
          .map(u => context.portMap(u.endPort))
          .getOrElse(vs1.port)

        val (stateAfter, actions): (State, Seq[ScheduleResults.Action]) =
          r.actions.foldLeft((vs1.copy(now = rcost.startDate, port = newPort), Seq.empty[ScheduleResults.Action])) {
            case ((vs, as), a) =>
              val (aouts, cost, newProduct): (Seq[ScheduleResults.Action], Cost, Schema.Product) = a match {

                case wt: PrepareAndTravelTo =>
                  println("prepare and travel To")
                  val cost = wt.cost(
                    vessel,
                    vs.portRestrictions,
                    vs.now,
                    vs.currentFuel,
                    vs.port,
                    vs.currentProduct,
                    r.initialProduct,
                    r.firstAction.productQuantityM3
                  )
                  val res = as ++ expandPrepareAndWait(vs, cost, wt.dest.id)
                  (res, cost, vs.currentProduct)

                case wt: PrepareAndTravelDist =>
                  println("prepare and travel Dist")
                  val cost = wt.cost(vessel, vs.portRestrictions, vs.now, vs.currentFuel, vs.port)
                  val res  = as ++ expandPrepareAndWait(vs, cost, wt.dest.id)
                  (res, cost, vs.currentProduct)

                case l: Load =>
                  val cost = l.cost(vs.vessel, vs.now)
                  val res = as :+ ScheduleResults.Action(
                    id = vessel.id + " load",
                    `type` = "load",
                    cost = actualCost(cost.cost, cost.duration),
                    speed = 0.0,
                    startsAt = toDate(cost.startDate),
                    endState = ScheduleResults.State(
                      id = vessel.id + " load end state",
                      endsAt = toDate(cost.startDate + cost.duration),
                      fuelRemaining = vs.currentFuel,
                      lastProduct = l.product.originalId,
                      location = vs.port.id
                    )
                  )
                  (res, cost, l.product)

                case l: Unload =>
                  val cost = l.cost(vs.vessel, vs.now)
                  val res = as :+ ScheduleResults.Action(
                    id = vessel.id + " unload",
                    `type` = "unload",
                    cost = actualCost(cost.cost, cost.duration),
                    speed = 0.0,
                    startsAt = toDate(cost.startDate),
                    endState = ScheduleResults.State(
                      id = vessel.id + " unload end state",
                      endsAt = toDate(cost.startDate + cost.duration),
                      fuelRemaining = vs.currentFuel,
                      lastProduct = l.product.originalId,
                      location = vs.port.id
                    )
                  )
                  (res, cost, l.product)

                case a =>
                  throw SchedulingError(s"Scheduling Failed - Internal error - VesselAction in expandScheduleI $a")
              }

              val completionTime = cost.startDate + cost.duration
              val newState = vs.copy(
                port = cost.locationAfter,
                now = completionTime,
                currentFuel = vs.currentFuel - cost.fuelConsumed,
                currentProduct = newProduct,
                totalCost = vs.totalCost + cost.cost,
                // Remove unavailable times we've passed
                unavailableTimes = vs.unavailableTimes.dropWhile(ut => completionTime > ut.dateRange.startDate)
              )

              (newState, aouts)
          }

        // TODO -- debug code - check Costs match those for the entire requirement
        val endTime = toDate(rcost.startDate + rcost.duration)
        println(s"Requirement Should start @ ${toDate(rcost.startDate)}")
        println(s"Requirement Should end @ $endTime - actually end @ ${actions.last.endState.endsAt}")
        println(s"Requirement Should cost @ ${rcost.cost} - actually costs @ ${actions.map(_.cost).sum}")
        // TODO

        // Adjust costs based on Charter costs
        val contract = state.contract
        if (contract.vesselId.isEmpty) println(s"WARNING - could not fetch contract for vessel ${vessel.id}")
        val actions1 = actions.map { a =>
          if (a.`type` == "clean") { // No charter cost for cleaning - done in parallel with Wait and Travel
            a
          } else {
            val duration = (a.endState.endsAt.toDate.getTime / 1000 - a.startsAt.toDate.getTime / 1000).toDouble / secondsPerDay
            a.copy(cost = a.cost + duration * contract.dailyCharterCost)
          }
        }

        val res = {
          val totalCost = actions1.map(_.cost).sum
          rs :+ ScheduleResults.Requirement(r.originalRequirement.id, totalCost, actions1)
        }
        stateAfter -> res
    }

    reqs
  }

  def expandSchedule(
      now: Long,
      vessel: Schema.VesselWithDimensions,
      requirementsToSchedule: Seq[Schema.Requirement],
      context: Context
  ): ScheduleResults.DetailedSchedule = {
    val startingPort = vessel.startLocation
    val startingFuel = vessel.startFuel
    // Schedule start occurs after latest of current requirement, ongoing Action or now
    val scheduleStartTime = Math.max(vessel.startDate, now)
    val reqs              = requirementsToSchedule.zipWithIndex.map { case (r, i) => Requirement(i, r, vessel.dimensions)(context) }.toList
    val state = State(
      vessel = vessel.dimensions,
      contract = vessel.contract,
      portRestrictions = vessel.portRestrictions,
      totalCost = 0,
      port = context.portMap(startingPort),
      now = scheduleStartTime,
      currentFuel = startingFuel,
      currentProduct = vessel.lastProduct,
      candidateRequirements = List.empty,
      unavailableTimes = vessel.unavailableTimes
    )

    val res = expandScheduleI(state, reqs)(context)

    ScheduleResults.DetailedSchedule(id = vessel.id, vessel = vessel.id, requirements = res)
  }

  def reorderSchedule(
      now: Long,
      vessel: Schema.VesselWithDimensions,
      requirementsToSchedule: Seq[Schema.Requirement],
      context: Context
  ): ScheduleResults.DetailedSchedule = {
    val startingPort = vessel.startLocation
    val startingFuel = vessel.startFuel
    // Schedule start occurs after latest of current requirement, ongoing Action or now
    val scheduleStartTime = Math.max(vessel.startDate, now)
    val reqs              = requirementsToSchedule.zipWithIndex.map { case (r, i) => Requirement(i, r, vessel.dimensions)(context) }.toList
    if (reqs.isEmpty) {
      // No requirements
      ScheduleResults.DetailedSchedule(vessel.id, vessel.id, Seq.empty)
    } else {

      val state = State(
        vessel = vessel.dimensions,
        contract = vessel.contract,
        portRestrictions = vessel.portRestrictions,
        totalCost = 0,
        port = context.portMap(startingPort),
        now = scheduleStartTime,
        currentFuel = startingFuel,
        currentProduct = vessel.lastProduct,
        candidateRequirements = reqs,
        unavailableTimes = vessel.unavailableTimes
      )
      // Generate the valid vessel Schedules - note there will be valid schedules of depth less than reqs.length
      val schedules = possibleSchedules(state)(context)
      val maxdepth = schedules.map(scheduleMaxDepth).fold(0L) { (a, b) =>
        Math.max(a, b)
      }
      if (maxdepth != reqs.length) {
        // No complete solution
        ScheduleResults.DetailedSchedule(vessel.id, vessel.id, Seq.empty)
      } else {
        // Filter out the max length schedules
        val validSchedules = schedules
          .flatMap { s =>
            walkSchedules(id => id, s)
          }
          .filter(_.length == reqs.length)
        val bestSchedule = validSchedules.map(sol => sol.map(_.cost.cost).sum -> sol.map(_.requirement)).minBy(_._1)._2
        // Expand the best schedule - note could reduce cost here but not critical
        val expandedSchedule = expandScheduleI(state, bestSchedule)(context)
        ScheduleResults.DetailedSchedule(vessel.id, vessel.id, expandedSchedule)
      }
    }
  }

}
