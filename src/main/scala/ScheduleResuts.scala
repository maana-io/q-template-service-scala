package io.maana

import io.maana.Schema.{PortId, VesselId}
import io.maana.{Schedule => Calculated}
import org.joda.time.DateTime
import sangria.schema.{Field, ListType, ObjectType}
import sangria.macros.derive._
import sangria.schema._
import Scalars._
import io.circe.generic.auto._
import io.circe.syntax._



object ScheduleResults {

  // Id, cost, children
  // Note we use short names and round up cost to to reduce the JSON overhead
  case class Schedule(id: String, c: Long, cs: Seq[Schedule])
  case class VesselSchedules(vesselId: VesselId, schedules: Seq[Schedule])

  // Recursive types need explicit field resolvers
  implicit lazy val ScheduleType : ObjectType[Unit, Schedule] = deriveObjectType[Unit, Schedule](
    ReplaceField("cs", Field("cs", ListType(ScheduleType), resolve = _.value.cs))
  )
  implicit val VesselSchedulesType = deriveObjectType[Any, VesselSchedules]()


  def toResultSchedule(vessel: Schema.VesselDimensions, in: Seq[Calculated.Schedule]) : VesselSchedules = {
    def createOut(in: Calculated.Schedule, parentCost: Double) : Schedule = {
      val thisCost = parentCost + in.action.cost.cost
      Schedule(
        id = in.action.requirement.originalRequirement.id,
        c = Math.ceil(thisCost).toLong,
        cs = in.children.map(c => createOut(c, thisCost))
      )
    }

    VesselSchedules(
      vesselId = vessel.id,
      schedules = in.map(s => createOut(s, 0))
    )
  }


  def walkSchedules[T](fn: Seq[Schedule] => T, schedule: Schedule, acc: Seq[Schedule] = Seq.empty): Seq[T] = schedule.cs match {
    case Nil => Seq(fn(acc :+ schedule))
    case x =>
      val newAcc = acc :+ schedule
      // Note that we must include the version of the schedule that terminates here
      fn(newAcc) +: x.flatMap(s => walkSchedules(fn, s, newAcc))
  }



  // Used by the Detailed Schedule Endpoint
  case class Requirement(id: String, cost: Double, actions: Seq[Action])
  case class State (endsAt: DateTime, fuelRemaining: Double, lastProduct: String, location: PortId)
  case class Action (`type`: String, cost: Double, speed: Double, startsAt: DateTime, endState: State)
  case class DetailedSchedule(vessel: VesselId, requirements: Seq[Requirement])

  implicit val StateType = deriveObjectType[Any, State]()
  implicit val ActionType = deriveObjectType[Any, Action]()
  implicit val RequirementType = deriveObjectType[Any, Requirement]()
  implicit val DetailedScheduleType = deriveObjectType[Any, DetailedSchedule]()
}
