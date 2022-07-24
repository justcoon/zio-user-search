package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import zio.{ZIO, ZLayer}

trait UserSearchRepoInit extends RepositoryInitializer[Any]

object UserSearchRepoInit {
  val init: ZIO[UserSearchRepoInit, Throwable, Boolean] = ZIO.serviceWithZIO[UserSearchRepoInit](_.init())
}

object EsUserSearchRepoInit {

  def layer(indexName: String): ZLayer[ElasticClient, Nothing, UserSearchRepoInit] =
    ZLayer.fromZIO {
      ZIO.serviceWith[ElasticClient] { elasticClient =>
        val res: UserSearchRepoInit = new ESRepositoryInitializer(indexName, EsUserSearchRepo.fields, elasticClient)
          with UserSearchRepoInit
        res
      }
    }
}
