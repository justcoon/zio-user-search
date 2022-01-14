package com.jc.kafka

import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.{Fiber, URIO, ZIO, ZRefM}
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.logging.{Logger, Logging}

object KafkaDistributedProcessing {

  def stopDaemon[R <: Clock with Logging](
    partition: Int,
    daemon: URIO[R, Fiber.Runtime[Throwable, Unit]],
    stopTimeout: Duration = 1.second): ZIO[R, Throwable, Unit] = {
    for {
      fiber <- daemon
      timeout <- fiber.join.resurrect.ignore.disconnect.timeout(stopTimeout)
      _ <- ZIO.when(timeout.isEmpty)(fiber.interruptFork)
      logger <- ZIO.service[Logger[String]]
      _ <- logger.debug(s"process - partition: ${partition} - stopped")
    } yield ()
  }

  def startDaemon[R <: Logging](
    partition: Int,
    process: ZIO[R, Throwable, Unit]): URIO[R, Fiber.Runtime[Throwable, Unit]] = {
    for {
      logger <- ZIO.service[Logger[String]]
      _ <- logger.debug(s"process - partition: ${partition} - starting")
      daemon <- process.ensuring(logger.debug(s"process - partition: ${partition} - stopping")).forkDaemon
    } yield daemon
  }

  def getUpdatedDaemons[R <: Clock with Logging](assignedPartitions: Set[Int], process: Int => ZIO[R, Throwable, Unit])(
    stored: Map[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]])
    : ZIO[R, Throwable, Map[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]]] = {
    val remove = stored.collect {
      case (partition, daemon) if (!assignedPartitions.contains(partition)) =>
        stopDaemon(partition, daemon)
    }

    val update = assignedPartitions.map { partition =>
      stored.get(partition) match {
        case Some(daemon) => URIO.succeed(partition -> daemon)
        case None =>
          startDaemon(partition, process(partition)).map(d => partition -> URIO.succeed(d))
      }
    }

    val removedPartitions = stored.keySet.filterNot(assignedPartitions.contains)

    for {
      logger <- ZIO.service[Logger[String]]
      _ <- logger.debug(
        s"process - partitions assigned: ${assignedPartitions.mkString(",")}, removing: ${removedPartitions.mkString(",")}")
      _ <- ZIO.collectAll(remove)
      updated <- ZIO.collectAll(update)
    } yield {
      updated.toMap
    }
  }

  // https://github.com/notxcain/aecor/tree/master/modules/kafka-distributed-processing/src/main/scala/aecor/kafkadistributedprocessing
  def start[R <: Clock with Blocking with Logging](
    name: String,
    settings: ConsumerSettings,
    process: Int => ZIO[R, Throwable, Unit]): ZIO[R, Throwable, Unit] = {

    val consumer = Consumer.make(settings)

    consumer.use[R, Throwable, Unit] { consumer =>
      val subscribe = consumer.subscribe(Subscription.topics(name))

      val run =
        ZRefM
          .make(Map.empty[Int, URIO[R, Fiber.Runtime[Throwable, Unit]]])
          .flatMap { store =>
            consumer
              .partitionedAssignmentStream[R, Array[Byte], Array[Byte]](Serde.byteArray, Serde.byteArray)
              .mapM { newPartitions =>
                val assignedPartitions = newPartitions.map { case (topicPartition, _) =>
                  topicPartition.partition()
                }.toSet

                for {
                  _ <- store.getAndUpdate(getUpdatedDaemons(assignedPartitions, process))
                  m <- store.get
                  logger <- ZIO.service[Logger[String]]
                  _ <- logger.debug(s"process - partitions assigned: ${m.keySet.mkString(",")} - done")
                } yield m
              }
              .runDrain
          }

      subscribe *> run
    }

  }

}
