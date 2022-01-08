package com.jc.auth.api

import com.jc.auth.JwtAuthenticator
import io.grpc.Status
import scalapb.zio_grpc.RequestContext
import zio.{Has, ZIO}

object GrpcJwtAuth {

  import io.grpc.Metadata

  private val AuthHeader: Metadata.Key[String] =
    Metadata.Key.of(JwtAuthenticator.AuthHeader, Metadata.ASCII_STRING_MARSHALLER)

  def authenticated[R <: Has[RequestContext]](authenticator: JwtAuthenticator.Service): ZIO[R, Status, String] = {
    for {
      ctx <- ZIO.service[scalapb.zio_grpc.RequestContext]
      maybeHeader <- ctx.metadata.get(AuthHeader)
      rawToken <- ZIO.getOrFailWith(io.grpc.Status.UNAUTHENTICATED)(maybeHeader)
      maybeSubject <- authenticator.authenticated(JwtAuthenticator.sanitizeBearerAuthToken(rawToken))
      subject <- ZIO.getOrFailWith(io.grpc.Status.UNAUTHENTICATED)(maybeSubject)
    } yield subject
  }

  def authenticated[R <: Has[RequestContext] with JwtAuthenticator]: ZIO[R, Status, String] = {
    ZIO.accessM[R] { env =>
      authenticated(env.get[JwtAuthenticator.Service])
    }
  }
}
