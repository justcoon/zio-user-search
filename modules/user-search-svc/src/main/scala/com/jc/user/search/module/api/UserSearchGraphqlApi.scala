package com.jc.user.search.module.api

import com.jc.user.search.api.graphql.UserSearchGraphqlApiService
import com.jc.user.search.api.graphql.model.{
  Department,
  DepartmentSearchResponse,
  FieldSort,
  SearchRequest,
  User,
  UserSearchResponse
}
import com.jc.user.search.module.repo.{DepartmentSearchRepo, SearchRepository, UserSearchRepo}
import zio.{UIO, ZLayer}
import zhttp.http._
import caliban.{CalibanError, GraphQLInterpreter, ZHttpAdapter}
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.UserSearchGraphqlApiService
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
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

    override def searchUsers(request: SearchRequest): UIO[UserSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      userSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .fold(
          _ => UserSearchResponse(Seq.empty, 0, 0, 0), // FIXME error handling
          r => UserSearchResponse(r.items.map(_.transformInto[User]), r.page, r.pageSize, r.count))

    }

    override def searchDepartments(request: SearchRequest): UIO[DepartmentSearchResponse] = {
      val ss = request.sorts.getOrElse(Seq.empty).map(toRepoFieldSort)
      departmentSearchRepo
        .search(request.query, request.page, request.pageSize, ss)
        .fold(
          _ => DepartmentSearchResponse(Seq.empty, 0, 0, 0), // FIXME error handling
          r => DepartmentSearchResponse(r.items.map(_.transformInto[Department]), r.page, r.pageSize, r.count)
        )

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
    interpreter: GraphQLInterpreter[Console with Clock with UserSearchGraphqlApiService, CalibanError]): Http[
    Console with Clock with UserSearchGraphqlApiService,
    HttpError,
    Request,
    Response[Console with Clock with UserSearchGraphqlApiService with Blocking, Throwable]] = {

    Http.route {
      case _ -> Root / "api" / "graphql" => ZHttpAdapter.makeHttpService(interpreter)
      case _ -> Root / "graphiql" => graphiql
    }
  }
}
