package net.nextlogic.airsim.api.msgpack

import java.io.{DataInputStream => DIS, DataOutputStream}

import org.velvia.msgpack.{Codec, FastByteMap, Format}

object MsgPackCodecs {
  class TupleCodec5[A1: Codec, A2: Codec, A3: Codec, A4: Codec, A5: Codec]
    extends Codec[(A1, A2, A3, A4, A5)] {

    private val codec1 = implicitly[Codec[A1]]
    private val codec2 = implicitly[Codec[A2]]
    private val codec3 = implicitly[Codec[A3]]
    private val codec4 = implicitly[Codec[A4]]
    private val codec5 = implicitly[Codec[A5]]

    def pack(out: DataOutputStream, item: (A1, A2, A3, A4, A5)): Unit = {
      out.write(0x05 | Format.MP_FIXARRAY)
      codec1.pack(out, item._1)
      codec2.pack(out, item._2)
      codec3.pack(out, item._3)
      codec4.pack(out, item._4)
      codec5.pack(out, item._5)
    }

    val unpackFuncMap = FastByteMap[UnpackFunc](
      (0x05 | Format.MP_FIXARRAY).toByte -> { in: DIS =>
        val r1 = codec1.unpack(in)
        val r2 = codec2.unpack(in)
        val r3 = codec3.unpack(in)
        val r4 = codec4.unpack(in)
        val r5 = codec5.unpack(in)
        (r1, r2, r3, r4, r5)
      }
    )
  }

  class TupleCodec6[A1: Codec, A2: Codec, A3: Codec, A4: Codec, A5: Codec, A6: Codec]
    extends Codec[(A1, A2, A3, A4, A5, A6)] {

    private val codec1 = implicitly[Codec[A1]]
    private val codec2 = implicitly[Codec[A2]]
    private val codec3 = implicitly[Codec[A3]]
    private val codec4 = implicitly[Codec[A4]]
    private val codec5 = implicitly[Codec[A5]]
    private val codec6 = implicitly[Codec[A6]]

    def pack(out: DataOutputStream, item: (A1, A2, A3, A4, A5, A6)): Unit = {
      out.write(0x06 | Format.MP_FIXARRAY)
      codec1.pack(out, item._1)
      codec2.pack(out, item._2)
      codec3.pack(out, item._3)
      codec4.pack(out, item._4)
      codec5.pack(out, item._5)
      codec6.pack(out, item._6)
    }

    val unpackFuncMap = FastByteMap[UnpackFunc](
      (0x06 | Format.MP_FIXARRAY).toByte -> { in: DIS =>
        val r1 = codec1.unpack(in)
        val r2 = codec2.unpack(in)
        val r3 = codec3.unpack(in)
        val r4 = codec4.unpack(in)
        val r5 = codec5.unpack(in)
        val r6 = codec6.unpack(in)
        (r1, r2, r3, r4, r5, r6)
      }
    )
  }

  class TupleCodec7[A1: Codec, A2: Codec, A3: Codec, A4: Codec, A5: Codec, A6: Codec, A7: Codec]
    extends Codec[(A1, A2, A3, A4, A5, A6, A7)] {

    private val codec1 = implicitly[Codec[A1]]
    private val codec2 = implicitly[Codec[A2]]
    private val codec3 = implicitly[Codec[A3]]
    private val codec4 = implicitly[Codec[A4]]
    private val codec5 = implicitly[Codec[A5]]
    private val codec6 = implicitly[Codec[A6]]
    private val codec7 = implicitly[Codec[A7]]

    def pack(out: DataOutputStream, item: (A1, A2, A3, A4, A5, A6, A7)): Unit = {
      out.write(0x07 | Format.MP_FIXARRAY)
      codec1.pack(out, item._1)
      codec2.pack(out, item._2)
      codec3.pack(out, item._3)
      codec4.pack(out, item._4)
      codec5.pack(out, item._5)
      codec6.pack(out, item._6)
      codec7.pack(out, item._7)
    }

    val unpackFuncMap = FastByteMap[UnpackFunc](
      (0x07 | Format.MP_FIXARRAY).toByte -> { in: DIS =>
        val r1 = codec1.unpack(in)
        val r2 = codec2.unpack(in)
        val r3 = codec3.unpack(in)
        val r4 = codec4.unpack(in)
        val r5 = codec5.unpack(in)
        val r6 = codec6.unpack(in)
        val r7 = codec7.unpack(in)
        (r1, r2, r3, r4, r5, r6, r7)
      }
    )
  }

  class CaseClassCodec5[T, A1, A2, A3, A4, A5](
                                            apply: (A1, A2, A3, A4, A5) => T,
                                            unapply: T => Option[(A1, A2, A3, A4, A5)]
                                          )(
                                            implicit K1: Codec[A1],
                                            K2: Codec[A2],
                                            K3: Codec[A3],
                                            K4: Codec[A4],
                                            K5: Codec[A5]
                                          ) extends Codec[T] {
    val codec = new TupleCodec5[A1, A2, A3, A4, A5]
    val _apply = Function.tupled(apply)

    override def pack(out: DataOutputStream, item: T): Unit = {
      codec.pack(out, unapply(item).get)
    }
    val unpackFuncMap = codec.unpackFuncMap.mapValues(_.andThen(_apply))
  }

//  class CaseClassCodec6[T, A1, A2, A3, A4, A5, A6](
//                                            apply: (A1, A2, A3, A4, A5, A6) => T,
//                                            unapply: T => Option[(A1, A2, A3, A4, A5, A6)]
//                                          )(
//                                            implicit K1: Codec[A1],
//                                            K2: Codec[A2],
//                                            K3: Codec[A3],
//                                            K4: Codec[A4],
//                                            K5: Codec[A5],
//                                            K6: Codec[A6]
//                                          ) extends Codec[T] {
//    val codec = new TupleCodec6[A1, A2, A3, A4, A5, A6]
//    val _apply = Function.tupled(apply)
//
//    override def pack(out: DataOutputStream, item: T): Unit = {
//      codec.pack(out, unapply(item).get)
//    }
//    val unpackFuncMap = codec.unpackFuncMap.mapValues(_.andThen(_apply))
//  }
//
//
//  class CaseClassCodec7[T, A1, A2, A3, A4, A5, A6, A7](
//                                            apply: (A1, A2, A3, A4, A5, A6, A7) => T,
//                                            unapply: T => Option[(A1, A2, A3, A4, A5, A6, A7)]
//                                          )(
//                                            implicit K1: Codec[A1],
//                                            K2: Codec[A2],
//                                            K3: Codec[A3],
//                                            K4: Codec[A4],
//                                            K5: Codec[A5],
//                                            K6: Codec[A6],
//                                            K7: Codec[A7],
//                                          ) extends Codec[T] {
//    val codec = new TupleCodec7[A1, A2, A3, A4, A5, A6, A7]
//    val _apply = Function.tupled(apply)
//
//    override def pack(out: DataOutputStream, item: T): Unit = {
//      codec.pack(out, unapply(item).get)
//    }
//    val unpackFuncMap = codec.unpackFuncMap.mapValues(_.andThen(_apply))
//  }

}

