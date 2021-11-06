package com.jc.user.search

import com.jc.user.search.model.config.{AppConfig, ElasticsearchConfig, PrometheusConfig}
import com.jc.user.search.module.api.{
  GrpcApiServer,
  HttpApiServer,
  UserSearchGraphqlApiHandler,
  UserSearchGrpcApiHandler
}
import com.jc.auth.JwtAuthenticator
import com.jc.logging.{LogbackLoggingSystem, LoggingSystem}
import com.jc.logging.api.{LoggingSystemGrpcApi, LoggingSystemGrpcApiHandler}
import com.jc.user.search.api.graphql.UserSearchGraphqlApi
import com.jc.user.search.api.graphql.UserSearchGraphqlApi.UserSearchGraphqlApiInterpreter
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.UserSearchGraphqlApiService
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
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.logging.slf4j.Slf4jLogger
import zio.logging.Logging
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.helpers._
import scalapb.zio_grpc.{Server => GrpcServer}
import org.http4s.server.{Server => HttpServer}
import eu.timepit.refined.auto._
import zio.magic._

object Main extends App {

  type AppEnvironment = Clock
    with Console with Blocking with Has[ElasticClient] with JwtAuthenticator with UserSearchRepo with UserSearchRepoInit
    with DepartmentSearchRepo with DepartmentSearchRepoInit with EventProcessor with KafkaConsumer with LoggingSystem
    with LoggingSystemGrpcApiHandler with UserSearchGrpcApiHandler with UserSearchGraphqlApiService
    with UserSearchGraphqlApiInterpreter with GrpcServer with Has[HttpServer] with Logging with Registry with Exporters

  private def metrics(config: PrometheusConfig): ZIO[AppEnvironment, Throwable, PrometheusHttpServer] = {
    for {
      registry <- getCurrentRegistry()
      _ <- initializeDefaultExports(registry)
      prometheusServer <- http(registry, config.port)
    } yield prometheusServer
  }

  private def createElasticClient(config: ElasticsearchConfig): ZLayer[Any, Throwable, Has[ElasticClient]] = {
    ZManaged.makeEffect {
      val prop = ElasticProperties(config.addresses.mkString(","))
      val jc = JavaClient(prop)
      ElasticClient(jc)
    }(_.close()).toLayer
  }

  private def createAppLayer(appConfig: AppConfig): ZLayer[Any, Throwable, AppEnvironment] = {
    ZLayer.fromMagic[AppEnvironment](
      Clock.live,
      Console.live,
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
      UserSearchGraphqlApiHandler.live,
      UserSearchGraphqlApi.apiInterpreter,
      GrpcApiServer.create(appConfig.grpcApi),
      HttpApiServer.create(appConfig.restApi),
      Registry.live,
      Exporters.live
    )
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val result: ZIO[zio.ZEnv, Throwable, Nothing] = for {
      appConfig <- AppConfig.getConfig

      runtime: ZIO[AppEnvironment, Throwable, Nothing] = ZIO.runtime[AppEnvironment].flatMap {
        implicit rts: Runtime[AppEnvironment] =>
          UserSearchRepoInit.init *>
            DepartmentSearchRepoInit.init *>
            metrics(appConfig.prometheus) *>
            KafkaConsumer.consume(appConfig.kafka) &>
            ZIO.never
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
