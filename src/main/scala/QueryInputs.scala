package io.maana

import io.maana.Schema.{DateRange, PortId, VesselId, TerminalId}
import org.joda.time.DateTime
import sangria.macros.derive._
import sangria.schema._
import Scalars._
import io.circe.generic.auto._
import io.circe.syntax._


object QueryInputs {

  // these are the types that are input to the main queries defined in the schema
  // there are also types here that are part of the input definition
  // seems a bit disorganised but still

  //def schedules(date: DateTime, vessels: Seq[VesselInput], requirements: Seq[RequirementInput]): String = Profile.prof("Query: schedules")


  //need to update this with the new vessel input 
  //stops having to query for the vessels and maintaing a cache
  sealed trait VesselInputBase {
    def id: VesselId
    def availableFrom: DateTime
    def startingFuel: Double
    def lastProduct: String
    def location: PortId
    def portRestrictions: Option[Seq[PortRestriction]]
    def unavailableTimes: Option[Seq[UnavailableTime]]
  }

  // Vessel dimensions are cached
  case class DateRangeInput(startDate: DateTime, endDate: DateTime)
  // doesn't embed date range to make it compatible with the old interface
  case class UnavailableTime(dateRange: DateRangeInput, startPort: Option[PortId], endPort: Option[PortId])

