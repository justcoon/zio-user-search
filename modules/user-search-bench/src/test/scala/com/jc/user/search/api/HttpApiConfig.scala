package com.jc.user.search.api

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.IPv4
import eu.timepit.refined.types.net.NonSystemPortNumber
import pureconfig.generic.semiauto.deriveReader

final case class HttpApiConfig(address: String Refined IPv4, port: NonSystemPortNumber)

object HttpApiConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[HttpApiConfig]
}
