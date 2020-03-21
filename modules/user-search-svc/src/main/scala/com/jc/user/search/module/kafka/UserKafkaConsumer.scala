package com.jc.user.search.module.kafka

import com.jc.user.domain.proto.UserPayloadEvent
import com.jc.user.search.model.config.KafkaConfig
import com.jc.user.search.module.processor.UserEventProcessor
import org.apache.kafka.common.header.Headers
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{RIO, ZIO, ZLayer}
import zio.duration._
import zio.kafka.consumer.Consumer.AutoOffsetStrategy
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde

object UserKafkaConsumer {

  def consumerSettings(config: KafkaConfig): ConsumerSettings = {
    import eu.timepit.refined.auto._
    ConsumerSettings(config.addresses)
      .withGroupId(s"user-search-${config.topic}")
      .withClientId("user-search-client")
      .withCloseTimeout(30.seconds)
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
  }

  val userEventDes: (String, Headers, Array[Byte]) => RIO[Any, UserPayloadEvent] = (_, _, data) =>
    RIO(UserPayloadEvent.parseFrom(data))

  val userEventSer: (String, Headers, UserPayloadEvent) => RIO[Any, Array[Byte]] = (_, _, event) =>
    RIO(event.toByteArray)

  val userEventSerde = Serde(userEventDes)(userEventSer)

  def live(config: KafkaConfig): ZLayer[Clock with Blocking, Throwable, UserKafkaConsumer] = {
    Consumer.make(consumerSettings(config), Serde.string, UserKafkaConsumer.userEventSerde)
  }

  def consume(userKafkaTopic: String) = {
    Consumer
      .subscribeAnd[Any, String, UserPayloadEvent](Subscription.topics(userKafkaTopic))
      .plainStream
      .flattenChunks
      .tap { cr =>
        ZIO.accessM[UserEventProcessor] {
          _.get.process(cr.value)
        }
      }
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapM(_.commit)
      .runDrain
  }
}
