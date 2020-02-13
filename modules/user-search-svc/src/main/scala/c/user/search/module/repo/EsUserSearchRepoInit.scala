package c.user.search.module.repo

import zio.logging.Logger
import com.sksamuel.elastic4s.ElasticClient
import zio.ZIO

trait EsUserSearchRepoInit extends UserSearchRepoInit {
  val elasticClient: ElasticClient

  val userSearchRepoIndexName: String

  val logger: Logger

  override val userSearchRepoInit: UserSearchRepoInit.Service = new UserSearchRepoInit.Service {
    import com.sksamuel.elastic4s.ElasticDsl._
    import com.sksamuel.elastic4s.zio.instances._

    override def init(): ZIO[Any, Throwable, Boolean] = {
      for {
        existResp <- elasticClient.execute {
          indexExists(userSearchRepoIndexName)
        }
        initResp <- if (!existResp.result.exists) {
          logger.log(s"init: $userSearchRepoIndexName") *>
            elasticClient.execute {
              createIndex(userSearchRepoIndexName)
            }.map(r => r.result.acknowledged)
        } else
          logger.log(s"init: $userSearchRepoIndexName - already initialized") *> ZIO(false)
      } yield initResp
    }
  }
}
