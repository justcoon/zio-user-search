package com.jc.user.search.api

import com.github.phisgr.gatling.grpc.Predef._
import com.github.phisgr.gatling.pb._
import com.jc.auth.JwtAuthenticator
import com.typesafe.config.ConfigFactory
import eu.timepit.refined.auto._
import io.gatling.core.Predef.{stringToExpression => _, _}
import io.gatling.core.session.Expression
import pureconfig.ConfigSource
import com.jc.user.search.api.proto._
import io.grpc.Metadata

final class UserSearchGrpcApiSimulation extends Simulation {

  import Feeders._

  val config = ConfigFactory.load
  val apiConfig = ConfigSource.fromConfig(config.getConfig("grpc-api")).loadOrThrow[HttpApiConfig]

  val grpcConf = grpc(managedChannelBuilder(name = apiConfig.address, port = apiConfig.port).usePlaintext())

  val jwtAuthHeader: Metadata.Key[String] =
    Metadata.Key.of(JwtAuthenticator.AuthHeader, Metadata.ASCII_STRING_MARSHALLER)

  val jwtToken =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJ6aW8tdXNlci1zZWFyY2giLCJzdWIiOiJ0ZXN0IiwiZXhwIjoyMjE1MDc4MTYwLCJpYXQiOjE2MTAyNzgxNjB9.CCTmZT-Iy-0bq2WnoEbr6E5hhP-VYI_YlaUUolH5y00kvBA5AYgR2BQyLSCO6QhG1i2Yv0_2Xv4w8PWoqfvcZg"

  val getDepartmentPayload: Expression[GetDepartmentReq] = GetDepartmentReq.defaultInstance
    .updateExpr(
      _.id :~ $("id")
    )

  val searchUserPayload: Expression[SearchUsersReq] = SearchUsersReq(pageSize = 10)
    .updateExpr(
      _.query :~ $("id")
    )

  val suggestUserPayload: Expression[SuggestUsersReq] = SuggestUsersReq.defaultInstance
    .updateExpr(
      _.query :~ $("id")
    )

  val getDepartmentSuccessfulCall = grpc("getDepartment")
    .rpc(com.jc.user.search.api.proto.UserSearchApiServiceGrpc.METHOD_GET_DEPARTMENT)
    .payload(getDepartmentPayload)
    .header(jwtAuthHeader)(jwtToken)

  val searchUsersSuccessfulCall = grpc("searchUsers")
    .rpc(com.jc.user.search.api.proto.UserSearchApiServiceGrpc.METHOD_SEARCH_USERS)
    .payload(searchUserPayload)

  val suggestUsersSuccessfulCall = grpc("suggestUsers")
    .rpc(com.jc.user.search.api.proto.UserSearchApiServiceGrpc.METHOD_SUGGEST_USERS)
    .payload(suggestUserPayload)

  val s =
    scenario("UserSearchGrpcApi")
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
  ).protocols(grpcConf)
}
