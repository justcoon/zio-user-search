package com.jc.user.search.module.auth

import java.time.Clock

import com.jc.user.search.model.config.JwtConfig
import io.circe.{Decoder, Encoder}
import pdi.jwt._
import zio.ZLayer

import scala.util.Try

object JwtAuthenticator {
  val AuthHeader = "Authorization"

  trait Service {
    def authenticated(rawToken: String): Option[String]
  }

  final case class PdiJwtAuthenticator(helper: PdiJwtHelper, clock: Clock) extends Service {

    override def authenticated(rawToken: String): Option[String] =
      for {
        claim <- helper.decodeClaim(rawToken).toOption
        subject <- if (claim.isValid(clock)) {
          claim.subject
        } else None
      } yield subject

  }

  def live(config: JwtConfig): ZLayer[Any, Nothing, JwtAuthenticator] = {
    val helper = new PdiJwtHelper(config)
    ZLayer.fromFunction(_ => PdiJwtAuthenticator(helper, Clock.systemUTC()))
  }
}

class PdiJwtHelper(val config: JwtConfig) {

  def claim[T: Encoder](
    content: T,
    issuer: Option[String] = None,
    subject: Option[String] = None,
    audience: Option[Set[String]] = None
  )(implicit clock: Clock): JwtClaim = {
    import io.circe.syntax._
    val jsContent = content.asJson.noSpaces

    JwtClaim(content = jsContent, issuer = issuer.orElse(config.issuer), subject = subject, audience = audience).issuedNow
      .expiresIn(config.expiration)
  }

  def encodeClaim(claim: JwtClaim): String =
    JwtCirce.encode(claim, config.secret, PdiJwtHelper.Algorithm)

  def encode[T: Encoder](
    content: T,
    issuer: Option[String] = None,
    subject: Option[String] = None,
    audience: Option[Set[String]] = None
  )(implicit clock: Clock): String =
    encodeClaim(claim(content, issuer, subject, audience))

  def decodeClaim(rawToken: String): Try[JwtClaim] =
    JwtCirce.decode(rawToken, config.secret, Seq(PdiJwtHelper.Algorithm), JwtOptions(expiration = false))

  def decode[T: Decoder](rawToken: String): Try[(JwtClaim, T, String)] =
    for {
      claim <- decodeClaim(rawToken)
      content <- io.circe.parser.decode[T](claim.content).toTry
    } yield (claim, content, rawToken)

}

object PdiJwtHelper {
  val Algorithm = JwtAlgorithm.HS512
}
