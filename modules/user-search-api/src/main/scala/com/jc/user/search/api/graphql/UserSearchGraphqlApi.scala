package com.jc.user.search.api.graphql

import com.jc.user.search.api.graphql.model.{
  Address,
  Department,
  DepartmentSearchResponse,
  FieldSort,
  GetDepartment,
  GetUser,
  PropertySuggestion,
  SearchRequest,
  SuggestRequest,
  SuggestResponse,
  TermSuggestion,
  User,
  UserSearchResponse
}
import caliban.schema.Annotations.GQLDescription
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.{CalibanError, GraphQL, GraphQLInterpreter, GraphQLRequest, GraphQLResponse, RootResolver}
import caliban.GraphQL.graphQL
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrapper.OverallWrapper
import caliban.wrappers.Wrappers._
import com.jc.user.domain.DepartmentEntity.{DepartmentId, DepartmentIdTag}
import com.jc.user.domain.UserEntity.{UserId, UserIdTag}
import com.jc.user.search.api.graphql.UserSearchGraphqlApiService.UserSearchGraphqlApiRequestContext

import zio.{RIO, ZIO, ZLayer}

import shapeless.tag.@@

import scala.language.postfixOps
import zio._

object UserSearchGraphqlApi extends GenericSchema[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] {

  type UserSearchGraphqlApiInterpreter =
    GraphQLInterpreter[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, CalibanError]

  final case class Queries(
    @GQLDescription("Get user")
    getUser: GetUser => RIO[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext, Option[User]],
    @GQLDescription("Search users")
    searchUsers: SearchRequest => RIO[
      UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      UserSearchResponse],
    @GQLDescription("Suggest users")
    suggestUsers: SuggestRequest => RIO[
      UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      SuggestResponse],
    @GQLDescription("Get department")
    getDepartment: GetDepartment => RIO[
      UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      Option[Department]],
    @GQLDescription("Search departments")
    searchDepartments: SearchRequest => RIO[
      UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      DepartmentSearchResponse],
    @GQLDescription("Suggest departments")
    suggestDepartments: SuggestRequest => RIO[
      UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext,
      SuggestResponse]
  )

  implicit def tagArgBuilder[A, T](implicit s: ArgBuilder[A]): ArgBuilder[A @@ T] = s.asInstanceOf[ArgBuilder[A @@ T]]

  implicit val userIdArgBuilder: ArgBuilder[UserId] = tagArgBuilder[String, UserIdTag]
  implicit val departmentIdArgBuilder: ArgBuilder[DepartmentId] = tagArgBuilder[String, DepartmentIdTag]

  implicit def taggedStringSchema[T]: Schema[Any, String @@ T] = Schema.stringSchema.contramap(identity)

  implicit val departmentId: Schema[Any, DepartmentId] = taggedStringSchema[DepartmentIdTag]
  implicit val userId: Schema[Any, UserId] = taggedStringSchema[UserIdTag]
  implicit val addressSchema: Schema[Any, Address] = Schema.gen
  implicit val departmentSchema: Schema[Any, Department] = Schema.gen
  implicit val getDepartmentSchema: Schema[Any, GetDepartment] = Schema.gen
  implicit val userSchema: Schema[Any, User] = Schema.gen
  implicit val getUserSchema: Schema[Any, GetUser] = Schema.gen
  implicit val fieldSortSchema: Schema[Any, FieldSort] = Schema.gen
  implicit val searchRequestSchema: Schema[Any, SearchRequest] = Schema.gen
  implicit val userSearchResponseSchema: Schema[Any, UserSearchResponse] = Schema.gen
  implicit val departmentSearchResponseSchema: Schema[Any, DepartmentSearchResponse] = Schema.gen
  implicit val suggestRequestSchema: Schema[Any, SuggestRequest] = Schema.gen
  implicit val termSuggestionSchema: Schema[Any, TermSuggestion] = Schema.gen
  implicit val propertySuggestionSchema: Schema[Any, PropertySuggestion] = Schema.gen
  implicit val suggestResponseSchema: Schema[Any, SuggestResponse] = Schema.gen

  val api: GraphQL[UserSearchGraphqlApiService with UserSearchGraphqlApiRequestContext] =
    graphQL(
      RootResolver(
        Queries(
          args => UserSearchGraphqlApiService.getUser(args),
          args => UserSearchGraphqlApiService.searchUsers(args),
          args => UserSearchGraphqlApiService.suggestUsers(args),
          args => UserSearchGraphqlApiService.getDepartment(args),
          args => UserSearchGraphqlApiService.searchDepartments(args),
          args => UserSearchGraphqlApiService.suggestDepartments(args)
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3 seconds) @@ // wrapper that fails slow queries
      logSlowQueries(500 millis) @@ // wrapper that logs slow queries
      logErrors @@ // wrapper that logs errors
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing

  val apiInterpreter: ZLayer[Any, CalibanError.ValidationError, UserSearchGraphqlApiInterpreter] =
    ZLayer.fromZIO(api.interpreter)

  lazy val logErrors: OverallWrapper[Any] =
    new OverallWrapper[Any] {

      def wrap[R1 <: Any](
        process: GraphQLRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]]
      ): GraphQLRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]] =
        request =>
          process(request).tap(response =>
            ZIO.foreach(response.errors) { e =>
              ZIO.logError(s"Error: $e")
            })
    }

  def logSlowQueries(duration: Duration): OverallWrapper[Any] =
    onSlowQueries(duration) { case (time, query) => ZIO.logDebug(s"Slow query took ${time.render}:\n$query") }

}
