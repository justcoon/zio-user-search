package com.jc.user.search.module

import com.jc.user.domain.proto.UserPayloadEvent
import zio.kafka.consumer.Consumer

package object kafka {
  type UserKafkaConsumer = Consumer[Any, String, UserPayloadEvent]
}
