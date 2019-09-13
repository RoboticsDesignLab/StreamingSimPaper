package net.nextlogic.airsim.paper.sensors.location

import net.nextlogic.airsim.paper.Structures.Vector3r

case class RelativePosition(relPosition: Vector3r, eTheta: Double, pTheta: Double)
case class RelPosCalculatorWithPhi(calc: RelativePositionCalculator, phi: Double)

case class RelativePositionCalculator(name: String, eLocation: Vector3r, eTheta: Double,
                                      pLocation: Vector3r, pTheta: Double,
                                      eLocationTime: Long = 0, pLocationTime: Long = 0) {
  def relativePosition: Vector3r = relativePosTo2D(pLocation, eLocation, pTheta)

  def relativePosTo2D(myPosition: Vector3r, otherPos: Vector3r, theta: Double): Vector3r = {
    val x = (otherPos.x - myPosition.x) * Math.cos(theta) + (otherPos.y - myPosition.y) * Math.sin(theta)
    val y = -(otherPos.x - myPosition.x) * Math.sin(theta) + (otherPos.y - myPosition.y) * Math.cos(theta)
    // println(s"(${otherPos.x} - ${myPosition.x}) x ${Math.cos(theta)} + (${otherPos.y} - ${myPosition.y}) * ${Math.sin(theta)} = ${x}")
    Vector3r(x, y)
  }

}