  @GraphQLDescription("""Vessels Dimensions """")
  case class VesselDimensions(id: String, beam: Double, overallLength: Double, aftParallelBodyDistance: Double, forwardParallelBodyDistance: Double)

  @GraphQLDescription(""""Vessel Speed Capabilities"""")
  case class SpeedCapabilities(
    id: String,
    ladenEconomicSpeed: Double,
    ladenMaxSpeed: Double,
    ballastEconomicSpeed: Double,
    ballastMaxSpeed: Double,
  )
  @GraphQLDescription(""""Vessel Carrying Capacity"""")
  case class CarryingCapacity(
    id: String,
    fuelCapacity: Double,
    diesalCapacity: Double,
    gasOilCapacity: Double,
  )

  @GraphQLDescription(""""Vessel Current Status"""")
  case class CurrentStatus(
    id: String,
    availableFrom: DateTime,
    lastKnownPort: PortId,
    lastProduct: String,
    startingFuel: Double,
  )
  @GraphQLDescription(""""Vessel Details """")
  case class VesselDetails(
    id: String,
    charteringCost: Double,
    contractExpiration: String,
    sizeCategory: String,
    cleaningTimeMultiplier: Double,
   
  )
  @GraphQLDescription(""""Vessel Bunker Requirements """")
  case class BunkerRequirements(
    id: String,
    laden_speed_11: Double,
	  laden_speed_12: Double,
	  laden_speed_12_5: Double,
	  laden_speed_13: Double,
	  laden_speed_13_5: Double,
	  laden_speed_14: Double,
	  laden_speed_14_5: Double,
	  laden_speed_15: Double,
	  ballast_speed_11: Double,
	  ballast_speed_12: Double,
	  ballast_speed_12_5: Double,
	  ballast_speed_13: Double,
	  ballast_speed_13_5: Double,
	  ballast_speed_14: Double,
	  ballast_speed_14_5: Double,
	  ballast_speed_15: Double,
	  no_eca_cold_cleaning: Option[Double],
	  no_eca_hot_cleaning: Option[Double],
  )

  @GraphQLDescription("""Vessel cannot enter the specified port or terminal (only specify one or the other) during the restricted date range""")
  case class PortRestriction(portId: Option[PortId], terminalId: Option[TerminalId], dateRange: DateRangeInput, reason: Option[String])
  @GraphQLDescription("""Initial state of the vessel and at what point it becomes free for scheduling. Passing empty string as the lastProduct stops cleaning being scheduled for the first requirement. product can be specified as either an aramco product or a BP product""")
  case class VesselInput(id: VesselId, 
                         name: String,
                          currentStatus: CurrentStatus,
                          clean: String,
                          details: VesselDetails,
                          isParked: Boolean,
                          portRestrictions: Option[Seq[PortRestriction]], 
                          bunkerRequirements: BunkerRequirements,
                          unavailableTimes: Option[Seq[UnavailableTime]],
  )

  @GraphQLDescription(""" q88 vessel model """")
  case class Q88VesselInput(
    id: VesselId,
    name: String,
    dimensions: VesselDimensions,
    speedCapabilities: SpeedCapabilities,
    carryingCapacity: CarryingCapacity,
    totalProductCapacity: Double,
    cleaningTimeMultiplier: Double,
    cargoPumpingRateM3PerS: Double,
    scnt: Double,
    imoClass: String,
  )
  @GraphQLDescription(""" new vessesl model """")
  case class VesselWithQ88AndStatusInput(
      id: VesselId,
      q88Vessel: Q88VesselInput,
      vessel: VesselInput,
  )

  case class VesselWithQ88AndStatusAndRequirementsInput(
    id: VesselId,
    q88Vessel: Q88VesselInput,
    vessel: VesselInput,
    requirements: Seq[String],
  ) 

  @GraphQLDescription("""Used for both Longs and Shorts. Product can be specified as either an aramco product or a BP product""")
  case class VesselActionInput(product: String, quantity: Double, valid: DateRangeInput, location: PortId, terminal: Option[TerminalId])
  @GraphQLDescription("""Requirement to be scheduled, requirements locked to vessels specify the ID of the vessel they are locked to. This list should contain only requirements that should be scheduled, i.e. omit requirements completed or in process.""")
  case class RequirementInput(id: String, 
      shorts: Seq[VesselActionInput], 
      longs: Seq[VesselActionInput], 
      locked: Option[VesselId], 
      cleanStatus: String)

  case class VesselWithRequirementsInput(id: VesselId, availableFrom: DateTime, startingFuel: Double, lastProduct: String, location: PortId,  portRestrictions: Option[Seq[PortRestriction]], unavailableTimes: Option[Seq[UnavailableTime]], requirements: Seq[String]) extends VesselInputBase

  @GraphQLDescription(""" Constants used for optimization""")
  case class Constants(
      id: String,
      defaultFuelPrice: Double, 
      defaultDiesalPrice: Double,
      refuelThreshold: Double,
      criticalRefuelThreshold: Double)

  // example configuration for inputs to resolvers
  type Name = String
  @GraphQLDescription("""Name Input""")
  case class NameInput(name: Option[Name])

  // sangira stuff that builds the schema
  implicit val NameInputType = deriveInputObjectType[NameInput]()
  implicit val DateRangeInputType = deriveInputObjectType[DateRangeInput]()
  implicit val VesselDimensionType = deriveInputObjectType[VesselDimensions]()
  implicit val SpeedCapabilitiesType = deriveInputObjectType[SpeedCapabilities]()
  implicit val CarryingCapacityType = deriveInputObjectType[CarryingCapacity]()
  implicit val CurrentStatusType = deriveInputObjectType[CurrentStatus]()
  implicit val BunkerRequirementsType =deriveInputObjectType[BunkerRequirements]()
  implicit val PortRestrictionType = deriveInputObjectType[PortRestriction]()
  
  implicit val UnavailableTimeInputType = deriveInputObjectType[UnavailableTime]()
  implicit val VesselDetailsType = deriveInputObjectType[VesselDetails]()
  
  implicit val VesselInputType = deriveInputObjectType[VesselInput]()
  implicit val ContstantsType = deriveInputObjectType[Constants]()
  implicit val Q88VesselInputType=deriveInputObjectType[Q88VesselInput]()

  implicit val VesselActionInputType = deriveInputObjectType[VesselActionInput]()
  implicit val RequirementInputType = deriveInputObjectType[RequirementInput]()

  implicit val VesselWithQ88AndStatusInputType = deriveInputObjectType[VesselWithQ88AndStatusInput]()
  implicit val VesselWithQ88AndStatusAndRequirementsInputInputType = deriveInputObjectType[VesselWithQ88AndStatusAndRequirementsInput]()

  implicit val VesselWithRequirementsInputType = deriveInputObjectType[VesselWithRequirementsInput]()

  def DateRangeStr(i: DateRangeInput) : String = {
    s"""{ startDate: "${i.startDate}", endDate: "${i.endDate}" }"""
  }

  //print functions

  def printVesselInput(v: VesselWithRequirementsInput)  = {
    val reqStr = v.requirements.map(r => s""""$r"""").mkString(", ")
    println (s"""{ id:"${v.id}", availableFrom: "${v.availableFrom}", startingFuel: ${v.startingFuel}, lastProduct: "${v.lastProduct}", location: "${v.location}", requirements:[$reqStr] }""")
  }


  def printRequirementInput(r : RequirementInput) = {
    val shortsStr = r.shorts.map {s =>
      s"""{product: "${s.product}", quantity: ${s.quantity}, valid:${DateRangeStr(s.valid)}, location: "${s.location}", terminal: "${s.terminal}"}"""
    }.mkString(", ")

    val longsStr = r.longs.map {s =>
      s"""{product: "${s.product}", quantity: ${s.quantity}, valid:${DateRangeStr(s.valid)}, location: "${s.location}", terminal: "${s.terminal}"}"""
    }.mkString(", ")

    println(s"""{id: "${r.id}", shorts: [${shortsStr}], longs: [${longsStr}], cleanStatus:"${r.cleanStatus}" }""")

  }

}
