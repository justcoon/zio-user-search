package com.jc.api.openapi

import io.circe.{yaml, Json}
import io.circe.yaml._

class OpenApiCirceMerger(customMerge: Map[List[String], OpenApiCirceMerger.MergeFn]) {

  def merge(main: Json, second: Json): Json = {

    def deepMerge(m: Json, s: Json, path: List[String] = Nil): Json =
      (m.asObject, s.asObject) match {
        case (Some(mhs), Some(shs)) =>
          Json.fromJsonObject(
            shs.toIterable.foldLeft(mhs) { case (acc, (key, value)) =>
              mhs(key).fold(acc.add(key, value)) { r =>
                val valuePath = key :: path
                val mergedValue = customMerge.get(valuePath) match {
                  case Some(fn) => fn(value, r)
                  case None => deepMerge(value, r, valuePath)
                }

                acc.add(key, mergedValue)
              }
            }
          )
        case _ => m
      }

    deepMerge(main, second)
  }

  def parseYaml(y: String): Either[String, Json] = {
    yaml.parser.parse(y).left.map(_.message)
  }

  def toYaml(j: Json): String = {
    io.circe.yaml
      .Printer(dropNullKeys = true, mappingStyle = Printer.FlowStyle.Block)
      .pretty(j)
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

object OpenApiCirceMerger {

  def apply(customMerge: Map[List[String], MergeFn] = customMergeDefault): OpenApiCirceMerger = {
    new OpenApiCirceMerger(customMerge)
  }

  type MergeFn = (Json, Json) => Json

  def stringConcatMerge(separator: String): MergeFn = { (m, s) =>
    (m.asString, s.asString) match {
      case (Some(m), Some(s)) => Json.fromString(s"${m}${separator}${s}")
      case _ => m
    }
  }

  final val stringConcatMergeNewLine: MergeFn = stringConcatMerge(separator = System.lineSeparator)

  final val customMergeDefault: Map[List[String], MergeFn] = Map(
    ("description" :: "info" :: Nil) -> stringConcatMergeNewLine)
}
