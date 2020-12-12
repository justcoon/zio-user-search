package com.jc.user.search.model.config

import eu.timepit.refined.types.net.NonSystemPortNumber
import pureconfig.generic.semiauto.deriveReader

final case class PrometheusConfig(port: NonSystemPortNumber)

object PrometheusConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[PrometheusConfig]
}
