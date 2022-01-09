package com.jc.kafka

import org.apache.kafka.common.TopicPartition
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Chunk, Exit, Fiber, RManaged, URIO, ZIO}
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.stream.ZStream

import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap

object KafkaDistributedProcessing {

  // https://github.com/notxcain/aecor/tree/master/modules/kafka-distributed-processing/src/main/scala/aecor/kafkadistributedprocessing
  def start[R <: Clock with Blocking](
    name: String,
    settings: ConsumerSettings,
    process: Int => ZIO[R, Throwable, Unit]) = {

    val consumer: RManaged[R, Consumer] = Consumer.make(settings)

    consumer.use[R, Throwable, Unit] { c =>
      val subscribe = c.subscribe(Subscription.topics(name))

      val store = new ConcurrentHashMap[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]]()

      val run: ZIO[R, Throwable, Unit] =
        c.partitionedAssignmentStream[R, Array[Byte], Array[Byte]](Serde.byteArray, Serde.byteArray)
          .mapM {
            newPartitions: Chunk[(
              TopicPartition,
              ZStream[R, Throwable, CommittableRecord[Array[Byte], Array[Byte]]])] =>
              val ops = store.keys().asScala.toSet

              val nps = newPartitions.map { case (topicPartition, _) =>
                topicPartition.partition()
              }.toSet

              val removed: Set[ZIO[R, Throwable, Unit]] = ops.filterNot(nps.contains).map { partition =>
                Option(store.remove(partition)).fold[ZIO[R, Throwable, Unit]](ZIO.unit) {
                  p: URIO[R, Fiber.Runtime[Throwable, Unit]] =>
                    for {
                      fiber <- p
                      _ <- fiber.interrupt
                    } yield ()
                }
              }

              val updated: Set[URIO[R, Fiber.Runtime[Throwable, Unit]]] = nps.map { partition =>
                store.computeIfAbsent(
                  partition,
                  { partition =>
                    process(partition).forkDaemon
                  })
              }

              for {
                _ <- ZIO.collectAll(removed)
                _ <- ZIO.collectAll(updated)
              } yield ()

          }
          .runDrain

      subscribe.flatMap(_ => run)
    }

  }

}
