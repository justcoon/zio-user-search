package com.jc.api.openapi

import io.circe.{yaml, Json}
import io.circe.yaml._

object OpenApiCirceMerger {

  def parseYaml(y: String): Either[String, Json] = {
    yaml.parser.parse(y).left.map(_.message)
  }

  def toYaml(j: Json): String = {
    io.circe.yaml
      .Printer(dropNullKeys = true, mappingStyle = Printer.FlowStyle.Block)
      .pretty(j)
  }

  def merge(main: Json, second: Json): Json = {
    second.deepMerge(main)
  }

  def mergeYamls(main: String, others: Iterable[String]): Either[String, String] = {
    val m = parseYaml(main)
    val oa = others.map(parseYaml).foldLeft(m) { (p1, p2) =>
      for {
        m <- p1
        s <- p2
      } yield merge(m, s)
    }
    oa.map(toYaml)
  }

  def mergeYamls(main: String, second: String): Either[String, String] = {
    for {
      m <- parseYaml(main)
      s <- parseYaml(second)
    } yield {
      val j = merge(m, s)
      toYaml(j)
    }
  }

}
