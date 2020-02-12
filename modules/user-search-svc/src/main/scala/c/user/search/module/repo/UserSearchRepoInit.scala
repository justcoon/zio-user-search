package c.user.search.module.repo

import c.user.search.module.logger.Logger
import zio.ZIO

trait UserSearchRepoInit {
  val userSearchRepoInit: UserSearchRepoInit.Service
}

object UserSearchRepoInit {

  trait Service {
    def init(): ZIO[Logger, Throwable, Boolean]
  }
}
