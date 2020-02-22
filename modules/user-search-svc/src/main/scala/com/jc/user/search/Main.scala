package com.jc.user.search

import cats.effect.ExitCode
import com.jc.user.search.api.openapi.user.UserResource
import com.jc.user.search.api.proto.UserSearchApiServiceFs2Grpc
import com.jc.user.search.model.config.{AppConfig, PrometheusConfig}
import com.jc.user.search.module.api.{GrpcServer, UserSearchGrpcApiHandler, UserSearchOpenApiHandler}
import com.jc.user.search.module.kafka.UserKafkaConsumer
import com.jc.user.search.module.processor.{LiveUserEventProcessor, UserEventProcessor}
import com.jc.user.search.module.repo.{EsUserSearchRepo, EsUserSearchRepoInit, UserSearchRepo, UserSearchRepoInit}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import io.grpc._
import io.grpc.protobuf.services.ProtoReflectionService
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => HttpServerLogger}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.putStrLn
import zio.interop.catz._
import zio.kafka.client.serde.Serde
import zio.kafka.client.{Consumer, Subscription}
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logger, Logging}
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._

object Main extends App {

  type AppEnvironment = Clock
    with Blocking with UserSearchRepo with UserSearchRepoInit with UserEventProcessor with UserKafkaConsumer
    with Logging with PrometheusRegistry with PrometheusExporters

  private val makeLogger =
    Slf4jLogger.make((_, message) => message)

  private val httpRoutes =
    new UserResource[ZIO[AppEnvironment, Throwable, *]]().routes(new UserSearchOpenApiHandler[AppEnvironment]())

  private val httpApp: HttpApp[ZIO[AppEnvironment, Throwable, *]] =
    HttpServerLogger.httpApp[ZIO[AppEnvironment, Throwable, *]](true, true)(httpRoutes.orNotFound)

  private def metrics(config: PrometheusConfig): ZIO[AppEnvironment, Throwable, PrometheusHttpServer] = {
    for {
      registry <- registry.getCurrent
      _ <- exporters.initializeDefaultExports(registry)
      prometheusServer <- exporters.http(registry, config.port)
    } yield prometheusServer
  }

  private val userSearchRepoInit = ZIO.accessM[AppEnvironment](_.userSearchRepoInit.init)

  private val userTopicSubscription = ZIO.accessM[AppEnvironment] { env =>
    env.userKafkaConsumer
      .subscribeAnd(Subscription.topics(env.userKafkaTopic))
      .plainStream(Serde.string, UserKafkaConsumer.userEventSerde)
      .flattenChunks
      .tap { cr =>
        ZIO.accessM[AppEnvironment](_.userEventProcessor.process(cr.value))
      }
      .map(_.offset)
      .aggregateAsync(Consumer.offsetBatches)
      .mapM(_.commit)
      .runDrain
  }

  private def managedResources(
    appConfig: AppConfig): ZManaged[Clock with Blocking, Throwable, (Consumer, ElasticClient)] = {
    for {
      kafka <- Consumer.make(UserKafkaConsumer.consumerSettings(appConfig.kafka))
      elastic <- Managed.makeEffect {
        val prop = ElasticProperties(appConfig.elasticsearch.addresses.mkString(","))
        val jc = JavaClient(prop)
        ElasticClient(jc)
      }(_.close())
    } yield (kafka, elastic)
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val result = for {
      applicationConfig <- AppConfig.getConfig

      logging <- makeLogger

      runtime = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        userSearchRepoInit *>
          metrics(applicationConfig.prometheus) *>
          userTopicSubscription &>
          BlazeServerBuilder[ZIO[AppEnvironment, Throwable, *]]
            .bindHttp(applicationConfig.restApi.port, applicationConfig.restApi.address)
            .withHttpApp(httpApp)
            .serve
            .compile[ZIO[AppEnvironment, Throwable, *], ZIO[AppEnvironment, Throwable, *], ExitCode]
            .drain &>
          GrpcServer
            .make(
              ServerBuilder
                .forPort(applicationConfig.grpcApi.port)
                .addService(UserSearchApiServiceFs2Grpc.bindService(new UserSearchGrpcApiHandler[AppEnvironment]()))
                .asInstanceOf[ServerBuilder[_]]
                .addService(ProtoReflectionService.newInstance())
                .asInstanceOf[ServerBuilder[_]])
            .useForever
      }

      program <- managedResources(applicationConfig).use {
        case (consumer, elastic) =>
          runtime.provideSome[ZEnv] { base =>
            new Clock
              with Blocking with EsUserSearchRepo with EsUserSearchRepoInit with UserKafkaConsumer
              with LiveUserEventProcessor with Logging with PrometheusRegistry with PrometheusExporters {
              val elasticClient: ElasticClient = elastic
              val userSearchRepoIndexName: String = applicationConfig.elasticsearch.indexName
              val userKafkaConsumer = consumer
              val userKafkaTopic = applicationConfig.kafka.topic
              val clock: Clock.Service[Any] = base.clock
              val blocking: Blocking.Service[Any] = base.blocking
              val logger: Logger = logging.logger
            }
          }
      }
    } yield program

    result
      .foldM(failure = err => putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1), success = _ => ZIO.succeed(0))
  }
}
