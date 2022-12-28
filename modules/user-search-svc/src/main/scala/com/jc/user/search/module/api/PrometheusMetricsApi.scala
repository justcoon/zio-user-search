package com.jc.user.search.module.api

import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response, Status}
import zio.{RIO, ZIO}
import zio.metrics.connectors.prometheus.PrometheusPublisher

object PrometheusMetricsApi {

  def httpRoutes[R <: PrometheusPublisher]: HttpRoutes[RIO[R, *]] = {
    import zio.interop.catz._
    val dsl = Http4sDsl[RIO[R, *]]
    import dsl._
    HttpRoutes.of[RIO[R, *]] { case GET -> Root / "metrics" =>
      ZIO.serviceWithZIO[PrometheusPublisher](
        _.get.map { res =>
          Response(Status.Ok).withEntity(res)
        }
      )
    }
  }
}
