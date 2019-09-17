package net.nextlogic.airsim.api.rpc

import akka.testkit.TestKit
import net.nextlogic.airsim.paper.Structures.Vector3r
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.LocationUpdate
import org.scalatest.{FunSpec, Matchers, WordSpecLike}

import scala.util.Random

class MsgPackSpec extends FunSpec with Matchers {
  describe("case class packing and unpacking") {
    import org.velvia.msgpack.CaseClassCodecs._
    import org.velvia.msgpack._
    import org.velvia.msgpack.SimpleCodecs._

    import net.nextlogic.airsim.paper.Structures.vCodec
    import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.luCodec

    it("should pack and unpack case class of 2 parameters") {
      case class C2(a1: Int, a2: Int)
      val codec2 = new CaseClassCodec2[C2, Int, Int](C2.apply, C2.unapply)
      val c = C2(Random.nextInt(), Random.nextInt())
      val unpacked = unpack(pack(c)(codec2))(codec2)
      unpacked.getClass should equal (classOf[C2])
      unpacked should equal (c)
    }



    it ("should pack and unpack Vector3r") {
      val v = Vector3r(10.453, 11.345, 12.53)

      val packed = pack(v)(vCodec)
      val unpacked = unpack(packed)(vCodec)
      unpacked.getClass should equal(classOf[Vector3r])
      unpacked should equal (v)
    }

    it ("should pack and unpack LocationUpdate") {
      val u = LocationUpdate("Testy", Vector3r(10.453, 11.345, 12.53))
      val packed = pack(u)
      val unpacked = unpack[LocationUpdate](packed)
      unpacked.getClass should equal(classOf[LocationUpdate])
      unpacked should equal(u)
    }

  }

}
