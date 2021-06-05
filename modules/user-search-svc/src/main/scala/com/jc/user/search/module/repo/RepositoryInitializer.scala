package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.fields.ElasticField
import zio.ZIO
import zio.logging.{Logger, Logging}

trait RepositoryInitializer {
  def init(): ZIO[Any, Throwable, Boolean]
}

class ESRepositoryInitializer(
  indexName: String,
  fields: Seq[ElasticField],
  elasticClient: ElasticClient,
  logger: Logger[String])
    extends RepositoryInitializer {
  import com.sksamuel.elastic4s.ElasticDsl._
  import com.sksamuel.elastic4s.zio.instances._

  val serviceLogger = logger.named(getClass.getName)

  override def init(): ZIO[Any, Throwable, Boolean] = {
    for {
      existResp <- elasticClient.execute {
        indexExists(indexName)
      }
      initResp <-
        if (!existResp.result.exists) {
          serviceLogger.debug(s"init - $indexName - initializing ...") *>
            elasticClient.execute {
              createIndex(indexName).mapping(properties(fields))
            }.map(r => r.result.acknowledged).tapError { e =>
              serviceLogger.error(s"init: $indexName - error: ${e.getMessage}") *>
                ZIO.fail(e)
            }
        } else {
          serviceLogger.debug(s"init - $indexName - updating ...") *>
            elasticClient.execute {
              putMapping(indexName).properties(fields)
            }.map(r => r.result.acknowledged).tapError { e =>
              serviceLogger.error(s"init - $indexName - error: ${e.getMessage}") *>
                ZIO.fail(e)
            }
        }
    } yield initResp
  }

//  import com.jc.logging.aspect.LoggingAspect
//  import com.jc.aspect.Aspect._
//
//  override def init(): ZIO[Any, Throwable, Boolean] = {
//    for {
//      existResp <- elasticClient.execute {
//        indexExists(indexName)
//      }
//      initResp <-
//        if (!existResp.result.exists) {
//          elasticClient.execute {
//            createIndex(indexName).mapping(properties(fields))
//          }.map(r => r.result.acknowledged) @@ LoggingAspect.loggingStartEnd(s"init - $indexName - initializing")
//        } else {
//          elasticClient.execute {
//            putMapping(indexName).fields(fields)
//          }.map(r => r.result.acknowledged) @@ LoggingAspect.loggingStartEnd(s"init - $indexName - updating")
//        }
//    } yield initResp
//  }
}
