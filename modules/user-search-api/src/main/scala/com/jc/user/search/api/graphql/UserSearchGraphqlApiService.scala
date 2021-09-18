package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{DepartmentSearchResponse, SearchRequest, UserSearchResponse}
import zio.{Has, UIO, URIO}

object UserSearchGraphqlApiService {
  type UserSearchGraphqlApiService = Has[Service]

  trait Service {
    def searchUsers(request: SearchRequest): UIO[UserSearchResponse]
    def searchDepartments(request: SearchRequest): UIO[DepartmentSearchResponse]
  }

  def searchUsers(origin: SearchRequest): URIO[UserSearchGraphqlApiService, UserSearchResponse] =
    URIO.serviceWith(_.searchUsers(origin))

  def searchDepartments(origin: SearchRequest): URIO[UserSearchGraphqlApiService, DepartmentSearchResponse] =
    URIO.serviceWith(_.searchDepartments(origin))
}
