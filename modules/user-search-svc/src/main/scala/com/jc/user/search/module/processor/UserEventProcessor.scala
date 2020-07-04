package com.jc.user.search.module.processor

import com.jc.user.domain.proto.UserPayloadEvent
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.UserSearchRepo
import zio.{ZIO, ZLayer}
import zio.logging.Logging
import zio.logging.Logger

object UserEventProcessor {

  trait Service {
    def process(event: UserPayloadEvent): ZIO[Any, ExpectedFailure, Boolean]
  }

  final case class LiveUserEventProcessorService(userSearchRepo: UserSearchRepo.Service, logger: Logger[String])
      extends UserEventProcessor.Service {

    override def process(event: UserPayloadEvent): ZIO[Any, ExpectedFailure, Boolean] = {
      logger.log(s"processing event - entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
        UserEventProcessor.index(event, userSearchRepo)
    }
  }

  val live: ZLayer[UserSearchRepo with Logging, Nothing, UserEventProcessor] =
    ZLayer.fromServices[UserSearchRepo.Service, Logger[String], UserEventProcessor.Service] {
      (userSearchRepo: UserSearchRepo.Service, logger: Logger[String]) =>
        LiveUserEventProcessorService(userSearchRepo, logger)
    }

  def index(event: UserPayloadEvent, userSearchRepo: UserSearchRepo.Service) = {
    userSearchRepo
      .find(event.entityId)
      .flatMap {
        case u @ Some(_) => userSearchRepo.update(getUpdatedUser(event, u))
        case None => userSearchRepo.insert(getUpdatedUser(event, None))
      }
  }

  def createUser(id: com.jc.user.domain.UserEntity.UserId): UserSearchRepo.User = UserSearchRepo.User(id, "", "", "")

  def getUpdatedUser(event: UserPayloadEvent, user: Option[UserSearchRepo.User]): UserSearchRepo.User = {
    import io.scalaland.chimney.dsl._
    import com.jc.user.domain.proto._
    import UserSearchRepo.User._

    val currentUser = user.getOrElse(createUser(event.entityId))

    event match {
      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.Created, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        usernameEmailPassAddressLens.set(currentUser)(
          (
            payload.value.username,
            payload.value.email,
            payload.value.pass,
            na
          ))

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.PasswordUpdated, _) =>
        passLens.set(currentUser)(payload.value.pass)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.EmailUpdated, _) =>
        emailLens.set(currentUser)(payload.value.email)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.AddressUpdated, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        addressLens.set(currentUser)(na)

      case UserPayloadEvent(_, _, _: UserPayloadEvent.Payload.Removed, _) =>
        deletedLens.set(currentUser)(true)

      case _ => currentUser
    }
  }
}
