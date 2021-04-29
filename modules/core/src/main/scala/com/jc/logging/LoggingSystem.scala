package com.jc.logging

import zio.UIO

object LoggingSystem {

  /** logging system component
    */
  trait Service {

    /** get log levels supported by logging system
      */
    def getSupportedLogLevels: UIO[Set[LogLevel]]

    /** get logger configuration by name
      */
    def getLoggerConfiguration(name: String): UIO[Option[LoggingSystem.LoggerConfiguration]]

    /** get all loggers configurations
      */
    def getLoggerConfigurations: UIO[List[LoggingSystem.LoggerConfiguration]]

    /** set [[level]] for logger with [[name]]
      *
      * @param name logger name
      * @param level if non empty, given level will be set for logger, otherwise custom level configuration will be cleared
      *
      * @return [[true]] if logger configuration was successfully updated
      */
    def setLogLevel(name: String, level: Option[LogLevel]): UIO[Boolean]
  }

  sealed trait LogLevel extends Product

  object LogLevel {
    final case object TRACE extends LogLevel

    final case object DEBUG extends LogLevel

    final case object INFO extends LogLevel

    final case object WARN extends LogLevel

    final case object ERROR extends LogLevel

    final case object FATAL extends LogLevel

    final case object OFF extends LogLevel
  }

  /** log levels mapping
    *
    * @param mappings first levels in sequence are priority (one [[LogLevel]] may be mapped to multiple [[L]])
    * @tparam L log level type of specific logger
    */
  final case class LogLevelMapping[L](mappings: Seq[(LogLevel, L)]) {

    val toLogger: Map[LogLevel, L] = mappings.groupBy(_._1).map { case (k, vs) =>
      k -> vs.head._2
    }

    val fromLogger: Map[L, LogLevel] = mappings.groupBy(_._2).map { case (k, vs) =>
      k -> vs.head._1
    }
  }

  /** logger configuration
    *
    * @param name logger name
    * @param effectiveLevel effective [[LogLevel]]
    * @param configuredLevel configured [[LogLevel]] - if is not empty, it means, that logger got custom/specific configuration
    */
  final case class LoggerConfiguration(name: String, effectiveLevel: LogLevel, configuredLevel: Option[LogLevel])

  /** create [[Ordering]] for [[LoggerConfiguration]]
    *
    * @param rootLoggerName root logger name - priority in case of ordering
    */
  def loggerConfigurationOrdering(rootLoggerName: String): Ordering[LoggerConfiguration] =
    new Ordering[LoggerConfiguration] {

      override def compare(o1: LoggerConfiguration, o2: LoggerConfiguration): Int =
        if (rootLoggerName == o1.name) -1
        else if (rootLoggerName == o2.name) 1
        else o1.name.compareTo(o2.name)
    }
}
