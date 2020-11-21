package com.jc.user.search.module.api

import com.jc.user.domain.UserEntity
import com.jc.user.search.api.openapi.definitions.{PropertySuggestion, TermSuggestion, UserSuggestResponse}
import com.jc.user.search.api.openapi.user.{
  GetUserResponse,
  SearchUsersResponse,
  SuggestUsersResponse,
  UserHandler,
  UserResource
}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.UserSearchRepo
import org.http4s.HttpRoutes
import org.http4s.server.Router
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import zio.ZIO
import zio.blocking.Blocking
import zio.clock.Clock

import scala.io.Source

final class UserSearchOpenApiHandler[R <: UserSearchRepo] extends UserHandler[ZIO[R, Throwable, *]] {
  type F[A] = ZIO[R, Throwable, A]

  import UserEntity._
  import com.jc.user.search.api.openapi.definitions.{User, UserSearchResponse}
  import io.scalaland.chimney.dsl._

  override def searchUsers(respond: SearchUsersResponse.type)(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sort: Option[Iterable[String]] = None): F[SearchUsersResponse] = {
    ZIO
      .accessM[R] { env =>
        val ss = sort.getOrElse(Seq.empty).map(UserSearchOpenApiHandler.toFieldSort)

        env.get.search(query, page, pageSize, ss)
      }
      .fold(
        e => respond.BadRequest(ExpectedFailure.getMessage(e)),
        r => {
          val items = r.items.map(_.transformInto[User]).toVector
          respond.Ok(UserSearchResponse(items, r.page, r.pageSize, r.count))
        }
      )
  }

  override def suggestUsers(respond: SuggestUsersResponse.type)(
    query: Option[String]): ZIO[R, Throwable, SuggestUsersResponse] = {
    ZIO
      .accessM[R] { env => env.get.suggest(query.getOrElse("")) }
      .fold(
        e => respond.BadRequest(ExpectedFailure.getMessage(e)),
        r => {
          val items = r.items
            .map(
              _.into[PropertySuggestion]
                .withFieldComputed(_.suggestions, v => v.suggestions.map(_.transformInto[TermSuggestion]).toVector)
                .transform)
            .toVector
          respond.Ok(UserSuggestResponse(items))
        }
      )
  }

  override def getUser(respond: GetUserResponse.type)(id: String): F[GetUserResponse] = {
    ZIO
      .accessM[R] { env =>
        env.get.find(id.asUserId).map {
          case Some(user) => respond.Ok(user.transformInto[User])
          case None => respond.NotFound
        }
      }
      .mapError[Throwable](e => new Exception(e))
  }
}

object UserSearchOpenApiHandler {

  // TODO improve parsing
  // sort - field:order, examples: username:asc, email:desc
  def toFieldSort(sort: String): UserSearchRepo.FieldSort =
    sort.split(":").toList match {
      case p :: o :: Nil =>
        (p, o.toLowerCase != "desc")
      case _ =>
        (sort, true)
    }

  def httpRoutes[E <: UserSearchRepo]() = {
    import zio.interop.catz._
    import sttp.tapir.server.http4s.ztapir._
    val yaml = Source.fromResource("UserSearchOpenApi.yaml").mkString

    val docRoutes: HttpRoutes[ZIO[E, Throwable, *]] = new SwaggerHttp4s(yaml).routes

    val userSearchApiRoutes: HttpRoutes[ZIO[E, Throwable, *]] =
      new UserResource[ZIO[E, Throwable, *]]().routes(new UserSearchOpenApiHandler[E]())

    Router("/" -> userSearchApiRoutes, "/" -> docRoutes)
  }

}
