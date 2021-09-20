package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{DepartmentSearchResponse, SearchRequest, UserSearchResponse}
import zio.{Has, IO, RIO}

object UserSearchGraphqlApiService {
  type UserSearchGraphqlApiService = Has[Service]

  trait Service {
    def searchUsers(request: SearchRequest): IO[Throwable, UserSearchResponse]
    def searchDepartments(request: SearchRequest): IO[Throwable, DepartmentSearchResponse]
  }

  def searchUsers(origin: SearchRequest): RIO[UserSearchGraphqlApiService, UserSearchResponse] =
    RIO.serviceWith(_.searchUsers(origin))

  def searchDepartments(origin: SearchRequest): RIO[UserSearchGraphqlApiService, DepartmentSearchResponse] =
    RIO.serviceWith(_.searchDepartments(origin))
}
