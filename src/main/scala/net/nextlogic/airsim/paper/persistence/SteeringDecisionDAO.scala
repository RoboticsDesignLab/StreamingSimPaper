package net.nextlogic.airsim.paper.persistence

import akka.actor.{Actor, ActorLogging}
import akka.stream.alpakka.slick.javadsl.SlickSession
import net.nextlogic.airsim.paper.Structures.Vector3r

case class SteeringDecision(name: String, relativePosition: Vector3r,
                               myPosition: Vector3r, myPositionTime: Long,
                               opponentPosition: Vector3r, oppPositionTime: Long,
                               myTheta: Double, opponentTheta: Double,
                               phi: Double, time: Long = System.currentTimeMillis()) {

}

object SteeringDecisionDAO {

}

class SteeringDecisionDAO(session: SlickSession) extends Actor with ActorLogging {
  import session.profile.api._

  override def receive: Receive = {
    case s =>
  }
}


