package com.jc.user.search.module.es

import com.jc.user.search.model.config.ElasticsearchConfig
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import com.sksamuel.elastic4s.http.JavaClient
import zio.{ZIO, ZLayer}

object EsClient {

  def make(config: ElasticsearchConfig): ZLayer[Any, Throwable, ElasticClient] = {
    import eu.timepit.refined.auto._
    ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.succeed {
          val prop = ElasticProperties(config.addresses.mkString(","))
          val jc = JavaClient(prop)
          ElasticClient(jc)
        }
      }(c => ZIO.attempt(c.close()).orDie)
    }
  }
}
