package com.jc.auth

import zio.test._
import zio.test.Assertion._
import pdi.jwt.JwtClaim
import zio.{RIO, ZIO, ZLayer}

import java.time.Clock
import zio.test.ZIOSpecDefault

object PdiJwtHelperSpec extends ZIOSpecDefault {
  import eu.timepit.refined.auto._

  implicit val clock: Clock = Clock.systemUTC

  val config = JwtConfig("mySecret", 604800000L, Some("zio-user-search"))
  val helper = ZLayer.succeed(new PdiJwtHelper(config))

  override def spec = suite("PdiJwtHelperSpec")(
    test("equal decoded jwt decoded token") {
      for {
        (authToken, token) <- getTestToken()
        authTokenDecoded <- decodeToken(token)
      } yield assert(authTokenDecoded.content)(equalTo(authToken))
    }.provideLayer(helper)
  )

  private def decodeToken(token: String): RIO[PdiJwtHelper, JwtClaim] = {
    ZIO.environmentWithZIO[PdiJwtHelper] { env =>
      ZIO.fromEither(env.get.decodeClaim(token).toEither)
    }
  }

  private def getTestToken(): RIO[PdiJwtHelper, (String, String)] = {
    ZIO.environment[PdiJwtHelper] { env =>
      val helper = env.get
      val authToken = "{}"
      val claim = helper.claim(authToken, subject = Some("test"), issuer = helper.config.issuer.map(_.value))
      val token = helper.encodeClaim(claim)

      authToken -> token
    }
  }
}
