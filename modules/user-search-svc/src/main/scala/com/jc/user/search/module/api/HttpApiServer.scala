package com.jc.user.search.module.api

import caliban.{CalibanError, GraphQLInterpreter}
import com.jc.auth.JwtAuthenticator
import com.jc.logging.LoggingSystem
import com.jc.user.search.api.graphql.UserSearchGraphqlApi.UserSearchGraphqlApiInterpreter
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.UserSearchGraphqlApiRequestContext
import com.jc.user.search.model.config.HttpApiConfig
import com.jc.user.search.module.repo.{DepartmentSearchRepo, UserSearchRepo}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import org.http4s.server.{Router, Server}
import org.http4s.implicits._
import zio.interop.catz._
import zio.{RIO, ZIO, ZLayer}
import eu.timepit.refined.auto._
import zio.metrics.connectors.prometheus.PrometheusPublisher

object HttpApiServer {

  type ServerEnv = UserSearchRepo
    with DepartmentSearchRepo with LoggingSystem with UserSearchGraphqlApiService with UserSearchGraphqlApiInterpreter
    with JwtAuthenticator with PrometheusPublisher

  private def httpRoutes(
    interpreter: GraphQLInterpreter[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, CalibanError])
    : HttpRoutes[RIO[ServerEnv, *]] =
    Router[RIO[ServerEnv, *]](
      "/" -> UserSearchOpenApiHandler.httpRoutes[ServerEnv],
      "/" -> UserSearchGraphqlApiHandler.graphqlRoutes[ServerEnv](interpreter),
      "/" -> HealthCheckApi.httpRoutes,
      "/" -> PrometheusMetricsApi.httpRoutes
    )

  private def httpApp(
    interpreter: GraphQLInterpreter[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, CalibanError])
    : HttpApp[RIO[ServerEnv, *]] =
    HttpServerLogger.httpApp[RIO[ServerEnv, *]](true, true)(httpRoutes(interpreter).orNotFound)

  def make(config: HttpApiConfig): ZLayer[ServerEnv, Throwable, Server] = {
    ZLayer.scoped {
      ZIO.serviceWithZIO[
        GraphQLInterpreter[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, CalibanError]] { in =>
        BlazeServerBuilder[RIO[ServerEnv, *]]
          .bindHttp(config.port, config.address)
          .withHttpApp(httpApp(in))
          .resource
          .toScopedZIO
      }
    }
  }

  def serve(config: HttpApiConfig) = {
    ZIO.environmentWithZIO[ServerEnv] { env =>
      val in =
        env.get[GraphQLInterpreter[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, CalibanError]]

      BlazeServerBuilder[RIO[ServerEnv, *]]
        .bindHttp(config.port, config.address)
        .withHttpApp(httpApp(in))
        .resource
        .toScopedZIO
    }
  }

}
