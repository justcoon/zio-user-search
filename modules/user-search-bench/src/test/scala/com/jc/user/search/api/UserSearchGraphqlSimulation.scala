package com.jc.user.search.api

import com.jc.auth.JwtAuthenticator
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.syntax._
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import pureconfig.ConfigSource

final class UserSearchGraphqlSimulation extends Simulation {

  import Feeders._

  val config = ConfigFactory.load
  val apiConfig = ConfigSource.fromConfig(config.getConfig("rest-api")).loadOrThrow[HttpApiConfig]

  val httpConf: HttpProtocolBuilder = http.baseUrl(s"http://${apiConfig.address.value}:${apiConfig.port.value}")

  val jwtToken =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJ6aW8tdXNlci1zZWFyY2giLCJzdWIiOiJ0ZXN0IiwiZXhwIjoyMjE1Mjg1MDU5LCJpYXQiOjE2MTA0ODUwNTl9.MONRFj9rSf23AV7rCCPfkyqHWVhHkI42R93CK5QHpxMSsb9oc_65YpWsmDfdX2IzzKVqdSP59rL_3_CRK_C4dg"

  final case class GraphqlRequest[V: Encoder](query: String, variables: Map[String, V] = Map.empty[String, V])

  object GraphqlRequest {
    import io.circe.generic.semiauto._
    implicit def encoder[V: Encoder]: Encoder[GraphqlRequest[V]] = deriveEncoder[GraphqlRequest[V]]
  }

  val getDepartmentExpression: Expression[String] = s => {
    val id = s.attributes.getOrElse("id", "d1").toString
    val q = """
              |query getDepartment($id: String!) {
              |  getDepartment(id: $id) {
              |      id
              |      name
              |      description
              |}
              |""".stripMargin
    GraphqlRequest(q, Map("id" -> id)).asJson.noSpaces
  }

  val getUser: Expression[String] = s => {
    val id = s.attributes.getOrElse("id", "d1").toString
    val q = """
              |query getUser($id: String!) {
              |  getUser(id: $id) {
              |      id
              |      email
              |      address {
              |        country
              |      }
              |      department {
              |        name
              |      }
              |  }
              |}
              |""".stripMargin
    GraphqlRequest(q, Map("id" -> id)).asJson.noSpaces
  }

  val searchUser: Expression[String] = s => {
    val id = s.attributes.getOrElse("id", "d1").toString
    val q = """
              |query searchUsers($query: String!) {
              |  searchUsers(query: $query, page: 0, pageSize: 10, sorts:[{
              |    field: "email"
              |    asc:true
              |  }]) {
              |    count
              |    items {
              |      id
              |      email
              |      address {
              |        country
              |      }
              |      department {
              |        name
              |      }
              |    }
              |  }
              |}
              |""".stripMargin
    GraphqlRequest(q, Map("query" -> id)).asJson.noSpaces
  }

  val suggestUser: Expression[String] = s => {
    val id = s.attributes.getOrElse("id", "d1").toString
    val q = """
              |query suggestUsers($query: String!) {
              |  suggestUsers(query: $query) {
              |    suggestions {
              |      property
              |      suggestions {
              |        text
              |      }
              |    }
              |  }
              |}
              |""".stripMargin
    GraphqlRequest(q, Map("query" -> id)).asJson.noSpaces
  }

  val getDepartmentSuccessfulCall: HttpRequestBuilder = http("getDepartment")
    .post("/api/graphql")
    .body(StringBody(getDepartmentExpression))
    .header(JwtAuthenticator.AuthHeader, jwtToken)
    .header("Content-Type", "application/json")

  val searchUsersSuccessfulCall: HttpRequestBuilder = http("searchUsers")
    .post("/api/graphql")
    .body(StringBody(searchUser))
    .header(JwtAuthenticator.AuthHeader, jwtToken)
    .header("Content-Type", "application/json")

  val suggestUsersSuccessfulCall: HttpRequestBuilder = http("suggestUsers")
    .post("/api/graphql")
    .body(StringBody(suggestUser))
    .header(JwtAuthenticator.AuthHeader, jwtToken)
    .header("Content-Type", "application/json")

  val s = scenario("UserSearchGraphqlApi")
    .repeat(100) {
      feed(departmentIdFeeder)
        .exec(getDepartmentSuccessfulCall)
        .exec(searchUsersSuccessfulCall)
        .feed(countryFeeder)
        .exec(searchUsersSuccessfulCall)
        .feed(suggestFeeder)
        .exec(suggestUsersSuccessfulCall)
    }

  setUp(
    s.inject(atOnceUsers(200))
  ).protocols(httpConf)
}
