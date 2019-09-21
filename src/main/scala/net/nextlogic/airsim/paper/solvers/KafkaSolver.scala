package net.nextlogic.airsim.paper.solvers

import akka.actor.{ActorSystem, Props}
import akka.kafka.{ConsumerMessage, ConsumerSettings, ProducerSettings, Subscriptions}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import net.nextlogic.airsim.paper.Constants
import net.nextlogic.airsim.paper.StreamUtils._
import net.nextlogic.airsim.paper.sensors.location.{RelPosCalculatorWithPhi, RelativePositionActor, RelativePositionCalculator}
import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.{LocationUpdate, ThetaUpdate}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import org.velvia.msgpack._

object KafkaSolver extends App {
  implicit val system: ActorSystem = ActorSystem("consumer-sample")
  implicit val materializer: Materializer = ActorMaterializer()

  import net.nextlogic.airsim.paper.sensors.location.RelativePositionActor.luCodec

  val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)

  Consumer.committableSource(consumerSettings, Subscriptions.topics("resetUpdates"))
    .map(_ => relPositionActor ! ThetaUpdate(Constants.e, Math.cos(0.5)))
    .map(_ => relPositionActor ! ThetaUpdate(Constants.p, 0))
    .runWith(Sink.ignore)

  val actionsSource = Consumer.committableSource(consumerSettings, Subscriptions.topics("locationUpdates"))
  val toLocationUpdate = Flow[ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]]]
    .map(_.record.value())
    .map(bytes => unpack[LocationUpdate](bytes))

  val relPositionActor = system.actorOf(Props[RelativePositionActor], "relPositionActor")
  relPositionActor ! ThetaUpdate(Constants.e, Math.cos(0.5))

  val toMessagePack = Flow[RelPosCalculatorWithPhi]
    .map(calc => pack(calc))
  val toProducerRecord = Flow[Array[Byte]]
    .map(msg => new ProducerRecord[Array[Byte], Array[Byte]]("actionUpdates", msg))
  val kafkaSerializer = Producer.plainSink(producerSettings)

  // TODO the first message is delayed by about 1s - need to do some warm-up messages first https://stackoverflow.com/questions/53728258/kafka-producer-is-slow-on-first-message
  val solver = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcastRelDistance = builder.add(Broadcast[RelativePositionCalculator](outputPorts = 2))
      val broadCastResult = builder.add(Broadcast[RelPosCalculatorWithPhi](outputPorts = 2))
      val merge = builder.add(Merge[RelPosCalculatorWithPhi](inputPorts = 2))

      actionsSource ~> toLocationUpdate ~> relativeDistanceFlow(relPositionActor) ~>
        broadcastRelDistance ~> calculateEvadePhiFlow ~> merge
        broadcastRelDistance ~> calculatePursuePhiFlow ~> merge
      merge ~> broadCastResult ~> toMessagePack ~> toProducerRecord ~> kafkaSerializer
               broadCastResult ~> updateTheta(relPositionActor)


      ClosedShape
    }
  ).run()


}
