package com.jc.user.search

import com.jc.user.search.api.proto.ZioUserSearchApi.RCUserSearchApiService
import com.jc.user.search.model.config.{AppConfig, ElasticsearchConfig, HttpApiConfig, PrometheusConfig}
import com.jc.user.search.module.api.{HealthCheckApi, UserSearchGrpcApiHandler, UserSearchOpenApiHandler}
import com.jc.auth.JwtAuthenticator
import com.jc.logging.{LogbackLoggingSystem, LoggingSystem}
import com.jc.logging.api.{LoggingSystemGrpcApi, LoggingSystemGrpcApiHandler}
import com.jc.logging.proto.ZioLoggingSystemApi.RCLoggingSystemApiService
import com.jc.user.search.module.kafka.KafkaConsumer
import com.jc.user.search.module.processor.EventProcessor
import com.jc.user.search.module.repo.{
  DepartmentSearchRepo,
  DepartmentSearchRepoInit,
  UserSearchRepo,
  UserSearchRepoInit
}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import io.grpc._
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.implicits._
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.interop.catz._
import zio.logging.slf4j.Slf4jLogger
import zio.logging.Logging
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.helpers._
import scalapb.zio_grpc.{Server => GrpcServer, ServerLayer => GrpcServerLayer, ServiceList => GrpcServiceList}
import eu.timepit.refined.auto._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import zio.magic._

object Main extends App {

  type AppEnvironment = Clock
    with Blocking with Has[ElasticClient] with JwtAuthenticator with UserSearchRepo with UserSearchRepoInit
    with DepartmentSearchRepo with DepartmentSearchRepoInit with EventProcessor with KafkaConsumer with LoggingSystem
    with LoggingSystemGrpcApiHandler with UserSearchGrpcApiHandler with GrpcServer with Logging with Registry
    with Exporters

  private val httpRoutes: HttpRoutes[ZIO[AppEnvironment, Throwable, *]] =
    Router[ZIO[AppEnvironment, Throwable, *]](
      "/" -> UserSearchOpenApiHandler.httpRoutes[AppEnvironment],
      "/" -> HealthCheckApi.httpRoutes)

  private val httpApp: HttpApp[ZIO[AppEnvironment, Throwable, *]] =
    HttpServerLogger.httpApp[ZIO[AppEnvironment, Throwable, *]](true, true)(httpRoutes.orNotFound)

  private def metrics(config: PrometheusConfig): ZIO[AppEnvironment, Throwable, PrometheusHttpServer] = {
    for {
      registry <- getCurrentRegistry()
      _ <- initializeDefaultExports(registry)
      prometheusServer <- http(registry, config.port)
    } yield prometheusServer
  }

  private def createElasticClient(config: ElasticsearchConfig): ZLayer[Any, Throwable, Has[ElasticClient]] = {
    ZLayer.fromManaged(Managed.makeEffect {
      val prop = ElasticProperties(config.addresses.mkString(","))
      val jc = JavaClient(prop)
      ElasticClient(jc)
    }(_.close()))
  }

  private def createGrpcServer(
    config: HttpApiConfig): ZLayer[LoggingSystemGrpcApiHandler with UserSearchGrpcApiHandler, Throwable, GrpcServer] = {
    GrpcServerLayer.fromServiceList(
      ServerBuilder.forPort(config.port),
      GrpcServiceList.access[RCLoggingSystemApiService[Any]].access[RCUserSearchApiService[Any]])
  }

  private def createAppLayer(appConfig: AppConfig): ZLayer[Any, Throwable, AppEnvironment] = {
    ZLayer.fromMagic[AppEnvironment](
      Clock.live,
      Blocking.live,
      createElasticClient(appConfig.elasticsearch),
      Slf4jLogger.make((_, message) => message),
      JwtAuthenticator.live(appConfig.jwt),
      KafkaConsumer.live(appConfig.kafka),
      DepartmentSearchRepoInit.elasticsearch(appConfig.elasticsearch.departmentIndexName),
      DepartmentSearchRepo.elasticsearch(appConfig.elasticsearch.departmentIndexName),
      UserSearchRepoInit.elasticsearch(appConfig.elasticsearch.userIndexName),
      UserSearchRepo.elasticsearch(appConfig.elasticsearch.userIndexName),
      EventProcessor.live,
      LogbackLoggingSystem.create(),
      LoggingSystemGrpcApi.live,
      UserSearchGrpcApiHandler.live,
      createGrpcServer(appConfig.grpcApi),
      Registry.live,
      Exporters.live
    )
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val result: ZIO[zio.ZEnv, Throwable, Nothing] = for {
      appConfig <- AppConfig.getConfig

      runtime: ZIO[AppEnvironment, Throwable, Nothing] = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        val ec = rts.platform.executor.asEC
        val server: ZIO[AppEnvironment, Throwable, Nothing] = BlazeServerBuilder[ZIO[AppEnvironment, Throwable, *]](ec)
          .bindHttp(appConfig.restApi.port, appConfig.restApi.address)
          .withHttpApp(httpApp)
          .serve
          .compile[ZIO[AppEnvironment, Throwable, *], ZIO[AppEnvironment, Throwable, *], cats.effect.ExitCode]
          .drain
          .forever
        UserSearchRepoInit.init *>
          DepartmentSearchRepoInit.init *>
          metrics(appConfig.prometheus) *>
          KafkaConsumer.consume(appConfig.kafka) &>
          server
      }

      appLayer = createAppLayer(appConfig)

      program <- runtime.provideCustomLayer[Throwable, AppEnvironment](appLayer)
    } yield program

    result
      .foldM(
        failure = err => {
          ZIO.accessM[ZEnv](_.get[Console.Service].putStrLn(s"Execution failed with: $err")).ignore *> ZIO.succeed(
            ExitCode.failure)
        },
        success = _ => ZIO.succeed(ExitCode.success)
      )
  }
}
