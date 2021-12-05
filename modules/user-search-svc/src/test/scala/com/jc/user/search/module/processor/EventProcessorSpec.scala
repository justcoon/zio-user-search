package com.jc.user.search.module.processor

import com.jc.user.domain.proto.{
  DepartmentCreatedPayload,
  DepartmentPayloadEvent,
  DepartmentRef,
  UserCreatedPayload,
  UserPayloadEvent
}
import com.jc.user.search.module.repo.{
  DepartmentSearchRepo,
  TestDepartmentSearchRepo,
  TestUserSearchRepo,
  UserSearchRepo
}
import com.jc.user.domain.DepartmentEntity._
import com.jc.user.domain.UserEntity._
import com.jc.user.search.module.processor.EventProcessor.EventEnvelope
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion._
import zio.test._
import zio.{ZIO, ZLayer}
import zio.magic._

object EventProcessorSpec extends DefaultRunnableSpec {

  val layer = ZLayer.fromMagic[UserSearchRepo with DepartmentSearchRepo with Logging with EventProcessor](
    Slf4jLogger.make((_, message) => message),
    TestUserSearchRepo.test,
    TestDepartmentSearchRepo.test,
    EventProcessor.live)

  val departmentTopic = "department"
  val userTopic = "user"

  val departmentCreatedEvent1 = DepartmentPayloadEvent(
    "d1".asDepartmentId,
    payload = DepartmentPayloadEvent.Payload.Created(DepartmentCreatedPayload("d1", "dep 1")))

  val userCreatedEvent1 = UserPayloadEvent(
    "u1".asUserId,
    payload = UserPayloadEvent.Payload.Created(
      UserCreatedPayload("u1", "u1@u.com", "pass", department = Some(DepartmentRef("d1".asDepartmentId))))
  )

  override def spec = suite("EventProcessorSpec")(
    testM("process") {
      for {
        _ <- EventProcessor.process(EventEnvelope.Department(departmentTopic, departmentCreatedEvent1))
        _ <- EventProcessor.process(EventEnvelope.User(userTopic, userCreatedEvent1))
        departmentRes1 <- ZIO.accessM[DepartmentSearchRepo](_.get.find(departmentCreatedEvent1.entityId))
        userRes1 <- ZIO.accessM[UserSearchRepo](_.get.find(userCreatedEvent1.entityId))
      } yield assert(departmentRes1.isDefined)(isTrue) && assert(userRes1.isDefined)(isTrue)
    }.provideLayer(layer)
  )
}
