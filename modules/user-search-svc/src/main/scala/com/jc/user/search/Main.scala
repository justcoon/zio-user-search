package com.jc.user.search

import com.jc.user.search.api.openapi.user.UserResource
import com.jc.user.search.api.proto.ZioUserSearchApi.UserSearchApiService
import com.jc.user.search.model.config.{AppConfig, ElasticsearchConfig, HttpApiConfig, PrometheusConfig}
import com.jc.user.search.module.api.{UserSearchGrpcApiHandler, UserSearchOpenApiHandler}
import com.jc.user.search.module.kafka.UserKafkaConsumer
import com.jc.user.search.module.processor.UserEventProcessor
import com.jc.user.search.module.repo.{UserSearchRepo, UserSearchRepoInit}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import io.grpc._
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.putStrLn
import zio.interop.catz._
import zio.logging.slf4j.Slf4jLogger
import zio.logging.Logging
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.helpers._
import scalapb.zio_grpc.{Server => GrpcServer}
import eu.timepit.refined.auto._

object Main extends App {

  type AppEnvironment = Clock
    with Blocking with Has[ElasticClient] with UserKafkaConsumer with UserSearchRepo with UserSearchRepoInit
    with UserEventProcessor with UserSearchGrpcApiHandler with GrpcServer with Logging with Registry with Exporters

  private val httpRoutes: HttpRoutes[ZIO[AppEnvironment, Throwable, *]] =
    new UserResource[ZIO[AppEnvironment, Throwable, *]]().routes(new UserSearchOpenApiHandler[AppEnvironment]())

  private val httpApp: HttpApp[ZIO[AppEnvironment, Throwable, *]] =
    HttpServerLogger.httpApp[ZIO[AppEnvironment, Throwable, *]](true, true)(httpRoutes.orNotFound)

  private def metrics(config: PrometheusConfig): ZIO[AppEnvironment, Throwable, PrometheusHttpServer] = {
    for {
      registry <- getCurrentRegistry()
      _ <- initializeDefaultExports(registry)
      prometheusServer <- http(registry, config.port)
    } yield prometheusServer
  }

  private val makeLogger: ZLayer[Any, Nothing, Logging] = Slf4jLogger.make((_, message) => message)

  private def createElasticClient(config: ElasticsearchConfig): ZLayer[Any, Throwable, Has[ElasticClient]] = {
    ZLayer.fromManaged(Managed.makeEffect {
      val prop = ElasticProperties(config.addresses.mkString(","))
      val jc = JavaClient(prop)
      ElasticClient(jc)
    }(_.close()))
  }

  private def createGrpcServer(config: HttpApiConfig): ZLayer[UserSearchGrpcApiHandler, Nothing, GrpcServer] = {
    GrpcServer.live[UserSearchApiService](ServerBuilder.forPort(config.port))
  }

  private def createAppLayer(appConfig: AppConfig): ZLayer[Any with Clock with Blocking, Throwable, AppEnvironment] = {
    val elasticLayer: ZLayer[Any, Throwable, Has[ElasticClient]] = createElasticClient(appConfig.elasticsearch)

    val loggerLayer: ZLayer[Any, Nothing, Logging] = makeLogger

    val userSearchRepoInitLayer: ZLayer[Any, Throwable, UserSearchRepoInit] = (elasticLayer ++ loggerLayer) >>>
      UserSearchRepoInit.elasticsearch(appConfig.elasticsearch.indexName)

    val userSearchRepoLayer: ZLayer[Any, Throwable, UserSearchRepo] = (elasticLayer ++ loggerLayer) >>>
      UserSearchRepo.elasticsearch(appConfig.elasticsearch.indexName)

    val userEventProcessorLayer
      : ZLayer[Any, Throwable, UserEventProcessor] = (userSearchRepoLayer ++ loggerLayer) >>> UserEventProcessor.live

    val userKafkaConsumerLayer: ZLayer[Clock with Blocking, Throwable, UserKafkaConsumer] =
      UserKafkaConsumer.live(appConfig.kafka)

    val userSearchGrpcApiHandlerLayer
      : ZLayer[Any, Throwable, UserSearchGrpcApiHandler] = (userSearchRepoLayer ++ loggerLayer) >>> UserSearchGrpcApiHandler.live

    val grpcServerLayer: ZLayer[Any, Throwable, GrpcServer] = userSearchGrpcApiHandlerLayer >>> createGrpcServer(
      appConfig.grpcApi)

    val metricsLayer: ZLayer[Any, Nothing, Registry with Exporters] = Registry.live ++ Exporters.live

    val appLayer: ZLayer[Any with Clock with Blocking, Throwable, AppEnvironment] = ZLayer.requires[Clock] ++
      ZLayer.requires[Blocking] ++
      elasticLayer ++
      loggerLayer ++
      userKafkaConsumerLayer ++
      userSearchRepoInitLayer ++
      userSearchRepoLayer ++
      userEventProcessorLayer ++
      userSearchGrpcApiHandlerLayer ++
      grpcServerLayer ++
      metricsLayer

    appLayer
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val result = for {
      appConfig <- AppConfig.getConfig

      runtime: ZIO[AppEnvironment, Throwable, Nothing] = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        val ec = rts.platform.executor.asEC
        UserSearchRepoInit.init *>
          metrics(appConfig.prometheus) *>
          UserKafkaConsumer.consume(appConfig.kafka.topic) &>
          BlazeServerBuilder[ZIO[AppEnvironment, Throwable, *]](ec)
            .bindHttp(appConfig.restApi.port, appConfig.restApi.address)
            .withHttpApp(httpApp)
            .serve
            .compile[ZIO[AppEnvironment, Throwable, *], ZIO[AppEnvironment, Throwable, *], cats.effect.ExitCode]
            .drain
            .forever
      }

      appLayer = createAppLayer(appConfig)

      program <- runtime.provideCustomLayer[Throwable, AppEnvironment](appLayer)
    } yield program

    result
      .foldM(
        failure = err => putStrLn(s"Execution failed with: $err") *> ZIO.succeed(ExitCode.failure),
        success = _ => ZIO.succeed(ExitCode.success))
  }
}
