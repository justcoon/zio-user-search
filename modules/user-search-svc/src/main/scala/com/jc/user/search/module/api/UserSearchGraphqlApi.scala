package com.jc.user.search.module.api

import com.jc.user.search.api.graphql.UserSearchGraphqlApiService
import com.jc.user.search.api.graphql.model.{Department, DepartmentSearchResponse, FieldSort, SearchRequest, User, UserSearchResponse}
import com.jc.user.search.module.repo.{DepartmentSearchRepo, SearchRepository, UserSearchRepo}
import zio.{Has, IO, ZIO, ZLayer}
import zhttp.http._
import caliban.{CalibanError, GraphQLInterpreter, ZHttpAdapter}
import com.jc.auth.JwtAuthenticator
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.UserSearchGraphqlApiService
import com.jc.user.search.model.ExpectedFailure
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging
import zio.stream.ZStream

object UserSearchGraphqlApiHandler {

  def toRepoFieldSort(sort: FieldSort): SearchRepository.FieldSort = {
    SearchRepository.FieldSort(sort.field, sort.asc)
  }

  final case class LiveUserSearchGraphqlApiService(
    userSearchRepo: UserSearchRepo.Service,
    departmentSearchRepo: DepartmentSearchRepo.Service)
      extends UserSearchGraphqlApiService.Service {
    import io.scalaland.chimney.dsl._

    override def searchUsers(request: SearchRequest): IO[Throwable, UserSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      userSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .mapError(e => CalibanError.ExecutionError(ExpectedFailure.getMessage(e)))
        .map(r => UserSearchResponse(r.items.map(_.transformInto[User]), r.page, r.pageSize, r.count))

    }

    override def searchDepartments(request: SearchRequest): IO[Throwable, DepartmentSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      departmentSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .mapError(e => CalibanError.ExecutionError(ExpectedFailure.getMessage(e)))
        .map(r => DepartmentSearchResponse(r.items.map(_.transformInto[Department]), r.page, r.pageSize, r.count))

    }
  }

  val live: ZLayer[
    UserSearchRepo with DepartmentSearchRepo,
    Nothing,
    UserSearchGraphqlApiService.UserSearchGraphqlApiService] =
    ZLayer.fromServices[UserSearchRepo.Service, DepartmentSearchRepo.Service, UserSearchGraphqlApiService.Service] {
      (userSearchRepo, departmentSearchRepo) =>
        LiveUserSearchGraphqlApiService(userSearchRepo, departmentSearchRepo)
    }

  private val graphiql =
    Http.succeed(Response.http(content = HttpData.fromStream(ZStream.fromResource("graphiql.html"))))

  def graphqlRoutes(
    interpreter: GraphQLInterpreter[Clock with Logging with UserSearchGraphqlApiService, CalibanError]): Http[
    Clock with Logging with UserSearchGraphqlApiService,
    HttpError,
    Request,
    Response[Clock with Logging with UserSearchGraphqlApiService with Blocking, Throwable]] = {

    Http.route {
      case _ -> Root / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter)
      case _ -> Root / "graphiql" => graphiql
    }
  }

  private val unauthorized = Http.fail(CalibanError.ExecutionError("Unauthorized"))

  def auth[R, B](app: Http[R, HttpError, Request, Response[R, HttpError]])
    : Http[R with JwtAuthenticator, Object, Request, Response[R, HttpError]] =
    Http
      .fromEffectFunction[Request] { (request: Request) =>
        ZIO
          .service[JwtAuthenticator.Service]
          .flatMap { authenticator =>
            for {
              rawToken <- ZIO.getOrFailWith(unauthorized)(request.headers
                .find(_.name == JwtAuthenticator.AuthHeader))
              maybeSubject <- authenticator.authenticated(
                JwtAuthenticator.sanitizeBearerAuthToken(rawToken.value.toString))
              _ <- ZIO.getOrFailWith(unauthorized)(maybeSubject)
            } yield app
          }
      }
      .flatten
}
