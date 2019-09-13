package net.nextlogic.airsim.paper

object Structures {
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

}
