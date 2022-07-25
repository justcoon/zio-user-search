package com.jc.user.search

import com.jc.user.search.model.config.{AppConfig, ElasticsearchConfig, PrometheusConfig}
import com.jc.user.search.module.api.{
  GrpcApiServer,
  HttpApiServer,
  UserSearchGraphqlApiHandler,
  UserSearchGrpcApiHandler
}
import com.jc.auth.{JwtAuthenticator, PdiJwtAuthenticator}
import com.jc.logging.{LogbackLoggingSystem, LoggingSystem}
import com.jc.logging.api.LoggingSystemGrpcApiHandler
import com.jc.user.search.api.graphql.UserSearchGraphqlApi
import com.jc.user.search.api.graphql.UserSearchGraphqlApi.UserSearchGraphqlApiInterpreter
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService
import com.jc.user.search.api.proto.ZioUserSearchApi.RCUserSearchApiService
import com.jc.user.search.module.Logger
import com.jc.user.search.module.es.EsClient
import com.jc.user.search.module.kafka.KafkaConsumer
import com.jc.user.search.module.processor.{EventProcessor, LiveEventProcessor}
import com.jc.user.search.module.repo.{
  DepartmentSearchRepo,
  DepartmentSearchRepoInit,
  EsDepartmentSearchRepo,
  EsDepartmentSearchRepoInit,
  EsUserSearchRepo,
  EsUserSearchRepoInit,
  UserSearchRepo,
  UserSearchRepoInit
}
import com.sksamuel.elastic4s.ElasticClient
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}
import zio._
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.helpers._
import scalapb.zio_grpc.{Server => GrpcServer}
import org.http4s.server.{Server => HttpServer}
import eu.timepit.refined.auto._
import zio.kafka.consumer.Consumer

object Main extends ZIOAppDefault {

  type AppEnvironment = ElasticClient
    with JwtAuthenticator with UserSearchRepo with UserSearchRepoInit with DepartmentSearchRepo
    with DepartmentSearchRepoInit with EventProcessor with Consumer with LoggingSystem with LoggingSystemGrpcApiHandler
    with RCUserSearchApiService[Any] with UserSearchGraphqlApiService with UserSearchGraphqlApiInterpreter
    with GrpcServer with HttpServer with Registry with Exporters

  private def metrics(config: PrometheusConfig): ZIO[AppEnvironment, Throwable, PrometheusHttpServer] = {
    for {
      registry <- getCurrentRegistry()
      _ <- initializeDefaultExports(registry)
      prometheusServer <- http(registry, config.port)
    } yield prometheusServer
  }

  private def createAppLayer(appConfig: AppConfig): ZLayer[Any, Throwable, AppEnvironment] = {
    ZLayer.make[AppEnvironment](
      EsClient.make(appConfig.elasticsearch),
      PdiJwtAuthenticator.make(appConfig.jwt),
      KafkaConsumer.make(appConfig.kafka),
      EsDepartmentSearchRepoInit.make(appConfig.elasticsearch.departmentIndexName),
      EsDepartmentSearchRepo.make(appConfig.elasticsearch.departmentIndexName),
      EsUserSearchRepoInit.make(appConfig.elasticsearch.userIndexName),
      EsUserSearchRepo.make(appConfig.elasticsearch.userIndexName),
      LiveEventProcessor.layer,
      LogbackLoggingSystem.make(),
      LoggingSystemGrpcApiHandler.layer,
      UserSearchGrpcApiHandler.layer,
      UserSearchGraphqlApiHandler.layer,
      UserSearchGraphqlApi.apiInterpreter,
      GrpcApiServer.make(appConfig.grpcApi),
      HttpApiServer.make(appConfig.restApi),
      Registry.live,
      Exporters.live
    )
  }

  override def run: ZIO[Scope, Any, ExitCode] = {
    val run = for {
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

      program <- runtime.provideLayer(appLayer)
    } yield program

    run.provideLayer(Logger.layer).as(ExitCode.success)
  }
}
