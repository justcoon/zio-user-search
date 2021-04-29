package com.jc.auth.api

import com.jc.auth.JwtAuthenticator
import io.grpc.Status
import scalapb.zio_grpc.RequestContext
import zio.{Has, ZIO}

object GrpcJwtAuth {

  import io.grpc.Metadata

  private val AuthHeader: Metadata.Key[String] =
    Metadata.Key.of(JwtAuthenticator.AuthHeader, Metadata.ASCII_STRING_MARSHALLER)

  def authenticated(authenticator: JwtAuthenticator.Service): ZIO[Has[RequestContext], Status, String] = {
    for {
      ctx <- ZIO.service[scalapb.zio_grpc.RequestContext]
      maybeHeader <- ctx.metadata.get(AuthHeader)
      rawToken <- ZIO.getOrFailWith(io.grpc.Status.UNAUTHENTICATED)(maybeHeader)
      maybeSubject <- authenticator.authenticated(rawToken)
      subject <- ZIO.getOrFailWith(io.grpc.Status.UNAUTHENTICATED)(maybeSubject)
    } yield subject
  }
}
