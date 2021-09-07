package com.jc.user.search.module.api

import com.jc.user.search.Main.AppEnvironment
import com.jc.user.search.api.openapi.user.UserResource
import com.jc.user.search.model.config.HttpApiConfig
import eu.timepit.refined.auto._
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.implicits._
import zio.ZIO
import zio.interop.catz._

object HttpServer {

//  private val httpRoutes: HttpRoutes[ZIO[AppEnvironment, Throwable, *]] =
//    new UserResource[ZIO[AppEnvironment, Throwable, *]]().routes(new UserSearchOpenApiHandler[AppEnvironment]())
//
//  private val httpApp: HttpApp[ZIO[AppEnvironment, Throwable, *]] =
//    HttpServerLogger.httpApp[ZIO[AppEnvironment, Throwable, *]](true, true)(httpRoutes.orNotFound)
//
//  def server(config: HttpApiConfig): ZIO[AppEnvironment, Nothing, Unit] =
//    ZIO
//      .runtime[AppEnvironment]
//      .flatMap { implicit rts =>
//        val ec = rts.platform.executor.asEC
//
//        BlazeServerBuilder[ZIO[AppEnvironment, Throwable, *]](ec)
//          .bindHttp(config.port, config.address)
//          .withHttpApp(httpApp)
//          .serve
//          .compile[ZIO[AppEnvironment, Throwable, *], ZIO[AppEnvironment, Throwable, *], cats.effect.ExitCode]
//          .drain
//      }
//      .orDie

//  def live[HttpApp: Tagged](
//    address: String,
//    port: Int
//  ): ZLayer[Has[HttpApp] with Clock, Nothing, Server] = {
//
//
//    ZLayer.fromServicesM { (httpApp: HttpApp, clock: Clock.Service) =>
//      import zio.interop.catz._
//      implicit val c = clock
//      BlazeServerBuilder[Task[*]]
//        .bindHttp(port, address)
//        .withHttpApp(httpApp)
//        .serve
//        .compile[Task[*], Task[*], ExitCode]
//        .drain
//        .forever
//    }
//  }
}
