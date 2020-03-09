package com.jc.user.search

import cats.effect.ExitCode
import com.jc.user.domain.proto.UserPayloadEvent
import com.jc.user.search.api.openapi.user.UserResource
import com.jc.user.search.api.proto.UserSearchApiServiceFs2Grpc
import com.jc.user.search.model.config.{AppConfig, ElasticsearchConfig, KafkaConfig, PrometheusConfig}
import com.jc.user.search.module.api.{GrpcServer, UserSearchGrpcApiHandler, UserSearchOpenApiHandler}
import com.jc.user.search.module.kafka.UserKafkaConsumer
import com.jc.user.search.module.processor.UserEventProcessor
import com.jc.user.search.module.repo.{UserSearchRepo, UserSearchRepoInit}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import io.grpc._
import io.grpc.protobuf.services.ProtoReflectionService
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
import zio.kafka.serde.Serde
import zio.kafka.consumer.{Consumer, Subscription}
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logger, Logging}
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters.Exporters
import zio.metrics.prometheus.helpers._

object Main extends App {

  type AppEnvironment = Clock
    with Blocking with Has[ElasticClient] with Consumer[Any, String, UserPayloadEvent] with UserSearchRepo
    with UserSearchRepoInit with UserEventProcessor with Logging with Registry with Exporters

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

  private val userSearchRepoInit = ZIO.accessM[UserSearchRepoInit](_.get.init)

  private def userTopicSubscription(userKafkaTopic: String) = {
    Consumer
      .subscribeAnd[Any, String, UserPayloadEvent](Subscription.topics(userKafkaTopic))
      .plainStream
      .flattenChunks
      .tap { cr =>
        ZIO.accessM[UserEventProcessor] {
          _.get.process(cr.value)
        }
      }
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapM(_.commit)
      .runDrain
  }

  private val makeLogger: ZLayer[Any, Nothing, Logging] = Slf4jLogger.make((_, message) => message)

  private def createElasticClient(config: ElasticsearchConfig): ZLayer[Any, Throwable, Has[ElasticClient]] = {
    ZLayer.fromManaged(Managed.makeEffect {
      val prop = ElasticProperties(config.addresses.mkString(","))
      val jc = JavaClient(prop)
      ElasticClient(jc)
    }(_.close()))
  }

  private def createUserKafkaConsumer(
    config: KafkaConfig): ZLayer[Clock with Blocking, Throwable, Consumer[Any, String, UserPayloadEvent]] = {
    Consumer
      .make(UserKafkaConsumer.consumerSettings(config), Serde.string, UserKafkaConsumer.userEventSerde)
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

    val userKafkaConsumerLayer: ZLayer[Clock with Blocking, Throwable, Consumer[Any, String, UserPayloadEvent]] =
      createUserKafkaConsumer(appConfig.kafka)

    val metricsLayer: ZLayer[Any, Nothing, Registry with Exporters] = Registry.live ++ Exporters.live

    val appLayer: ZLayer[Any with Clock with Blocking, Throwable, AppEnvironment] = ZLayer.requires[Clock] ++
      ZLayer.requires[Blocking] ++
      elasticLayer ++
      loggerLayer ++
      userKafkaConsumerLayer ++
      userSearchRepoInitLayer ++
      userSearchRepoLayer ++
      userEventProcessorLayer ++
      metricsLayer

    appLayer
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val result = for {
      appConfig <- AppConfig.getConfig

      runtime: ZIO[AppEnvironment, Throwable, Nothing] = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        userSearchRepoInit *>
          metrics(appConfig.prometheus) *>
          userTopicSubscription(appConfig.kafka.topic) &>
          BlazeServerBuilder[ZIO[AppEnvironment, Throwable, *]]
            .bindHttp(appConfig.restApi.port, appConfig.restApi.address)
            .withHttpApp(httpApp)
            .serve
            .compile[ZIO[AppEnvironment, Throwable, *], ZIO[AppEnvironment, Throwable, *], ExitCode]
            .drain &>
          GrpcServer
            .make(
              ServerBuilder
                .forPort(appConfig.grpcApi.port)
                .addService(UserSearchApiServiceFs2Grpc.bindService(new UserSearchGrpcApiHandler[AppEnvironment]()))
                .asInstanceOf[ServerBuilder[_]]
                .addService(ProtoReflectionService.newInstance())
                .asInstanceOf[ServerBuilder[_]])
            .useForever
      }

      appLayer = createAppLayer(appConfig)

      program <- runtime.provideCustomLayer[Throwable, AppEnvironment](appLayer)
    } yield program

    result
      .foldM(failure = err => putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1), success = _ => ZIO.succeed(0))
  }
}
