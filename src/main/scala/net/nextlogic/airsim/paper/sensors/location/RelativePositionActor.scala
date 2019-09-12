package net.nextlogic.airsim.paper.sensors.location

import akka.actor.{Actor, ActorLogging}
import net.nextlogic.airsim.paper.Constants
import net.nextlogic.airsim.paper.Structures.Vector3r
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}

import scala.collection.mutable

object RelativePositionActor {
  case class LocationUpdate(name: String, loc: Vector3r)
  case class ThetaUpdate(name: String, theta: Double)
  case object GetRelativePosition
}

class RelativePositionActor extends Actor with ActorLogging {
  var locs: mutable.Map[String, Vector3r] = mutable.Map[String, Vector3r]()
  var thetas: mutable.Map[String, Double] = mutable.Map[String, Double]()

  override def receive: Receive = {
    case LocationUpdate(name, loc) =>
      log.debug(s"Received loc update: $name: $loc")
      locs.update(name, loc)

      val relativePosition = RelativePositionCalculator(
        name,
        locs.getOrElse(Constants.e, Vector3r()), thetas.getOrElse(Constants.e, 0.0),
        locs.getOrElse(Constants.p, Vector3r()), thetas.getOrElse(Constants.p, 0.0)
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
