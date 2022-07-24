package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient

import zio.{ZIO, ZLayer}

trait DepartmentSearchRepoInit extends RepositoryInitializer[Any]

object DepartmentSearchRepoInit {

  val init: ZIO[DepartmentSearchRepoInit, Throwable, Boolean] = ZIO.serviceWithZIO[DepartmentSearchRepoInit](_.init())
}

object EsDepartmentSearchRepoInit {

  def make(indexName: String): ZLayer[ElasticClient, Nothing, DepartmentSearchRepoInit] =
    ZLayer.fromZIO {
      ZIO.serviceWith[ElasticClient] { elasticClient =>
        val res: DepartmentSearchRepoInit =
          new ESRepositoryInitializer(indexName, EsDepartmentSearchRepo.fields, elasticClient)
            with DepartmentSearchRepoInit

        res
      }
    }

}
