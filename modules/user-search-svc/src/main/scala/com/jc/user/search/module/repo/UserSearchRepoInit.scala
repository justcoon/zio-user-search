package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import zio.{Has, ZIO, ZLayer}
import zio.logging.{Logger, Logging}

object UserSearchRepoInit {

  trait Service extends RepositoryInitializer

  def elasticsearch(indexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepoInit] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepoInit.Service] { (elasticClient, logger) =>
      new ESRepositoryInitializer(indexName, UserSearchRepo.EsUserSearchRepoService.fields, elasticClient, logger)
        with Service
    }

  val init: ZIO[UserSearchRepoInit, Throwable, Boolean] = ZIO.accessM[UserSearchRepoInit](_.get.init())
}
