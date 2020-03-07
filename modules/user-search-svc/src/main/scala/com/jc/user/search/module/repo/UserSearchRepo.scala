package com.jc.user.search.module.repo

import com.jc.user.domain.UserEntity
import com.jc.user.search.model.ExpectedFailure
import zio.ZIO

trait UserSearchRepo {
  val userSearchRepo: UserSearchRepo.Service
}

object UserSearchRepo {

  trait Service {
    def insert(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean]

    def update(user: UserSearchRepo.User): ZIO[Any, ExpectedFailure, Boolean]

    def find(id: UserEntity.UserId): ZIO[Any, ExpectedFailure, Option[UserSearchRepo.User]]

    def findAll(): ZIO[Any, ExpectedFailure, Array[UserSearchRepo.User]]

    def search(query: Option[String], page: Int, pageSize: Int, sorts: Iterable[UserSearchRepo.FieldSort])
      : ZIO[Any, ExpectedFailure, UserSearchRepo.PaginatedSequence[UserSearchRepo.User]]
  }

  type FieldSort = (String, Boolean)

  final case class Address(
    street: String,
    number: String,
    zip: String,
    city: String,
    state: String,
    country: String
  )

  final case class User(
    id: UserEntity.UserId,
    username: String,
    email: String,
    pass: String,
    address: Option[Address] = None,
    deleted: Boolean = false
  )

  object User {
    import shapeless._

    val usernameLens: Lens[User, String] = lens[User].username
    val emailLens: Lens[User, String] = lens[User].email
    val passLens: Lens[User, String] = lens[User].pass
    val addressLens: Lens[User, Option[Address]] = lens[User].address
    val deletedLens: Lens[User, Boolean] = lens[User].deleted

    val usernameEmailPassAddressLens = usernameLens ~ emailLens ~ passLens ~ addressLens
  }

  final case class PaginatedSequence[T](items: Seq[T], page: Int, pageSize: Int, count: Int)
}
