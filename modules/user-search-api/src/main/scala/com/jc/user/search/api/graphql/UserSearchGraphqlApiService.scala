package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.UserSearchGraphqlApiRequestContext
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
import zio.{RIO, ZIO}

trait UserSearchGraphqlApiService {
  def getUser(request: GetUser): RIO[UserSearchGraphqlApiRequestContext, Option[User]]
  def searchUsers(request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, UserSearchResponse]
  def suggestUsers(request: SuggestRequest): RIO[UserSearchGraphqlApiRequestContext, SuggestResponse]
  def getDepartment(request: GetDepartment): RIO[UserSearchGraphqlApiRequestContext, Option[Department]]
  def searchDepartments(request: SearchRequest): RIO[UserSearchGraphqlApiRequestContext, DepartmentSearchResponse]
  def suggestDepartments(request: SuggestRequest): RIO[UserSearchGraphqlApiRequestContext, SuggestResponse]
}

object UserSearchGraphqlApiService {

  type UserSearchGraphqlApiRequestContext = RequestContext

  trait RequestContext {
    def get(name: String): Option[String]
    def names: Set[String]
  }

  def getUser(
    origin: GetUser): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[User]] = {
    ZIO.environmentWithZIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] { env =>
      env.get[UserSearchGraphqlApiService].getUser(origin).provideEnvironment(env)
    }
  }

  def searchUsers(origin: SearchRequest)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, UserSearchResponse] =
    ZIO.environmentWithZIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] { env =>
      env.get[UserSearchGraphqlApiService].searchUsers(origin).provideEnvironment(env)
    }

  def suggestUsers(
    origin: SuggestRequest): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, SuggestResponse] =
    ZIO.environmentWithZIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] { env =>
      env.get[UserSearchGraphqlApiService].suggestUsers(origin).provideEnvironment(env)
    }

  def getDepartment(origin: GetDepartment)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[Department]] =
    ZIO.environmentWithZIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] { env =>
      env.get[UserSearchGraphqlApiService].getDepartment(origin).provideEnvironment(env)
    }

  def searchDepartments(origin: SearchRequest)
    : RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, DepartmentSearchResponse] =
    ZIO.environmentWithZIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] { env =>
      env.get[UserSearchGraphqlApiService].searchDepartments(origin).provideEnvironment(env)
    }

  def suggestDepartments(
    origin: SuggestRequest): RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, SuggestResponse] =
    ZIO.environmentWithZIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] { env =>
      env.get[UserSearchGraphqlApiService].suggestDepartments(origin).provideEnvironment(env)
    }
}
