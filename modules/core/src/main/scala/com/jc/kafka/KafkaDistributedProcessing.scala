package com.jc.kafka

import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.{Fiber, URIO, ZIO, ZRefM}
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.logging.{Logger, Logging}

object KafkaDistributedProcessing {

  // https://github.com/notxcain/aecor/tree/master/modules/kafka-distributed-processing/src/main/scala/aecor/kafkadistributedprocessing
  def start[R <: Clock with Blocking with Logging](
    name: String,
    settings: ConsumerSettings,
    process: Int => ZIO[R, Throwable, Unit]) = {

    val consumer = Consumer.make(settings)

    consumer.use[R, Throwable, Unit] { consumer =>
      val subscribe = consumer.subscribe(Subscription.topics(name))

      val run: ZIO[R, Throwable, Unit] = {
        ZIO.accessM[R] { env =>
          val logger = env.get[Logger[String]]

          def stopDaemon(partition: Int, daemon: URIO[R, Fiber.Runtime[Throwable, Unit]]): ZIO[R, Throwable, Unit] = {
            for {
              fiber <- daemon
              //                      _ <- fiber.interrupt
              timeout <- fiber.join.resurrect.ignore.disconnect.timeout(1.seconds)
              _ <- ZIO.when(timeout.isEmpty)(fiber.interruptFork)
              _ <- logger.debug(s"process - partition: ${partition} - stopped")
            } yield ()
          }

          def startDaemon(partition: Int): URIO[R, Fiber.Runtime[Throwable, Unit]] = {
            logger.debug(s"process - partition: ${partition} - starting") *>
              process(partition).forkDaemon
          }

//          val store = ZRefM
//            .make(Map.empty[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]])
//            .toManaged { stored =>
//              val res = for {
//                m <- stored.get
//                _ <- ZIO.collectAll(m.collect { case (partition, daemon) =>
//                  stopDaemon(partition, daemon)
//                })
//                _ <- logger.debug(s"process - partitions: ${m.keySet.mkString(",")} - stopped")
//              } yield ()
//
//              res.ignore
//            }

          ZRefM
            .make(Map.empty[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]])
            .flatMap { stored =>
              consumer
                .partitionedAssignmentStream[R, Array[Byte], Array[Byte]](Serde.byteArray, Serde.byteArray)
                .mapM { newPartitions =>
                  def getUpdated(stored: Map[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]]) = {
                    val assignedPartitions = newPartitions.map { case (topicPartition, _) =>
                      topicPartition.partition()
                    }.toSet

                    val removedPartitions = stored.keySet.filterNot(assignedPartitions.contains)

                    val remove = stored.collect {
                      case (partition, daemon) if (!assignedPartitions.contains(partition)) =>
                        stopDaemon(partition, daemon)
                    }

                    val update = assignedPartitions.map { partition =>
                      stored.get(partition) match {
                        case Some(daemon) => URIO.succeed(partition -> daemon)
                        case None =>
                          startDaemon(partition).map { d =>
                            partition -> URIO.succeed(d)
                          }
                      }
                    }

                    for {
                      _ <- logger.debug(s"process - partitions assigned: ${assignedPartitions.mkString(
                        ",")}, removing: ${removedPartitions.mkString(",")}")
                      _ <- ZIO.collectAll(remove)
                      updated <- ZIO.collectAll(update)
                    } yield {
                      updated.toMap
                    }
                  }

                  for {
                    _ <- stored.getAndUpdate(getUpdated)
                    m <- stored.get
                    _ <- logger.debug(s"process - partitions assigned: ${m.keySet.mkString(",")} - done")
                  } yield m
                }
                .runDrain
            }
        }
      }

      subscribe.flatMap(_ => run)
    }

  }

}
