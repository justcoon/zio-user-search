package com.jc.user.search.module

import zio.Has

package object repo {
  type UserSearchRepo = Has[UserSearchRepo.Service]
  type UserSearchRepoInit = Has[UserSearchRepoInit.Service]
}
