package com.jc.user.search.module.processor

import com.jc.user.domain.proto.DepartmentPayloadEvent
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.DepartmentSearchRepo
import zio.{ZIO, ZLayer}
import zio.logging.{Logger, Logging}

object DepartmentEventProcessor {

  trait Service {
    def process(event: DepartmentPayloadEvent): ZIO[Any, ExpectedFailure, Boolean]
  }

  final case class LiveDepartmentEventProcessorService(
    departmentSearchRepo: DepartmentSearchRepo.Service,
    logger: Logger[String])
      extends DepartmentEventProcessor.Service {

    val serviceLogger = logger.named(getClass.getName)

    override def process(event: DepartmentPayloadEvent): ZIO[Any, ExpectedFailure, Boolean] = {
      serviceLogger.log(
        s"processing event - entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
        DepartmentEventProcessor.index(event, departmentSearchRepo)
    }
  }

  val live: ZLayer[DepartmentSearchRepo with Logging, Nothing, DepartmentEventProcessor] =
    ZLayer.fromServices[DepartmentSearchRepo.Service, Logger[String], DepartmentEventProcessor.Service] {
      (departmentSearchRepo: DepartmentSearchRepo.Service, logger: Logger[String]) =>
        LiveDepartmentEventProcessorService(departmentSearchRepo, logger)
    }

  def index(event: DepartmentPayloadEvent, departmentSearchRepo: DepartmentSearchRepo.Service) = {
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
    import io.scalaland.chimney.dsl._
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
