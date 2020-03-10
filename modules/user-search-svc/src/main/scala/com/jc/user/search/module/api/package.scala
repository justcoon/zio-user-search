package com.jc.user.search.module

import com.jc.user.search.api.proto.ZioUserSearchApi.UserSearchApiService

import zio.Has

package object api {
  type UserSearchGrpcApiHandler = Has[UserSearchApiService]
}
