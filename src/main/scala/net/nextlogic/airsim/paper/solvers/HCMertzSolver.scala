package net.nextlogic.airsim.paper.solvers

import net.nextlogic.airsim.paper.Constants
import net.nextlogic.airsim.paper.sensors.location.RelativePosition

object HCMertzSolver {

  def evade(relPos: RelativePosition): Double = {
    val minR = Constants.turningRadius
    val relPosition = relPos.eRelativePosition
    val x = relPosition.x
    val y = relPosition.y

    val phi =
      if ((x * x + (y - minR) * (y - minR)) < minR * minR) {
        // System.out.println("EVADER: In left turning circle")
        relPos.pTheta + Math.atan2(y + minR, x)
      }
      else if ((x * x + (y + minR) * (y + minR)) < minR * minR) {
        // System.out.println("EVADER: In right turning circle")
        relPos.pTheta + Math.atan2(y - minR, x)
      }
      else if (Math.hypot(x, y) < minR * 2) {
        // println("EVADER: Starting the turn...")
        relPos.pTheta + Math.atan2(y, x) + Math.PI / 2
      }
      else {
//        val newTheta = relPos.eTheta + Math.atan2(y, x) + Math.PI
        val newTheta = relPos.pTheta + Math.atan2(y, x)
        // println(s"EVADER:  relPos: ${relPosition} Running away, opp theta ${relPos.pTheta}, minR: $minR, new theta: $newTheta...")
        newTheta
      }

    phi
  }

  def pursue(relPos: RelativePosition): Double = {
    val relPosition = relPos.pRelativePosition
    val x = relPosition.x
    val y = relPosition.y

    val phi = y match {
      case 0 if (x < 0) => 1
      case 0 => 0
      case _ => Math.signum(y)
    }

    val dtheta = phi * (Constants.pVelocity / Constants.turningRadius)
    val newTheta = relPos.pTheta + dtheta * Constants.timeStepForAngleChange

    // println(s"PURUSER: rel position: ${relPosition}, current th: ${relPos.pTheta}, resulting phi: $phi, resulting theta: ${newTheta}")

    newTheta
  }

}
