package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{SearchRequest, UserSearchResponse}
import zio.{Has, UIO}

object UserSearchGraphqlApiService {
  type UserSearchGraphqlApiService = Has[Service]
  trait Service {
    def searchUsers(request: SearchRequest): UIO[UserSearchResponse]
  }
}
