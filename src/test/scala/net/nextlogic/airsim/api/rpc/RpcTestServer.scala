package net.nextlogic.airsim.api.rpc

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.{IO, Tcp}
import akka.io.Tcp.{Bind, Bound, Connected, ConnectionClosed, Received, Register, Write}
import akka.util.ByteStringBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import net.nextlogic.airsim.api.rpc.RpcConnectionHandler.CommandWithArgs
import org.msgpack.jackson.dataformat.MessagePackFactory

import scala.collection.JavaConverters._

class RpcConnectionManager(address: String, port: Int) extends Actor with ActorLogging {
  import context.system
  IO(Tcp) ! Bind(self, new InetSocketAddress(address, port))

  override def receive: Receive = {
    case Bound(local) =>
      log.info(s"Server started on $local")
    case Connected(remote, local) =>
      val handler = context.actorOf(Props[RpcConnectionHandler])
      log.info(s"New connnection: $local -> $remote")
      sender() ! Register(handler)
  }
}

object RpcConnectionHandler {
  case class CommandWithArgs(msgId: Int, command: String, args: java.util.ArrayList[Any])

  val state: java.util.Map[String, Int] = Map(
      "timestamp" -> 53959,
      "landed_state" -> 0
  ).asJava

  val responses = Map[String, Any](
    "ping" -> true,
    "getServerVersion" -> "Version 10.345",
    "getMultirotorState" -> state
  )

}

class RpcConnectionHandler extends Actor with ActorLogging {
  val mapper = new ObjectMapper(new MessagePackFactory())
  override def receive: Actor.Receive = {
    case Received(data) =>
      val decoded = mapper.readValue(data.toArray, classOf[Array[Any]])
      log.info(s"Received decoded: ${decoded.mkString(", ")}")
      val cmd = CommandWithArgs(
        decoded(1).asInstanceOf[Int],
        decoded(2).asInstanceOf[String],
        decoded(3).asInstanceOf[java.util.ArrayList[Any]]
      )
      val bytes = mapper.writeValueAsBytes(
        Array[Any](MsgPackRpcActor.RESPONSE, cmd.msgId, RpcConnectionHandler.responses.getOrElse(cmd.command, "Unknown Command!"))
      )
      val bs = new ByteStringBuilder()
        .putBytes(bytes)
        .result()
      log.info(s"BS bytes: ${bs.mkString(", ")}")
      sender() ! Write(bs)
    case message: ConnectionClosed =>
      println("Connection has been closed")
      context stop self
  }

}
