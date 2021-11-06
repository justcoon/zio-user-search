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
import zio.{Has, ZIO, ZLayer}
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
      CalibanError]): HttpRoutes[ZIO[ServerEnv, Throwable, *]] =
    Router[ZIO[ServerEnv, Throwable, *]](
      "/" -> UserSearchOpenApiHandler.httpRoutes[ServerEnv],
      "/" -> UserSearchGraphqlApiHandler.graphqlRoutes[ServerEnv](interpreter),
      "/" -> HealthCheckApi.httpRoutes
    )

  private def httpApp(
    interpreter: GraphQLInterpreter[
      Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      CalibanError]): HttpApp[ZIO[ServerEnv, Throwable, *]] =
    HttpServerLogger.httpApp[ZIO[ServerEnv, Throwable, *]](true, true)(httpRoutes(interpreter).orNotFound)

//  def create(config: HttpApiConfig): ZLayer[ServerEnv, Throwable, Has[Server]] = {
//    ZLayer.fromServiceManaged[
//      GraphQLInterpreter[
//        Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
//        CalibanError],
//      ServerEnv,
//      Throwable,
//      Server] { in =>
//      BlazeServerBuilder[ZIO[ServerEnv, Throwable, *]]
//        .bindHttp(config.port, config.address)
//        .withHttpApp(httpApp(in))
//        .resource
//        .toManagedZIO
//    }
//  }

//  def create(config: HttpApiConfig): ZLayer[ServerEnv, Throwable, Has[Server]] = {
//    ZLayer.fromServiceM[
//      GraphQLInterpreter[
//        Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
//        CalibanError],
//      ServerEnv,
//      Throwable,
//      Server] { in =>
//      BlazeServerBuilder[ZIO[ServerEnv, Throwable, *]]
//        .bindHttp(config.port, config.address)
//        .withHttpApp(httpApp(in))
//        .resource
//        .toManagedZIO
//        .useForever
//    }
//  }

  def serve(config: HttpApiConfig) = {
    ZIO.accessM[ServerEnv] { env =>
      val in = env.get[GraphQLInterpreter[
        Clock with Logging with UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
        CalibanError]]

      BlazeServerBuilder[ZIO[ServerEnv, Throwable, *]]
        .bindHttp(config.port, config.address)
        .withHttpApp(httpApp(in))
        .resource
        .toManagedZIO
        .useForever
    }
  }

}
