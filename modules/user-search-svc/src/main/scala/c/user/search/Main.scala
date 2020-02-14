package c.user.search

import c.user.search.api.openapi.user.UserResource
import c.user.search.model.config.{KafkaConfig, PrometheusConfig}
import c.user.search.module.kafka.UserKafkaConsumer
import c.user.search.module.processor.{LiveUserEventProcessor, UserEventProcessor}
import c.user.search.module.repo.{EsUserSearchRepoInit, UserSearchRepoInit}
import org.http4s.HttpApp
import zio.blocking.Blocking
import zio.kafka.client.serde.Serde
import zio.kafka.client.{Consumer, Subscription}
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logger, Logging}
import zio.metrics.prometheus._
import zio.metrics.prometheus.helpers._
import zio._
import zio.clock.Clock
import zio.console.putStrLn
import zio.interop.catz._
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}

//import c.user.search.api.proto.userSearchApiService.UserSearchApiService

import cats.effect.ExitCode
import c.user.search.model.config.AppConfig
import c.user.search.module.api.{GrpcServer, UserSearchGrpcApi, UserSearchOpenApiHandler}
import c.user.search.module.repo.{EsUserSearchRepo, UserSearchRepo}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => HttpServerLogger}

object Main extends App {

  type AppEnvironment = Clock
    with Blocking with UserSearchRepo with UserSearchRepoInit with UserEventProcessor with Logging
    with PrometheusRegistry with PrometheusExporters

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

  private val userSearchRepoInit = ZIO.access[AppEnvironment](_.userSearchRepoInit.init)

  private def userKafkaConsumer(config: KafkaConfig) = {
    Consumer
      .make(UserKafkaConsumer.consumerSettings(config))
      .use { c =>
        c.subscribeAnd(Subscription.topics(config.topic))
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
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val result = for {
      applicationConfig <- AppConfig.getConfig

//      applicationElasticClient <- Managed.makeEffect {
//        val prop = ElasticProperties(applicationConfig.elasticsearch.addresses.mkString(","))
//        val jc = JavaClient(prop)
//        ElasticClient(jc)
//      }(_.close()).useForever

      logging <- makeLogger

      runtime = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        userSearchRepoInit *>
          metrics(applicationConfig.prometheus) *>
          userKafkaConsumer(applicationConfig.kafka) &>
          BlazeServerBuilder[ZIO[AppEnvironment, Throwable, *]]
            .bindHttp(applicationConfig.restApi.port, applicationConfig.restApi.address)
            .withHttpApp(httpApp)
            .serve
            .compile[ZIO[AppEnvironment, Throwable, *], ZIO[AppEnvironment, Throwable, *], ExitCode]
            .drain

      //        val builder = ServerBuilder
      //          .forPort(applicationConfig.grpcApi.port)
      //          .addService(UserSearchApiService.bindService[UserSearchRepo](rts, UserSearchGrpcApi.Live))
      //          .asInstanceOf[ServerBuilder[_]]
      ////        /  .addService(UserSearchApiService.bindService[UserSearchRepo](rts, UserSearchGrpcApi.Live))
      //        GrpcServer.make(builder).useForever
      }

      program <- runtime.provideSome[ZEnv] { base =>
        new Clock
          with Blocking with EsUserSearchRepo with EsUserSearchRepoInit with LiveUserEventProcessor with Logging
          with PrometheusRegistry with PrometheusExporters {
          val elasticClient: ElasticClient = {
            val prop = ElasticProperties(applicationConfig.elasticsearch.addresses.mkString(","))
            val jc = JavaClient(prop)
            val ec = ElasticClient(jc)
            ec
          }
          val userSearchRepoIndexName: String = applicationConfig.elasticsearch.indexName
          val clock: Clock.Service[Any] = base.clock
          val blocking: Blocking.Service[Any] = base.blocking
          val logger: Logger = logging.logger
        }
      }
    } yield program

    result
      .foldM(failure = err => putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1), success = _ => ZIO.succeed(0))
  }
}
