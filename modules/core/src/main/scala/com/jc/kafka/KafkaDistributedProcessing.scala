package com.jc.kafka

import org.apache.kafka.common.TopicPartition
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{Chunk, RManaged, ZIO}
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde
import zio.stream.ZStream

object KafkaDistributedProcessing {

  // https://github.com/notxcain/aecor/tree/master/modules/kafka-distributed-processing/src/main/scala/aecor/kafkadistributedprocessing
  def start[R <: Clock with Blocking](
    name: String,
    settings: ConsumerSettings,
    process: Int => ZIO[R, Throwable, Unit]) = {

    val consumer: RManaged[R, Consumer] = Consumer.make(settings)

    consumer.use[R, Throwable, Unit] { c =>
      val subscribe = c.subscribe(Subscription.topics(name))

      val run: ZIO[R, Throwable, Unit] =
        c.partitionedAssignmentStream[R, Array[Byte], Array[Byte]](Serde.byteArray, Serde.byteArray)
          .mapM { ch: Chunk[(TopicPartition, ZStream[R, Throwable, CommittableRecord[Array[Byte], Array[Byte]]])] =>
            ch.mapM { case (topicPartition, _) =>
              process(topicPartition.partition())
            }
          }
          .runDrain

      subscribe.flatMap(_ => run)
    }

  }

}
