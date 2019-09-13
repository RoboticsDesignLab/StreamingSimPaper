package net.nextlogic.airsim.paper.models

import java.net.InetSocketAddress
import java.sql.Timestamp

import akka.pattern.ask
import akka.actor.{ActorSystem, Props}
import akka.routing.{Broadcast, SmallestMailboxPool}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import net.nextlogic.airsim.api.rpc.{AirSimDataHandler, MsgPackRpcActor}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimRequest, RpcConnect}
import net.nextlogic.airsim.paper.StreamUtils.setUpAndConnectAirSim
import net.nextlogic.airsim.paper.{AirsimUtils, Constants}
import net.nextlogic.airsim.paper.persistence.SteeringDecision
import net.nextlogic.airsim.paper.sensors.location.RelativePositionCalculator
import net.nextlogic.airsim.paper.solvers.HCMertzSolver

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Model03 extends App {
  implicit val system = ActorSystem("paper-model-03")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(1.seconds)
  val run = new Timestamp(System.currentTimeMillis() / 1000 * 1000)

  implicit val session = SlickSession.forConfig("slick-postgres")
  system.registerOnTermination(() => session.close())
  import session.profile.api._

  val airSimPoolMaster = setUpAndConnectAirSim(system)
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

  // airSimPoolMaster ? AirSimRequest("simPause", Array(true))
  Thread.sleep(100)

  var eTheta = Math.cos(0.5)
  var pTheta = 0.0

  val startTime = System.currentTimeMillis()

  (1 to 300).foreach{i =>
    Future {
      val eLocationE = AirsimUtils.getPositionBlocking(airSimPoolMaster ? AirSimRequest("simGetGroundTruthKinematics", Array(Constants.e)))
      val eLocationTimeE = System.currentTimeMillis()
      val pLocationE = AirsimUtils.getPositionBlocking(airSimPoolMaster ? AirSimRequest("simGetGroundTruthKinematics", Array(Constants.p)))
      val pLocationTimeE = System.currentTimeMillis()
      val eRelPos = RelativePositionCalculator(Constants.e, eLocationE, eTheta, pLocationE, pTheta)

      val ePhi = HCMertzSolver.evade(eRelPos)

      steeringDecisions offer SteeringDecision(Constants.e, eRelPos.relativePosition, eLocationE,
        eLocationTimeE - startTime, pLocationE, pLocationTimeE - startTime,
        eTheta, pTheta, ePhi, System.currentTimeMillis() - startTime)

      eTheta = ePhi

      // moveByVelocity is a non-blocking call and returns after timeout provided
      // but we don't need to wait for the response - any other move request will interrupt the previous one
      airSimPoolMaster ? AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.e, eTheta, Constants.eVelocity))
    }

    Future {
      val eLocationP = AirsimUtils.getPositionBlocking(airSimPoolMaster ? AirSimRequest("simGetGroundTruthKinematics", Array(Constants.e)))
      val eLocationTimeP = System.currentTimeMillis()
      val pLocationP = AirsimUtils.getPositionBlocking(airSimPoolMaster ? AirSimRequest("simGetGroundTruthKinematics", Array(Constants.p)))
      val pLocationTimeP = System.currentTimeMillis()
      val pRelPos = RelativePositionCalculator(Constants.p, eLocationP, eTheta, pLocationP, pTheta)

      val pPhi = HCMertzSolver.pursue(pRelPos)

      steeringDecisions offer SteeringDecision(Constants.p, pRelPos.relativePosition, pLocationP,
        pLocationTimeP - startTime, eLocationP, eLocationTimeP - startTime,
        pTheta, eTheta, pPhi, System.currentTimeMillis() - startTime)

      pTheta = pPhi

      // moveByVelocity is a non-blocking call and returns after timeout provided
      // but we don't need to wait for the response - any other move request will interrupt the previous one
      airSimPoolMaster ? AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.p, pTheta, Constants.pVelocity))
    }

    Thread.sleep(Constants.stepLength)
  }

  (airSimPoolMaster ? AirSimRequest("reset", Array())).foreach(_ => airSimPoolMaster ! akka.routing.Broadcast("close"))

  system.scheduler.scheduleOnce(500.millis){
    Await.result(system.terminate(), 1.second)
    System.exit(1)
  }

  def setupPersistenceFlow(): SourceQueueWithComplete[SteeringDecision] = {
    val steeringDecisions = Source.queue[SteeringDecision](100, OverflowStrategy.dropHead)
      .via(Slick.flow(4, p =>
        sqlu"""INSERT INTO steering_decisions (label, run, name, time, rel_pos_x, rel_pos_y, my_pos_x, my_pos_y, my_pos_time, opp_pos_x, opp_pos_y, opp_pos_time, my_theta, opp_theta, phi) VALUES
                ('Model 03 VPN',
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
