package c.user.search.module.repo

import c.user.domain.UserEntity.UserId
import c.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import zio.ZIO

trait EsUserSearchRepo extends UserSearchRepo {
  val elasticClient: ElasticClient

  val userSearchRepoIndexName: String

  override val userSearchRepo: UserSearchRepo.Service = new UserSearchRepo.Service {
    import com.sksamuel.elastic4s.zio.instances._
    import com.sksamuel.elastic4s.ElasticDsl.{update => updateIndex, search => searchIndex, _}
    import com.sksamuel.elastic4s.requests.searches.queries.{NoopQuery, QueryStringQuery}
    import com.sksamuel.elastic4s.circe._
    import io.circe.generic.auto._

    override def insert(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      elasticClient.execute {
        indexInto(userSearchRepoIndexName).doc(user).id(user.id)
      }.bimap(e => RepoFailure(e), _.isSuccess)
    }

    override def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      elasticClient.execute {
        updateIndex(user.id).in(userSearchRepoIndexName).doc(user)
      }.bimap(e => RepoFailure(e), _.isSuccess)
    }

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]] = {
      elasticClient.execute {
        get(id).from(userSearchRepoIndexName)
      }.bimap(
        e => RepoFailure(e),
        r =>
          if (r.result.exists)
            Option(r.result.to[UserSearchRepo.User])
          else
            Option.empty)
    }

    override def findAll(): ZIO[Any, ExpectedFailure, Array[UserSearchRepo.User]] = {
      elasticClient.execute {
        searchIndex(userSearchRepoIndexName).matchAllQuery
      }.bimap(e => RepoFailure(e), _.result.to[UserSearchRepo.User].toArray)
    }

    override def search(
      query: Option[String],
      page: Int,
      pageSize: Int): ZIO[Any, ExpectedFailure, UserSearchRepo.PaginatedSequence[UserSearchRepo.User]] = {
      val q = query.map(QueryStringQuery(_)).getOrElse(NoopQuery)
      elasticClient.execute {
        searchIndex(userSearchRepoIndexName).matchAllQuery.query(q).from(page).limit(pageSize)
      }.bimap(e => RepoFailure(e), res => {
        val items = res.result.to[UserSearchRepo.User]
        UserSearchRepo.PaginatedSequence(items, page, pageSize, res.result.totalHits.toInt)
      })
    }
  }
}
