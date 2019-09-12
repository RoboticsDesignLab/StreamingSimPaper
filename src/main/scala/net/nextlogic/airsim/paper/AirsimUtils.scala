package net.nextlogic.airsim.paper

import akka.util.Timeout
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.AirSimMapResponse
import net.nextlogic.airsim.paper.Structures.Vector3r

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object AirsimUtils {
  implicit val timeout = Timeout(1.seconds)

  def moveToPositionArgs(name: String, position: Vector3r): Array[Any] =
    Array(
      position.x, position.y, position.z, Constants.pVelocity, Constants.simTimeOut,
      Constants.driveTrain, Constants.yaw, Constants.lookahead, Constants.adaptiveLookahead,
      name
    )

  def moveByVelocityZArgs(name: String, theta: Double, maxVelocity: Double): Array[Any] = {
    val velocity = Vector3r(
      (maxVelocity * Math.cos(theta)).toFloat,
      (maxVelocity * Math.sin(theta)).toFloat,
      Constants.altitude
    )

    Array(
      velocity.x, velocity.y, velocity.z, Constants.simTimeOut,
      Constants.driveTrain, Constants.yaw, name
    )
  }


  def getPositionBlocking(kinematics: Future[Any]): Vector3r = {
    val r = Await.result(kinematics, timeout.duration).asInstanceOf[AirSimMapResponse]
    val p = r.result("position").asInstanceOf[Map[String, Float]]
    // println(p)

    Vector3r(p("x_val"), p("y_val"), p("z_val"))
  }


}
