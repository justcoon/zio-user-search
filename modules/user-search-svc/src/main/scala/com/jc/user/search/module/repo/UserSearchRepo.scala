package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity
import com.jc.user.domain.UserEntity.UserId
import com.jc.user.search.model.{ExpectedFailure, RepoFailure}
import com.sksamuel.elastic4s.ElasticClient
import zio.logging.{Logger, Logging}
import zio.{Has, ZIO, ZLayer}

object UserSearchRepo {

  trait Service {
    def insert(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean]

    def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean]

    def find(id: UserEntity.UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]]

    def findAll(): ZIO[Any, ExpectedFailure, Seq[UserSearchRepo.User]]

    def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[UserSearchRepo.FieldSort])
      : ZIO[Any, ExpectedFailure, UserSearchRepo.PaginatedSequence[UserSearchRepo.User]]

    def suggest(query: String): ZIO[Any, ExpectedFailure, UserSearchRepo.SuggestResponse]
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

  final case class SuggestResponse(items: Seq[PropertySuggestions])

  final case class TermSuggestion(text: String, score: Double, freq: Int)

  final case class PropertySuggestions(property: String, suggestions: Seq[TermSuggestion])

  final case class EsUserSearchRepoService(
    elasticClient: ElasticClient,
    userSearchRepoIndexName: String,
    logger: Logger[String])
      extends UserSearchRepo.Service {

    import com.sksamuel.elastic4s.zio.instances._
    import com.sksamuel.elastic4s.ElasticDsl.{search => searchIndex, _}
    import com.sksamuel.elastic4s.requests.searches.queries.QueryStringQuery
    import com.sksamuel.elastic4s.requests.searches.queries.matches.MatchAllQuery
    import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
    import com.sksamuel.elastic4s.requests.searches.suggestion
    import com.sksamuel.elastic4s.circe._
    import io.circe.generic.auto._

    val serviceLogger = logger.named(getClass.getName)

    override def insert(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      serviceLogger.debug(s"insert - id: ${user.id}") *>
        elasticClient.execute {
          indexInto(userSearchRepoIndexName).doc(user).id(user.id)
        }.mapError(e => RepoFailure(e)).map(_.isSuccess).tapError { e =>
          serviceLogger.error(s"insert - id: ${user.id} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean] = {
      serviceLogger.debug(s"update - id: ${user.id}") *>
        elasticClient.execute {
          updateById(userSearchRepoIndexName, user.id).doc(user)
        }.mapError(e => RepoFailure(e)).map(_.isSuccess).tapError { e =>
          serviceLogger.error(s"update - id: ${user.id} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def find(id: UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]] = {
      serviceLogger.debug(s"find - id: ${id}") *>
        elasticClient.execute {
          get(userSearchRepoIndexName, id)
        }.mapError { e =>
          RepoFailure(e)
        }.map { r =>
          if (r.result.exists)
            Option(r.result.to[UserSearchRepo.User])
          else
            Option.empty
        }.tapError { e =>
          serviceLogger.error(s"find - id: ${id} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def findAll(): ZIO[Any, ExpectedFailure, Seq[UserSearchRepo.User]] = {
      serviceLogger.debug("findAll") *>
        elasticClient.execute {
          searchIndex(userSearchRepoIndexName).matchAllQuery()
        }.mapError(e => RepoFailure(e)).map(_.result.to[UserSearchRepo.User]).tapError { e =>
          serviceLogger.error(s"findAll - error: ${e.throwable.getMessage}") *>
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
      serviceLogger.debug(s"search - query: '${query
        .getOrElse("N/A")}', page: $page, pageSize: $pageSize, sorts: ${sorts.mkString("[", ",", "]")}") *>
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
          serviceLogger.error(
            s"search - query: '${query.getOrElse("N/A")}', page: $page, pageSize: $pageSize, sorts: ${sorts
              .mkString("[", ",", "]")} - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    override def suggest(query: String): ZIO[Any, ExpectedFailure, SuggestResponse] = {

      val termSuggestions = suggestFields.map { p =>
        suggestion
          .TermSuggestion(ElasticUtils.getTermSuggestionName(p), p, Some(query))
          .mode(suggestion.SuggestMode.Always)
      }

      serviceLogger.debug(s"suggest - query: '$query'") *>
        elasticClient.execute {
          searchIndex(userSearchRepoIndexName).suggestions(termSuggestions)
        }.mapError { e =>
          RepoFailure(e)
        }.flatMap { res =>
          if (res.isSuccess) {
            val elasticSuggestions = res.result.suggestions
            val suggestions = suggestFields.map { p =>
              val propertySuggestions = elasticSuggestions(ElasticUtils.getTermSuggestionName(p))
              val suggestions = propertySuggestions.flatMap { v =>
                val t = v.toTerm
                t.options.map { o =>
                  TermSuggestion(o.text, o.score, o.freq)
                }
              }

              PropertySuggestions(p, suggestions)
            }
            ZIO.succeed(SuggestResponse(suggestions))
          } else {
            ZIO.fail(RepoFailure(new Exception(ElasticUtils.getReason(res.error))))
          }
        }.tapError { e =>
          serviceLogger.error(s"suggest - query: '$query' - error: ${e.throwable.getMessage}") *>
            ZIO.fail(e)
        }
    }

    private val suggestFields = Seq("username", "email")
  }

  def elasticsearch(userSearchRepoIndexName: String): ZLayer[Has[ElasticClient] with Logging, Nothing, UserSearchRepo] =
    ZLayer.fromServices[ElasticClient, Logger[String], UserSearchRepo.Service] {
      (elasticClient: ElasticClient, logger: Logger[String]) =>
        EsUserSearchRepoService(elasticClient, userSearchRepoIndexName, logger)
    }
}
