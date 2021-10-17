package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{
  Department,
  DepartmentSearchResponse,
  GetDepartment,
  GetUser,
  SearchRequest,
  SuggestRequest,
  SuggestResponse,
  User,
  UserSearchResponse
}
import zio.{Has, RIO}

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
    def suggestUsers(request: SuggestRequest): RIO[UserSearchGraphqlApiRequestContext, SuggestResponse]
    def getDepartment(request: GetDepartment): RIO[UserSearchGraphqlApiRequestContext, Option[Department]]
    def searchDepartments(request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, DepartmentSearchResponse]
    def suggestDepartments(request: SuggestRequest): RIO[UserSearchGraphqlApiRequestContext, SuggestResponse]
  }

  def getUser(
    origin: GetUser): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[User]] = {
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[User]] { env =>
      env.get[UserSearchGraphqlApiService.Service].getUser(origin).provide(env)
    }
  }

  def searchUsers(origin: SearchRequest)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, UserSearchResponse] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, UserSearchResponse] { env =>
      env.get[UserSearchGraphqlApiService.Service].searchUsers(origin).provide(env)
    }

  def suggestUsers(
    origin: SuggestRequest): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, SuggestResponse] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, SuggestResponse] { env =>
      env.get[UserSearchGraphqlApiService.Service].suggestUsers(origin).provide(env)
    }

  def getDepartment(origin: GetDepartment)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[Department]] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[Department]] { env =>
      env.get[UserSearchGraphqlApiService.Service].getDepartment(origin).provide(env)
    }

  def searchDepartments(origin: SearchRequest)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, DepartmentSearchResponse] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, DepartmentSearchResponse] {
      env =>
        env.get[UserSearchGraphqlApiService.Service].searchDepartments(origin).provide(env)
    }

  def suggestDepartments(
    origin: SuggestRequest): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, SuggestResponse] =
    RIO.fromFunctionM[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, SuggestResponse] { env =>
      env.get[UserSearchGraphqlApiService.Service].suggestDepartments(origin).provide(env)
    }
}
