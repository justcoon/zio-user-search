package com.jc.user.search.module.processor

import com.jc.user.domain.proto.UserPayloadEvent
import com.jc.user.search.model.ExpectedFailure
import com.jc.user.search.module.repo.UserSearchRepo
import com.jc.user.search.module.repo.UserSearchRepo.Service
import zio.ZIO
import zio.logging.Logger

trait LiveUserEventProcessor extends UserEventProcessor {
  val userSearchRepo: Service
  val logger: Logger

  override val userEventProcessor: UserEventProcessor.Service = new UserEventProcessor.Service {

    override def process(event: UserPayloadEvent): ZIO[Any, ExpectedFailure, Boolean] = {
      logger.log(s"processing event - entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
        UserEventProcessor.index(event, userSearchRepo)
    }
  }
}
