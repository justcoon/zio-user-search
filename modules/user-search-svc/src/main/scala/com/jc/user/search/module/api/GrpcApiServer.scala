package com.jc.user.search.module.api

import com.jc.logging.api.LoggingSystemGrpcApiHandler
import com.jc.logging.proto.ZioLoggingSystemApi.RCLoggingSystemApiService
import com.jc.user.search.api.proto.ZioUserSearchApi.RCUserSearchApiService
import com.jc.user.search.model.config.HttpApiConfig
import scalapb.zio_grpc.{Server => GrpcServer, ServerLayer => GrpcServerLayer, ServiceList => GrpcServiceList}
import io.grpc.ServerBuilder
import zio.ZLayer

import eu.timepit.refined.auto._

object GrpcApiServer {

  type ServerEnv = LoggingSystemGrpcApiHandler with UserSearchGrpcApiHandler

  def create(config: HttpApiConfig): ZLayer[ServerEnv, Throwable, GrpcServer] = {
    GrpcServerLayer.fromServiceList(
      ServerBuilder.forPort(config.port),
      GrpcServiceList.access[RCLoggingSystemApiService[Any]].access[RCUserSearchApiService[Any]])
  }
}
