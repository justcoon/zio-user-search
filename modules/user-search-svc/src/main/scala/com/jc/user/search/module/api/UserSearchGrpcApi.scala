package com.jc.user.search.module.api

import com.jc.user.domain.{proto, UserEntity}
import com.jc.user.search.api.proto.UserSearchApiServiceFs2Grpc
import com.jc.user.search.api.proto.{GetUserReq, GetUserRes, SearchUsersReq, SearchUsersRes}
import com.jc.user.search.module.repo.UserSearchRepo

import io.grpc.{ServerBuilder, Status}
import zio.{Managed, ZIO}
import zio.blocking._
import zio.clock.Clock
import zio.console.Console

class UserSearchGrpcApiHandler[R <: UserSearchRepo]
    extends UserSearchApiServiceFs2Grpc[ZIO[R, Throwable, *], io.grpc.Metadata] {
  import io.scalaland.chimney.dsl._
  type F[A] = ZIO[R, Throwable, A]

  override def getUser(request: GetUserReq, ctx: io.grpc.Metadata): F[GetUserRes] = {
    import UserEntity._
    ZIO
      .accessM[R] { env =>
        env.get.find(request.id.asUserId).map { r =>
          GetUserRes(r.map(_.transformInto[proto.User]))
        }
      }
      .mapError[Throwable](e => new Exception(e))
  }

  override def searchUsers(request: SearchUsersReq, ctx: io.grpc.Metadata): F[SearchUsersRes] = {
    ZIO
      .accessM[R] { env =>
        val ss = request.sorts.map { sort =>
          (sort.field, sort.order.isAsc)
        }
        env.get.search(Some(request.query), request.page, request.pageSize, ss).map { r =>
          SearchUsersRes(r.items.map(_.transformInto[proto.User]), r.page, r.pageSize, r.count)
        }
      }
      .mapError[Throwable](e => new Exception(e))
  }
}

class GrpcServer(private val underlying: io.grpc.Server) {

  def serve() = effectBlocking {
    underlying.start()
    underlying.awaitTermination()
  }
}

object GrpcServer {

  def make(builder: ServerBuilder[_]): Managed[Throwable, io.grpc.Server] = {
    Managed.make(ZIO.effect(builder.build.start))(
      s => ZIO.effect(s.shutdown).ignore
    )
  }
//  def runServer(
//                 config: HttpApiConfig
//               ): ZIO[UserSearchRepo with Clock with Blocking with Console, Throwable, Unit] = {
//    for {
//      rts <- ZIO.runtime[Clock with Console]
//      builder = ServerBuilder
//        .forPort(config.port)
//        .addService(UserSearchApiService.bindService(rts, UserSearchGrpcApi.Live))
//      server = GrpcServer.make(builder).useForever
//      _ <- server raceAttempt serverWait
//    } yield ()
//  }
}

object UserSearchGrpcApi {
//  trait Live extends UserSearchApiService.Service[UserSearchRepo] {
//    import UserEntity._
//    import io.scalaland.chimney.dsl._
//
//    override def getUser(request: GetUserReq): ZIO[UserSearchRepo, Status, GetUserRes] = {
//      ZIO.accessM { env =>
//        env.userSearchRepo
//          .find(request.id.asUserId)
//          .bimap(
//            _ => Status.INTERNAL, //FIXME
//            r => GetUserRes(r.map(_.transformInto[proto.User])))
//      }
//    }
//
//    override def searchUsers(request: SearchUsersReq): ZIO[UserSearchRepo, Status, SearchUsersRes] = {
//      ZIO.accessM { env =>
//        env.userSearchRepo
//          .search(Some(request.query), request.page, request.pageSize)
//          .bimap(
//            _ => Status.INTERNAL, //FIXME
//            r => SearchUsersRes(r.items.map(_.transformInto[proto.User]), r.page, r.pageSize, r.count))
//      }
//    }
//  }
//
//  object Live extends Live
}
