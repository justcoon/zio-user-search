package com.jc.user.search.model.config

import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException
import pureconfig.generic.semiauto._
import zio.ZIO

final case class AppConfig(
  elasticsearch: ElasticsearchConfig,
  kafka: KafkaConfig,
  grpcApi: HttpApiConfig,
  restApi: HttpApiConfig,
  prometheus: PrometheusConfig)

object AppConfig {
  implicit lazy val elasticsearchConfigReader = deriveReader[ElasticsearchConfig]
  implicit lazy val kafkaConfigReader = deriveReader[KafkaConfig]
  implicit lazy val httpApiConfigReader = deriveReader[HttpApiConfig]
  implicit lazy val prometheusConfigReader = deriveReader[PrometheusConfig]
  implicit lazy val appConfigReader = deriveReader[AppConfig]

  val getConfig: ZIO[Any, ConfigReaderException[Nothing], AppConfig] =
    ZIO.fromEither(ConfigSource.default.load[AppConfig]).mapError(ConfigReaderException.apply)
}
