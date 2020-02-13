package c.user.search.module.processor

import c.user.domain.proto.UserPayloadEvent
import c.user.search.model.ExpectedFailure
import c.user.search.module.repo.UserSearchRepo
import zio.ZIO
import zio.logging.Logger

trait LiveUserEventProcessor extends UserEventProcessor {
  val userSearchRepo: UserSearchRepo.Service
  val logger: Logger

  override val userEventProcessor: UserEventProcessor.Service = new UserEventProcessor.Service {

    override def process(event: UserPayloadEvent): ZIO[Any, ExpectedFailure, Boolean] = {
      logger.log(s"processing event - entityId: ${event.entityId}, type: ${event.payload.getClass.getSimpleName}") *>
        UserEventProcessor.index(event, userSearchRepo)
    }
  }
}
