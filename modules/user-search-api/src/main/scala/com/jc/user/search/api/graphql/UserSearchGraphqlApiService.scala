package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{
  Department,
  DepartmentSearchResponse,
  GetDepartment,
  GetUser,
  SearchRequest,
  User,
  UserSearchResponse
}
import zio.{Has, IO, RIO}

object UserSearchGraphqlApiService {
  type UserSearchGraphqlApiService = Has[Service]

  trait Service {

    def getUser(request: GetUser): IO[Throwable, Option[User]]
    def searchUsers(request: SearchRequest): IO[Throwable, UserSearchResponse]
    def getDepartment(request: GetDepartment): IO[Throwable, Option[Department]]
    def searchDepartments(request: SearchRequest): IO[Throwable, DepartmentSearchResponse]
  }

  def getUser(origin: GetUser): RIO[UserSearchGraphqlApiService, Option[User]] =
    RIO.serviceWith(_.getUser(origin))

  def searchUsers(origin: SearchRequest): RIO[UserSearchGraphqlApiService, UserSearchResponse] =
    RIO.serviceWith(_.searchUsers(origin))

  def getDepartment(origin: GetDepartment): RIO[UserSearchGraphqlApiService, Option[Department]] =
    RIO.serviceWith(_.getDepartment(origin))

  def searchDepartments(origin: SearchRequest): RIO[UserSearchGraphqlApiService, DepartmentSearchResponse] =
    RIO.serviceWith(_.searchDepartments(origin))
}
