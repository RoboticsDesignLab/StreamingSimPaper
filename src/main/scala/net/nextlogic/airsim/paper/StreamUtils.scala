package net.nextlogic.airsim.paper

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.routing.{RoundRobinPool, SmallestMailboxPool}
import akka.stream.{Attributes, KillSwitches, OverflowStrategy, SharedKillSwitch, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import net.nextlogic.airsim.api.rpc.{AirSimDataHandler, MsgPackRpcActor}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimMapResponse, AirSimRequest, RpcConnect}
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}
import net.nextlogic.airsim.paper.sensors.location.{RelPosCalculatorWithPhi, RelativePositionCalculator}
import net.nextlogic.airsim.paper.solvers.HCMertzSolver

import scala.concurrent.duration._

object StreamUtils {
  implicit val timeout = Timeout(500.millis)

  val sharedKillSwitch: SharedKillSwitch = KillSwitches.shared("stopAll")

  val decider: Supervision.Decider = {
    case _: AskTimeoutException => Supervision.Resume
    case _                      => Supervision.Stop
  }

  def setUpAndConnectAirSim(system: ActorSystem): ActorRef = {
    // !!!!!!!!!!! round robin has a way better performance than smallest mailbox !!!!!!!!!!!!
    val airSimPoolMaster = system.actorOf(SmallestMailboxPool(5).props( //SmallestMailboxPool
      MsgPackRpcActor.props(
        new InetSocketAddress(Constants.ip, Constants.port), system.actorOf(Props[AirSimDataHandler]))
    ), "airSimClientPool"
    )
    airSimPoolMaster ! akka.routing.Broadcast(RpcConnect) //("35.244.124.148", 41451)

    airSimPoolMaster
  }

  def locationsSource(name: String, airSimPoolMaster: ActorRef,
                      initDelay: FiniteDuration = 100.millis, delay: FiniteDuration = 100.millis): Source[LocationUpdate, Cancellable] =
    Source.tick(initDelay, delay, name)
      .via(sharedKillSwitch.flow)
      .via(locationFlow(name, airSimPoolMaster))
      .log("Getting Location: ")

  def locationFlow(name: String, airSimPoolMaster: ActorRef): Flow[String, LocationUpdate, NotUsed] =
    Flow[String]
      .map(name => AirSimRequest("simGetGroundTruthKinematics", Array(name)))
      .ask[AirSimMapResponse](parallelism = 4)(airSimPoolMaster)
      .map(AirsimUtils.getPosition)
      .map(p => LocationUpdate(name, p))

  def relativeDistanceFlow(relPositionActor: ActorRef): Flow[LocationUpdate, RelativePositionCalculator, NotUsed] =
    Flow[LocationUpdate]
      .ask[RelativePositionCalculator](relPositionActor)

  def calculateEvadePhiFlow: Flow[RelativePositionCalculator, RelPosCalculatorWithPhi, NotUsed] =
    Flow[RelativePositionCalculator]
      .map(rc => RelPosCalculatorWithPhi(rc, HCMertzSolver.evade(rc)))

  def calculatePursuePhiFlow: Flow[RelativePositionCalculator, RelPosCalculatorWithPhi, NotUsed] =
    Flow[RelativePositionCalculator]
      .map(rc => RelPosCalculatorWithPhi(rc, HCMertzSolver.pursue(rc)))

  val throttle = Flow[RelPosCalculatorWithPhi]
    .buffer(1, overflowStrategy = OverflowStrategy.dropHead)
    .throttle(1, 100.millis)


  def evadeAirSim(airSimPoolMaster: ActorRef): Sink[RelPosCalculatorWithPhi, NotUsed] =
    Flow[RelPosCalculatorWithPhi]
      .via(throttle)
      .map(rcPhi => AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.e, rcPhi.phi, Constants.eVelocity)))
      .to(Sink.foreach[AirSimRequest](r => airSimPoolMaster ? r))

  def pursueAirSim(airSimPoolMaster: ActorRef): Sink[RelPosCalculatorWithPhi, NotUsed] =
    Flow[RelPosCalculatorWithPhi]
      .via(throttle)
      .map(rcPhi => AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.p, rcPhi.phi, Constants.pVelocity)))
      .to(Sink.foreach[AirSimRequest](r => airSimPoolMaster ? r))

  def updateTheta(relPositionActor: ActorRef): Sink[RelPosCalculatorWithPhi, NotUsed] =
    Flow[RelPosCalculatorWithPhi]
      .map(rcPhi => ThetaUpdate(rcPhi.calc.name, rcPhi.phi))
      .to(Sink.foreach[ThetaUpdate](tu => relPositionActor ? tu))

  def streamLogger[A]: Flow[A, A, NotUsed] = Flow[A]
    .addAttributes(
      Attributes.logLevels(
        onElement = Attributes.LogLevels.Off,
        onFailure = Attributes.LogLevels.Error,
        onFinish = Attributes.LogLevels.Info)
    )


}
