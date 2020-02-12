package c.user.search.module.processor

import c.user.domain.proto.UserPayloadEvent
import c.user.search.model.ExpectedFailure
import c.user.search.module.repo.UserSearchRepo
import zio.ZIO

trait LiveUserEventProcessor extends UserEventProcessor {

  override val userEventProcessor: UserEventProcessor.Service = new UserEventProcessor.Service {
    override def process(event: UserPayloadEvent): ZIO[UserSearchRepo, ExpectedFailure, Boolean] = {
      ZIO.accessM { env =>
        UserEventProcessor.index(event, env.userSearchRepo)
      }
    }
  }
}
