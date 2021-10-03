package com.jc.user.search.module.api

import com.jc.user.search.api.graphql.UserSearchGraphqlApiService
import com.jc.user.search.api.graphql.model.{
  Department,
  DepartmentSearchResponse,
  FieldSort,
  GetDepartment,
  GetUser,
  SearchRequest,
  User,
  UserSearchResponse
}
import com.jc.user.search.module.repo.{DepartmentSearchRepo, SearchRepository, UserSearchRepo}
import zio.{Has, IO, URIO, ZIO, ZLayer}
import zhttp.http._
import caliban.{CalibanError, GraphQLInterpreter, ZHttpAdapter}
import com.jc.auth.JwtAuthenticator
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.{
  UserSearchGraphqlApiRequestContext,
  UserSearchGraphqlApiService
}
import com.jc.user.search.model.ExpectedFailure
import zio.blocking.Blocking
import zio.clock.Clock
import zio.logging.Logging
import zio.stream.ZStream

object UserSearchGraphqlApiHandler {

  def toRepoFieldSort(sort: FieldSort): SearchRepository.FieldSort = {
    SearchRepository.FieldSort(sort.field, sort.asc)
  }

  def toCalibanError(e: ExpectedFailure): CalibanError = {
    CalibanError.ExecutionError(ExpectedFailure.getMessage(e))
  }

  final case class HttpRequestContext(headers: List[Header]) extends UserSearchGraphqlApiService.RequestContext {

    override def get(name: String): Option[String] = {
      headers.find(_.name == name).map(_.value.toString)
    }
    override val names: Set[String] = headers.map(_.name.toString).toSet
  }

  final case class LiveUserSearchGraphqlApiService(
    userSearchRepo: UserSearchRepo.Service,
    departmentSearchRepo: DepartmentSearchRepo.Service)
      extends UserSearchGraphqlApiService.Service {
    import io.scalaland.chimney.dsl._

    override def getUser(request: GetUser): IO[Throwable, Option[User]] = {
      userSearchRepo
        .find(request.id)
        .mapError(toCalibanError)
        .map(_.map(_.transformInto[User]))
    }

    override def searchUsers(request: SearchRequest): IO[Throwable, UserSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      userSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .mapError(toCalibanError)
        .map(r => UserSearchResponse(r.items.map(_.transformInto[User]), r.page, r.pageSize, r.count))
    }

    override def getDepartment(request: GetDepartment): IO[Throwable, Option[Department]] = {
      departmentSearchRepo
        .find(request.id)
        .mapError(toCalibanError)
        .map(_.map(_.transformInto[Department]))
    }

    override def searchDepartments(request: SearchRequest): IO[Throwable, DepartmentSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      departmentSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .mapError(toCalibanError)
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

  def graphqlRoutes[R <: Clock with Logging with UserSearchGraphqlApiService with JwtAuthenticator](
    interpreter: GraphQLInterpreter[R, CalibanError]): Http[
    R with UserSearchGraphqlApiRequestContext,
    HttpError,
    Request,
    Response[R with UserSearchGraphqlApiRequestContext with Blocking, Throwable]] = {

    Http.route {
      case _ -> Root / "api" / "graphql" => httpService(interpreter) //ZHttpAdapter.makeHttpService(interpreter)
      case _ -> Root / "graphiql" => graphiql
    }
  }

  private val unauthorized = CalibanError.ExecutionError("Unauthorized")

  def httpService[R <: Clock with Logging with UserSearchGraphqlApiService with JwtAuthenticator](
    interpreter: GraphQLInterpreter[R, CalibanError]) =
    Http
      .fromFunction[Request] { (request: Request) =>
        val context = ZIO.succeed(HttpRequestContext(request.headers)).toLayer

        ZHttpAdapter.makeHttpService(interpreter.provideSomeLayer[R with UserSearchGraphqlApiRequestContext](context))
      }
      .flatten

  def auth[R, B](app: Http[R, HttpError, Request, Response[R, HttpError]])
    : Http[R with JwtAuthenticator, Throwable, Request, Response[R, HttpError]] =
    Http
      .fromEffectFunction[Request] { (request: Request) =>
        ZIO
          .service[JwtAuthenticator.Service]
          .flatMap { authenticator =>
            val res = for {
              rawToken <- ZIO.getOrFailWith(unauthorized)(request.headers
                .find(_.name == JwtAuthenticator.AuthHeader))
              maybeSubject <- authenticator.authenticated(
                JwtAuthenticator.sanitizeBearerAuthToken(rawToken.value.toString))
              subject <- ZIO.getOrFailWith(unauthorized)(maybeSubject)
            } yield subject

            res.fold(e => Http.fail(e), _ => app)
          }
      }
      .flatten
}
