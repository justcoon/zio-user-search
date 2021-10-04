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
import zio.{RIO, ZIO, ZLayer}
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
    departmentSearchRepo: DepartmentSearchRepo.Service,
    jwtAuthenticator: JwtAuthenticator.Service)
      extends UserSearchGraphqlApiService.Service {
    import io.scalaland.chimney.dsl._

    private val unauthorized = CalibanError.ExecutionError("Unauthorized")

    def authenticated(): ZIO[UserSearchGraphqlApiRequestContext, CalibanError, String] = {
      ZIO.serviceWith { context =>
        for {
          rawToken <- ZIO.getOrFailWith(unauthorized)(context.get(JwtAuthenticator.AuthHeader))
          maybeSubject <- jwtAuthenticator.authenticated(JwtAuthenticator.sanitizeBearerAuthToken(rawToken))
          subject <- ZIO.getOrFailWith(unauthorized)(maybeSubject)
        } yield subject
      }
    }

    override def getUser(request: GetUser): RIO[UserSearchGraphqlApiRequestContext, Option[User]] = {
      authenticated().flatMap { _ =>
        userSearchRepo
          .find(request.id)
          .mapError(toCalibanError)
          .map(_.map(_.transformInto[User]))
      }
    }

    override def searchUsers(request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, UserSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      userSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .mapError(toCalibanError)
        .map(r => UserSearchResponse(r.items.map(_.transformInto[User]), r.page, r.pageSize, r.count))
    }

    override def getDepartment(request: GetDepartment): RIO[UserSearchGraphqlApiRequestContext, Option[Department]] = {
      authenticated().flatMap { _ =>
        departmentSearchRepo
          .find(request.id)
          .mapError(toCalibanError)
          .map(_.map(_.transformInto[Department]))
      }
    }

    override def searchDepartments(
      request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, DepartmentSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      departmentSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .mapError(toCalibanError)
        .map(r => DepartmentSearchResponse(r.items.map(_.transformInto[Department]), r.page, r.pageSize, r.count))
    }

  }

  val live: ZLayer[
    UserSearchRepo with DepartmentSearchRepo with JwtAuthenticator,
    Nothing,
    UserSearchGraphqlApiService.UserSearchGraphqlApiService] =
    ZLayer.fromServices[
      UserSearchRepo.Service,
      DepartmentSearchRepo.Service,
      JwtAuthenticator.Service,
      UserSearchGraphqlApiService.Service] { (userSearchRepo, departmentSearchRepo, jwtAuthenticator) =>
      LiveUserSearchGraphqlApiService(userSearchRepo, departmentSearchRepo, jwtAuthenticator)
    }

  private val graphiql: HttpApp[Blocking, HttpError] = {
    val c = HttpData.fromStream(ZStream.fromResource("graphiql.html").mapError(_ => HttpError.InternalServerError()))
    Http.succeed(Response.http(content = c))
  }

  def graphqlRoutes[R <: Clock with Logging with UserSearchGraphqlApiService with JwtAuthenticator with Blocking](
    interpreter: GraphQLInterpreter[R with UserSearchGraphqlApiRequestContext, CalibanError]): HttpApp[R, HttpError] = {
    Http.route {
      case _ -> Root / "api" / "graphql" => httpService[R](interpreter) //ZHttpAdapter.makeHttpService(interpreter)
      case _ -> Root / "graphiql" => graphiql
    }
  }

  def httpService[R <: Clock with Logging with UserSearchGraphqlApiService with JwtAuthenticator](
    interpreter: GraphQLInterpreter[R with UserSearchGraphqlApiRequestContext, CalibanError]): HttpApp[R, HttpError] =
    Http
      .fromFunction[Request] { (request: Request) =>
        val context: ZLayer[R, Nothing, UserSearchGraphqlApiRequestContext] =
          ZIO.succeed(HttpRequestContext(request.headers)).toLayer

        ZHttpAdapter.makeHttpService(interpreter.provideSomeLayer[R](context))
      }
      .flatten

}
