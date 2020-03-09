package com.jc.user.search.module.kafka

import com.jc.user.domain.proto.UserPayloadEvent
import com.jc.user.search.model.config.KafkaConfig
import org.apache.kafka.common.header.Headers
import zio.RIO
import zio.duration._
import zio.kafka.consumer.Consumer.AutoOffsetStrategy
import zio.kafka.consumer.{Consumer, ConsumerSettings}
import zio.kafka.serde.Serde

object UserKafkaConsumer {

  def consumerSettings(config: KafkaConfig): ConsumerSettings = {
    ConsumerSettings(config.addresses)
      .withGroupId(s"user-search-${config.topic}")
      .withClientId("user-search-client")
      .withCloseTimeout(30.seconds)
      //      .withPollInterval(250.millis)
      //      .withPollTimeout(50.millis)
      //      .withPerPartitionChunkPrefetch(2)
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
  }

  val userEventDes: (String, Headers, Array[Byte]) => RIO[Any, UserPayloadEvent] = (_, _, data) =>
    RIO(UserPayloadEvent.parseFrom(data))

  val userEventSer: (String, Headers, UserPayloadEvent) => RIO[Any, Array[Byte]] = (_, _, event) =>
    RIO(event.toByteArray)

  val userEventSerde = Serde(userEventDes)(userEventSer)
}
