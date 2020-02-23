package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import zio.ZIO
import zio.logging.{LogLevel, Logger}

trait EsUserSearchRepo extends UserSearchRepo {
  val elasticClient: ElasticClient

  val userSearchRepoIndexName: String

  val logger: Logger

  override val userSearchRepo: UserSearchRepo.Service = new UserSearchRepo.Service {
    import com.sksamuel.elastic4s.zio.instances._
    import com.sksamuel.elastic4s.ElasticDsl.{update => updateIndex, search => searchIndex, _}
    import com.sksamuel.elastic4s.requests.searches.queries.{NoopQuery, QueryStringQuery}
    import com.sksamuel.elastic4s.circe._
    import io.circe.generic.auto._

    override def insert(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      logger.log(s"insert - id: ${user.id}") *>
        elasticClient.execute {
          indexInto(userSearchRepoIndexName).doc(user).id(user.id)
        }.bimap(e => RepoFailure(e), _.isSuccess)
    }

    override def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      logger.log(s"update - id: ${user.id}") *>
        elasticClient.execute {
          updateIndex(user.id).in(userSearchRepoIndexName).doc(user)
        }.bimap(e => RepoFailure(e), _.isSuccess)
    }

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]] = {
      logger.log(s"find - id: ${id}") *>
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
      logger.log("findAll") *>
        elasticClient.execute {
          searchIndex(userSearchRepoIndexName).matchAllQuery
        }.bimap(e => RepoFailure(e), _.result.to[UserSearchRepo.User].toArray)
    }

    override def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[UserSearchRepo.FieldSort])
      : ZIO[Any, ExpectedFailure, UserSearchRepo.PaginatedSequence[UserSearchRepo.User]] = {
      val q = query.map(QueryStringQuery(_)).getOrElse(MatchAllQuery())
      val ss = sorts.map {
        case (property, asc) =>
          val o = if (asc) SortOrder.Asc else SortOrder.Desc
          FieldSort(property, order = o)
      }
      logger.log(s"search - query: '$query', page: $page, pageSize: $pageSize, sorts: ${sorts.mkString("[", ",", "]")}") *>
        elasticClient.execute {
          searchIndex(userSearchRepoIndexName).query(q).from(page * pageSize).limit(pageSize).sortBy(ss)
        }.mapError { e =>
          RepoFailure(e)
        }.flatMap { res =>
          if (res.isSuccess) {
            val items = res.result.to[UserSearchRepo.User]
            ZIO.succeed(UserSearchRepo.PaginatedSequence(items, page, pageSize, res.result.totalHits.toInt))
          } else {
            ZIO.fail(RepoFailure(res.error.asException))
          }
        }
    }
  }
}
