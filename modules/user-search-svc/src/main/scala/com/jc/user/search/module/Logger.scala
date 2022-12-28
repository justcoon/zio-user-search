package com.jc.user.search.module

import zio.logging.backend.SLF4J
import zio.logging.logMetrics
import zio.{Runtime, ULayer}

object Logger {

  val layer: ULayer[Unit] = Runtime.removeDefaultLoggers >>> (SLF4J.slf4j ++ logMetrics)
}
