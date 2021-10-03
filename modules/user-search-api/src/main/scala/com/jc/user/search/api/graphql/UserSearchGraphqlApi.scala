package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{
  Address,
  Department,
  DepartmentSearchResponse,
  FieldSort,
  GetDepartment,
  GetUser,
  SearchRequest,
  User,
  UserSearchResponse
}
import caliban.schema.Annotations.GQLDescription
import caliban.schema.{ArgBuilder, GenericSchema}
import caliban.{CalibanError, GraphQL, GraphQLRequest, GraphQLResponse, RootResolver}
import caliban.GraphQL.graphQL
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrapper.OverallWrapper
import caliban.wrappers.Wrappers._
import izumi.reflect.Tag
import zio.{Has, RIO, ZIO}
import zio.clock.Clock
import zio.duration._
import shapeless.tag.@@
import zio.logging.Logging

import scala.language.postfixOps

class UserSearchGraphqlApi[C: Tag] extends GenericSchema[Has[UserSearchGraphqlApiService.Service[C] with Has[C]]] {

  case class Queries(
    @GQLDescription("Get user")
    getUser: GetUser => RIO[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], Option[User]],
    @GQLDescription("Search users")
    searchUsers: SearchRequest => RIO[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], UserSearchResponse],
    @GQLDescription("Get department")
    getDepartment: GetDepartment => RIO[Has[UserSearchGraphqlApiService.Service[C]] with Has[C], Option[Department]],
    @GQLDescription("Search departments")
    searchDepartments: SearchRequest => RIO[
      Has[UserSearchGraphqlApiService.Service[C]] with Has[C],
      DepartmentSearchResponse]
  )

  implicit def tagSchema[A, T](implicit s: Typeclass[A]): Typeclass[A @@ T] =
    s.asInstanceOf[Typeclass[A @@ T]]

  implicit def tagArgBuilder[A, T](implicit s: ArgBuilder[A]): ArgBuilder[A @@ T] = s.asInstanceOf[ArgBuilder[A @@ T]]

  implicit val addressSchema = gen[Address]
  implicit val departmentSchema = gen[Department]
  implicit val getDepartmentSchema = gen[GetDepartment]
  implicit val userSchema = gen[User]
  implicit val getUserSchema = gen[GetUser]
  implicit val fieldSortSchema = gen[FieldSort]
  implicit val searchRequestSchema = gen[SearchRequest]
  implicit val userSearchResponseSchema = gen[UserSearchResponse]
  implicit val departmentSearchResponseSchema = gen[DepartmentSearchResponse]

  implicit val queriesSchema = gen[Queries]

  def api(): GraphQL[Logging with Clock with Has[UserSearchGraphqlApiService.Service[C] with Has[C]]] =
    graphQL(
      RootResolver(
        Queries(
          args => UserSearchGraphqlApiService.getUser2[C](args),
          args => UserSearchGraphqlApiService.searchUsers2[C](args),
          args => UserSearchGraphqlApiService.getDepartment2[C](args),
          args => UserSearchGraphqlApiService.searchDepartments2[C](args)
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3 seconds) @@ // wrapper that fails slow queries
      UserSearchGraphqlApi.logSlowQueries(500 millis) @@ // wrapper that logs slow queries
      UserSearchGraphqlApi.logErrors @@ // wrapper that logs errors
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing

}

object UserSearchGraphqlApi {

  lazy val logErrors: OverallWrapper[Logging] =
    new OverallWrapper[Logging] {

      def wrap[R1 <: Logging](
        process: GraphQLRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]]
      ): GraphQLRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]] =
        request =>
          process(request).tap(response =>
            ZIO.foreach(response.errors) { e =>
              Logging.throwable("Error", e)
            })
    }

  def logSlowQueries(duration: Duration): OverallWrapper[Logging with Clock] =
    onSlowQueries(duration) { case (time, query) => Logging.debug(s"Slow query took ${time.render}:\n$query") }

}

