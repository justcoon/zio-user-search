package com.jc.user.search.module.kafka

import com.jc.user.domain.proto.{DepartmentPayloadEvent, UserPayloadEvent}
import com.jc.user.search.model.config.KafkaConfig
import com.jc.user.search.module.processor.EventProcessor
import zio._
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.{Deserializer, Serde}
import eu.timepit.refined.auto._

object KafkaConsumer {

  def consumerSettings(config: KafkaConfig): ConsumerSettings = {
    ConsumerSettings(config.addresses)
      .withGroupId(s"user-search-${config.userTopic}")
      .withClientId("user-search-client")
      .withCloseTimeout(30.seconds)
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(Consumer.AutoOffsetStrategy.Earliest))
  }

  def eventDes(config: KafkaConfig): Deserializer[Any, EventProcessor.EventEnvelope[_]] = {
    Deserializer((topic, _, data) =>
      ZIO.succeed {
        if (topic == config.userTopic.value) {
          EventProcessor.EventEnvelope.User(topic, UserPayloadEvent.parseFrom(data))
        } else if (topic == config.departmentTopic.value) {
          EventProcessor.EventEnvelope.Department(topic, DepartmentPayloadEvent.parseFrom(data))
        } else EventProcessor.EventEnvelope.Unknown(topic, data)
      })
  }

  def make(config: KafkaConfig): ZLayer[Any, Throwable, Consumer] = {
    ZLayer.scoped(Consumer.make(consumerSettings(config)))
  }

  def consume(config: KafkaConfig) = {
    Consumer
      .subscribeAnd(Subscription.topics(config.userTopic, config.departmentTopic))
      .plainStream(Serde.string, KafkaConsumer.eventDes(config))
      .groupedWithin(50, 5.seconds)
      .tap { crs =>
        EventProcessor.process(crs.map(_.value))
      }
      .flattenChunks
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapZIO(_.commit)
      .runDrain
  }
}
