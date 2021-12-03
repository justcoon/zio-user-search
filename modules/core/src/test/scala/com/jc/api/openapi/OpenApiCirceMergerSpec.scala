package com.jc.api.openapi

import zio.{Has, ZIO, ZLayer}
import zio.test.Assertion.isTrue
import zio.test.{assert, DefaultRunnableSpec}

import scala.io.Source

object OpenApiCirceMergerSpec extends DefaultRunnableSpec {

  val y1 = Source.fromResource("UserSearchOpenApi.yaml").mkString
  val y2 = Source.fromResource("LoggingSystemOpenApi.yaml").mkString

  val merger = ZLayer.succeed(OpenApiCirceMerger())

  override def spec = suite("OpenApiCirceMergerSpec")(
    testM("mergeYamls") {
      for {
        m <- mergeYamls(y1, y2)
      } yield assert(m.nonEmpty)(isTrue)
    }.provideLayer(merger)
  )

  def mergeYamls(main: String, second: String): ZIO[Has[OpenApiCirceMerger], String, String] = {
    ZIO.accessM[Has[OpenApiCirceMerger]] { env =>
      val merger = env.get
      ZIO.fromEither(merger.mergeYamls(main, second))
    }
  }
}
