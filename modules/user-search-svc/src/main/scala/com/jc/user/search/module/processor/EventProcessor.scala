package com.jc.user.search.module.processor

import com.jc.user.domain.DepartmentEntity.DepartmentId
import com.jc.user.domain.UserEntity.UserId
import com.jc.user.domain.proto.{DepartmentPayloadEvent, UserPayloadEvent}
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.{DepartmentSearchRepo, UserSearchRepo}
import zio.{Chunk, ZIO, ZLayer}
import zio.stream.{ZSink, ZStream}

trait EventProcessor {
  def process(eventEnvelopes: Chunk[EventProcessor.EventEnvelope[_]]): ZIO[Any, ExpectedFailure, Boolean]
}

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

  def process(eventEnvelopes: Chunk[EventEnvelope[_]]): ZIO[EventProcessor, ExpectedFailure, Boolean] =
    ZIO.serviceWithZIO[EventProcessor](_.process(eventEnvelopes))
}

final case class LiveEventProcessor(userSearchRepo: UserSearchRepo, departmentSearchRepo: DepartmentSearchRepo)
    extends EventProcessor {

  override def process(eventEnvelopes: Chunk[EventProcessor.EventEnvelope[_]]): ZIO[Any, ExpectedFailure, Boolean] = {
    val userEvents: Map[UserId, Chunk[UserPayloadEvent]] = eventEnvelopes.collect {
      case EventProcessor.EventEnvelope.User(_, event) => event
    }.groupBy(_.entityId)

    val departmentEvents: Map[DepartmentId, Chunk[DepartmentPayloadEvent]] = eventEnvelopes.collect {
      case EventProcessor.EventEnvelope.Department(_, event) => event
    }.groupBy(_.entityId)

    for {
      _ <- ZIO.when(userEvents.nonEmpty)(
        ZIO.logDebug(s"processing events - user, entityIds: ${userEvents.keySet.mkString(",")}"))
      usersRes <- LiveEventProcessor.processUsers(userEvents, userSearchRepo, departmentSearchRepo)
      _ <-
        ZIO.when(departmentEvents.nonEmpty)(
          ZIO.logDebug(s"processing events - department, entityIds: ${departmentEvents.keySet.mkString(",")}"))
      departmentRes <- LiveEventProcessor.processDepartments(departmentEvents, userSearchRepo, departmentSearchRepo)
    } yield usersRes || departmentRes
  }
}

object LiveEventProcessor {

  def processUsers(
    events: Map[UserId, Chunk[UserPayloadEvent]],
    userSearchRepo: UserSearchRepo,
    departmentSearchRepo: DepartmentSearchRepo): ZIO[Any, ExpectedFailure, Boolean] = {

    ZIO.foldLeft(events)(false) { case (r, (entityId, events)) =>
      val res =
        if (events.exists(isUserRemoved))
          userSearchRepo.delete(entityId)
        else
          userSearchRepo
            .find(entityId)
            .flatMap {
              case u @ Some(_) =>
                getUpdatedUser(entityId, events, u, departmentSearchRepo).flatMap(userSearchRepo.update)
              case None =>
                getUpdatedUser(entityId, events, None, departmentSearchRepo).flatMap(userSearchRepo.insert)
            }
      res.map(_ || r)
    }
  }

  def isUserRemoved(event: UserPayloadEvent): Boolean = {
    event.payload.isRemoved
  }

  def createUser(id: com.jc.user.domain.UserEntity.UserId): UserSearchRepo.User = UserSearchRepo.User(id, "", "", "")

