package c.user.search.module.api

import c.user.domain.{proto, UserEntity}
//import c.user.search.api.proto.userSearchApiService._
import c.user.search.api.proto.{GetUserReq, GetUserRes, SearchUsersReq, SearchUsersRes}
import c.user.search.model.config.HttpApiConfig
import c.user.search.module.repo.UserSearchRepo
import io.grpc.{ServerBuilder, Status}
import zio.{Managed, ZIO}
import zio.blocking._
import zio.clock.Clock
import zio.console.Console

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
