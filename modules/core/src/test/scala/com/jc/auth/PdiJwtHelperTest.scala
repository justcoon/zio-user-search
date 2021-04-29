package com.jc.auth

import pdi.jwt.JwtClaim
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Clock
import scala.util.{Success, Try}

class PdiJwtHelperTest extends AnyWordSpecLike with should.Matchers with BeforeAndAfterAll {
  import eu.timepit.refined.auto._

  implicit val clock: Clock = Clock.systemUTC

  val config = JwtConfig("mySecret", 604800000L, Some("zio-user-search"))
  val helper = new PdiJwtHelper(config)

  "token" must {
    "equal decoded jwt decoded token" in {

      val (authToken, token) = getTestToken(helper)

      val authTokenDecoded: Try[JwtClaim] = helper.decodeClaim(token)

      authTokenDecoded.map(_.content) shouldBe Success(authToken)
    }

  }

  private def getTestToken(helper: PdiJwtHelper, printToken: Boolean = true) = {

    val authToken = "{}"
    val claim = helper.claim(authToken, subject = Some("test"), issuer = helper.config.issuer.map(_.value))
    val token = helper.encodeClaim(claim)

    if (printToken) {
      println(claim.toJson)
      println(token)
    }

    authToken -> token
  }
}
