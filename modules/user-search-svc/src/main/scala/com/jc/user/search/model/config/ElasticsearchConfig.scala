package com.jc.user.search.model.config

import pureconfig.generic.semiauto.deriveReader

final case class ElasticsearchConfig(addresses: Addresses, userIndexName: IndexName, departmentIndexName: IndexName)

object ElasticsearchConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[ElasticsearchConfig]
}
