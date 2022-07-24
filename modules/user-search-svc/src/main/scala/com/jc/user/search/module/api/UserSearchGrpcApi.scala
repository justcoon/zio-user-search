package com.jc.user.search.module.api

import com.jc.user.domain.proto
import com.jc.user.search.api.proto.ZioUserSearchApi.RCUserSearchApiService
import com.jc.user.search.api.proto.{
  FieldSort,
  GetDepartmentReq,
  GetDepartmentRes,
  GetUserReq,
  GetUserRes,
  PropertySuggestion,
  SearchDepartmentStreamReq,
  SearchDepartmentsReq,
  SearchDepartmentsRes,
  SearchUserStreamReq,
  SearchUsersReq,
  SearchUsersRes,
  SuggestDepartmentsReq,
  SuggestDepartmentsRes,
  SuggestUsersReq,
  SuggestUsersRes,
  UserView
}
import com.jc.user.search.model.ExpectedFailure
import com.jc.auth.JwtAuthenticator
import com.jc.auth.api.GrpcJwtAuth
import com.jc.user.search.module.repo.{DepartmentSearchRepo, SearchRepository, UserSearchRepo}
import io.grpc.Status
import scalapb.zio_grpc.RequestContext
import zio.{IO, ZIO, ZLayer}
import zio.stream.ZStream

object UserSearchGrpcApiHandler {

  def toRepoFieldSort(sort: FieldSort): SearchRepository.FieldSort = {
    SearchRepository.FieldSort(sort.field, sort.order.isAsc)
  }

  def toStatus(e: ExpectedFailure): Status = {
    Status.INTERNAL.withDescription(ExpectedFailure.getMessage(e))
  }

  final case class LiveUserSearchApiService(
    userSearchRepo: UserSearchRepo,
    departmentSearchRepo: DepartmentSearchRepo,
    jwtAuthenticator: JwtAuthenticator)
      extends RCUserSearchApiService[Any] {
    import io.scalaland.chimney.dsl._

    private val authenticated = GrpcJwtAuth.authenticated(jwtAuthenticator)

    override def getUser(request: GetUserReq): ZIO[RequestContext, Status, GetUserRes] = {
      for {
        _ <- authenticated
        res <- userSearchRepo
          .find(request.id)
          .mapError(toStatus)
      } yield GetUserRes(res.map(_.transformInto[UserView]))
    }

    override def searchUserStream(request: SearchUserStreamReq): ZStream[Any, Status, UserView] = {
      val ss = request.sorts.map(toRepoFieldSort)
      val q = if (request.query.isBlank) None else Some(request.query)

      val pageInitial = 0
      val pageSize = 20

      ZStream
        .unfoldZIO(pageInitial) { page =>
          userSearchRepo
            .search(q, page, pageSize, ss)
            .map { r =>
              if ((r.page * r.pageSize) < r.count || r.items.nonEmpty) Some((r.items, r.page + 1))
              else None
            }
        }
        .mapConcat(identity)
        .map(_.transformInto[UserView])
        .mapError(toStatus)
    }

    override def searchUsers(request: SearchUsersReq): IO[Status, SearchUsersRes] = {
      val ss = request.sorts.map(toRepoFieldSort)
      val q = if (request.query.isBlank) None else Some(request.query)
      userSearchRepo
        .search(q, request.page, request.pageSize, ss)
        .fold(
          e => SearchUsersRes(result = SearchUsersRes.Result.Failure(ExpectedFailure.getMessage(e))),
          r =>
            SearchUsersRes(
              r.items.map(_.transformInto[UserView]),
              r.page,
              r.pageSize,
              r.count,
              SearchUsersRes.Result.Success(""))
        )
    }

    override def suggestUsers(request: SuggestUsersReq): IO[Status, SuggestUsersRes] = {
      userSearchRepo
        .suggest(request.query)
        .fold(
          e => SuggestUsersRes(result = SuggestUsersRes.Result.Failure(ExpectedFailure.getMessage(e))),
          r => SuggestUsersRes(r.items.map(_.transformInto[PropertySuggestion]), SuggestUsersRes.Result.Success(""))
        )
    }

    override def getDepartment(request: GetDepartmentReq): ZIO[RequestContext, Status, GetDepartmentRes] = {
      for {
        _ <- authenticated
        res <- departmentSearchRepo
          .find(request.id)
          .mapError(toStatus)
      } yield GetDepartmentRes(res.map(_.transformInto[proto.Department]))
    }

    override def searchDepartments(request: SearchDepartmentsReq): IO[Status, SearchDepartmentsRes] = {
      val ss = request.sorts.map(toRepoFieldSort)
      val q = if (request.query.isBlank) None else Some(request.query)
      departmentSearchRepo
        .search(q, request.page, request.pageSize, ss)
        .fold(
          e => SearchDepartmentsRes(result = SearchDepartmentsRes.Result.Failure(ExpectedFailure.getMessage(e))),
          r =>
            SearchDepartmentsRes(
              r.items.map(_.transformInto[proto.Department]),
              r.page,
              r.pageSize,
              r.count,
              SearchDepartmentsRes.Result.Success(""))
        )
    }

    override def searchDepartmentStream(request: SearchDepartmentStreamReq): ZStream[Any, Status, proto.Department] = {
      val ss = request.sorts.map(toRepoFieldSort)
      val q = if (request.query.isBlank) None else Some(request.query)

      val pageInitial = 0
      val pageSize = 20

      ZStream
        .unfoldZIO(pageInitial) { page =>
          departmentSearchRepo
            .search(q, page, pageSize, ss)
            .map { r =>
              if ((r.page * r.pageSize) < r.count || r.items.nonEmpty) Some((r.items, r.page + 1))
              else None
            }
        }
        .mapConcat(identity)
        .map(_.transformInto[proto.Department])
        .mapError(toStatus)
    }

    override def suggestDepartments(request: SuggestDepartmentsReq): IO[Status, SuggestDepartmentsRes] = {
      departmentSearchRepo
        .suggest(request.query)
        .fold(
          e => SuggestDepartmentsRes(result = SuggestDepartmentsRes.Result.Failure(ExpectedFailure.getMessage(e))),
          r =>
            SuggestDepartmentsRes(
              r.items.map(_.transformInto[PropertySuggestion]),
              SuggestDepartmentsRes.Result.Success(""))
        )
    }

  }

  val layer
    : ZLayer[UserSearchRepo with DepartmentSearchRepo with JwtAuthenticator, Nothing, RCUserSearchApiService[Any]] =
    ZLayer.fromZIO {
      for {
        userSearchRepo <- ZIO.service[UserSearchRepo]
        departmentSearchRepo <- ZIO.service[DepartmentSearchRepo]
        jwtAuthenticator <- ZIO.service[JwtAuthenticator]
      } yield LiveUserSearchApiService(userSearchRepo, departmentSearchRepo, jwtAuthenticator)
    }
}
