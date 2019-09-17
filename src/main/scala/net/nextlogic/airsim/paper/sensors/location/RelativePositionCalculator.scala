package net.nextlogic.airsim.paper.sensors.location

import net.nextlogic.airsim.paper.Structures.{Quaternionr, Vector3r}
import net.nextlogic.airsim.api.msgpack.MsgPackCodecs.CaseClassCodec5
import org.velvia.msgpack.CaseClassCodecs.CaseClassCodec2

case class RelativePosition(relPosition: Vector3r, eTheta: Double, pTheta: Double)
case class RelPosCalculatorWithPhi(calc: RelativePositionCalculator, phi: Double) {
  def toRelPosCalculatorQWithPhi = RelPosCalculatorQWithPhi(calc.toRelativePositionCalculatorWithQ, phi)
}


case class RelPosCalculatorQWithPhi(calc: RelativePositionCalculatorWithQ, phi: Double) {
  def toRelPosCalculatorWithPhi =
    RelPosCalculatorWithPhi(calc.toRelativePositionCalculator, phi)
}
object RelPosCalculatorQWithPhi {
  import RelativePositionCalculatorWithQ.rpcCodec
  import org.velvia.msgpack.SimpleCodecs.DoubleCodec

  implicit val rpcQCodec = new CaseClassCodec2[RelPosCalculatorQWithPhi, RelativePositionCalculatorWithQ, Double](RelPosCalculatorQWithPhi.apply, RelPosCalculatorQWithPhi.unapply)

}

case class RelativePositionCalculatorWithQ(name: String,
                                           eLocation: Quaternionr,
                                           pLocation: Quaternionr,
                                           eLocationTime: Long, pLocationTime: Long) {
  def toRelativePositionCalculator: RelativePositionCalculator =
    RelativePositionCalculator(
      name,
      Vector3r(eLocation.x, eLocation.y, eLocation.z), eLocation.w,
      Vector3r(pLocation.x, pLocation.y, pLocation.z), pLocation.w,
      eLocationTime, pLocationTime
    )
}
object RelativePositionCalculatorWithQ {
  import org.velvia.msgpack.SimpleCodecs._
  import org.velvia.msgpack.RawStringCodecs._
  import net.nextlogic.airsim.paper.Structures.qCodec

  implicit val rpcCodec: CaseClassCodec5[RelativePositionCalculatorWithQ, String, Quaternionr, Quaternionr, Long, Long] =
    new CaseClassCodec5[RelativePositionCalculatorWithQ, String, Quaternionr, Quaternionr, Long, Long](RelativePositionCalculatorWithQ.apply, RelativePositionCalculatorWithQ.unapply)
}


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

  def toRelativePositionCalculatorWithQ: RelativePositionCalculatorWithQ =
    RelativePositionCalculatorWithQ(
      name,
      Quaternionr(eLocation.x, eLocation.y, eLocation.z, eTheta),
      Quaternionr(pLocation.x, pLocation.y, pLocation.z, pTheta),
      eLocationTime, pLocationTime
    )
}
