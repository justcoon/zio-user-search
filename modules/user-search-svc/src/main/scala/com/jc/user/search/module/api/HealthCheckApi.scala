package com.jc.user.search.module.api

import cats.effect.{ContextShift, Sync}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.ZIO

object HealthCheckApi {

  def httpRoutes[E <: Any]: HttpRoutes[ZIO[E, Throwable, *]] = {
    import zio.interop.catz._
    healthCheckRoutes
  }

  private def healthCheckRoutes[F[_]: ContextShift: Sync]: HttpRoutes[F] = {
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
