package com.jc.user.search.module.processor

import com.jc.user.domain.proto.{DepartmentPayloadEvent, UserPayloadEvent}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.{DepartmentSearchRepo, UserSearchRepo}
import zio.{ZIO, ZLayer}
import zio.logging.{Logger, Logging}
import zio.stream.{Sink, ZStream}

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
        case EventEnvelope.User(topic, event) =>
          serviceLogger.log(
            s"processing event - topic: ${topic}, entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
            processUser(event, userSearchRepo, departmentSearchRepo)
        case EventEnvelope.Department(topic, event) =>
          serviceLogger.log(
            s"processing event - topic: ${topic}, entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
            processDepartment(event, userSearchRepo, departmentSearchRepo)
        case e: EventEnvelope[_] =>
          serviceLogger.error(
            s"processing event - topic: ${e.topic}, type: ${e.event.getClass.getSimpleName}) - unknown event, skipping") *>
            ZIO.succeed(false)
      }
    }
  }

  val live: ZLayer[UserSearchRepo with DepartmentSearchRepo with Logging, Nothing, EventProcessor] =
    ZLayer.fromServices[UserSearchRepo.Service, DepartmentSearchRepo.Service, Logger[String], EventProcessor.Service] {
      (userSearchRepo, departmentSearchRepo, logger) =>
        LiveEventProcessorService(userSearchRepo, departmentSearchRepo, logger)
    }

  def processUser(
    event: UserPayloadEvent,
    userSearchRepo: UserSearchRepo.Service,
    departmentSearchRepo: DepartmentSearchRepo.Service): ZIO[Any, ExpectedFailure, Boolean] = {
    if (isUserRemoved(event))
      userSearchRepo.delete(event.entityId)
    else
      userSearchRepo
        .find(event.entityId)
        .flatMap {
          case u @ Some(_) => getUpdatedUser(event, u, departmentSearchRepo).flatMap(userSearchRepo.update)
          case None => getUpdatedUser(event, None, departmentSearchRepo).flatMap(userSearchRepo.insert)
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

  def getUpdatedUser(
    event: UserPayloadEvent,
    user: Option[UserSearchRepo.User],
    departmentSearchRepo: DepartmentSearchRepo.Service): ZIO[Any, ExpectedFailure, UserSearchRepo.User] = {
    import io.scalaland.chimney.dsl._
    import com.jc.user.domain.proto._
    import UserSearchRepo.User._

    val currentUser = user.getOrElse(createUser(event.entityId))

    def getDepartment(d: Option[com.jc.user.domain.proto.DepartmentRef])
      : ZIO[Any, ExpectedFailure, Option[UserSearchRepo.Department]] = {
      d match {
        case Some(d) => departmentSearchRepo.find(d.id).map(_.map(_.transformInto[UserSearchRepo.Department]))
        case _ => ZIO.succeed(None)
      }
    }

    event match {
      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.Created, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        getDepartment(payload.value.department).map { nd =>
          usernameEmailPassAddressDepartmentLens.set(currentUser)(
            (
              payload.value.username,
              payload.value.email,
              payload.value.pass,
              na,
              nd
            ))
        }

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.PasswordUpdated, _) =>
        ZIO.succeed(passLens.set(currentUser)(payload.value.pass))

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.EmailUpdated, _) =>
        ZIO.succeed(emailLens.set(currentUser)(payload.value.email))

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.AddressUpdated, _) =>
        val na = payload.value.address.map(_.transformInto[UserSearchRepo.Address])
        ZIO.succeed(addressLens.set(currentUser)(na))

      case UserPayloadEvent(_, _, payload: UserPayloadEvent.Payload.DepartmentUpdated, _) =>
        getDepartment(payload.value.department).map { nd => departmentLens.set(currentUser)(nd) }

      case _ => ZIO.succeed(currentUser)
    }
  }

  def processDepartment(
    event: DepartmentPayloadEvent,
    userSearchRepo: UserSearchRepo.Service,
    departmentSearchRepo: DepartmentSearchRepo.Service): ZIO[Any, ExpectedFailure, Boolean] = {
    if (isDepartmentRemoved(event))
      departmentSearchRepo.delete(event.entityId)
    else
      departmentSearchRepo
        .find(event.entityId)
        .flatMap {
          case d @ Some(_) =>
            for {
              dep <- getUpdatedDepartment(event, d)
              res <- departmentSearchRepo.update(dep)
              _ <- updateUsersDepartment(dep, userSearchRepo)
            } yield res
          case None => getUpdatedDepartment(event, None).flatMap(departmentSearchRepo.insert)
        }
  }

  def updateUsersDepartment(
    department: DepartmentSearchRepo.Department,
    userSearchRepo: UserSearchRepo.Service): ZIO[Any, ExpectedFailure, Long] = {

    import io.scalaland.chimney.dsl._
    import UserSearchRepo.User._

    val pageInitial = 0
    val pageSize = 20

    val userDepartment = Some(department.transformInto[UserSearchRepo.Department])

    ZStream
      .unfoldM(pageInitial) { page =>
        userSearchRepo
          .searchByDepartment(department.id, page, pageSize)
          .map { r =>
            if ((r.page * r.pageSize) < r.count || r.items.nonEmpty) Some((r.items, r.page + 1))
            else None
          }
      }
      .mapConcat(identity)
      .map(user => departmentLens.set(user)(userDepartment))
      .tap(userSearchRepo.update)
      .run(Sink.count)
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
    user: Option[DepartmentSearchRepo.Department]): ZIO[Any, ExpectedFailure, DepartmentSearchRepo.Department] = {
    import com.jc.user.domain.proto._
    import DepartmentSearchRepo.Department._

    val currentDepartment = user.getOrElse(createDepartment(event.entityId))

    event match {
      case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Created, _) =>
        ZIO.succeed(nameDescriptionLens.set(currentDepartment)((payload.value.name, payload.value.description)))
      case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Updated, _) =>
        ZIO.succeed(nameDescriptionLens.set(currentDepartment)((payload.value.name, payload.value.description)))
      case _ => ZIO.succeed(currentDepartment)
    }
  }
}
