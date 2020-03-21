package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity
import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import zio.logging.Logging
import zio.logging.Logger
import zio.{Has, ZIO, ZLayer}

object UserSearchRepo {

  trait Service {
    def insert(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean]

    def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean]

    def find(id: UserEntity.UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]]

    def findAll(): ZIO[Any, ExpectedFailure, Array[UserSearchRepo.User]]

    def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[UserSearchRepo.FieldSort])
      : ZIO[Any, ExpectedFailure, UserSearchRepo.PaginatedSequence[UserSearchRepo.User]]
  }

  type FieldSort = (String, Boolean)

  final case class Address(
    street: String,
    number: String,
    zip: String,
    city: String,
    state: String,
    country: String
  )

  final case class User(
    id: UserEntity.UserId,
    username: String,
    email: String,
    pass: String,
    address: Option[Address] = None,
    deleted: Boolean = false
  )

  object User {
    import shapeless._

    val usernameLens: Lens[User, String] = lens[User].username
    val emailLens: Lens[User, String] = lens[User].email
    val passLens: Lens[User, String] = lens[User].pass
    val addressLens: Lens[User, Option[Address]] = lens[User].address
    val deletedLens: Lens[User, Boolean] = lens[User].deleted

    val usernameEmailPassAddressLens = usernameLens ~ emailLens ~ passLens ~ addressLens
  }

  final case class PaginatedSequence[T](items: Seq[T], page: Int, pageSize: Int, count: Int)

  final case class EsUserSearchRepoService(
    elasticClient: ElasticClient,
    userSearchRepoIndexName: String,
    logger: Logger)
      extends UserSearchRepo.Service {
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

  def elasticsearch(
    userSearchRepoIndexName: String): ZLayer[Has[ElasticClient] with Logging.Logging, Nothing, UserSearchRepo] =
    ZLayer.fromServices[ElasticClient, Logging.Service, UserSearchRepo.Service] {
      (elasticClient: ElasticClient, logger: Logging.Service) =>
        EsUserSearchRepoService(elasticClient, userSearchRepoIndexName, logger.logger)
    }
}
