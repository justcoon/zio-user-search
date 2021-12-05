package com.jc.user.search.module.processor

import com.jc.user.domain.proto.{DepartmentCreatedPayload, DepartmentPayloadEvent}
import com.jc.user.search.module.repo.{
  DepartmentSearchRepo,
  TestDepartmentSearchRepo,
  TestUserSearchRepo,
  UserSearchRepo
}
import com.jc.user.domain.DepartmentEntity._
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

  override def spec = suite("EventProcessorSpec")(
    testM("process") {
      val de1 = DepartmentPayloadEvent(
        "d1".asDepartmentId,
        payload = DepartmentPayloadEvent.Payload.Created(DepartmentCreatedPayload("d1", "dep 1")))

      for {
        _ <- EventProcessor.process(EventEnvelope.Department("department", de1))
        r <- ZIO.accessM[DepartmentSearchRepo](_.get.find(de1.entityId))
      } yield assert(r.isDefined)(isTrue)
    }.provideLayer(layer)
  )
}
