package net.nextlogic.airsim.api.rpc

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimBooleanResponse, AirSimMapResponse, AirSimRequest, AirSimStringResponse, RpcConnect}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActorSpec.TestySender
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
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

  "Concurrent AirSimClient" should {
    "handle queries from multiple senders" in {
      val testy = system.actorOf(Props(classOf[TestySender], client), "testy")
      (1 to 10).foreach(i => testy ! s"Test $i")

      client ! AirSimRequest("ping", Array())
      expectMsg(AirSimBooleanResponse(true))
    }
  }
}

object MsgPackRpcActorSpec {
  class TestySender(client: ActorRef) extends Actor {
    implicit val timeout = Timeout(1.second)
    import context.dispatcher

    override def receive: Receive = {
      case _ => (client ? AirSimRequest("getServerVersion", Array("Pursuer"))).mapTo[AirSimStringResponse].map(r =>
        assert(r.result == RpcConnectionHandler.responses("getServerVersion").asInstanceOf[String] )
      )
    }
  }
}
