package com.jc.user.search.module

import zio.Has
import zio.kafka.consumer.Consumer

package object kafka {
  type KafkaConsumer = Has[Consumer]
}
