package com.jc.user.search.module.api

import com.jc.user.domain.proto.User
import com.jc.user.domain.{proto, UserEntity}
import com.jc.user.search.api.proto.ZioUserSearchApi.{RCUserSearchApiService, UserSearchApiService}
import com.jc.user.search.api.proto.{
  GetUserReq,
  GetUserRes,
  PropertySuggestion,
  SearchUserStreamReq,
  SearchUsersReq,
  SearchUsersRes,
  SuggestUsersReq,
  SuggestUsersRes
}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.auth.JwtAuthenticator
import com.jc.user.search.module.repo.UserSearchRepo
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

  final case class LiveUserSearchApiService(
    userSearchRepo: UserSearchRepo.Service,
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
      val ss = request.sorts.map { sort => (sort.field, sort.order.isAsc) }
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
      val ss = request.sorts.map { sort => (sort.field, sort.order.isAsc) }
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
  }

  val live: ZLayer[UserSearchRepo with JwtAuthenticator with Logging, Nothing, UserSearchGrpcApiHandler] =
    ZLayer.fromServices[UserSearchRepo.Service, JwtAuthenticator.Service, Logger[String], RCUserSearchApiService[Any]] {
      (userSearchRepo: UserSearchRepo.Service, jwtAuth: JwtAuthenticator.Service, logger: Logger[String]) =>
        LiveUserSearchApiService(userSearchRepo, jwtAuth, logger)
    }
}
