package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields.ElasticField
import zio.ZIO

trait RepositoryInitializer[R] {
  def init(): ZIO[R, Throwable, Boolean]
}

class ESRepositoryInitializer(indexName: String, fields: Seq[ElasticField], elasticClient: ElasticClient)
    extends RepositoryInitializer[Any] {
  import com.sksamuel.elastic4s.ElasticDsl._
  import com.jc.user.search.module.es.instances._

  override def init(): ZIO[Any, Throwable, Boolean] = {
    for {
      existResp <- elasticClient.execute {
        indexExists(indexName)
      }
      initResp <-
        if (!existResp.result.exists) {
          ZIO.logInfo(s"init - $indexName - initializing") *>
            elasticClient.execute {
              createIndex(indexName).mapping(properties(fields))
            }.map(r => r.result.acknowledged).tapError { e =>
              ZIO.logError(s"init - $indexName - error: ${e.getMessage}")
            }
        } else {
          ZIO.logInfo(s"init - $indexName - updating") *>
            elasticClient.execute {
              putMapping(indexName).properties(fields)
            }.map(r => r.result.acknowledged).tapError { e =>
              ZIO.logError(s"init - $indexName - error: ${e.getMessage}")
            }
        }
    } yield initResp

  }
}
