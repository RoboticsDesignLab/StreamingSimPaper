package net.nextlogic.airsim.paper

import net.nextlogic.airsim.paper.Structures.Vector3r

object Constants {
//  val ip = "35.197.172.171"
  val ip = "10.10.0.82"
  val port = 41451
  val e = "Evader"
  val p = "Pursuer"

  val simTimeOut = 10.0f

  val eVelocity = 5.0
  val pVelocity = 10.0

  val turningRadius = 8.0

  val altitude = -20.0

  val stepLength = 100

  val eInitialPosition = Vector3r(12.8, 0, altitude)
  val pInitialPosition = Vector3r(0, 0, altitude)
  val initialPositionWait = 10000

  val yaw = Map("is_rate" -> true, "yaw_or_rate" -> 0.0)
  val driveTrain = 0
  val lookahead = -1
  val adaptiveLookahead = 1

  val timeStepForAngleChange = 0.08
}
