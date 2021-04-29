package com.jc.auth.api

import com.jc.auth.JwtAuthenticator
import org.http4s.{Header, Headers, Status}
import org.http4s.util.CaseInsensitiveString
import zio.ZIO

object HttpJwtAuth {

  private val AuthHeader = CaseInsensitiveString(JwtAuthenticator.AuthHeader)

  def authenticated(headers: Headers, authenticator: JwtAuthenticator.Service): ZIO[Any, Status, String] = {
    for {
      rawToken <- ZIO.getOrFailWith(Status.Unauthorized)(headers.get(AuthHeader))
      maybeSubject <- authenticator.authenticated(rawToken.value)
      subject <- ZIO.getOrFailWith(Status.Unauthorized)(maybeSubject)
    } yield subject
  }

}
