package net.nextlogic.airsim.paper

import org.velvia.msgpack.CaseClassCodecs.{CaseClassCodec3, CaseClassCodec4}

object Structures {
  import org.velvia.msgpack.SimpleCodecs.DoubleCodec

  case class Vector3r(x: Double = 0f, y: Double = 0f, z: Double = 0f) {
    override def toString: String = s"[$x, $y, $z]"

    def toMap: Map[String, Double] = Map("x_val" -> x, "y_val" -> y, "z_val" -> z)

    def distance(other: Vector3r): Double = {
      val xDiff = other.x - this.x
      val yDiff = other.y - this.y
      val zDiff = other.z - this.z

      Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff)
    }

    def distance2D(other: Vector3r): Double = {
      val xDiff = other.x - this.x
      val yDiff = other.y - this.y

      Math.sqrt(xDiff * xDiff + yDiff * yDiff)
    }
  }

  implicit val vCodec = new CaseClassCodec3[Vector3r, Double, Double, Double](Vector3r.apply, Vector3r.unapply)

  case class Quaternionr(x: Double = 0f, y: Double = 0f, z: Double = 0f, w: Double = 1f) {
    override def toString: String = s"[$x, $y, $z, $w]"

    def distance2D(other: Quaternionr): Double = {
      val xDiff = other.x - this.x
      val yDiff = other.y - this.y

      Math.sqrt(xDiff * xDiff + yDiff * yDiff)
    }

    // https://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToEuler/indexLocal.htm
    def roll: Double = Math.atan2(
      2.0 * (y * z + x * w),(-x * x - y * y + z * z + w * w)
    )

    def pitch: Double = Math.asin(-2.0 * (x * z - y * w))

    def yaw: Double = Math.atan2(
      2.0 * (x * y + z * w), x * x - y * y - z * z + w * w
    )
  }

  implicit val qCodec = new CaseClassCodec4[Quaternionr, Double, Double, Double, Double](Quaternionr.apply, Quaternionr.unapply)

}
