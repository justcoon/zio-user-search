package com.jc.user.search.module

import com.jc.user.search.api.proto.ZioUserSearchApi.{RCUserSearchApiService, UserSearchApiService}
import zio.Has

package object api {
  type UserSearchGrpcApiHandler = Has[RCUserSearchApiService[Any]]
}
