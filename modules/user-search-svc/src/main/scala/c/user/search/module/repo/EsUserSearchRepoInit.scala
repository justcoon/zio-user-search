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
          logger.log(s"init: $userSearchRepoIndexName - initializing ...") *>
            elasticClient.execute {
              createIndex(userSearchRepoIndexName).mapping(properties(EsUserSearchRepoInit.fields))
            }.map(r => r.result.acknowledged)
        } else {
          logger.log(s"init: $userSearchRepoIndexName - updating ...") *>
            elasticClient.execute {
              putMapping(userSearchRepoIndexName).fields(EsUserSearchRepoInit.fields)
            }.map(r => r.result.acknowledged)
        }
      } yield initResp
    }
  }
}

object EsUserSearchRepoInit {
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
