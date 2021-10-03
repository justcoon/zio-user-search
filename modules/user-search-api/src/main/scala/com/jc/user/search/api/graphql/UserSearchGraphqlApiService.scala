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
//  type UserSearchGraphqlApiService = Has[Service]


  trait RequestContext {
    def get(header: String): Option[String]
    def headers: Set[String]
  }



  trait Service[Context] {

    def getUser(request: GetUser): RIO[Has[Context], Option[User]]
    def searchUsers(request: SearchRequest): RIO[Has[Context], UserSearchResponse]
    def getDepartment(request: GetDepartment): RIO[Has[Context], Option[Department]]
    def searchDepartments(request: SearchRequest): RIO[Has[Context], DepartmentSearchResponse]
  }

  def getUser2[C: Tag](origin: GetUser): RIO[Has[Service[C]] with Has[C], Option[User]] = {
    RIO.fromFunctionM[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], Option[User]] { env =>
      env.get[UserSearchGraphqlApiService.Service[C]].getUser(origin).provide(env)
    }
  }

  def searchUsers2[C: Tag](
    origin: SearchRequest): RIO[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], UserSearchResponse] =
    RIO.fromFunctionM[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], UserSearchResponse] { env =>
      env.get[UserSearchGraphqlApiService.Service[C]].searchUsers(origin).provide(env)
    }

  def getDepartment2[C: Tag](
    origin: GetDepartment): RIO[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], Option[Department]] =
    RIO.fromFunctionM[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], Option[Department]] { env =>
      env.get[UserSearchGraphqlApiService.Service[C]].getDepartment(origin).provide(env)
    }

  def searchDepartments2[C: Tag](
    origin: SearchRequest): RIO[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], DepartmentSearchResponse] =
    RIO.fromFunctionM[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], DepartmentSearchResponse] { env =>
      env.get[UserSearchGraphqlApiService.Service[C]].searchDepartments(origin).provide(env)
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
