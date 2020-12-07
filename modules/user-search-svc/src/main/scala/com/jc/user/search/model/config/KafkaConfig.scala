package com.jc.user.search.model.config

import pureconfig.generic.semiauto.deriveReader

case class KafkaConfig(addresses: Addresses, userTopic: TopicName, departmentTopic: TopicName)

object KafkaConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[KafkaConfig]
}
