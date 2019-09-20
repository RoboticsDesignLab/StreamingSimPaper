package net.nextlogic.airsim.paper.models

import java.sql.Timestamp

import akka.Done
import akka.actor.{ActorSystem, Props}
import akka.kafka.{ConsumerMessage, ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, ClosedShape, OverflowStrategy}
import akka.stream.alpakka.slick.javadsl.SlickSession
import akka.stream.alpakka.slick.scaladsl.Slick
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import akka.pattern.ask
import net.nextlogic.airsim.api.rpc.MsgPackRpcActor.AirSimRequest
import net.nextlogic.airsim.paper.{AirsimUtils, Constants}
import net.nextlogic.airsim.paper.StreamUtils.{decider, evadeAirSim, locationsSource, pursueAirSim, relativeDistanceFlow, setUpAndConnectAirSim, sharedKillSwitch, streamLogger, updateTheta}
import net.nextlogic.airsim.paper.persistence.SteeringDecision
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}
import net.nextlogic.airsim.paper.sensors.location.{RelPosCalculatorWithPhi, RelativePositionActor, RelativePositionCalculator}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, BytesSerializer, StringSerializer}
import org.velvia.MsgPack
import org.velvia.msgpack._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Model05 extends App {
  implicit val system = ActorSystem("paper-model-04")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  val resetProducerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
  val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)

  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(5.seconds)
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
  println("Init position reached...")

  val startTime = System.currentTimeMillis()

  val eLocations = locationsSource(Constants.e, airSimPoolMaster, 0.millis, 100.millis)
    .via(streamLogger[LocationUpdate])
  val pLocations = locationsSource(Constants.p, airSimPoolMaster, 50.millis, 100.millis)
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
  val toMessagePack = Flow[LocationUpdate]
    .map(lu => pack(lu))
  val toProducerRecord = Flow[Array[Byte]]
    .map(msg => new ProducerRecord[Array[Byte], Array[Byte]]("locationUpdates", msg))
  val kafkaSerializer = Producer.plainSink(producerSettings)

  Source.single("reset")
    .map(msg => new ProducerRecord[String, String]("resetUpdates", msg))
    .to(Producer.plainSink(resetProducerSettings))

  val producerGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit  builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[LocationUpdate](2))

      eLocations ~> merge
      pLocations ~> merge

      merge ~> toMessagePack ~> toProducerRecord ~> kafkaSerializer

      ClosedShape
    }
  )

  val actionsSource = Consumer.committableSource(consumerSettings, Subscriptions.topics("actionUpdates"))
  val unpackCalculator = Flow[ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]]]
    .map(_.record.value())
    .map(bytes => unpack[RelPosCalculatorWithPhi](bytes))
    .log("received action")

  val consumerGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[RelPosCalculatorWithPhi](outputPorts = 2))
      val eB = builder.add(Broadcast[RelPosCalculatorWithPhi](outputPorts = 2))
      val pB = builder.add(Broadcast[RelPosCalculatorWithPhi](outputPorts = 2))
      val eFilter = builder.add(Flow[RelPosCalculatorWithPhi]
        .filter(_.calc.name == Constants.e)
        .buffer(1, overflowStrategy = OverflowStrategy.dropHead)
        .throttle(1, 100.millis)
      )
      val pFilter = builder.add(Flow[RelPosCalculatorWithPhi]
        .filter(_.calc.name == Constants.p)
        .buffer(1, overflowStrategy = OverflowStrategy.dropHead)
        .throttle(50, 100.millis)
      )


      actionsSource ~> unpackCalculator ~> broadcast ~> eFilter ~> eB ~> evadeAirSim(airSimPoolMaster)
                                                                   eB ~> eSaveSD
                                           broadcast ~> pFilter ~> pB ~> pursueAirSim(airSimPoolMaster)
                                                                   pB ~> pSaveSD

      ClosedShape
    }
  )

  producerGraph.run()
  consumerGraph.run()

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
                ('Model 05 - delay 50, ml1, round robin',
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
