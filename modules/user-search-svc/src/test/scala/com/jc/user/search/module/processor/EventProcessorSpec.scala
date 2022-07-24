package com.jc.user.search.module.processor

import com.jc.user.domain.proto.{
  DepartmentCreatedPayload,
  DepartmentPayloadEvent,
  DepartmentRef,
  DepartmentRemovedPayload,
  DepartmentUpdatedPayload,
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
import com.jc.user.search.module.Logger
import com.jc.user.search.module.processor.EventProcessor.EventEnvelope
import zio.test.Assertion._
import zio.test._
import zio.{Chunk, ZLayer}

object EventProcessorSpec extends ZIOSpecDefault {

  val layer = ZLayer.make[UserSearchRepo with DepartmentSearchRepo with EventProcessor](
    TestUserSearchRepo.layer,
    TestDepartmentSearchRepo.layer,
    LiveEventProcessor.layer)

  val departmentTopic = "department"
  val userTopic = "user"

  val departmentCreatedEvent1 = DepartmentPayloadEvent(
    "d1".asDepartmentId,
    payload = DepartmentPayloadEvent.Payload.Created(DepartmentCreatedPayload("d1", "dep 1")))

  val departmentUpdatedEvent1 = DepartmentPayloadEvent(
    "d1".asDepartmentId,
    payload = DepartmentPayloadEvent.Payload.Updated(DepartmentUpdatedPayload("d1", "dep 1 upd")))

  val departmentRemovedEvent1 = DepartmentPayloadEvent(
    "d1".asDepartmentId,
    payload = DepartmentPayloadEvent.Payload.Removed(DepartmentRemovedPayload()))

  val userCreatedEvent1 = UserPayloadEvent(
    "u1".asUserId,
    payload = UserPayloadEvent.Payload.Created(
      UserCreatedPayload("u1", "u1@u.com", "pass", department = Some(DepartmentRef("d1".asDepartmentId))))
  )

  override def spec = suite("EventProcessorSpec")(
    test("process - create") {
      for {
        _ <- EventProcessor.process(Chunk(EventEnvelope.Department(departmentTopic, departmentCreatedEvent1)))
        _ <- EventProcessor.process(Chunk(EventEnvelope.User(userTopic, userCreatedEvent1)))
        departmentRes1 <- DepartmentSearchRepo.find(departmentCreatedEvent1.entityId)
        userRes1 <- UserSearchRepo.find(userCreatedEvent1.entityId)
      } yield {
        assert(departmentRes1.isDefined)(isTrue) && assert(userRes1.isDefined)(isTrue) && assert(
          userRes1.flatMap(_.department).map(_.name))(equalTo(departmentRes1.map(_.name)))
      }
    },
    test("process - update") {
      for {
        _ <- EventProcessor.process(Chunk(EventEnvelope.Department(departmentTopic, departmentUpdatedEvent1)))
        departmentRes1 <- DepartmentSearchRepo.find(departmentUpdatedEvent1.entityId)
        userRes1 <- UserSearchRepo.find(userCreatedEvent1.entityId)
      } yield {
        assert(departmentRes1.isDefined)(isTrue) && assert(userRes1.isDefined)(isTrue) && assert(
          userRes1.flatMap(_.department).map(_.name))(equalTo(departmentRes1.map(_.name)))
      }
    },
    test("process - remove") {
      for {
        _ <- EventProcessor.process(Chunk(EventEnvelope.Department(departmentTopic, departmentRemovedEvent1)))
        departmentRes1 <- DepartmentSearchRepo.find(departmentCreatedEvent1.entityId)
      } yield assert(departmentRes1.isEmpty)(isTrue)
    }
  ).provideLayer(layer ++ Logger.layer) @@ TestAspect.sequential
}
