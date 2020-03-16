package com.jc.user.search.model.config

import eu.timepit.refined.types.net.NonSystemPortNumber
import pureconfig.generic.semiauto.deriveReader

case class HttpApiConfig(address: IpAddress, port: NonSystemPortNumber)

object HttpApiConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[HttpApiConfig]
}
