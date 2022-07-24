package com.jc.user.search.module.api

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.RIO

object HealthCheckApi {

  def httpRoutes[R <: Any]: HttpRoutes[RIO[R, *]] = {
    import zio.interop.catz._
    healthCheckRoutes
  }

  private def healthCheckRoutes[F[_]: Sync]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of[F] {
      case GET -> Root / "ready" =>
        Ok("OK")
      case GET -> Root / "alive" =>
        Ok("OK")
    }
  }
}
