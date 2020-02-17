package c.user.search.module.kafka

import c.user.domain.proto.UserPayloadEvent
import c.user.search.model.config.KafkaConfig
import org.apache.kafka.common.header.Headers
import zio.RIO
import zio.duration._
import zio.kafka.client.Consumer.AutoOffsetStrategy
import zio.kafka.client.serde.Serde
import zio.kafka.client.{Consumer, ConsumerSettings}

trait UserKafkaConsumer {
  def userKafkaConsumer: Consumer
  def userKafkaTopic: String
}

object UserKafkaConsumer {

  def consumerSettings(config: KafkaConfig): ConsumerSettings = {
    ConsumerSettings(
      bootstrapServers = config.addresses,
      groupId = s"user-search-${config.topic}",
      clientId = "user-search-client",
      closeTimeout = 30.seconds,
      extraDriverSettings = Map(),
      pollInterval = 250.millis,
      pollTimeout = 50.millis,
      perPartitionChunkPrefetch = 2,
      offsetRetrieval = Consumer.OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest)
    )
  }

  val userEventDes: (String, Headers, Array[Byte]) => RIO[Any, UserPayloadEvent] = (_, _, data) =>
    RIO(UserPayloadEvent.parseFrom(data))

  val userEventSer: (String, Headers, UserPayloadEvent) => RIO[Any, Array[Byte]] = (_, _, event) =>
    RIO(event.toByteArray)

  val userEventSerde = Serde(userEventDes)(userEventSer)
}
