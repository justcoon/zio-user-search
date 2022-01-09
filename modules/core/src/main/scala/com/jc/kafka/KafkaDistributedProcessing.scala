package com.jc.kafka

import org.apache.kafka.common.TopicPartition
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Chunk, Exit, Fiber, RManaged, URIO, ZIO}
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.logging.{Logger, Logging}
import zio.stream.ZStream

import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap

object KafkaDistributedProcessing {

  // https://github.com/notxcain/aecor/tree/master/modules/kafka-distributed-processing/src/main/scala/aecor/kafkadistributedprocessing
  def start[R <: Clock with Blocking with Logging](
    name: String,
    settings: ConsumerSettings,
    process: Int => ZIO[R, Throwable, Unit]) = {

    val consumer: RManaged[R, Consumer] = Consumer.make(settings)

    consumer.use[R, Throwable, Unit] { c =>
      val subscribe = c.subscribe(Subscription.topics(name))

      val store = new ConcurrentHashMap[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]]()

      val run: ZIO[R, Throwable, Unit] = {
        ZIO.accessM[R] { env =>
          val logger = env.get[Logger[String]]
          c.partitionedAssignmentStream[R, Array[Byte], Array[Byte]](Serde.byteArray, Serde.byteArray)
            .mapM {
              newPartitions: Chunk[(
                TopicPartition,
                ZStream[R, Throwable, CommittableRecord[Array[Byte], Array[Byte]]])] =>
                val ops = store.keys().asScala.toSet


                val nps = newPartitions.map { case (topicPartition, _) =>
                  topicPartition.partition()
                }.toSet

                val rps = ops.filterNot(nps.contains)

                lazy val removed: Set[ZIO[R, Throwable, Unit]] = rps.map { partition =>
                  Option(store.remove(partition)).fold[ZIO[R, Throwable, Unit]](ZIO.unit) { d =>
                    for {
                      fiber <- d
                      _ <- fiber.interrupt
                      _ <- logger.debug(s"process - partition: ${partition} - stopped")
                    } yield ()
                  }
                }

                lazy val updated: Set[URIO[R, Fiber.Runtime[Throwable, Unit]]] = nps.map { partition =>
                  store.computeIfAbsent(
                    partition,
                    { _ =>
                      logger.debug(s"process - partition: ${partition} - starting") *>
                        process(partition).forkDaemon
                    })
                }

                for {
                  _ <- logger.debug(s"process - partitions assigned: ${nps.mkString(",")}, removing: ${rps.mkString(",")}")
                  _ <- ZIO.collectAll(removed)
                  _ <- ZIO.collectAll(updated)
                } yield ()

            }
            .runDrain
        }
      }

      subscribe.flatMap(_ => run)
    }

  }

}
