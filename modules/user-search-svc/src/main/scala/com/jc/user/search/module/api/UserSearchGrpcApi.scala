package com.jc.user.search.module.api

import com.jc.user.domain.{proto, UserEntity}

import com.jc.user.search.api.proto.ZioUserSearchApi.UserSearchApiService
import com.jc.user.search.api.proto.{GetUserReq, GetUserRes, SearchUsersReq, SearchUsersRes}
import com.jc.user.search.module.repo.UserSearchRepo
import io.grpc.Status
import zio.IO
import zio.ZLayer
import zio.logging.Logging
import zio.logging.Logger

object UserSearchGrpcApiHandler {

  final case class LiveUserSearchApiService(userSearchRepo: UserSearchRepo.Service, logger: Logger)
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
      userSearchRepo
        .search(Some(request.query), request.page, request.pageSize, ss)
        .map { r =>
          SearchUsersRes(r.items.map(_.transformInto[proto.User]), r.page, r.pageSize, r.count)
        }
        .mapError[Status](_ => Status.INTERNAL)
    }
  }

  val live: ZLayer[UserSearchRepo with Logging, Nothing, UserSearchGrpcApiHandler] =
    ZLayer.fromServices[UserSearchRepo.Service, Logging.Service, UserSearchApiService] {
      (userSearchRepo: UserSearchRepo.Service, logger: Logging.Service) =>
        LiveUserSearchApiService(userSearchRepo, logger.logger)
    }
}
