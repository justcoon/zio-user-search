package com.jc.user.search.module.api

import caliban.{CalibanError, GraphQLInterpreter}
import com.jc.auth.JwtAuthenticator
import com.jc.logging.LoggingSystem
import com.jc.user.search.api.graphql.UserSearchGraphqlApi.UserSearchGraphqlApiInterpreter
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.{
  UserSearchGraphqlApiRequestContext,
  UserSearchGraphqlApiService
}
import com.jc.user.search.model.config.HttpApiConfig
import com.jc.user.search.module.repo.{DepartmentSearchRepo, UserSearchRepo}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import org.http4s.server.{Router, Server}
import org.http4s.implicits._
import zio.interop.catz._
import zio.{Has, RIO, ZIO, ZLayer}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import eu.timepit.refined.auto._

object HttpApiServer {

  type ServerEnv = UserSearchRepo
    with DepartmentSearchRepo with LoggingSystem with UserSearchGraphqlApiService with UserSearchGraphqlApiInterpreter
    with JwtAuthenticator with Clock with Blocking with Logging

  private def httpRoutes(
    interpreter: GraphQLInterpreter[
      Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      CalibanError]): HttpRoutes[RIO[ServerEnv, *]] =
    Router[RIO[ServerEnv, *]](
      "/" -> UserSearchOpenApiHandler.httpRoutes[ServerEnv],
      "/" -> UserSearchGraphqlApiHandler.graphqlRoutes[ServerEnv](interpreter),
      "/" -> HealthCheckApi.httpRoutes
    )

  private def httpApp(
    interpreter: GraphQLInterpreter[
      Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      CalibanError]): HttpApp[RIO[ServerEnv, *]] =
    HttpServerLogger.httpApp[RIO[ServerEnv, *]](true, true)(httpRoutes(interpreter).orNotFound)

  def create(config: HttpApiConfig): ZLayer[ServerEnv, Throwable, Has[Server]] = {
    ZLayer.fromServiceManaged[
      GraphQLInterpreter[
        Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
        CalibanError],
      ServerEnv,
      Throwable,
      Server] { in =>
      BlazeServerBuilder[RIO[ServerEnv, *]]
        .bindHttp(config.port, config.address)
        .withHttpApp(httpApp(in))
        .resource
        .toManagedZIO
    }
  }

  def serve(config: HttpApiConfig) = {
    ZIO.accessM[ServerEnv] { env =>
      val in = env.get[GraphQLInterpreter[
        Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
        CalibanError]]

      BlazeServerBuilder[RIO[ServerEnv, *]]
        .bindHttp(config.port, config.address)
        .withHttpApp(httpApp(in))
        .resource
        .toManagedZIO
        .useForever
    }
  }

}
