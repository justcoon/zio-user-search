package com.jc.api.openapi

import io.swagger.v3.core.util.Yaml
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.io.Source

class OpenApiMergerTest extends AnyWordSpecLike with should.Matchers with BeforeAndAfterAll {

  val y1 = Source.fromResource("UserSearchOpenApi.yaml").mkString
  val y2 = Source.fromResource("LoggingSystemOpenApi.yaml").mkString

  "OpenApiMerger" must {

    "mergeOpenAPIs" in {
      val p1 = OpenApiReader.read(y1)
      val p2 = OpenApiReader.read(y2)

      p1.isRight shouldBe true
      p2.isRight shouldBe true

      val m = for {
        o1 <- p1
        o2 <- p2
      } yield OpenApiMerger.mergeOpenAPIs(o1, o2)

      m.isRight shouldBe true

      m.foreach { o =>
        val y = Yaml.pretty(o)
        println(y)
      }
    }

    "mergeYamls" in {
      val m = OpenApiMerger.mergeYamls(y1, y2 :: Nil)

      m.isRight shouldBe true

      m.foreach { o =>
        val y = Yaml.pretty(o)
        println(y)
      }
    }

  }

}
