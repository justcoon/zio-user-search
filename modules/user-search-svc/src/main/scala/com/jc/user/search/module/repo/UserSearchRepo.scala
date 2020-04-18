package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity
import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import zio.logging.{LogLevel, Logger, Logging}
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
    logger: Logger[String])
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
        }.mapError(e => RepoFailure(e)).map(_.isSuccess).tapError { e =>
          logger.error(s"insert - id: ${user.id} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      logger.log(s"update - id: ${user.id}") *>
        elasticClient.execute {
          updateIndex(user.id).in(userSearchRepoIndexName).doc(user)
        }.mapError(e => RepoFailure(e)).map(_.isSuccess).tapError { e =>
          logger.error(s"update - id: ${user.id} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]] = {
      logger.log(s"find - id: ${id}") *>
        elasticClient.execute {
          get(id).from(userSearchRepoIndexName)
        }.mapError { e =>
          RepoFailure(e)
        }.map { r =>
          if (r.result.exists)
            Option(r.result.to[UserSearchRepo.User])
          else
            Option.empty
        }.tapError { e =>
          logger.error(s"find - id: ${id} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def findAll(): ZIO[Any, ExpectedFailure, Array[UserSearchRepo.User]] = {
      logger.log("findAll") *>
        elasticClient.execute {
          searchIndex(userSearchRepoIndexName).matchAllQuery
        }.mapError(e => RepoFailure(e)).map(_.result.to[UserSearchRepo.User].toArray).tapError { e =>
          logger.error(s"findAll - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
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
            ZIO.fail(RepoFailure(new Exception(ElasticUtils.getReason(res.error))))
          }
        }.tapError { e =>
          logger.error(s"search - query: '$query', page: $page, pageSize: $pageSize, sorts: ${sorts
            .mkString("[", ",", "]")} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }
  }

  def elasticsearch(userSearchRepoIndexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepo] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepo.Service] {
      (elasticClient: ElasticClient, logger: Logger[String]) =>
        EsUserSearchRepoService(elasticClient, userSearchRepoIndexName, logger)
    }
}
