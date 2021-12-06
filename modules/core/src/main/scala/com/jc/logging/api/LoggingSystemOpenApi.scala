package com.jc.logging.api

import com.jc.auth.JwtAuthenticator
import com.jc.auth.api.HttpJwtAuth
import com.jc.logging.LoggingSystem
import com.jc.logging.openapi.definitions.{
  LogLevel,
  LoggerConfiguration,
  LoggerConfigurationRes,
  LoggerConfigurationsRes
}
import com.jc.logging.openapi.logging.{LoggingHandler, LoggingResource}
import com.jc.logging.openapi.logging.LoggingResource.{
  GetLoggerConfigurationResponse,
  GetLoggerConfigurationsResponse,
  SetLoggerConfigurationResponse
}
import org.http4s.{Headers, HttpRoutes}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.{UIO, ZIO}

final class LoggingSystemOpenApiHandler[R <: LoggingSystem with JwtAuthenticator]
    extends LoggingHandler[ZIO[R, Throwable, *], Headers] {
  type F[A] = ZIO[R, Throwable, A]

  def toApiLoggerConfiguration(configuration: LoggingSystem.LoggerConfiguration): LoggerConfiguration =
    LoggerConfiguration(
      configuration.name,
      LoggingSystemOpenApiHandler.logLevelMapping.toLogger(configuration.effectiveLevel),
      configuration.configuredLevel.flatMap(LoggingSystemOpenApiHandler.logLevelMapping.toLogger.get)
    )

  override def getLoggerConfiguration(respond: GetLoggerConfigurationResponse.type)(name: String)(
    extracted: Headers): ZIO[R, Throwable, GetLoggerConfigurationResponse] =
    ZIO.services[LoggingSystem.Service, JwtAuthenticator.Service].flatMap { case (loggingSystem, authenticator) =>
      HttpJwtAuth
        .authenticated(extracted, authenticator)
        .foldM(
          _ => ZIO.succeed(respond.Unauthorized),
          _ =>
            for {
              configuration <- loggingSystem.getLoggerConfiguration(name)
              levels <- LoggingSystemOpenApiHandler.getSupportedLogLevels(loggingSystem)
            } yield {
              respond.Ok(LoggerConfigurationRes(configuration.map(toApiLoggerConfiguration), levels))
            }
        )
    }

  override def getLoggerConfigurations(respond: GetLoggerConfigurationsResponse.type)()(
    extracted: Headers): ZIO[R, Throwable, GetLoggerConfigurationsResponse] =
    ZIO.services[LoggingSystem.Service, JwtAuthenticator.Service].flatMap { case (loggingSystem, authenticator) =>
      HttpJwtAuth
        .authenticated(extracted, authenticator)
        .foldM(
          _ => ZIO.succeed(respond.Unauthorized),
          _ =>
            for {
              configurations <- loggingSystem.getLoggerConfigurations
              levels <- LoggingSystemOpenApiHandler.getSupportedLogLevels(loggingSystem)
            } yield {
              respond.Ok(LoggerConfigurationsRes(configurations.map(toApiLoggerConfiguration), levels))
            }
        )
    }

  override def setLoggerConfiguration(
    respond: SetLoggerConfigurationResponse.type)(name: String, body: Option[LogLevel])(
    extracted: Headers): ZIO[R, Throwable, SetLoggerConfigurationResponse] =
    ZIO.services[LoggingSystem.Service, JwtAuthenticator.Service].flatMap { case (loggingSystem, authenticator) =>
      HttpJwtAuth
        .authenticated(extracted, authenticator)
        .foldM(
          _ => ZIO.succeed(respond.Unauthorized),
          _ =>
            for {
              res <- loggingSystem
                .setLogLevel(name, body.flatMap(LoggingSystemOpenApiHandler.logLevelMapping.fromLogger.get))
              levels <- LoggingSystemOpenApiHandler.getSupportedLogLevels(loggingSystem)
              configuration <-
                if (res) {
                  loggingSystem.getLoggerConfiguration(name)
                } else ZIO.succeed(None)
            } yield {
              respond.Ok(LoggerConfigurationRes(configuration.map(toApiLoggerConfiguration), levels))
            }
        )
    }
}

object LoggingSystemOpenApiHandler {

  private val logLevelMapping: LoggingSystem.LogLevelMapping[LogLevel] = LoggingSystem.LogLevelMapping(
    Seq(
      (LoggingSystem.LogLevel.TRACE, LogLevel.Trace),
      (LoggingSystem.LogLevel.DEBUG, LogLevel.Debug),
      (LoggingSystem.LogLevel.INFO, LogLevel.Info),
      (LoggingSystem.LogLevel.WARN, LogLevel.Warn),
      (LoggingSystem.LogLevel.ERROR, LogLevel.Error),
      (LoggingSystem.LogLevel.FATAL, LogLevel.Fatal),
      (LoggingSystem.LogLevel.OFF, LogLevel.Off)
    )
  )

  def getSupportedLogLevels(loggingSystem: LoggingSystem.Service): UIO[Seq[LogLevel]] =
    loggingSystem.getSupportedLogLevels.map { levels =>
      levels.map(LoggingSystemOpenApiHandler.logLevelMapping.toLogger).toSeq
    }

  def loggingSystemApiRoutes[E <: LoggingSystem with JwtAuthenticator with Clock with Blocking]
    : HttpRoutes[ZIO[E, Throwable, *]] = {
    import zio.interop.catz._
    new LoggingResource[ZIO[E, Throwable, *], Headers](customExtract = _ => req => req.headers)
      .routes(new LoggingSystemOpenApiHandler[E]())
  }

}
