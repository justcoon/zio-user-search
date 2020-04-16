package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import zio.{Has, ZIO, ZLayer}
import zio.logging.LogLevel
import zio.logging.Logging
import zio.logging.Logger

object UserSearchRepoInit {

  trait Service {
    def init(): ZIO[Any, Throwable, Boolean]
  }

  final case class EsUserSearchRepoInitService(
    elasticClient: ElasticClient,
    userSearchRepoIndexName: String,
    logger: Logger[String])
      extends UserSearchRepoInit.Service {
    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.zio.instances._

    override def init(): ZIO[Any, Throwable, Boolean] = {
      for {
        existResp <- elasticClient.execute {
          indexExists(userSearchRepoIndexName)
        }
        initResp <- if (!existResp.result.exists) {
          logger.debug(s"init: $userSearchRepoIndexName - initializing ...") *>
            elasticClient.execute {
              createIndex(userSearchRepoIndexName).mapping(properties(EsUserSearchRepoInitService.fields))
            }.map(r => r.result.acknowledged).tapError { e =>
              logger.error(s"init: $userSearchRepoIndexName - error: ${e.getMessage}") *>
                ZIO.fail(e)
            }
        } else {
          logger.debug(s"init: $userSearchRepoIndexName - updating ...") *>
            elasticClient.execute {
              putMapping(userSearchRepoIndexName).fields(EsUserSearchRepoInitService.fields)
            }.map(r => r.result.acknowledged).tapError { e =>
              logger.error(s"init: $userSearchRepoIndexName - error: ${e.getMessage}") *>
                ZIO.fail(e)
            }
        }
      } yield initResp
    }
  }

  object EsUserSearchRepoInitService {
    import com.sksamuel.elastic4s.ElasticDsl._

    val fields = Seq(
      textField("id").fielddata(true),
      textField("username").fielddata(true),
      textField("email").fielddata(true),
      textField("address.street").fielddata(true),
      textField("address.number").fielddata(true),
      textField("address.city").fielddata(true),
      textField("address.state").fielddata(true),
      textField("address.zip").fielddata(true),
      textField("address.country").fielddata(true),
      booleanField("deleted")
    )
  }

  def elasticsearch(
    userSearchRepoIndexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepoInit] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepoInit.Service] {
      (elasticClient: ElasticClient, logger: Logger[String]) =>
        EsUserSearchRepoInitService(elasticClient, userSearchRepoIndexName, logger)
    }

  val init: ZIO[UserSearchRepoInit, Throwable, Boolean] = ZIO.accessM[UserSearchRepoInit](_.get.init)
}
