package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}

object DepartmentSearchRepoInit {

  trait Service extends RepositoryInitializer

  def elasticsearch(indexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, DepartmentSearchRepoInit] =
    ZLayer.fromServices[ElasticClient, Logger[String], DepartmentSearchRepoInit.Service] { (elasticClient, logger) =>
      new ESRepositoryInitializer(
        indexName,
        DepartmentSearchRepo.EsDepartmentSearchRepoService.fields,
        elasticClient,
        logger) with Service
    }

  val init: ZIO[DepartmentSearchRepoInit, Throwable, Boolean] = ZIO.accessM[DepartmentSearchRepoInit](_.get.init())
}
