package net.nextlogic.airsim.paper.model01

import java.net.InetSocketAddress
import java.sql.Timestamp

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.routing.{Broadcast, SmallestMailboxPool}
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import net.nextlogic.airsim.api.rpc.{AirSimDataHandler, MsgPackRpcActor}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimRequest, RpcConnect}
import net.nextlogic.airsim.paper.persistence.SteeringDecision
import net.nextlogic.airsim.paper.sensors.location.RelativePosition
import net.nextlogic.airsim.paper.solvers.HCMertzSolver
import net.nextlogic.airsim.paper.{AirsimUtils, Constants}

import scala.concurrent.Await
import scala.concurrent.duration._

object Model01 extends App {
  implicit val system = ActorSystem("paper-model-01")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1.seconds)
  val run = new Timestamp(System.currentTimeMillis() / 1000 * 1000)

  implicit val session = SlickSession.forConfig("slick-postgres")
  system.registerOnTermination(() => session.close())
  import session.profile.api._

  val airSimPoolMaster = system.actorOf(SmallestMailboxPool(5).props(
    MsgPackRpcActor.props(
      new InetSocketAddress(Constants.ip, Constants.port), system.actorOf(Props[AirSimDataHandler]))
  ), "airSimClientPool"
  )
  airSimPoolMaster ! Broadcast(RpcConnect) //("35.244.124.148", 41451)
  Thread.sleep(1000)

  val steeringDecisions = setupPersistenceFlow()

  airSimPoolMaster ? AirSimRequest("simPause", Array(false))

  Seq(Constants.e, Constants.p).foreach{name =>
    (airSimPoolMaster ? AirSimRequest("enableApiControl", Array(true, name)))
      .map(_ => airSimPoolMaster ? AirSimRequest("takeoff", Array(1, name)))
  }
  Thread.sleep(1000)

  airSimPoolMaster ? AirSimRequest("moveToPosition", AirsimUtils.moveToPositionArgs(Constants.e, Constants.eInitialPosition))
  airSimPoolMaster ? AirSimRequest("moveToPosition", AirsimUtils.moveToPositionArgs(Constants.p, Constants.pInitialPosition))

  Thread.sleep(Constants.initialPositionWait)

  airSimPoolMaster ? AirSimRequest("simPause", Array(true))
  Thread.sleep(100)

  var eTheta = Math.cos(0.5)
  var pTheta = 0.0

  val startTime = System.currentTimeMillis()

  (1 to 300).foreach{i =>
    val eLocationFuture = airSimPoolMaster ? AirSimRequest("simGetGroundTruthKinematics", Array(Constants.e))
    val eLocation = AirsimUtils.getPosition(eLocationFuture)
    val eLocationTime = System.currentTimeMillis()
    val pLocationFuture = airSimPoolMaster ? AirSimRequest("simGetGroundTruthKinematics", Array(Constants.p))
    val pLocation = AirsimUtils.getPosition(pLocationFuture)
    val pLocationTime = System.currentTimeMillis()
    val relPos = RelativePosition(eLocation, eTheta, pLocation, pTheta)

    val ePhi = HCMertzSolver.evade(relPos)
    val pPhi = HCMertzSolver.pursue(relPos)

    steeringDecisions offer SteeringDecision(Constants.e, relPos.pRelativePosition, eLocation,
      eLocationTime, pLocation, pLocationTime, eTheta, pTheta, ePhi, System.currentTimeMillis() - startTime)
    steeringDecisions offer SteeringDecision(Constants.p, relPos.pRelativePosition, pLocation,
      pLocationTime, eLocation, eLocationTime, pTheta, eTheta, pPhi, System.currentTimeMillis() - startTime)

    eTheta = ePhi
    pTheta = pPhi

    airSimPoolMaster ? AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.e, eTheta, Constants.eVelocity))
    airSimPoolMaster ? AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.p, pTheta, Constants.pVelocity))

    (airSimPoolMaster ? AirSimRequest("simContinueForTime", Array(Constants.stepLength / 1000.0))).map{_ =>
      Thread.sleep(Constants.stepLength)
    }


  }

  airSimPoolMaster ? AirSimRequest("reset", Array())

  system.scheduler.scheduleOnce(100.millis){
    Await.result(system.terminate(), 1.second)
    System.exit(1)
  }

  def setupPersistenceFlow(): SourceQueueWithComplete[SteeringDecision] = {
    val steeringDecisions = Source.queue[SteeringDecision](100, OverflowStrategy.dropHead)
      .via(Slick.flow(4, p =>
        sqlu"""INSERT INTO steering_decisions (label, run, name, time, rel_pos_x, rel_pos_y, my_pos_x, my_pos_y, my_pos_time, opp_pos_x, opp_pos_y, opp_pos_time, my_theta, opp_theta, phi) VALUES
                ('Model 01',
                  $run, ${p.name}, ${p.time}, ${p.relativePosition.x}, ${p.relativePosition.y},
                  ${p.myPosition.x}, ${p.myPosition.y}, ${p.myPositionTime},
                  ${p.opponentPosition.x}, ${p.opponentPosition.y}, ${p.oppPositionTime},
                  ${p.myTheta}, ${p.opponentTheta}, ${p.phi}
                  )""")
      )
      .to(Sink.ignore)
      .run()

    steeringDecisions
  }

}
