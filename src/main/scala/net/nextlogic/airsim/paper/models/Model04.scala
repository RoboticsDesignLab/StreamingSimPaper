package net.nextlogic.airsim.paper.models

import java.net.InetSocketAddress
import java.sql.Timestamp

import akka.NotUsed
import akka.pattern.ask
import akka.actor.{ActorSystem, Props}
import akka.routing.SmallestMailboxPool
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape, FanOutShape2, KillSwitches, OverflowStrategy}
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Broadcast, BroadcastHub, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import net.nextlogic.airsim.api.rpc.{AirSimDataHandler, MsgPackRpcActor}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimMapResponse, AirSimRequest, RpcConnect}
import net.nextlogic.airsim.paper.Structures.Vector3r
import net.nextlogic.airsim.paper.{AirsimUtils, Constants}
import net.nextlogic.airsim.paper.persistence.SteeringDecision
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}
import net.nextlogic.airsim.paper.sensors.location.{RelPosCalculatorWithPhi, RelativePosition, RelativePositionActor, RelativePositionCalculator}
import net.nextlogic.airsim.paper.solvers.HCMertzSolver
import net.nextlogic.airsim.paper.StreamUtils._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Model04 extends App {
  implicit val system = ActorSystem("paper-model-04")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(5.seconds)
  val run = new Timestamp(System.currentTimeMillis() / 1000 * 1000)



  implicit val session = SlickSession.forConfig("slick-postgres")
  system.registerOnTermination(() => session.close())
  import session.profile.api._

  val airSimPoolMaster = setUpAndConnectAirSim(system)

  val eRelPositionActor = system.actorOf(Props[RelativePositionActor], "relPositionEvader")
  val pRelPositionActor = system.actorOf(Props[RelativePositionActor], "relPositionPursuer")

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
  println("Init position reached...")

  var eTheta = Math.cos(0.5)
  var pTheta = 0.0

  eRelPositionActor ! ThetaUpdate(Constants.e, eTheta)

  val startTime = System.currentTimeMillis()

  val eLocationsE = locationsSource(Constants.e, airSimPoolMaster, 0.millis, 100.millis)
    .via(streamLogger[LocationUpdate])
  val pLocationsE = locationsSource(Constants.p, airSimPoolMaster, 0.millis, 100.millis)
    .via(streamLogger[LocationUpdate])

  val eSaveSD =
    Sink.foreach[RelPosCalculatorWithPhi](r =>
      steeringDecisions offer SteeringDecision(
        Constants.e, r.calc.relativePosition, r.calc.eLocation, r.calc.eLocationTime - startTime,
        r.calc.pLocation, r.calc.pLocationTime - startTime,
        r.calc.eTheta, r.calc.pTheta, r.phi,
        System.currentTimeMillis() - startTime
      )
    )

  val pSaveSD = Sink.foreach[RelPosCalculatorWithPhi](r =>
      steeringDecisions offer SteeringDecision(
        Constants.p, r.calc.relativePosition, r.calc.pLocation, r.calc.pLocationTime - startTime,
        r.calc.eLocation, r.calc.eLocationTime - startTime,
        r.calc.pTheta, r.calc.eTheta, r.phi,
        System.currentTimeMillis() - startTime
      )
  )

  val graph = RunnableGraph.fromGraph(
    GraphDSL.create(){implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[LocationUpdate](2))
      val broadcastRelDistance = builder.add(Broadcast[RelativePositionCalculator](outputPorts = 2))
      val eBroadcast = builder.add(Broadcast[RelPosCalculatorWithPhi](outputPorts = 3))
      val pBroadcast = builder.add(Broadcast[RelPosCalculatorWithPhi](outputPorts = 3))
      // unfiltered model overloads the AirSim when run without VPN
//      val eFilter = builder.add(Flow[RelativePositionCalculator].filter(_.name == Constants.e))
//      val pFilter = builder.add(Flow[RelativePositionCalculator].filter(_.name == Constants.p))

      eLocationsE ~> merge
      pLocationsE ~> merge

      merge ~> relativeDistanceFlow(eRelPositionActor) ~>
        broadcastRelDistance ~> calculateEvadePhiFlow ~> eBroadcast ~> evadeAirSim(airSimPoolMaster)
                                                                    eBroadcast ~> updateTheta(Constants.e, eRelPositionActor)
                                                                    eBroadcast  ~> eSaveSD
        broadcastRelDistance ~> calculatePursuePhiFlow ~> pBroadcast ~> pursueAirSim(airSimPoolMaster)
                                                                     pBroadcast ~> updateTheta(Constants.p, eRelPositionActor)
                                                                     pBroadcast ~> pSaveSD
      ClosedShape
    }
  )
  graph.run()

  system.scheduler.scheduleOnce(30.seconds) {
    sharedKillSwitch.shutdown()

    (airSimPoolMaster ? AirSimRequest("reset", Array())).foreach(_ => airSimPoolMaster ! akka.routing.Broadcast("close"))

    system.scheduler.scheduleOnce(500.millis){
      Await.result(system.terminate(), 1.second)
      System.exit(1)
    }
  }

  def setupPersistenceFlow(): SourceQueueWithComplete[SteeringDecision] = {
    val steeringDecisions = Source.queue[SteeringDecision](100, OverflowStrategy.dropHead)
      .via(Slick.flow(4, p =>
        sqlu"""INSERT INTO steering_decisions (label, run, name, time, rel_pos_x, rel_pos_y, my_pos_x, my_pos_y, my_pos_time, opp_pos_x, opp_pos_y, opp_pos_time, my_theta, opp_theta, phi) VALUES
                ('M04 VPN take 3',
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
