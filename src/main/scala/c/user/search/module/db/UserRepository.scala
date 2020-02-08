package c.user.search.module.db

import c.user.search.model.ExpectedFailure
import c.user.search.model.database.User
import zio.ZIO

trait UserRepository {

  val repository: UserRepository.Service
}

object UserRepository {

  trait Service {

    def get(id: Long): ZIO[Any, ExpectedFailure, Option[User]]

    def create(user: User): ZIO[Any, ExpectedFailure, Unit]

    def delete(id: Long): ZIO[Any, ExpectedFailure, Unit]
  }
}
