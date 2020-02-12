package c.user.search.module.api

import c.user.domain.UserEntity
import c.user.search.api.openapi.user.{GetUserResponse, SearchUsersResponse, UserHandler}
import c.user.search.model.ExpectedFailure
import c.user.search.module.repo.UserSearchRepo
import zio.{RIO, ZIO}

class UserSearchOpenApiHandler[R <: UserSearchRepo] extends UserHandler[ZIO[R, Throwable, *]] {
  type F[A] = ZIO[R, Throwable, A]

  import UserEntity._
  import c.user.search.api.openapi.definitions.{User, UserSearchResponse}
  import io.scalaland.chimney.dsl._

  override def searchUsers(
    respond: SearchUsersResponse.type)(query: Option[String], page: Int, pageSize: Int): F[SearchUsersResponse] = {
    val res = ZIO.accessM[R] { env =>
      env.userSearchRepo.search(query, page, pageSize).map { res =>
        val items = res.items.map(_.transformInto[User]).toVector
        respond.Ok(UserSearchResponse(items, res.page, res.pageSize, res.count))
      }
    }
    handleError(res)
  }

  override def getUser(respond: GetUserResponse.type)(id: String): F[GetUserResponse] = {
    val res = ZIO.accessM[R] { env =>
      env.userSearchRepo.find(id.asUserId).map {
        case Some(user) => respond.Ok(user.transformInto[User])
        case None => respond.NotFound
      }
    }
    handleError(res)
  }

  private def handleError[A](result: ZIO[R, ExpectedFailure, A]): F[A] = {
    result
      .mapError[Throwable](e => new Exception(e))
  }
}
