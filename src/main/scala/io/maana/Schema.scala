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
import sangria.marshalling.circe._
import com.typesafe.config.ConfigFactory
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

  //create case class for input types
  // can move these to separate objects and import them here
  @GraphQLDescription("Person")
  @GraphQLName("PersonInput")
  case class PersonInput(
      id: String,
      name: String,
  )
  //need to use ReplaceInputField to case to the ID type that Q requires
  implicit val PersonInputType = deriveInputObjectType[PersonInput](
    ReplaceInputField("id", InputField("id", OptionInputType(IDType)))
  )

  //output types

  @GraphQLDescription("Greeting")
  @GraphQLName("Greeting")
  case class Greeting(
      id: String,
      greeting: String,
  )
  implicit val ActionType = deriveObjectType[Any, Greeting](
    ReplaceField("id", Field("id", IDType, resolve = _.value.id))
  )

  //resovlvers

  trait Query {

    @GraphQLDescription("""
        Test resolver

        """.stripMargin)
    @GraphQLField
    def testResolver(
        person: PersonInput
    ): Greeting = Profile.prof("Query: testResolver") {

      Greeting(
        id = "greeting",
        greeting = "hello " + person.name
      )

    }
  }

  case object QueryImpl extends Query
  //case object MutationImpl extends Mutation

  //
  val QueryType: ObjectType[Any, Unit] = deriveContextObjectType[Any, Query, Unit](_ => QueryImpl)
  //val MutationType: ObjectType[Any, Unit] = deriveContextObjectType[Any, Mutation, Unit](_ => MutationImpl)

  val schema = sangria.schema.Schema[Any, Unit](QueryType) // Some(MutationType))
}
