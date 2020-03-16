package com.jc.user.search.model.config

import pureconfig.generic.semiauto.deriveReader

case class ElasticsearchConfig(addresses: Addresses, indexName: IndexName)

object ElasticsearchConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[ElasticsearchConfig]
}
