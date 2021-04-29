package com.jc.logging.aspect

import com.jc.aspect.Aspect
import zio.ZIO
import zio.logging.Logging

object LoggingAspect {

  def loggingStartEnd[R <: Logging](message: => String): Aspect[R, Nothing] = new Aspect[R, Nothing] {

    override def apply[R1 <: R, E1 >: Nothing, A](zio: ZIO[R1, E1, A]): ZIO[R1, E1, A] = {
      ZIO.environment[Logging].flatMap { logging =>
        val logger = logging.get

        logger.debug(s"$message - start") *> zio.tap { _ => logger.debug(s"$message - end") }.tapError {
          case e: Throwable => logger.error(s"$message - error: ${e.getMessage}")
          case e => logger.error(s"$message - error: $e")
        }
      }
    }
  }
}
