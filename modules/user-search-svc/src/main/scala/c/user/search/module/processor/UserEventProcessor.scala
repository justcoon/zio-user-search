package c.user.search.module.processor

import c.user.domain.proto.UserPayloadEvent
import c.user.search.model.ExpectedFailure
import c.user.search.module.repo.UserSearchRepo
import zio.ZIO

trait UserEventProcessor {
  val userEventProcessor: UserEventProcessor.Service
}

object UserEventProcessor {

  trait Service {
    def process(event: UserPayloadEvent): ZIO[UserSearchRepo, ExpectedFailure, Boolean]
  }

  def index(event: UserPayloadEvent, userSearchRepo: UserSearchRepo.Service) = {
    userSearchRepo
      .find(event.entityId)
      .flatMap {
        case u @ Some(_) => userSearchRepo.update(getUpdatedUser(event, u))
        case None => userSearchRepo.insert(getUpdatedUser(event, None))
      }
  }

  def getUpdatedUser(event: UserPayloadEvent, user: Option[UserSearchRepo.User]): UserSearchRepo.User = {
    import io.scalaland.chimney.dsl._
    val currentUser = user.getOrElse(UserSearchRepo.User(event.entityId, "", "", ""))
    import c.user.domain.proto._
    event match {
      case UserPayloadEvent(entityId, _, payload: UserPayloadEvent.Payload.Created, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        currentUser.copy(
          id = entityId,
          username = payload.value.username,
          email = payload.value.email,
          pass = payload.value.pass,
          address = na
        )

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.PasswordUpdated, _) =>
        currentUser.copy(pass = payload.value.pass)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.EmailUpdated, _) =>
        currentUser.copy(email = payload.value.email)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.AddressUpdated, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        currentUser.copy(address = na)

      case UserPayloadEvent(_, _, _: UserPayloadEvent.Payload.Removed, _) =>
        currentUser.copy(deleted = true)

      case _ => currentUser
    }
  }
}
