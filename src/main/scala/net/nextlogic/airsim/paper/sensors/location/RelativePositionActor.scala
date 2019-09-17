package net.nextlogic.airsim.paper.sensors.location

import akka.actor.{Actor, ActorLogging}
import net.nextlogic.airsim.paper.Constants
import net.nextlogic.airsim.paper.Structures.Vector3r
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}

import scala.collection.mutable

object RelativePositionActor {
  import org.velvia.msgpack.CaseClassCodecs._
  import org.velvia.msgpack.RawStringCodecs._
  import net.nextlogic.airsim.paper.Structures.vCodec

  case class LocationUpdate(name: String, loc: Vector3r)
  case class ThetaUpdate(name: String, theta: Double)
  case object GetRelativePosition

  implicit val luCodec = new CaseClassCodec2[LocationUpdate, String, Vector3r](LocationUpdate.apply, LocationUpdate.unapply) //(StringCodec, vCodec)
}

class RelativePositionActor extends Actor with ActorLogging {
  var locs: mutable.Map[String, Vector3r] = mutable.Map[String, Vector3r]()
  var locUpdates: mutable.Map[String, Long] = mutable.Map[String, Long]()
  var thetas: mutable.Map[String, Double] = mutable.Map[String, Double]()

  override def receive: Receive = {
    case LocationUpdate(name, loc) =>
      log.info(s"Received loc update: $name: $loc")
      locs.update(name, loc)
      val time = System.currentTimeMillis()
      locUpdates.update(name, time)

      val relativePosition = RelativePositionCalculator(
        name,
        locs.getOrElse(Constants.e, Vector3r()), thetas.getOrElse(Constants.e, 0.0),
        locs.getOrElse(Constants.p, Vector3r()), thetas.getOrElse(Constants.p, 0.0),
        locUpdates.getOrElse(Constants.e, time), locUpdates.getOrElse(Constants.p, time )
      )
      // val rp = relativePosition.relativePositionWithThetas
      // log.debug(s"Rel distances: $rp")
      sender ! relativePosition

    case ThetaUpdate(name, theta) =>
      log.debug(s"Received theta update: $name: $theta")
      thetas.update(name, theta)

    case other => log.error(s"Received something else $other")
  }
}
