package com.jc.user.search.module.api

import com.jc.user.domain.UserEntity
import com.jc.user.search.api.openapi.user.{GetUserResponse, SearchUsersResponse, UserHandler}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.UserSearchRepo
import zio.ZIO

final class UserSearchOpenApiHandler[R <: UserSearchRepo] extends UserHandler[ZIO[R, Throwable, *]] {
  type F[A] = ZIO[R, Throwable, A]

  import UserEntity._
  import com.jc.user.search.api.openapi.definitions.{User, UserSearchResponse}
  import io.scalaland.chimney.dsl._

  override def searchUsers(respond: SearchUsersResponse.type)(
    query: Option[String],
    page: Int,
    pageSize: Int,
    sort: Option[Iterable[String]] = None): F[SearchUsersResponse] = {
    ZIO
      .accessM[R] { env =>
        val ss = sort.getOrElse(Seq.empty).map(UserSearchOpenApiHandler.toFieldSort)

        env.get.search(query, page, pageSize, ss)
      }
      .fold(
        e => respond.BadRequest(ExpectedFailure.getMessage(e)),
        r => {
          val items = r.items.map(_.transformInto[User]).toVector
          respond.Ok(UserSearchResponse(items, r.page, r.pageSize, r.count))
        }
      )
  }

  override def getUser(respond: GetUserResponse.type)(id: String): F[GetUserResponse] = {
    ZIO
      .accessM[R] { env =>
        env.get.find(id.asUserId).map {
          case Some(user) => respond.Ok(user.transformInto[User])
          case None => respond.NotFound
        }
      }
      .mapError[Throwable](e => new Exception(e))
  }
}

object UserSearchOpenApiHandler {

  // TODO improve parsing
  // sort - field:order, examples: username:asc, email:desc
  def toFieldSort(sort: String): UserSearchRepo.FieldSort =
    sort.split(":").toList match {
      case p :: o :: Nil if o.toLowerCase == "desc" =>
        (p, false)
      case _ =>
        (sort, true)
    }
}
