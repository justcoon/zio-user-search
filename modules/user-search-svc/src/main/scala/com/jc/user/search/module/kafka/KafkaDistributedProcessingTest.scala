package com.jc.user.search.module.kafka

import com.jc.kafka.KafkaDistributedProcessing
import com.jc.user.search.model.config.KafkaConfig
import zio.kafka.consumer.Consumer.AutoOffsetStrategy
import zio.kafka.consumer.{Consumer, ConsumerSettings}
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
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
  }

  def distributionTest[R <: Clock with Blocking with Logging](config: KafkaConfig) = {
    val settings = consumerSettings(config)

    def process(partition: Int): ZIO[R, Throwable, Unit] = {
      ZIO.accessM[R] { env =>
        val logger = env.get[Logger[String]]
        logger.log(s"distributionTest - partition ${partition} - starting") >>>
          logger
            .log(s"distributionTest - partition ${partition} - exec")
            .repeat(Schedule.duration(10.seconds))
            .provide(env) >>>
          ZIO.effect(())
      }
    }

    KafkaDistributedProcessing.start[R](distributionTestTopic, settings, process)
  }
}
