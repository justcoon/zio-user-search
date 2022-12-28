package com.jc.user.search.module

import zio._
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus.{prometheusLayer, publisherLayer, PrometheusPublisher}
import zio.metrics.jvm.{
  ClassLoading,
  DefaultJvmMetrics,
  GarbageCollector,
  MemoryAllocation,
  MemoryPools,
  Standard,
  Thread
}

object Metrics {

  type JvmMetrics = ClassLoading with GarbageCollector with MemoryAllocation with MemoryPools with Standard with Thread

  val layer: ZLayer[Any, Throwable, PrometheusPublisher with JvmMetrics] =
    ZLayer.make[PrometheusPublisher with JvmMetrics](
      ZLayer.succeed(MetricsConfig(5.seconds)),
      publisherLayer,
      prometheusLayer,
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live
    )
}
