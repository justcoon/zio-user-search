package c.user.search

import c.user.search.api.openapi.user.UserResource
import c.user.search.module.kafka.UserKafkaConsumer
import c.user.search.module.processor.{LiveUserEventProcessor, UserEventProcessor}
import c.user.search.module.repo.{EsUserSearchRepoInit, UserSearchRepoInit}
import org.http4s.HttpApp
import zio.blocking.Blocking
import zio.kafka.client.serde.Serde
import zio.kafka.client.{Consumer, Subscription}
//import c.user.search.api.proto.userSearchApiService.UserSearchApiService

import cats.effect.ExitCode
import c.user.search.model.config.AppConfig
import c.user.search.module.api.{GrpcServer, UserSearchGrpcApi, UserSearchOpenApiHandler}
import c.user.search.module.logger.{LiveLogger, Logger => MyLogger}
import c.user.search.module.repo.{EsUserSearchRepo, UserSearchRepo}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import zio._
import zio.clock.Clock
import zio.console.putStrLn
import zio.interop.catz._

object Main extends App {

  type AppEnvironment = Clock
    with Blocking with UserSearchRepo with UserSearchRepoInit with UserEventProcessor with MyLogger

  private val httpRoutes =
    new UserResource[ZIO[AppEnvironment, Throwable, *]]().routes(new UserSearchOpenApiHandler[AppEnvironment]())

  private val httpApp: HttpApp[ZIO[AppEnvironment, Throwable, *]] =
    Logger.httpApp[ZIO[AppEnvironment, Throwable, *]](true, true)(httpRoutes.orNotFound)

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val result = for {
      applicationConfig <- AppConfig.getConfig

//      applicationElasticClient <- Managed.makeEffect {
//        val prop = ElasticProperties(applicationConfig.elasticsearch.addresses.mkString(","))
//        val jc = JavaClient(prop)
//        ElasticClient(jc)
//      }(_.close()).useForever

      runtime = ZIO.runtime[AppEnvironment].flatMap { implicit rts =>
        rts.environment.userSearchRepoInit.init *>
        Consumer
          .make(UserKafkaConsumer.consumerSettings(applicationConfig.kafka))
          .use { c =>
            c.subscribeAnd(Subscription.topics(applicationConfig.kafka.topic))
              .plainStream(Serde.string, UserKafkaConsumer.userEventSerde)
              .flattenChunks
              .tap { cr =>
                rts.environment.userEventProcessor.process(cr.value)
              }
              .map(_.offset)
              .aggregateAsync(Consumer.offsetBatches)
              .mapM(_.commit)
              .runDrain
        }  &>
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
          with Blocking with EsUserSearchRepo with EsUserSearchRepoInit with LiveUserEventProcessor with LiveLogger {
          val elasticClient: ElasticClient = {
            val prop = ElasticProperties(applicationConfig.elasticsearch.addresses.mkString(","))
            val jc = JavaClient(prop)
            val ec = ElasticClient(jc)
            ec
          }
          val userSearchRepoIndexName: String = applicationConfig.elasticsearch.indexName
          val clock: Clock.Service[Any] = base.clock
          val blocking: Blocking.Service[Any] = base.blocking
        }
      }
    } yield program

    result
      .foldM(failure = err => putStrLn(s"Execution failed with: $err") *> ZIO.succeed(1), success = _ => ZIO.succeed(0))
  }
}
