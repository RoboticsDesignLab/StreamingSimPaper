package net.nextlogic.airsim.paper.sensors.location

import net.nextlogic.airsim.paper.Structures.{Quaternionr, Vector3r}
import net.nextlogic.airsim.api.msgpack.MsgPackCodecs.{CaseClassCodec5, CaseClassCodec7}
import org.velvia.msgpack.CaseClassCodecs.CaseClassCodec2

case class RelativePosition(relPosition: Vector3r, eTheta: Double, pTheta: Double)
case class RelPosCalculatorWithPhi(calc: RelativePositionCalculator, phi: Double)
object RelPosCalculatorWithPhi {
  import org.velvia.msgpack.SimpleCodecs._
  import RelativePositionCalculator.rpcCodec

  implicit val rpcPhiCodec: CaseClassCodec2[RelPosCalculatorWithPhi, RelativePositionCalculator, Double] =
    new CaseClassCodec2[RelPosCalculatorWithPhi, RelativePositionCalculator, Double](RelPosCalculatorWithPhi.apply, RelPosCalculatorWithPhi.unapply)
}

object RelativePositionCalculator {
  import org.velvia.msgpack.SimpleCodecs._
  import org.velvia.msgpack.RawStringCodecs._

  implicit val rpcCodec: CaseClassCodec7[RelativePositionCalculator, String, Vector3r, Double, Vector3r, Double, Long, Long] =
    new CaseClassCodec7[RelativePositionCalculator, String, Vector3r, Double, Vector3r, Double, Long, Long](RelativePositionCalculator.apply, RelativePositionCalculator.unapply)
}
case class RelativePositionCalculator(name: String,
                                      eLocation: Vector3r, eTheta: Double,
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