  def getUpdatedUser(
    entityId: UserId,
    events: Chunk[UserPayloadEvent],
    user: Option[UserSearchRepo.User],
    departmentSearchRepo: DepartmentSearchRepo): ZIO[Any, ExpectedFailure, UserSearchRepo.User] = {
    import io.scalaland.chimney.dsl._
    import com.jc.user.domain.proto._
    import UserSearchRepo.User._

    val currentUser = user.getOrElse(createUser(entityId))

    def getDepartment(d: Option[com.jc.user.domain.proto.DepartmentRef])
      : ZIO[Any, ExpectedFailure, Option[UserSearchRepo.Department]] = {
      d match {
        case Some(d) => departmentSearchRepo.find(d.id).map(_.map(_.transformInto[UserSearchRepo.Department]))
        case _ => ZIO.succeed(None)
      }
    }

    val updatedUser = events.foldZIO(currentUser) { (currentUser, event) =>
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
          if (currentUser.department.map(_.id) != payload.value.department.map(_.id)) {
            getDepartment(payload.value.department).map { nd => departmentLens.set(currentUser)(nd) }
          } else ZIO.succeed(currentUser)

        case _ => ZIO.succeed(currentUser)
      }
    }
    updatedUser
  }

  def processDepartments(
    events: Map[DepartmentId, Chunk[DepartmentPayloadEvent]],
    userSearchRepo: UserSearchRepo,
    departmentSearchRepo: DepartmentSearchRepo): ZIO[Any, ExpectedFailure, Boolean] = {

    ZIO.foldLeft(events)(false) { case (r, (entityId, events)) =>
      val res =
        if (events.exists(isDepartmentRemoved))
          departmentSearchRepo.delete(entityId)
        else
          departmentSearchRepo
            .find(entityId)
            .flatMap {
              case d @ Some(_) =>
                for {
                  dep <- getUpdatedDepartment(entityId, events, d)
                  res <- departmentSearchRepo.update(dep)
                  _ <- updateUsersDepartment(dep, userSearchRepo)
                } yield res
              case None =>
                getUpdatedDepartment(entityId, events, None).flatMap(departmentSearchRepo.insert)
            }

      res.map(_ || r)
    }
  }

  def updateUsersDepartment(
    department: DepartmentSearchRepo.Department,
    userSearchRepo: UserSearchRepo): ZIO[Any, ExpectedFailure, Long] = {

    import io.scalaland.chimney.dsl._
    import UserSearchRepo.User._

    val pageInitial = 0
    val pageSize = 20

    val userDepartment = Some(department.transformInto[UserSearchRepo.Department])

    ZStream
      .unfoldZIO(pageInitial) { page =>
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
      .run(ZSink.count)
  }

  def isDepartmentRemoved(event: DepartmentPayloadEvent): Boolean = {
    event.payload.isRemoved
  }

  def createDepartment(id: com.jc.user.domain.DepartmentEntity.DepartmentId): DepartmentSearchRepo.Department =
    DepartmentSearchRepo.Department(id, "", "")

  def getUpdatedDepartment(
    entityId: DepartmentId,
    events: Chunk[DepartmentPayloadEvent],
    user: Option[DepartmentSearchRepo.Department]): ZIO[Any, ExpectedFailure, DepartmentSearchRepo.Department] = {
    import com.jc.user.domain.proto._
    import DepartmentSearchRepo.Department._

    val currentDepartment = user.getOrElse(createDepartment(entityId))

    val updatedDepartment = events.foldLeft(currentDepartment) { (currentDepartment, event) =>
      event match {
        case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Created, _) =>
          nameDescriptionLens.set(currentDepartment)((payload.value.name, payload.value.description))
        case DepartmentPayloadEvent(_, _, payload: DepartmentPayloadEvent.Payload.Updated, _) =>
          nameDescriptionLens.set(currentDepartment)((payload.value.name, payload.value.description))
        case _ => currentDepartment
      }
    }

    ZIO.succeed(updatedDepartment)
  }

  val layer: ZLayer[UserSearchRepo with DepartmentSearchRepo, Nothing, EventProcessor] = {
    ZLayer.fromZIO {
      for {
        userSearchRepo <- ZIO.service[UserSearchRepo]
        departmentSearchRepo <- ZIO.service[DepartmentSearchRepo]
      } yield LiveEventProcessor(userSearchRepo, departmentSearchRepo)
    }
  }
}
