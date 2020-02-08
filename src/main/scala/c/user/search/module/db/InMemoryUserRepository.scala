package c.user.search.module.db

import c.user.search.model.ExpectedFailure
import c.user.search.model.database.User
import zio.{Ref, ZIO}

final case class InMemoryUserRepository(ref: Ref[Map[Long, User]]) extends UserRepository.Service {

  def get(id: Long): ZIO[Any, ExpectedFailure, Option[User]] =
    for {
      user <- ref.get.map(_.get(id))
      u <- user match {
        case Some(s) => ZIO.some(s)
        case None => ZIO.none
      }
    } yield {
      u
    }

  def create(user: User): ZIO[Any, ExpectedFailure, Unit] = ref.update(map => map.+(user.id -> user)).unit

  def delete(id: Long): ZIO[Any, ExpectedFailure, Unit] = ref.update(map => map.-(id)).unit
}
