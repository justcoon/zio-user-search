package c.user.search.module.repo

import c.user.search.module.logger.Logger
import com.sksamuel.elastic4s.ElasticClient
import zio.ZIO

trait EsUserSearchRepoInit extends UserSearchRepoInit {
  val elasticClient: ElasticClient

  val userSearchRepoIndexName: String

  override val userSearchRepoInit: UserSearchRepoInit.Service = new UserSearchRepoInit.Service {
    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.zio.instances._

    override def init(): ZIO[Logger, Throwable, Boolean] = {
      ZIO.accessM { env =>
        for {
          existResp <- elasticClient.execute {
            indexExists(userSearchRepoIndexName)
          }
          initResp <- if (!existResp.result.exists) {
            env.logger.debug(s"init: $userSearchRepoIndexName") *>
            elasticClient.execute {
              createIndex(userSearchRepoIndexName)
            }.map(r => r.result.acknowledged)
          } else
            ZIO(false)
//            env.logger.warn(s"init: $userSearchRepoIndexName - already initialized").map(_ => false)
        } yield initResp
      }
    }
  }
}
