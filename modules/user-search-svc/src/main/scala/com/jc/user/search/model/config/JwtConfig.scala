package com.jc.user.search.model.config

import pureconfig.generic.semiauto.deriveReader

case class JwtConfig(secret: String, expiration: Long, issuer: Option[String] = None)

object JwtConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[JwtConfig]
}
