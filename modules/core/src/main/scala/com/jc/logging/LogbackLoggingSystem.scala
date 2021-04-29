package com.jc.logging

import ch.qos.logback.classic
import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.Logger
import org.slf4j.impl.StaticLoggerBinder
import zio.{UIO, ZIO, ZLayer}

import scala.util.Try

object LogbackLoggingSystem {

  val logLevelMapping: LoggingSystem.LogLevelMapping[Level] = LoggingSystem.LogLevelMapping(
    Seq(
      (LoggingSystem.LogLevel.TRACE, Level.TRACE),
      (LoggingSystem.LogLevel.TRACE, Level.ALL),
      (LoggingSystem.LogLevel.DEBUG, Level.DEBUG),
      (LoggingSystem.LogLevel.INFO, Level.INFO),
      (LoggingSystem.LogLevel.WARN, Level.WARN),
      (LoggingSystem.LogLevel.ERROR, Level.ERROR),
      (LoggingSystem.LogLevel.FATAL, Level.ERROR),
      (LoggingSystem.LogLevel.OFF, Level.OFF)
    )
  )

  def toLoggerConfiguration(logger: classic.Logger): LoggingSystem.LoggerConfiguration = {
    val effectiveLevel = logLevelMapping.fromLogger.getOrElse(logger.getEffectiveLevel, LoggingSystem.LogLevel.OFF)
    val configuredLevel = logLevelMapping.fromLogger.get(logger.getLevel)
    val name = if (Option(logger.getName).forall(_.isBlank)) Logger.ROOT_LOGGER_NAME else logger.getName
    LoggingSystem.LoggerConfiguration(name, effectiveLevel, configuredLevel)
  }

  /** Logback [[LoggingSystem]] Service
    *
    * @param loggerContext logback logger context
    */
  final class LogbackLoggingSystemService(loggerContext: LoggerContext) extends LoggingSystem.Service {

    implicit private val ordering = LoggingSystem.loggerConfigurationOrdering(Logger.ROOT_LOGGER_NAME)

    private def getLogger(name: String): Option[classic.Logger] = {
      val loggerName = if (name.isBlank) Logger.ROOT_LOGGER_NAME else name
      // just existing logger
      Option(loggerContext.exists(loggerName))
    }

    override val getSupportedLogLevels: UIO[Set[LoggingSystem.LogLevel]] =
      ZIO.succeed {
        LogbackLoggingSystem.logLevelMapping.toLogger.keySet
      }

    override def getLoggerConfiguration(name: String): UIO[Option[LoggingSystem.LoggerConfiguration]] =
      ZIO.succeed {
        getLogger(name).map(LogbackLoggingSystem.toLoggerConfiguration)
      }

    override def getLoggerConfigurations: UIO[List[LoggingSystem.LoggerConfiguration]] = {
      import scala.jdk.CollectionConverters._
      ZIO.succeed {
        loggerContext.getLoggerList.asScala.toList.map(LogbackLoggingSystem.toLoggerConfiguration).sorted
      }
    }

    override def setLogLevel(name: String, level: Option[LoggingSystem.LogLevel]): UIO[Boolean] =
      ZIO.succeed {
        val maybeLogger = getLogger(name)
        maybeLogger match {
          case Some(logger) =>
            val loggerLevel = level.flatMap(LogbackLoggingSystem.logLevelMapping.toLogger.get).orNull
            Try(logger.setLevel(loggerLevel)).isSuccess
          case None => false
        }
      }
  }

  def create(): ZLayer[Any, Throwable, LoggingSystem] = {
    StaticLoggerBinder.getSingleton.getLoggerFactory match {
      case loggerContext: LoggerContext => ZLayer.succeed(new LogbackLoggingSystemService(loggerContext))
      case _ => ZLayer.fail(new RuntimeException("LoggerFactory is not a Logback LoggerContext"))
    }
  }
}
