package com.jc.auth

import zio.test._
import zio.test.Assertion._
import pdi.jwt.JwtClaim
import zio.{Has, RIO, ZIO, ZLayer}
import zio.test.DefaultRunnableSpec

import java.time.Clock

object PdiJwtHelperSpec extends DefaultRunnableSpec {
  import eu.timepit.refined.auto._

  implicit val clock: Clock = Clock.systemUTC

  val config = JwtConfig("mySecret", 604800000L, Some("zio-user-search"))
  val helper = ZLayer.succeed(new PdiJwtHelper(config))

  override def spec = suite("PdiJwtHelperSpec")(
    testM("equal decoded jwt decoded token") {
      for {
        (authToken, token) <- getTestToken()
        authTokenDecoded <- decodeToken(token)
      } yield assert(authTokenDecoded.content)(equalTo(authToken))
    }.provideLayer(helper)
  )

  private def decodeToken(token: String): RIO[Has[PdiJwtHelper], JwtClaim] = {
    ZIO.accessM[Has[PdiJwtHelper]] { env =>
      RIO.fromEither(env.get.decodeClaim(token).toEither)
    }
  }

  private def getTestToken(): RIO[Has[PdiJwtHelper], (String, String)] = {
    ZIO.access[Has[PdiJwtHelper]] { env =>
      val helper = env.get
      val authToken = "{}"
      val claim = helper.claim(authToken, subject = Some("test"), issuer = helper.config.issuer.map(_.value))
      val token = helper.encodeClaim(claim)

      authToken -> token
    }
  }
}