//object UserSearchGraphqlApi extends GenericSchema[Has[UserSearchGraphqlApiService.Service[Any]]] {
//
//  case class Queries[C](
//    @GQLDescription("Get user")
//    getUser: GetUser => RIO[Has[UserSearchGraphqlApiService.Service[C]], Option[User]],
//    @GQLDescription("Search users")
//    searchUsers: SearchRequest => RIO[Has[UserSearchGraphqlApiService.Service[C]], UserSearchResponse],
//    @GQLDescription("Get department")
//    getDepartment: GetDepartment => RIO[Has[UserSearchGraphqlApiService.Service[C]], Option[Department]],
//    @GQLDescription("Search departments")
//    searchDepartments: SearchRequest => RIO[Has[UserSearchGraphqlApiService.Service[C]], DepartmentSearchResponse]
//  )
//
//  implicit def tagSchema[A, T](implicit s: UserSearchGraphqlApi.Typeclass[A]): UserSearchGraphqlApi.Typeclass[A @@ T] =
//    s.asInstanceOf[UserSearchGraphqlApi.Typeclass[A @@ T]]
//
//  implicit def tagArgBuilder[A, T](implicit s: ArgBuilder[A]): ArgBuilder[A @@ T] = s.asInstanceOf[ArgBuilder[A @@ T]]
//
//  implicit val addressSchema = gen[Address]
//  implicit val departmentSchema = gen[Department]
//  implicit val getDepartmentSchema = gen[GetDepartment]
//  implicit val userSchema = gen[User]
//  implicit val getUserSchema = gen[GetUser]
//  implicit val fieldSortSchema = gen[FieldSort]
//  implicit val searchRequestSchema = gen[SearchRequest]
//  implicit val userSearchResponseSchema = gen[UserSearchResponse]
//  implicit val departmentSearchResponseSchema = gen[DepartmentSearchResponse]
//
//  def capi[C: Tag](): GraphQL[Clock with Logging with Has[UserSearchGraphqlApiService.Service[C]] with Has[C]] =
//    graphQL(
//      RootResolver(
//        Queries[C](
//          args => UserSearchGraphqlApiService.getUser2[C](args),
//          args => UserSearchGraphqlApiService.searchUsers2[C](args),
//          args => UserSearchGraphqlApiService.getDepartment2[C](args),
//          args => UserSearchGraphqlApiService.searchDepartments2[C](args)
//        )
//      )
//    ) @@
//      maxFields(200) @@ // query analyzer that limit query fields
//      maxDepth(30) @@ // query analyzer that limit query depth
//      timeout(3 seconds) @@ // wrapper that fails slow queries
//      logSlowQueries(500 millis) @@ // wrapper that logs slow queries
//      logErrors @@ // wrapper that logs errors
//      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
//
////  val api: GraphQL[Clock with Logging with UserSearchGraphqlApiService.UserSearchGraphqlApiService] =
////    graphQL(
////      RootResolver(
////        Queries(
////          args => UserSearchGraphqlApiService.getUser(args),
////          args => UserSearchGraphqlApiService.searchUsers(args),
////          args => UserSearchGraphqlApiService.getDepartment(args),
////          args => UserSearchGraphqlApiService.searchDepartments(args)
////        )
////      )
////    ) @@
////      maxFields(200) @@ // query analyzer that limit query fields
////      maxDepth(30) @@ // query analyzer that limit query depth
////      timeout(3 seconds) @@ // wrapper that fails slow queries
////      logSlowQueries(500 millis) @@ // wrapper that logs slow queries
////      logErrors @@ // wrapper that logs errors
////      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
//
//  lazy val logErrors: OverallWrapper[Logging] =
//    new OverallWrapper[Logging] {
//
//      def wrap[R1 <: Logging](
//        process: GraphQLRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]]
//      ): GraphQLRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]] =
//        request =>
//          process(request).tap(response =>
//            ZIO.foreach(response.errors) { e =>
//              Logging.throwable("Error", e)
//            })
//    }
//
//  def logSlowQueries(duration: Duration): OverallWrapper[Logging with Clock] =
//    onSlowQueries(duration) { case (time, query) => Logging.debug(s"Slow query took ${time.render}:\n$query") }
//
//}
