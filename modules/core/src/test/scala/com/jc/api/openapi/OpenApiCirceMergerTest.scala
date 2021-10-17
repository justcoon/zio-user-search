package com.jc.api.openapi

import org.scalatest.matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.io.Source

class OpenApiCirceMergerTest extends AnyWordSpecLike with should.Matchers {

  val y1 = Source.fromResource("UserSearchOpenApi.yaml").mkString
  val y2 = Source.fromResource("LoggingSystemOpenApi.yaml").mkString

  "OpenApiCirceMerger" must {

    "mergeYamls" in {
      val m = OpenApiCirceMerger().mergeYamls(y1, y2)

      m.isRight shouldBe true

      m.foreach { y =>
        println(y)
      }
    }

  }

}
