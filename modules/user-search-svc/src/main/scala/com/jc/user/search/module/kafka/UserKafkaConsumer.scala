package com.jc.user.search.module.kafka

import com.jc.user.domain.proto.{DepartmentPayloadEvent, UserPayloadEvent}
import com.jc.user.search.model.config.KafkaConfig
import com.jc.user.search.module.processor.{DepartmentEventProcessor, UserEventProcessor}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{RIO, ZIO, ZLayer}
import zio.duration._
import zio.kafka.consumer.Consumer.AutoOffsetStrategy
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.{Deserializer, Serde}

object UserKafkaConsumer {

  def consumerSettings(config: KafkaConfig): ConsumerSettings = {
    import eu.timepit.refined.auto._
    ConsumerSettings(config.addresses)
      .withGroupId(s"user-search-${config.userTopic}")
      .withClientId("user-search-client")
      .withCloseTimeout(30.seconds)
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
  }

  sealed trait EventEnvelope[E] {
    def topic: String
    def event: E
  }

  final case class UserEventEnvelope(topic: String, event: UserPayloadEvent) extends EventEnvelope[UserPayloadEvent]

  final case class DepartmentEventEnvelope(topic: String, event: DepartmentPayloadEvent)
      extends EventEnvelope[DepartmentPayloadEvent]

  final case class UnknownEventEnvelope(topic: String, event: Array[Byte]) extends EventEnvelope[Array[Byte]]

  def eventDes(userTopic: String, departmentTopic: String): Deserializer[Any, EventEnvelope[_]] =
    Deserializer((topic, _, data) =>
      RIO {
        topic match {
          case t if (t == userTopic) => UserEventEnvelope(userTopic, UserPayloadEvent.parseFrom(data))
          case t if (t == departmentTopic) =>
            DepartmentEventEnvelope(departmentTopic, DepartmentPayloadEvent.parseFrom(data))
          case _ => UnknownEventEnvelope(topic, data)
        }
      })

  def live(config: KafkaConfig): ZLayer[Clock with Blocking, Throwable, UserKafkaConsumer] = {
    ZLayer.fromManaged(Consumer.make(consumerSettings(config)))
  }

  def consume(userTopic: String, departmentTopic: String) = {
    Consumer
      .subscribeAnd(Subscription.topics(userTopic, departmentTopic))
      .plainStream(Serde.string, UserKafkaConsumer.eventDes(userTopic, departmentTopic))
      .tap { cr =>
        cr.value match {
          case e: UserEventEnvelope =>
            ZIO.accessM[UserEventProcessor] {
              _.get.process(e.event)
            }
          case e: DepartmentEventEnvelope =>
            ZIO.accessM[DepartmentEventProcessor] {
              _.get.process(e.event)
            }
        }
      }
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapM(_.commit)
      .runDrain
  }
}
