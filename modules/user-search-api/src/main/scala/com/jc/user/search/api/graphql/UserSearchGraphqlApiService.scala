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
import izumi.reflect.Tag
import zio.{Has, IO, RIO, ZIO}

object UserSearchGraphqlApiService {
  type UserSearchGraphqlApiService = Has[Service]

  type UserSearchGraphqlApiRequestContext = Has[RequestContext]

  trait RequestContext {
    def get(name: String): Option[String]
    def names: Set[String]
  }

  trait Service {
    def getUser(request: GetUser): RIO[UserSearchGraphqlApiRequestContext, Option[User]]
    def searchUsers(request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, UserSearchResponse]
    def getDepartment(request: GetDepartment): RIO[UserSearchGraphqlApiRequestContext, Option[Department]]
    def searchDepartments(request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, DepartmentSearchResponse]
  }

  def getUser2(
    origin: GetUser): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[User]] = {
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[User]] { env =>
      env.get[UserSearchGraphqlApiService.Service].getUser(origin).provide(env)
    }
  }

  def searchUsers2(origin: SearchRequest)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, UserSearchResponse] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, UserSearchResponse] { env =>
      env.get[UserSearchGraphqlApiService.Service].searchUsers(origin).provide(env)
    }

  def getDepartment2(origin: GetDepartment)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[Department]] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[Department]] { env =>
      env.get[UserSearchGraphqlApiService.Service].getDepartment(origin).provide(env)
    }

  def searchDepartments2(origin: SearchRequest)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, DepartmentSearchResponse] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, DepartmentSearchResponse] {
      env =>
        env.get[UserSearchGraphqlApiService.Service].searchDepartments(origin).provide(env)
    }

//  def getUser(origin: GetUser): RIO[UserSearchGraphqlApiService, Option[User]] =
//    RIO.serviceWith(_.getUser(origin))
//
//  def searchUsers(origin: SearchRequest): RIO[UserSearchGraphqlApiService, UserSearchResponse] =
//    RIO.serviceWith(_.searchUsers(origin))
//
//  def getDepartment(origin: GetDepartment): RIO[UserSearchGraphqlApiService, Option[Department]] =
//    RIO.serviceWith(_.getDepartment(origin))
//
//  def searchDepartments(origin: SearchRequest): RIO[UserSearchGraphqlApiService, DepartmentSearchResponse] =
//    RIO.serviceWith(_.searchDepartments(origin))
}
