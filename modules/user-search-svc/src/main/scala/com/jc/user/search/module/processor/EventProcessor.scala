package com.jc.user.search.module.processor

import com.jc.user.domain.proto.{DepartmentPayloadEvent, UserPayloadEvent}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.{DepartmentSearchRepo, UserSearchRepo}
import zio.{ZIO, ZLayer}
import zio.logging.{Logger, Logging}

object EventProcessor {

  sealed trait EventEnvelope[E] {
    def topic: String
    def event: E
  }

  object EventEnvelope {
    final case class User(topic: String, event: UserPayloadEvent) extends EventEnvelope[UserPayloadEvent]

    final case class Department(topic: String, event: DepartmentPayloadEvent)
        extends EventEnvelope[DepartmentPayloadEvent]

    final case class Unknown(topic: String, event: Array[Byte]) extends EventEnvelope[Array[Byte]]
  }

  trait Service {
    def process(eventEnvelope: EventEnvelope[_]): ZIO[Any, ExpectedFailure, Boolean]
  }

  final case class LiveEventProcessorService(
    userSearchRepo: UserSearchRepo.Service,
    departmentSearchRepo: DepartmentSearchRepo.Service,
    logger: Logger[String])
      extends EventProcessor.Service {

    val serviceLogger = logger.named(getClass.getName)

    override def process(eventEnvelope: EventEnvelope[_]): ZIO[Any, ExpectedFailure, Boolean] = {
      eventEnvelope match {
        case EventEnvelope.User(_, event) =>
          serviceLogger.log(
            s"processing event - entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
            indexUser(event, userSearchRepo)
        case EventEnvelope.Department(_, event) =>
          serviceLogger.log(
            s"processing event - entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
            indexDepartment(event, departmentSearchRepo)
        case e: EventEnvelope[_] =>
          serviceLogger.error(
            s"processing event - ${e.topic}, type: ${e.event.getClass.getSimpleName}) - unknown event, skipping") *>
            ZIO.succeed(false)
      }
    }
  }

  val live: ZLayer[UserSearchRepo with DepartmentSearchRepo with Logging, Nothing, EventProcessor] =
    ZLayer.fromServices[UserSearchRepo.Service, DepartmentSearchRepo.Service, Logger[String], EventProcessor.Service] {
      (userSearchRepo, departmentSearchRepo, logger) =>
        LiveEventProcessorService(userSearchRepo, departmentSearchRepo, logger)
    }

  def indexUser(event: UserPayloadEvent, userSearchRepo: UserSearchRepo.Service) = {
    if (isUserRemoved(event))
      userSearchRepo.delete(event.entityId)
    else
      userSearchRepo
        .find(event.entityId)
        .flatMap {
          case u @ Some(_) => userSearchRepo.update(getUpdatedUser(event, u))
          case None => userSearchRepo.insert(getUpdatedUser(event, None))
        }
  }

  def isUserRemoved(event: UserPayloadEvent): Boolean = {
    import com.jc.user.domain.proto._
    event match {
      case UserPayloadEvent(_, _, _: UserPayloadEvent.Payload.Removed, _) => true
      case _ => false
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
        val nd = payload.value.department.map(_.transformInto[UserSearchRepo.Department])
        usernameEmailPassAddressDepartmentLens.set(currentUser)(
          (
            payload.value.username,
            payload.value.email,
            payload.value.pass,
            na,
            nd
          ))

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.PasswordUpdated, _) =>
        passLens.set(currentUser)(payload.value.pass)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.EmailUpdated, _) =>
        emailLens.set(currentUser)(payload.value.email)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.AddressUpdated, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        addressLens.set(currentUser)(na)

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.DepartmentUpdated, _) =>
        val nd = payload.value.department.map(_.transformInto[UserSearchRepo.Department])
        departmentLens.set(currentUser)(nd)

      case _ => currentUser
    }
  }

  def indexDepartment(event: DepartmentPayloadEvent, departmentSearchRepo: DepartmentSearchRepo.Service) = {
    if (isDepartmentRemoved(event))
      departmentSearchRepo.delete(event.entityId)
    else
      departmentSearchRepo
        .find(event.entityId)
        .flatMap {
          case u @ Some(_) => departmentSearchRepo.update(getUpdatedDepartment(event, u))
          case None => departmentSearchRepo.insert(getUpdatedDepartment(event, None))
        }
  }

  def isDepartmentRemoved(event: DepartmentPayloadEvent): Boolean = {
    import com.jc.user.domain.proto._
    event match {
      case DepartmentPayloadEvent(_, _, _: DepartmentPayloadEvent.Payload.Removed, _) => true
      case _ => false
    }
  }

  def createDepartment(id: com.jc.user.domain.DepartmentEntity.DepartmentId): DepartmentSearchRepo.Department =
    DepartmentSearchRepo.Department(id, "", "")

  def getUpdatedDepartment(
    event: DepartmentPayloadEvent,
    user: Option[DepartmentSearchRepo.Department]): DepartmentSearchRepo.Department = {
    import com.jc.user.domain.proto._
    import DepartmentSearchRepo.Department._

    val currentDepartment = user.getOrElse(createDepartment(event.entityId))

    event match {
      case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Created, _) =>
        nameDescriptionLens.set(currentDepartment)((payload.value.name, payload.value.description))
      case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Updated, _) =>
        nameDescriptionLens.set(currentDepartment)((payload.value.name, payload.value.description))
      case _ => currentDepartment
    }
  }
}
