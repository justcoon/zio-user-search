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

    def stringConcatMerge(m: Json, s: Json, sep: String): Json = {
      (m.asString, s.asString) match {
        case (Some(m), Some(s)) => Json.fromString(s"${m}${sep}${s}")
        case _ => m
      }
    }

    def customMerge(m: Json, s: Json, path: List[String]): Json = {
      path match {
        case "description" :: "info" :: Nil => stringConcatMerge(m, s, "\n")
        case _ => deepMerge(m, s, path)
      }
    }

    def deepMerge(m: Json, s: Json, path: List[String] = Nil): Json =
      (m.asObject, s.asObject) match {
        case (Some(mhs), Some(shs)) =>
          Json.fromJsonObject(
            shs.toIterable.foldLeft(mhs) { case (acc, (key, value)) =>
              mhs(key).fold(acc.add(key, value)) { r =>
                acc.add(key, customMerge(value, r, key :: path))
              }
            }
          )
        case _ => m
      }

    deepMerge(main, second)
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
