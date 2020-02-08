package c.user.search.module

import c.user.search.model.ExpectedFailure
import c.user.search.model.database.User
import zio.ZIO

package object db {

  def get(id: Long): ZIO[UserRepository, ExpectedFailure, Option[User]] =
    ZIO.accessM(_.repository.get(id))

  def create(user: User): ZIO[UserRepository, ExpectedFailure, Unit] =
    ZIO.accessM(_.repository.create(user))

  def delete(id: Long): ZIO[UserRepository, ExpectedFailure, Unit] =
    ZIO.accessM(_.repository.delete(id))
}
