package com.jc.user.search.api.graphql

import caliban.schema.Annotations.GQLDescription
import caliban.schema.GenericSchema
import com.jc.user.search.api.graphql.model.{Address, Department, FieldSort, SearchRequest, User, UserSearchResponse}
import caliban.GraphQL
import caliban.GraphQL.graphQL
import caliban.RootResolver
import caliban.schema.Annotations.{GQLDeprecated, GQLDescription}
import caliban.schema.GenericSchema
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers._
import zio.URIO
import zio.clock.Clock
import zio.console.Console
import zio.duration._

import scala.language.postfixOps

object UserSearchGraphqlApi extends GenericSchema[UserSearchGraphqlApiService.UserSearchGraphqlApiService] {

  case class Queries(
    @GQLDescription("Search users")
    searchUsers: SearchRequest => URIO[UserSearchGraphqlApiService.UserSearchGraphqlApiService, UserSearchResponse]
  )

  implicit val addressSchema = gen[Address]
  implicit val departmentSchema = gen[Department]
  implicit val userSchema = gen[User]
  implicit val fieldSortSchema = gen[FieldSort]
  implicit val searchRequestSchema = gen[SearchRequest]
  implicit val userSearchResponseSchema = gen[UserSearchResponse]

//  val api: GraphQL[Console with Clock with UserSearchGraphqlApiService.UserSearchGraphqlApiService] =
//    graphQL(
//      RootResolver(
//        Queries(
//          args => ExampleService.getCharacters(args.origin)
//        )
//      )
//    ) @@
//      maxFields(200) @@               // query analyzer that limit query fields
//      maxDepth(30) @@                 // query analyzer that limit query depth
//      timeout(3 seconds) @@           // wrapper that fails slow queries
//      printSlowQueries(500 millis) @@ // wrapper that logs slow queries
//      printErrors @@                  // wrapper that logs errors
//      apolloTracing                   // wrapper for https://github.com/apollographql/apollo-tracing
}
