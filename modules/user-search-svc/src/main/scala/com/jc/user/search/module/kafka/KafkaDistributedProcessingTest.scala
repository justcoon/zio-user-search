package com.jc.user.search.module.kafka

import com.jc.kafka.KafkaDistributedProcessing
import com.jc.user.search.model.config.KafkaConfig
import zio.kafka.consumer.ConsumerSettings
import zio.duration._
import eu.timepit.refined.auto._
import zio.{Schedule, ZIO}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.{Logger, Logging}

object KafkaDistributedProcessingTest {

  val distributionTestTopic = "c-distribution-test"

  def consumerSettings(config: KafkaConfig): ConsumerSettings = {
    ConsumerSettings(config.addresses)
      .withGroupId(s"user-search-dist-$distributionTestTopic")
      .withClientId("user-search-dist-client")
      .withCloseTimeout(30.seconds)
  }

  def distributionTest[R <: Clock with Blocking with Logging](config: KafkaConfig): ZIO[R, Throwable, Unit] = {
    val settings = consumerSettings(config)

    def process(partition: Int): ZIO[R, Throwable, Unit] = {
      for {
        logger <- ZIO.service[Logger[String]]
        _ <- logger.log(s"distributionTest - partition ${partition} - starting")
        _ <- logger
          .log(s"distributionTest - partition ${partition} - exec")
          .repeat(Schedule.fixed(10.seconds))
        _ <- logger.log(s"distributionTest - partition ${partition} - done")
      } yield ()
    }

    KafkaDistributedProcessing.start[R](distributionTestTopic, settings, process)
  }
}
