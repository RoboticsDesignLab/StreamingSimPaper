package net.nextlogic.airsim.paper

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.routing.SmallestMailboxPool
import akka.stream.{Attributes, KillSwitches, SharedKillSwitch, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import net.nextlogic.airsim.api.rpc.{AirSimDataHandler, MsgPackRpcActor}
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.{AirSimMapResponse, AirSimRequest, RpcConnect}
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}
import net.nextlogic.airsim.paper.sensors.location.RelativePositionCalculator
import net.nextlogic.airsim.paper.solvers.HCMertzSolver

import scala.concurrent.duration._

object StreamUtils {
  implicit val timeout = Timeout(50.millis)

  val sharedKillSwitch: SharedKillSwitch = KillSwitches.shared("stopAll")

  val decider: Supervision.Decider = {
    case _: AskTimeoutException => Supervision.Resume
    case _                      => Supervision.Stop
  }

  def setUpAndConnectAirSim(system: ActorSystem): ActorRef = {
    val airSimPoolMaster = system.actorOf(SmallestMailboxPool(5).props(
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
      .ask[AirSimMapResponse](airSimPoolMaster)
      .map(AirsimUtils.getPosition)
      .map(p => LocationUpdate(name, p))

  def relativeDistanceFlow(relPositionActor: ActorRef): Flow[LocationUpdate, RelativePositionCalculator, NotUsed] =
    Flow[LocationUpdate]
      .ask[RelativePositionCalculator](relPositionActor)

  def calculateEvadePhiFlow: Flow[RelativePositionCalculator, Double, NotUsed] =
    Flow[RelativePositionCalculator]
      .map(HCMertzSolver.evade)

  def calculatePursuePhiFlow: Flow[RelativePositionCalculator, Double, NotUsed] =
    Flow[RelativePositionCalculator]
      .map(HCMertzSolver.pursue)

  def evadeAirSim(airSimPoolMaster: ActorRef): Sink[Double, NotUsed] =
    Flow[Double]
      .map(phi => AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.e, phi, Constants.eVelocity)))
      .to(Sink.foreach[AirSimRequest](r => airSimPoolMaster ? r))

  def pursueAirSim(airSimPoolMaster: ActorRef): Sink[Double, NotUsed] =
    Flow[Double]
      .map(phi => AirSimRequest("moveByVelocityZ", AirsimUtils.moveByVelocityZArgs(Constants.p, phi, Constants.pVelocity)))
      .to(Sink.foreach[AirSimRequest](r => airSimPoolMaster ? r))

  def updateTheta(name: String, relPositionActor: ActorRef): Sink[Double, NotUsed] = Flow[Double]
    .map(phi => ThetaUpdate(name, phi))
    .to(Sink.foreach[ThetaUpdate](tu => relPositionActor ? tu))

  def streamLogger[A]: Flow[A, A, NotUsed] = Flow[A]
    .addAttributes(
      Attributes.logLevels(
        onElement = Attributes.LogLevels.Off,
        onFailure = Attributes.LogLevels.Error,
        onFinish = Attributes.LogLevels.Info)
    )


}
