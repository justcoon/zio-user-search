package com.jc.api.openapi

import zio.{ZIO, ZLayer}
import zio.test.Assertion.isTrue
import zio.test.assert

import scala.io.Source
import zio.test.ZIOSpecDefault

object OpenApiCirceMergerSpec extends ZIOSpecDefault {

  val y1 = Source.fromResource("UserSearchOpenApi.yaml").mkString
  val y2 = Source.fromResource("LoggingSystemOpenApi.yaml").mkString

  val merger = ZLayer.succeed(OpenApiCirceMerger())

  override def spec = suite("OpenApiCirceMergerSpec")(
    test("mergeYamls") {
      for {
        m <- mergeYamls(y1, y2)
      } yield assert(m.nonEmpty)(isTrue)
    }.provideLayer(merger)
  )

  def mergeYamls(main: String, second: String): ZIO[OpenApiCirceMerger, String, String] = {
    ZIO.serviceWithZIO[OpenApiCirceMerger] { merger =>
      ZIO.fromEither(merger.mergeYamls(main, second))
    }
  }
}
