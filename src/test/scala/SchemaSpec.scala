package io.maana

import org.scalatest.{Matchers, WordSpec}

import sangria.ast.Document
import sangria.macros._
import sangria.execution.Executor
import sangria.execution.deferred.DeferredResolver
import sangria.marshalling.circe._

import io.circe._
import io.circe.parser._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class SchemaSpec extends WordSpec with Matchers {
}
