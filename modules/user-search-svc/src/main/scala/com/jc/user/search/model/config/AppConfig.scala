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
  prometheus: PrometheusConfig,
  jwt: JwtConfig)

object AppConfig {
  implicit lazy val appConfigReader = deriveReader[AppConfig]

  val getConfig: ZIO[Any, ConfigReaderException[Nothing], AppConfig] =
    ZIO.fromEither(ConfigSource.default.load[AppConfig]).mapError(ConfigReaderException.apply)
}
