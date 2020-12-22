package io.maana

import scala.language.postfixOps
import io.maana.Queries.VesselDimensions
import io.maana.Schema.{Port, PortMap, SchemaError }
import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import com.github.jarlakxen.drunk.GraphQLClient


//only deals with ports and distances 
object Shared {
  val portsToCache = 10

  implicit val executionContext = Schema.executionContext


  // not a distance at all, just some measure of closeness for sorting ports
  def veryApproximateDistanceSQNoUnits(from: (Double, Double), to: (Double, Double)) : Double = {
    val dLat = to._1 - from._1
    val dLon = to._2 - from._2
    dLat*dLat * dLon*dLon
  }

  def GCDistanceNM(from: (Double, Double), to: (Double, Double)) : Double = {
    val AVERAGE_RADIUS_OF_EARTH_NM = 3440
    val latDistance = Math.toRadians(from._1 - to._1)
    val lngDistance = Math.toRadians(from._2 - to._2)
    val sinLat = Math.sin(latDistance / 2)
    val sinLng = Math.sin(lngDistance / 2)
    val a = sinLat * sinLat + (Math.cos(Math.toRadians(from._1)) * Math.cos(Math.toRadians(to._1))  * sinLng * sinLng)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    AVERAGE_RADIUS_OF_EARTH_NM * c
  }




  def AwaitWithRetry[T](in: Future[T], fn: => Unit , retries: Int = 3): T = {
    if (retries == 0) {
      throw SchemaError("Failed: Inetrnal Error - Could not read data from servers")
    } else {
      try {
        Await.result(in, 100.seconds)
      } catch { case e: Throwable => synchronized{
        try  {
          // Could have been done on another thread
          Await.result(in, 100.seconds)
        } catch { case e: Throwable =>
          println(s"WARNING - query failed - $e - retrying in 1s")
          Thread.sleep(1000)
          fn
          AwaitWithRetry(in, fn, retries - 1)
        }
      }}
    }

  }

  var portMapF : java.util.concurrent.atomic.AtomicReference[Future[PortMap]] = new java.util.concurrent.atomic.AtomicReference(null)
  def portMap : PortMap =  AwaitWithRetry(portMapF.get, getPorts(_))


//  def getContracts: Unit =



  def getPorts(client: GraphQLClient): Unit = {
    // Ports
    println("getting ports")
    val portsS: Future[Seq[Port]] = Queries.ports(client).map { ps =>
      // Cache the 10 closest neighboring ports for refueling calculation - if port can't provide refueling
      val refuelingPorts = ps.filter(_.canRefuel).toVector
      // Note only care for ports that can't provide refueling
      val res = ps.filterNot(_.canRefuel).map { p =>
        println(p.id)
        if (!p.canRefuel) {
          val l0 = (p.latitude, p.longitude)
          val neighbors = refuelingPorts
            .sortBy { p1 =>
              val l1 = (p1.latitude, p1.longitude)
              GCDistanceNM(l0, l1)
            }.take(portsToCache)

          p.copy(neighbors = neighbors)
        } else {
          p
        }
      }
      val out = res ++ refuelingPorts
      out
    }

    

  

    val f = portsS.map { ports =>
      ports.map { p =>
        p.id -> p
      }.toMap
    }

    portMapF.set(f)
  }





  def reloadCaches(client: GraphQLClient): Unit = synchronized {
    
    getPorts(client)
  }




  val cacheCheckThread = new Runnable {
    def run() = while(true) {

      // Check the cached result is valid every 10s
      // println(s"Checking cache")
      // ensure the existing requests are valid and re-request if they fail
      try {
        portMap
      } catch { case e : Throwable =>
        println(s"Failed to get valid port data: $e")

      }

      Thread.sleep(1000)
    }
  }

  def init(client: GraphQLClient): Unit = Profile.prof("Shared: init"){
    println("Reading static data")

    reloadCaches(client)
    //println(client)

    try {
//    Must get results to continue
//      Await.result(portMapF.zip(vesselDimensionMapF), 100.seconds)
      println("Ports  requested")
    } catch {case e : Throwable=>
      println(s"ERROR  - $e \nFailed to fetch preloaded data exiting in 10s docker will restart.")
      Thread.sleep(10000)
      System.exit(-1)
    }

    executionContext.execute(cacheCheckThread)
  }
}
