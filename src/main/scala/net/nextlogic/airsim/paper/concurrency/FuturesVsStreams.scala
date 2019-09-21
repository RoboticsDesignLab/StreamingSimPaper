package net.nextlogic.airsim.paper.concurrency

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object FuturesVsStreams extends App {
  implicit val system = ActorSystem("FuturesVsStreams")
  implicit val materializer = ActorMaterializer()
  val rand = scala.util.Random

  println("Execution order of a series of futures is not guaranteed:")
  (1 to 10).foreach(i => Future{println(i)})

  Thread.sleep(1000)

  println("Order in async stream can be guaranteed:")
  Source(1 to 10)
    .mapAsync(parallelism = 10)(x => Future(x))
    .runWith(Sink.foreach(println))

  println("Futures sleeping random time:")
  (1 to 10).foreach(i => Future{
    val timeout = rand.nextInt(1000)
    Thread.sleep(timeout)
    println(s"Future of $i: slept for $timeout")
  })

  //Thread.sleep(10000)
  println("Same with streams:")
  Source(1 to 10)
    .mapAsync(parallelism = 10)(i => Future{
      val timeout = rand.nextInt(1000)
      Thread.sleep(timeout)
      (i, timeout)
    })
    .runWith(Sink.foreach(i => println(s"Stream Future of ${i._1}: slept for ${i._2}")))



}
