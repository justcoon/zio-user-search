package com.jc.user.search.module.api

import com.jc.user.domain.{proto, UserEntity}
import com.jc.user.search.api.proto.ZioUserSearchApi.UserSearchApiService
import com.jc.user.search.api.proto.{
  GetUserReq,
  GetUserRes,
  PropertySuggestion,
  SearchUsersReq,
  SearchUsersRes,
  SuggestUsersReq,
  SuggestUsersRes
}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.UserSearchRepo
import io.grpc.Status
import zio.IO
import zio.ZLayer
import zio.logging.Logging
import zio.logging.Logger

object UserSearchGrpcApiHandler {

  final case class LiveUserSearchApiService(userSearchRepo: UserSearchRepo.Service, logger: Logger[String])
      extends UserSearchApiService {
    import io.scalaland.chimney.dsl._

    override def getUser(request: GetUserReq): IO[Status, GetUserRes] = {
      import UserEntity._
      userSearchRepo
        .find(request.id.asUserId)
        .map { r =>
          GetUserRes(r.map(_.transformInto[proto.User]))
        }
        .mapError[Status](_ => Status.INTERNAL)
    }

    override def searchUsers(request: SearchUsersReq): IO[Status, SearchUsersRes] = {
      val ss = request.sorts.map { sort =>
        (sort.field, sort.order.isAsc)
      }
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

  val live: ZLayer[UserSearchRepo with Logging, Nothing, UserSearchGrpcApiHandler] =
    ZLayer.fromServices[UserSearchRepo.Service, Logger[String], UserSearchApiService] {
      (userSearchRepo: UserSearchRepo.Service, logger: Logger[String]) =>
        LiveUserSearchApiService(userSearchRepo, logger)
    }
}
