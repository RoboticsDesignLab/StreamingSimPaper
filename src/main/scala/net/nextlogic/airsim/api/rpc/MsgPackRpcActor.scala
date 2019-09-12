package net.nextlogic.airsim.api.rpc

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.io.{IO, Tcp}
import akka.util.{ByteString, ByteStringBuilder, Timeout}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimBooleanResponse, AirSimErrorResponse, AirSimMapResponse, AirSimNullResponse, AirSimRequest, AirSimResponseWithMsgId, AirSimStringResponse, RpcConnect}
import org.velvia.MsgPack

import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random


object MsgPackRpcActor {
  def props(remote: InetSocketAddress, listener: ActorRef) =
    Props(new MsgPackRpcActor(remote, listener))

  val REQUEST = 0
  val RESPONSE = 1
  val NOTIFY = 2

  def pack(a: Array[Any]): Array[Byte] = MsgPack.pack(a)

  def toByteString(bytes: Array[Byte]): ByteString = new ByteStringBuilder()
    .putBytes(bytes)
    .result()

  def packAndByteString(a: Array[Any]): ByteString = toByteString(pack(a))

  case object RpcConnect
  case class AirSimRequest(command: String, args: Array[Any])
  sealed trait AirSimResponse
  case class AirSimBooleanResponse(result: Boolean) extends AirSimResponse
  case class AirSimStringResponse(result: String) extends AirSimResponse
  case class AirSimMapResponse(result: Map[String, Any]) extends AirSimResponse
  case class AirSimIntResponse(result: Int) extends AirSimResponse
  case class AirSimErrorResponse(error: String) extends AirSimResponse
  case object AirSimNullResponse extends AirSimResponse

  case class AirSimResponseWithMsgId(msgId: Int, response: AirSimResponse)
}

class MsgPackRpcActor(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {
  import Tcp._
  import context.system
  import context.dispatcher

  val r: Random.type = scala.util.Random
  var senders: mutable.Map[Int, ActorRef] = mutable.Map()
  implicit val timeout = Timeout(2.second)

  override def receive: Receive = receiveDisconnected()

  def receiveDisconnected(): Receive = {
    case RpcConnect =>
      IO(Tcp) ! Connect(remote)

    case CommandFailed(_: Connect) =>
      listener ! "connect failed"
      log.error("Connect failed")
      val delay = r.nextInt(30).seconds

      log.info(s"Retrying connection in $delay seconds...")
      system.scheduler.scheduleOnce(delay) {
        IO(Tcp) ! Connect(remote)
      }
    // context stop self

    case c: Connected =>
      log.info("Connected")
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context.become(receiveConnected(connection))
  }

  def receiveConnected(connection: ActorRef): Receive = {
    case data: ByteString =>
      log.debug(s"Received data to be written: $data")
      connection ! Write(data)

    case cmd: AirSimRequest =>
      val msgid = uniqueMsgId
      log.debug(s"Received command: ${cmd.command} assigned msgid: $msgid (args: ${cmd.args.mkString(", ")})")
      senders.update(msgid, sender())
      val message = MsgPackRpcActor.packAndByteString(
        Array[Any](MsgPackRpcActor.REQUEST, msgid, cmd.command, cmd.args)
      )

      connection ! Write(message)

    case CommandFailed(w: Write) =>
      // O/S buffer was full
      listener ! "write failed"

    case Received(response) =>
      //log.debug(s"Received response from the socket: ${response.utf8String}")
      (listener ? response).mapTo[AirSimResponseWithMsgId]
        .map(r =>
          senders.remove(r.msgId)
            .map(s => s ! r.response)
            .getOrElse(log.error(s"Cannot find sender for msgId: ${r.msgId}"))
        )

    case "close" =>
      log.info("Closing connection...")
      connection ! Close
    case _: ConnectionClosed =>
      listener ! "connection closed"
      log.error("connection closed")

      context.become(receiveDisconnected())

    //      log.info("Retrying connection in 2 seconds...")
    //      system.scheduler.scheduleOnce(2.seconds) {
    //        IO(Tcp) ! Connect(remote)
    //      }


    // context stop self

  }

  def uniqueMsgId: Int = {
    val id = r.nextInt(Int.MaxValue)
    if (senders.get(id).isEmpty) id else uniqueMsgId
  }

}

class AirSimDataHandler extends Actor  with akka.actor.ActorLogging {
  import akka.io.Tcp._
  import org.velvia.msgpack.CollectionCodecs._
  import org.velvia.msgpack.SimpleCodecs._
  import org.velvia.msgpack._

//   val intSeqCodec = new SeqCodec[Int]
   // val anySeqCodec = new SeqCodec[Any]

  def receive: Receive = {
    case Received(data) =>
      sender() ! Write(data)
      log.debug(s"Received data in handler: $data")

    case response: ByteString =>
      // println(s"Received ByteString in handler: ${response.toArray.mkString(", ")}")
      val bytes = response.toArray
      // AirSim returns [type, msgID, error, result]
//      val decoded = mapper.readValue(bytes, classOf[Array[Any]])
      val decoded = MsgPack.unpack(bytes).asInstanceOf[Seq[Any]]
      log.debug(s"Decoded response: ${decoded.mkString(", ")}")
      // println(s"Class of result: ${mapAsScalaMap(decoded(3).asInstanceOf[java.util.LinkedHashMap[String, Any]])("kinematics_estimated").toString}")

      val msgId = decoded(1).asInstanceOf[Int]
      val error = decoded(2).asInstanceOf[String]
      if (error == null) {
        val resp = decoded(3) match {
          case r: Boolean => AirSimBooleanResponse(r)
          case r: String => AirSimStringResponse(r)
          case r: Map[String, Any] => AirSimMapResponse(r)
          case null => AirSimNullResponse
          case r => AirSimStringResponse(r.toString)
        }
        sender() ! AirSimResponseWithMsgId(msgId, resp)
      } else {
        log.error(error)
        sender () ! AirSimErrorResponse(error)
      }


    case data: String =>
      // println(s"Received string in handler:\n$data")
      log.warning(s"!!!Received string instead of data in handler: $data")

      if (data == "connection closed") {
        log.error("connection closed")
        // context stop self

      }

    case PeerClosed  => context stop self
  }
}
