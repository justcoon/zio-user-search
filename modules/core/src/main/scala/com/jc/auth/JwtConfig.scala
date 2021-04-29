package com.jc.auth

import eu.timepit.refined.types.numeric.PosLong
import eu.timepit.refined.types.string.NonEmptyString
import pureconfig.generic.semiauto.deriveReader

final case class JwtConfig(secret: NonEmptyString, expiration: PosLong, issuer: Option[NonEmptyString] = None)

object JwtConfig {
  import eu.timepit.refined.pureconfig._
  implicit lazy val configReader = deriveReader[JwtConfig]
}
