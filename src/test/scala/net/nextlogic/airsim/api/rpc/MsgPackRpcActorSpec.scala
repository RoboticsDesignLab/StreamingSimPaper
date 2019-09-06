package net.nextlogic.airsim.api.rpc

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.ConfigFactory
import net.nextlogic.airsim.api.AirSimClientActor
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimBooleanResponse, AirSimMapResponse, AirSimRequest, AirSimStringResponse, RpcConnect}
import net.nextlogic.rpc.Server.system
import net.nextlogic.rpc.TCPConnectionManager
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scala.collection.JavaConverters._

class MsgPackRpcActorSpec extends TestKit(
  ActorSystem("msg-pack-actor-spec", ConfigFactory.load().getConfig("interceptingLogMessages"))
)
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {


  val server = system.actorOf(Props(classOf[RpcConnectionManager], "localhost", 10000), "server")
  val handler = system.actorOf(Props[AirSimDataHandler], "handler")
  val client = system.actorOf(MsgPackRpcActor.props(new InetSocketAddress("127.0.0.1", 10000), handler), "client")
  client ! RpcConnect

  val mapper = new ObjectMapper(new MessagePackFactory())

  override def beforeAll(): Unit = {

  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

//  import BasicSpec._

  "AirSimClient" should {
    "connect and disconnect" in {
      EventFilter.info(message = "Closing connection...", occurrences = 1) intercept {
        client ! "close"
      }
      EventFilter.info(message = "Connected", occurrences = 1) intercept {
        client ! RpcConnect
      }
    }

    "send and handle a boolean message" in {
      client ! AirSimRequest("ping", Array())
      expectMsg(AirSimBooleanResponse(true))
    }

    "send and handle a string message" in {
      client ! AirSimRequest("getServerVersion", Array())
      expectMsg(AirSimStringResponse(RpcConnectionHandler.responses("getServerVersion").asInstanceOf[String]))
    }

    "send and handle a command returning a map" in {
      client ! AirSimRequest("getMultirotorState", Array("Evader"))
      val mapResp = RpcConnectionHandler.state
      expectMsg(
        AirSimMapResponse(mapResp.asScala.toMap)
      )
    }
  }
}
