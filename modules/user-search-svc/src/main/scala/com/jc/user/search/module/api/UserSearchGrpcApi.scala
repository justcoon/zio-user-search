package com.jc.user.search.module.api

import com.jc.user.domain.proto.{Department, User}
import com.jc.user.domain.{proto, DepartmentEntity, UserEntity}
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
  SuggestUsersRes
}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.auth.JwtAuthenticator
import com.jc.user.search.module.repo.{DepartmentSearchRepo, SearchRepository, UserSearchRepo}
import io.grpc.Status
import scalapb.zio_grpc.RequestContext
import zio.{Has, IO, ZIO, ZLayer}
import zio.logging.Logging
import zio.logging.Logger
import zio.stream.ZStream

object UserSearchGrpcApiHandler {

  import io.grpc.Metadata

  private val jwtAuthHeader: Metadata.Key[String] =
    Metadata.Key.of(JwtAuthenticator.AuthHeader, Metadata.ASCII_STRING_MARSHALLER)

  def authenticated(authenticator: JwtAuthenticator.Service): ZIO[Has[RequestContext], Status, String] = {
    for {
      ctx <- ZIO.service[scalapb.zio_grpc.RequestContext]
      maybeHeader <- ctx.metadata.get(jwtAuthHeader)
      subject <- maybeHeader.flatMap { rawToken => authenticator.authenticated(rawToken) } match {
        case Some(subject) => ZIO.succeed(subject)
        case None => ZIO.fail(io.grpc.Status.UNAUTHENTICATED)
      }
    } yield {
      subject
    }
  }

  def toRepoFieldSort(sort: FieldSort): SearchRepository.FieldSort = {
    SearchRepository.FieldSort(sort.field, sort.order.isAsc)
  }

  final case class LiveUserSearchApiService(
    userSearchRepo: UserSearchRepo.Service,
    departmentSearchRepo: DepartmentSearchRepo.Service,
    jwtAuthenticator: JwtAuthenticator.Service,
    logger: Logger[String])
      extends RCUserSearchApiService[Any] {
    import io.scalaland.chimney.dsl._

    override def getUser(request: GetUserReq): ZIO[Has[RequestContext], Status, GetUserRes] = {
      import UserEntity._

      val res: ZIO[Has[RequestContext], Status, GetUserRes] = for {
        _ <- authenticated(jwtAuthenticator)
        res <- userSearchRepo
          .find(request.id.asUserId)
          .mapError[Status](e => Status.INTERNAL.withDescription(ExpectedFailure.getMessage(e)))
      } yield GetUserRes(res.map(_.transformInto[proto.User]))

      res
    }

    override def searchUserStream(request: SearchUserStreamReq): ZStream[Any, Status, User] = {
      val ss = request.sorts.map(toRepoFieldSort)
      val q = if (request.query.isBlank) None else Some(request.query)

      val pageInitial = 0
      val pageSize = 20

      ZStream
        .unfoldM(pageInitial) { page =>
          userSearchRepo
            .search(q, page, pageSize, ss)
            .map { r =>
              if ((r.page * r.pageSize) < r.count || r.items.nonEmpty) Some((r.items, r.page + 1))
              else None
            }
        }
        .mapConcat(identity)
        .map(_.transformInto[proto.User])
        .mapError { e => Status.INTERNAL.withDescription(ExpectedFailure.getMessage(e)) }
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
              r.items.map(_.transformInto[proto.User]),
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

    override def getDepartment(request: GetDepartmentReq): ZIO[Has[RequestContext], Status, GetDepartmentRes] = {
      import DepartmentEntity._

      val res: ZIO[Has[RequestContext], Status, GetDepartmentRes] = for {
        _ <- authenticated(jwtAuthenticator)
        res <- departmentSearchRepo
          .find(request.id.asDepartmentId)
          .mapError[Status](e => Status.INTERNAL.withDescription(ExpectedFailure.getMessage(e)))
      } yield GetDepartmentRes(res.map(_.transformInto[proto.Department]))

      res
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

    override def searchDepartmentStream(request: SearchDepartmentStreamReq): ZStream[Any, Status, Department] = {
      val ss = request.sorts.map(toRepoFieldSort)
      val q = if (request.query.isBlank) None else Some(request.query)

      val pageInitial = 0
      val pageSize = 20

      ZStream
        .unfoldM(pageInitial) { page =>
          departmentSearchRepo
            .search(q, page, pageSize, ss)
            .map { r =>
              if ((r.page * r.pageSize) < r.count || r.items.nonEmpty) Some((r.items, r.page + 1))
              else None
            }
        }
        .mapConcat(identity)
        .map(_.transformInto[proto.Department])
        .mapError { e => Status.INTERNAL.withDescription(ExpectedFailure.getMessage(e)) }
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

  val live: ZLayer[
    UserSearchRepo with DepartmentSearchRepo with JwtAuthenticator with Logging,
    Nothing,
    UserSearchGrpcApiHandler] =
    ZLayer.fromServices[
      UserSearchRepo.Service,
      DepartmentSearchRepo.Service,
      JwtAuthenticator.Service,
      Logger[String],
      RCUserSearchApiService[Any]] { (userSearchRepo, departmentSearchRepo, jwtAuth, logger) =>
      LiveUserSearchApiService(userSearchRepo, departmentSearchRepo, jwtAuth, logger)
    }
}
