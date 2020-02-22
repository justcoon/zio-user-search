package com.jc.user.search.module.repo

import zio.ZIO

trait UserSearchRepoInit {
  val userSearchRepoInit: UserSearchRepoInit.Service
}

object UserSearchRepoInit {

  trait Service {
    def init(): ZIO[Any, Throwable, Boolean]
  }
}
