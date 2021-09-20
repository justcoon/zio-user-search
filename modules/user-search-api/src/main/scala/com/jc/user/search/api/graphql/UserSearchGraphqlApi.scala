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
import zio.{RIO, ZIO}
import zio.clock.Clock
import zio.duration._
import shapeless.tag.@@
import zio.logging.Logging

import scala.language.postfixOps

object UserSearchGraphqlApi extends GenericSchema[UserSearchGraphqlApiService.UserSearchGraphqlApiService] {

  case class Queries(
    @GQLDescription("Get user")
    getUser: GetUser => RIO[UserSearchGraphqlApiService.UserSearchGraphqlApiService, Option[User]],
    @GQLDescription("Search users")
    searchUsers: SearchRequest => RIO[UserSearchGraphqlApiService.UserSearchGraphqlApiService, UserSearchResponse],
    @GQLDescription("Get department")
    getDepartment: GetDepartment => RIO[UserSearchGraphqlApiService.UserSearchGraphqlApiService, Option[Department]],
    @GQLDescription("Search departments")
    searchDepartments: SearchRequest => RIO[
      UserSearchGraphqlApiService.UserSearchGraphqlApiService,
      DepartmentSearchResponse]
  )

  implicit def tagSchema[A, T](implicit s: UserSearchGraphqlApi.Typeclass[A]): UserSearchGraphqlApi.Typeclass[A @@ T] =
    s.asInstanceOf[UserSearchGraphqlApi.Typeclass[A @@ T]]

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

  val api: GraphQL[Clock with Logging with UserSearchGraphqlApiService.UserSearchGraphqlApiService] =
    graphQL(
      RootResolver(
        Queries(
          args => UserSearchGraphqlApiService.getUser(args),
          args => UserSearchGraphqlApiService.searchUsers(args),
          args => UserSearchGraphqlApiService.getDepartment(args),
          args => UserSearchGraphqlApiService.searchDepartments(args)
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3 seconds) @@ // wrapper that fails slow queries
      logSlowQueries(500 millis) @@ // wrapper that logs slow queries
      logErrors @@ // wrapper that logs errors
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing

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
