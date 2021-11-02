package com.jc.user.search.module.api

import com.jc.api.openapi.OpenApiCirceMerger
import com.jc.user.search.api.openapi.definitions.{
  Department,
  DepartmentSearchResponse,
  PropertySuggestion,
  SuggestResponse,
  TermSuggestion,
  User,
  UserSearchResponse
}
import com.jc.user.search.api.openapi.user.{
  GetDepartmentResponse,
  GetUserResponse,
  SearchDepartmentsResponse,
  SearchUsersResponse,
  SuggestDepartmentsResponse,
  SuggestUsersResponse,
  UserHandler,
  UserResource
}
import com.jc.user.search.model.ExpectedFailure
import com.jc.auth.JwtAuthenticator
import com.jc.auth.api.HttpJwtAuth
import com.jc.logging.LoggingSystem
import com.jc.logging.api.LoggingSystemOpenApiHandler
import com.jc.user.search.module.repo.{DepartmentSearchRepo, SearchRepository, UserSearchRepo}
import org.http4s.{Headers, HttpRoutes}
import org.http4s.server.Router
import sttp.tapir.swagger.SwaggerUI
import zio.ZIO
import zio.blocking.Blocking
import zio.clock.Clock

import scala.io.Source

final class UserSearchOpenApiHandler[R <: UserSearchRepo with DepartmentSearchRepo with JwtAuthenticator]
    extends UserHandler[ZIO[R, Throwable, *], Headers] {
  type F[A] = ZIO[R, Throwable, A]

  import io.scalaland.chimney.dsl._

  override def getDepartment(respond: GetDepartmentResponse.type)(id: com.jc.user.domain.DepartmentEntity.DepartmentId)(
    extracted: Headers): F[GetDepartmentResponse] = {
    ZIO.services[DepartmentSearchRepo.Service, JwtAuthenticator.Service].flatMap { case (repo, authenticator) =>
      HttpJwtAuth
        .authenticated(extracted, authenticator)
        .foldM(
          _ => ZIO.succeed(respond.Unauthorized),
          _ =>
            repo
              .find(id)
              .map {
                case Some(dep) => respond.Ok(dep.transformInto[Department])
                case None => respond.NotFound
              }
              .mapError[Throwable](e => new Exception(e))
        )
    }
  }

  override def searchDepartments(respond: SearchDepartmentsResponse.type)(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sort: Option[Iterable[String]])(extracted: Headers): F[SearchDepartmentsResponse] = {
    ZIO
      .accessM[DepartmentSearchRepo] { env =>
        val ss = sort.getOrElse(Seq.empty).map(UserSearchOpenApiHandler.toFieldSort)
        env.get.search(query, page, pageSize, ss)
      }
      .fold(
        e => respond.BadRequest(ExpectedFailure.getMessage(e)),
        r => {
          val items = r.items.map(_.transformInto[Department]).toVector
          respond.Ok(DepartmentSearchResponse(items, r.page, r.pageSize, r.count))
        }
      )
  }

  override def suggestDepartments(respond: SuggestDepartmentsResponse.type)(query: Option[String])(
    extracted: Headers): F[SuggestDepartmentsResponse] = {
    ZIO
      .accessM[DepartmentSearchRepo] { env => env.get.suggest(query.getOrElse("")) }
      .fold(
        e => respond.BadRequest(ExpectedFailure.getMessage(e)),
        r => {
          val items = r.items
            .map(
              _.into[PropertySuggestion]
                .withFieldComputed(_.suggestions, v => v.suggestions.map(_.transformInto[TermSuggestion]).toVector)
                .transform)
            .toVector
          respond.Ok(SuggestResponse(items))
        }
      )
  }

  override def getUser(respond: GetUserResponse.type)(id: com.jc.user.domain.UserEntity.UserId)(
    extracted: Headers): F[GetUserResponse] = {
    ZIO.services[UserSearchRepo.Service, JwtAuthenticator.Service].flatMap { case (repo, authenticator) =>
      HttpJwtAuth
        .authenticated(extracted, authenticator)
        .foldM(
          _ => ZIO.succeed(respond.Unauthorized),
          _ =>
            repo
              .find(id)
              .map {
                case Some(user) => respond.Ok(user.transformInto[User])
                case None => respond.NotFound
              }
              .mapError[Throwable](e => new Exception(e))
        )
    }
  }

  override def searchUsers(respond: SearchUsersResponse.type)(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sort: Option[Iterable[String]] = None)(extracted: Headers): F[SearchUsersResponse] = {
    ZIO
      .accessM[UserSearchRepo] { env =>
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

  override def suggestUsers(respond: SuggestUsersResponse.type)(query: Option[String])(
    extracted: Headers): F[SuggestUsersResponse] = {
    ZIO
      .accessM[UserSearchRepo] { env => env.get.suggest(query.getOrElse("")) }
      .fold(
        e => respond.BadRequest(ExpectedFailure.getMessage(e)),
        r => {
          val items = r.items
            .map(
              _.into[PropertySuggestion]
                .withFieldComputed(_.suggestions, v => v.suggestions.map(_.transformInto[TermSuggestion]).toVector)
                .transform)
            .toVector
          respond.Ok(SuggestResponse(items))
        }
      )
  }
}

object UserSearchOpenApiHandler {

  // TODO improve parsing
  // sort - field:order, examples: username:asc, email:desc
  def toFieldSort(sort: String): SearchRepository.FieldSort =
    sort.split(":").toList match {
      case p :: o :: Nil =>
        SearchRepository.FieldSort(p, o.toLowerCase != "desc")
      case _ =>
        SearchRepository.FieldSort(sort, true)
    }

  def userSearchApiRoutes[E <: UserSearchRepo with DepartmentSearchRepo with JwtAuthenticator with Clock with Blocking]
    : HttpRoutes[ZIO[E, Throwable, *]] = {
    import zio.interop.catz._
    new UserResource[ZIO[E, Throwable, *], Headers](customExtract = _ => req => req.headers)
      .routes(new UserSearchOpenApiHandler[E]())
  }

  def httpRoutes[
    E <: UserSearchRepo with DepartmentSearchRepo with LoggingSystem with JwtAuthenticator with Clock with Blocking]
    : HttpRoutes[ZIO[E, Throwable, *]] = {
    import zio.interop.catz._
    import sttp.tapir.server.http4s.ztapir._
    val y1 = Source.fromResource("UserSearchOpenApi.yaml").mkString
    val y2 = Source.fromResource("LoggingSystemOpenApi.yaml").mkString
    val my = OpenApiCirceMerger().mergeYamls(y1, y2)
    val yaml = my.getOrElse("")

    val docRoutes = ZHttp4sServerInterpreter().from(SwaggerUI[ZIO[E, Throwable, *]](yaml)).toRoutes

    val usApiRoutes: HttpRoutes[ZIO[E, Throwable, *]] = userSearchApiRoutes

    val lsApiRoutes: HttpRoutes[ZIO[E, Throwable, *]] = LoggingSystemOpenApiHandler.loggingSystemApiRoutes

    Router("/" -> usApiRoutes, "/" -> lsApiRoutes, "/" -> docRoutes)
  }

}
